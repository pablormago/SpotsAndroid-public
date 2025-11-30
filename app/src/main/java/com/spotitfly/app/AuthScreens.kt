package com.spotitfly.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.delay
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.core.content.FileProvider
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.spotitfly.app.auth.UsernameStatus

private val IOSLinkBlue = Color(0xFF007AFF)   // azul enlace iOS
private val IOSFieldBg  = Color(0xFFF2F2F7)   // gris iOS SystemGray6

// ----------------------------------------------------
// Login "simple" (estado local) - se puede seguir usando
// ----------------------------------------------------
@Composable
fun LoginScreen(
    onForgot: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onEmailChange: (String) -> Unit = {},
    onPassChange: (String) -> Unit = {},
    onResetPassword: (String) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var pass  by remember { mutableStateOf("") }
    val canSignIn = email.isNotBlank() && pass.isNotBlank()
    var showReset by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 28.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Image(
                painter = painterResource(id = R.drawable.spotitfly_logo),
                contentDescription = "Spotitfly logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(5.dp))
            Image(
                painter = painterResource(id = R.drawable.spotitfly_wordmark),
                contentDescription = "FPV Spotitfly",
                modifier = Modifier
                    .height(180.dp)
                    .wrapContentWidth()
            )
            Spacer(Modifier.height(16.dp))

            PillTextField(
                value = email,
                onValueChange = { email = it; onEmailChange(it) },
                placeholder = "Correo electrónico",
                isEmail = true,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(14.dp))
            PillTextField(
                value = pass,
                onValueChange = { pass = it; onPassChange(it) },
                placeholder = "Contraseña",
                isPassword = true,
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    resetEmail = email
                    showReset = true
                    onForgot()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
            ) {
                Text("¿Has olvidado tu contraseña?")
            }

            // 👇 un poco menos bruto que 100.dp
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onSignIn,
                enabled = canSignIn,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IOSLinkBlue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE5E5EA),
                    disabledContentColor = Color(0xFFB0B0B5)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) { Text("Iniciar sesión", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCreateAccount) {
                Text(
                    "Crear cuenta",
                    color = IOSLinkBlue,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }


    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Recuperar contraseña") },
            text = {
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    singleLine = true,
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("tu@email.com") }
                )
            },
            confirmButton = {
                val enabled = resetEmail.contains("@") && resetEmail.contains(".")
                TextButton(
                    onClick = {
                        onResetPassword(resetEmail.trim())
                        showReset = false
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReset = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Cancelar") }
            }
        )
    }
}

// Enum local para la versión "simple" de registro
private enum class AuthUsernameStatus { Idle, Checking, Free, Taken, Invalid }

// ----------------------------------------------------
// Register "simple" (estado local) - mantiene checker opcional
// ----------------------------------------------------
@Composable
fun RegisterScreen(
    onShowRules: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onRegister: () -> Unit,
    checkUsernameAvailability: (suspend (String) -> Boolean)? = null
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }

    var usernameStatus by remember { mutableStateOf(AuthUsernameStatus.Idle) }

    // Debounce 1 s + validación básica (solo en la versión "simple")
    LaunchedEffect(username, checkUsernameAvailability) {
        val candidate = username.trim()
        if (candidate.isEmpty()) {
            usernameStatus = AuthUsernameStatus.Idle
            return@LaunchedEffect
        }
        val valid = Regex("^[A-Za-z0-9_.]{3,}$").matches(candidate)
        if (!valid) {
            usernameStatus = AuthUsernameStatus.Invalid
            return@LaunchedEffect
        }
        if (checkUsernameAvailability == null) {
            usernameStatus = AuthUsernameStatus.Idle
            return@LaunchedEffect
        }
        usernameStatus = AuthUsernameStatus.Checking
        delay(1000)
        val free = try { checkUsernameAvailability(candidate) } catch (_: Exception) { false }
        usernameStatus = if (free) AuthUsernameStatus.Free else AuthUsernameStatus.Taken
    }

    val canRegister = accepted &&
            username.isNotBlank() && email.isNotBlank() && pass.isNotBlank() &&
            (checkUsernameAvailability == null || usernameStatus == AuthUsernameStatus.Free)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Image(
            painter = painterResource(id = R.drawable.spotitfly_logo),
            contentDescription = "Spotitfly logo",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(id = R.drawable.spotitfly_wordmark),
            contentDescription = "FPV Spotitfly",
            modifier = Modifier
                .height(180.dp)
                .wrapContentWidth()
        )

        // Botón de foto circular (placeholder)
        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFFEAEAEA))
                .clickable { /* TODO: elegir foto */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = "Elegir foto",
                tint = IOSLinkBlue,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.height(22.dp))
        PillTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = "Nombre de usuario",
            imeAction = ImeAction.Next,
            trailing = {
                when (usernameStatus) {
                    AuthUsernameStatus.Idle -> {}
                    AuthUsernameStatus.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = IOSLinkBlue
                        )

                    }
                    AuthUsernameStatus.Free -> {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = "Disponible", tint = Color(0xFF2ECC71))
                    }
                    AuthUsernameStatus.Taken, AuthUsernameStatus.Invalid -> {
                        Icon(Icons.Outlined.Cancel, contentDescription = "No disponible", tint = Color(0xFFE74C3C))
                    }
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        PillTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Correo electrónico",
            isEmail = true,
            imeAction = ImeAction.Next
        )
        Spacer(Modifier.height(14.dp))
        PillTextField(
            value = pass,
            onValueChange = { pass = it },
            placeholder = "Contraseña",
            isPassword = true,
            imeAction = ImeAction.Done
        )
        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "He leído y acepto las Normas de la comunidad",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = accepted,
                onCheckedChange = { accepted = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = IOSLinkBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFB0B0B5)
                )
            )

        }

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable { onShowRules() }
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = IOSLinkBlue
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Ver Normas de la comunidad",
                color = IOSLinkBlue,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onRegister,
            enabled = canRegister,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IOSLinkBlue,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE5E5EA),
                disabledContentColor = Color(0xFFB0B0B5)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Registrarse", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onAlreadyHaveAccount) {
            Text(
                "Ya tengo cuenta",
                color = IOSLinkBlue,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -----------------------------------------------
// TextField tipo "pill" (común para todas las pantallas)
// -----------------------------------------------
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    isEmail: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)
    val focusManager = LocalFocusManager.current

    val keyboardOptions = when {
        isPassword -> KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        )
        isEmail -> KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Email,
            imeAction = imeAction
        )
        else -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction
        )
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Transparent),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = IOSFieldBg,
            focusedContainerColor = IOSFieldBg,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            cursorColor = IOSLinkBlue,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedPlaceholderColor = Color(0xFF9AA0A6),
            unfocusedPlaceholderColor = Color(0xFF9AA0A6)
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        ),
        trailingIcon = trailing
    )
}

