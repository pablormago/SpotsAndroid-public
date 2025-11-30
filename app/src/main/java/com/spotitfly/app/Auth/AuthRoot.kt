package com.spotitfly.app.auth

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.LoginScreen
import com.spotitfly.app.RegisterScreen
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


enum class AuthStep { Login, Register, VerifyEmail }

@Composable
fun AuthRoot(
    onAuthenticated: () -> Unit
) {
    val vm: AuthViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(AuthStep.Login) }

    val ctx = LocalContext.current

    // Acción que queremos ejecutar tras resolver el permiso (entrar en la app)
    var pendingPostLoginAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Launcher para pedir POST_NOTIFICATIONS en Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Actualiza meta + devices según el resultado
        vm.handleNotificationsAfterLogin(granted)
        // Continúa el flujo normal (entrar a la app)
        pendingPostLoginAction?.invoke()
        pendingPostLoginAction = null
    }

    // -- Estado para reset de contraseña
    //var showReset by remember { mutableStateOf(false) }
    //var resetEmail by remember { mutableStateOf("") }

    // -- Tick para forzar re-chequeo de verificación
    var refreshTick by remember { mutableIntStateOf(0) }

    // Función común: pedir permiso de notis (si hace falta), inicializar en Firestore y entrar
    fun runPostLoginNotificationsAndEnter() {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyGranted = ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (alreadyGranted) {
                    // Ya tenía permiso → inicializamos y entramos
                    vm.handleNotificationsAfterLogin(true)
                    onAuthenticated()
                } else {
                    // Guardamos la acción de entrada y lanzamos el diálogo del sistema
                    pendingPostLoginAction = onAuthenticated
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // < Android 13 → no hay popup del sistema
                vm.handleNotificationsAfterLogin(true)
                onAuthenticated()
            }
        }
    }

    when (step) {
        AuthStep.Login -> {
            // Tomamos el estado actual una sola vez para evitar múltiples collectAsState()
            val l = vm.login.collectAsState().value

            LoginScreen(
                email = l.email,
                pass = l.pass,
                loading = l.loading,
                error = l.error,
                onEmailChange = vm::setLoginEmail,
                onPassChange = vm::setLoginPass,
                onForgot = { }, // ← No abrimos ningún diálogo aquí
                onCreateAccount = { step = AuthStep.Register },
                onSignIn = {
                    vm.signIn {
                        scope.launch {
                            val verified = vm.isEmailVerified()
                            if (!verified) {
                                step = AuthStep.VerifyEmail
                                return@launch
                            }

                            // Email ya verificado → usamos la función común
                            runPostLoginNotificationsAndEnter()
                        }
                    }
                },
                // ⬇️ AÑADE ESTA LÍNEA
                onResetPassword = { email -> vm.sendPasswordReset(email.trim()) { _, _ -> } }
            )



        }
        AuthStep.Register -> {
            val r = vm.register.collectAsState().value
            RegisterScreen(
                username = r.username,
                email = r.email,
                pass = r.pass,
                accepted = r.accepted,
                usernameStatus = r.usernameStatus,
                loading = r.loading,
                error = r.error,
                onUsernameChange = vm::setRegUsername,
                onEmailChange = vm::setRegEmail,
                onPassChange = vm::setRegPass,
                onAcceptedChange = vm::setRegAccepted,
                onShowRules = { /* abrir sheet normas */ },
                onAlreadyHaveAccount = { step = AuthStep.Login },
                onRegister = { vm.register { step = AuthStep.VerifyEmail } },
// ⬇️ AÑADE ESTA LÍNEA
                onPhotoSelected = { _ -> }
            )
        }
        AuthStep.VerifyEmail -> {
            // Chequea verificación y, si es true, pide notis y entra al mapa
            LaunchedEffect(refreshTick) {
                val verified = vm.isEmailVerified()
                if (verified) {
                    runPostLoginNotificationsAndEnter()
                }
            }
            VerifyEmailScreen(
                onRefresh = { refreshTick++ }
            )
        }

    }

}

@Composable
private fun VerifyEmailScreen(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Te hemos enviado un email de verificación.")
        Spacer(Modifier.height(8.dp))
        Text("Cuando verifiques, vuelve a esta pantalla.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRefresh) { Text("Ya lo verifiqué") }
    }
}