// =========================
// Simple screens (contenido real controlado por VM)
// =========================
@Composable
fun LoginScreenContent(
    onForgot: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onEmailChange: (String) -> Unit = {},
    onPassChange: (String) -> Unit = {},
    onResetPassword: (String) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var pass  by remember { mutableStateOf("") }
    val canSignIn = email.isNotBlank() && pass.isNotBlank()
    var showReset by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 28.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Image(
                painter = painterResource(id = R.drawable.spotitfly_logo),
                contentDescription = "Spotitfly logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(5.dp))
            Image(
                painter = painterResource(id = R.drawable.spotitfly_wordmark),
                contentDescription = "FPV Spotitfly",
                modifier = Modifier.height(180.dp).wrapContentWidth()
            )
            Spacer(Modifier.height(16.dp))

            PillTextField(
                value = email,
                onValueChange = { email = it; onEmailChange(it) },
                placeholder = "Correo electrónico",
                isEmail = true,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(14.dp))
            PillTextField(
                value = pass,
                onValueChange = { pass = it; onPassChange(it) },
                placeholder = "Contraseña",
                isPassword = true,
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    resetEmail = email
                    showReset = true
                    onForgot()
                    Toast.makeText(ctx, "Te enviaremos un email para recuperar tu contraseña", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
            ) { Text("¿Has olvidado tu contraseña?") }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onSignIn,
                enabled = canSignIn,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IOSLinkBlue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE5E5EA),
                    disabledContentColor = Color(0xFFB0B0B5)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Iniciar sesión", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCreateAccount) {
                Text("Crear cuenta", color = IOSLinkBlue, style = MaterialTheme.typography.titleMedium)
            }
        }
    }


    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Recuperar contraseña") },
            text = {
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    singleLine = true,
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("tu@email.com") }
                )
            },
            confirmButton = {
                val enabled = resetEmail.contains("@") && resetEmail.contains(".")
                TextButton(
                    onClick = {
                        onResetPassword(resetEmail.trim())
                        showReset = false
                        Toast.makeText(ctx, "Email de recuperación enviado", Toast.LENGTH_LONG).show()
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReset = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreenContent(
    username: String,
    email: String,
    pass: String,
    accepted: Boolean,
    usernameStatus: UsernameStatus,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onAcceptedChange: (Boolean) -> Unit,
    onShowRules: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onRegister: () -> Unit,
    onPhotoSelected: (Uri?) -> Unit = {}
) {
    val ctx = LocalContext.current

    // Picker estado (solo para previsualizar la foto elegida)
    var showPhotoSheet by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) { previewUri = uri; onPhotoSelected(uri) }
    }

    fun tempImageUri(): Uri {
        val dir = ctx.cacheDir
        val f = kotlin.io.path.createTempFile(prefix = "profile_", suffix = ".jpg", directory = dir.toPath()).toFile()
        f.deleteOnExit()
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val uri = cameraUri
            if (uri != null) {
                previewUri = uri
                onPhotoSelected(uri)
            }
        }
    }

    // Habilitación del botón según estado del VM
    val canRegister =
        accepted &&
                username.isNotBlank() && email.isNotBlank() && pass.isNotBlank() &&
                (usernameStatus == UsernameStatus.Free)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 28.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Image(
                painter = painterResource(id = R.drawable.spotitfly_logo),
                contentDescription = "Spotitfly logo",
                modifier = Modifier.size(80.dp)
            )
            Image(
                painter = painterResource(id = R.drawable.spotitfly_wordmark),
                contentDescription = "FPV Spotitfly",
                modifier = Modifier.height(140.dp).wrapContentWidth()
            )

            Box(
                Modifier
                    .size(100.dp)
                    .offset(y = (-30).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAEAEA))
                    .clickable { showPhotoSheet = true },
                contentAlignment = Alignment.Center
            ) {
                if (previewUri != null) {
                    AsyncImage(
                        model = previewUri,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = "Elegir foto",
                        tint = IOSLinkBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            PillTextField(
                value = username,
                onValueChange = onUsernameChange,
                placeholder = "Nombre de usuario",
                imeAction = ImeAction.Next,
                trailing = {
                    when (usernameStatus) {
                        UsernameStatus.Idle -> {}
                        UsernameStatus.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = IOSLinkBlue
                            )

                        }
                        UsernameStatus.Free -> {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Disponible", tint = Color(0xFF2ECC71))
                        }
                        UsernameStatus.Taken, UsernameStatus.Invalid -> {
                            Icon(Icons.Outlined.Cancel, contentDescription = "No disponible", tint = Color(0xFFE74C3C))
                        }
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            PillTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "Correo electrónico",
                isEmail = true,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(14.dp))
            PillTextField(
                value = pass,
                onValueChange = onPassChange,
                placeholder = "Contraseña",
                isPassword = true,
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "He leído y acepto las Normas de la comunidad",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = accepted,
                    onCheckedChange = onAcceptedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IOSLinkBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFB0B0B5)
                    )
                )

            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start).clickable { onShowRules() }
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = IOSLinkBlue)
                Spacer(Modifier.width(8.dp))
                Text("Ver Normas de la comunidad", color = IOSLinkBlue, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onRegister,
            enabled = canRegister,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IOSLinkBlue,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE5E5EA),
                disabledContentColor = Color(0xFFB0B0B5)
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Registrarse", fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAlreadyHaveAccount) {
            Text("Ya tengo cuenta", color = IOSLinkBlue, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showPhotoSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = sheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                TextButton(
                    onClick = {
                        showPhotoSheet = false
                        cameraUri = tempImageUri()
                        val target = cameraUri
                        if (target != null) takePicture.launch(target)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Usar cámara") }

                TextButton(
                    onClick = {
                        showPhotoSheet = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = IOSLinkBlue)
                ) { Text("Elegir de galería") }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// =========================
// Wrappers conectados al VM
// =========================

@Composable
fun LoginScreen(
    email: String,
    pass: String,
    loading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onForgot: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onResetPassword: (String) -> Unit
) {
    val ctx = LocalContext.current

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            val e = error.lowercase()
            val msg =
                if (e.contains("invalid") || e.contains("wrong") || e.contains("credential") ||
                    e.contains("password") || e.contains("auth"))
                    "Email o contraseña incorrectos"
                else error
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    // UI contenido
    LoginScreenContent(
        onForgot = onForgot,
        onCreateAccount = onCreateAccount,
        onSignIn = onSignIn,
        onEmailChange = onEmailChange,
        onPassChange = onPassChange,
        onResetPassword = onResetPassword
    )
}

@Composable
fun RegisterScreen(
    username: String,
    email: String,
    pass: String,
    accepted: Boolean,
    usernameStatus: Any?,
    loading: Boolean,
    error: String?,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onAcceptedChange: (Boolean) -> Unit,
    onShowRules: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onRegister: () -> Unit,
    onPhotoSelected: (Uri?) -> Unit,
    checkUsernameAvailability: (suspend (String) -> Boolean)? = null
) {
    RegisterScreenContent(
        username = username,
        email = email,
        pass = pass,
        accepted = accepted,
        usernameStatus = (usernameStatus as? UsernameStatus) ?: UsernameStatus.Idle,
        onUsernameChange = onUsernameChange,
        onEmailChange = onEmailChange,
        onPassChange = onPassChange,
        onAcceptedChange = onAcceptedChange,
        onShowRules = onShowRules,
        onAlreadyHaveAccount = onAlreadyHaveAccount,
        onRegister = onRegister,
        onPhotoSelected = onPhotoSelected
    )
}
