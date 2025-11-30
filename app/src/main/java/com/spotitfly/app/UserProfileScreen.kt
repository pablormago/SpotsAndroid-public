package com.spotitfly.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
//import androidx.compose.material3.Profile
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spotitfly.app.auth.AuthRepository
import com.spotitfly.app.data.chat.ChatLinkPreviewCache
import com.spotitfly.app.data.chat.ChatsLocalCache
import com.spotitfly.app.data.context.overlays.prefs.OverlayPrefs
import com.spotitfly.app.data.local.AppDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.firestore.Query
import androidx.compose.material3.AlertDialog
import com.google.firebase.functions.FirebaseFunctions


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onOpenMySpots: () -> Unit,
    onOpenGuidelines: () -> Unit
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid

    // ---- Estado ----
    var username by remember { mutableStateOf("") }
    var usernameBusy by remember { mutableStateOf(false) }
    var usernameOk by remember { mutableStateOf<Boolean?>(null) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }


    var profileUrl by remember { mutableStateOf<String?>(null) }
    var bust by remember { mutableStateOf<String?>(null) }

    var snack by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Notificaciones (paridad iOS: users/{uid}/meta/notifications)
    var notifEnabled by remember { mutableStateOf(false) }
    var notifMessages by remember { mutableStateOf(true) }
    var notifComments by remember { mutableStateOf(true) }

    var showMySpots by remember { mutableStateOf(false) }

    // Número de operador (solo local, como en iOS)
    var operatorNumber by remember { mutableStateOf("") }

    // Tap admin (5 taps abre biometría)
    var tapCount by remember { mutableStateOf(0) }

    // Navegador preferido (solo local: Google Maps / Waze)
    var preferredNavApp by remember { mutableStateOf(PreferredNavApp.GOOGLE_MAPS) }

    // Sheet para cambiar foto
    var showPhotoSheet by remember { mutableStateOf(false) }

    // ---- Launchers ----
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && uid != null) {
            scope.launch {
                try {
                    loading = true
                    val url = repo.uploadProfilePhoto(uid, uri)
                    repo.updateUserPhotoUrl(uid, url)
                    profileUrl = url
                    bust = System.currentTimeMillis().toString()
                    snack = "Avatar actualizado"
                } catch (e: Exception) {
                    snack = "Error al subir imagen"
                } finally {
                    loading = false
                }
            }
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null && uid != null) {
            scope.launch {
                try {
                    loading = true
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    val data = baos.toByteArray()
                    val url = repo.uploadProfilePhotoBytes(uid, data)
                    repo.updateUserPhotoUrl(uid, url)
                    profileUrl = url
                    bust = System.currentTimeMillis().toString()
                    snack = "Avatar actualizado"
                } catch (e: Exception) {
                    snack = "Error al subir imagen"
                } finally {
                    loading = false
                }
            }
        }
    }

    // Permiso de notificaciones (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permiso concedido → registramos el device para push
            if (uid != null) {
                scope.launch {
                    try {
                        repo.registerDeviceForPushIfPossible()
                    } catch (_: Exception) {
                        // No rompemos la UI si falla
                    }
                }
            }
        } else {
            // Permiso denegado → apagamos el master y guardamos prefs como OFF
            notifEnabled = false
            saveNotif(uid, notifEnabled, notifMessages, notifComments)
            snack = "Permiso de notificaciones denegado"
        }
    }

    // Permiso de cámara (runtime)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Una vez concedido, lanzamos la cámara
            takePhoto.launch(null)
        } else {
            snack = "Necesitas permitir el acceso a la cámara para hacer una foto"
        }
    }


    // ---- Carga inicial (paridad iOS) ----
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val doc = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .awaitOrNull()

        username = doc?.getString("username").orEmpty()
        profileUrl = doc?.getString("profileImageUrl")
        bust = doc?.getString("avatarBustToken")

        // notificaciones
        val notif = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("meta").document("notifications")
            .get()
            .awaitOrNull()
        notifEnabled = notif?.getBoolean("enabled") ?: false
        notifMessages = notif?.getBoolean("messages") ?: true
        notifComments = notif?.getBoolean("comments") ?: true

        // Número de operador local (UserDefaults en iOS → SharedPreferences aquí)
        val opPrefs = ctx.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        operatorNumber = opPrefs.getString("operatorNumber", "") ?: ""

        // Navegador preferido local (Google Maps / Waze)
        val navPrefs = ctx.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        val rawNav = navPrefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
        preferredNavApp = when (rawNav) {
            "waze" -> PreferredNavApp.WAZE
            else -> PreferredNavApp.GOOGLE_MAPS
        }
    }



    // ---- UI ----
    Scaffold(
        topBar = {
            ProfileTopBar(
                title = "Perfil",
                onBack = onBack,
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() }) { data ->
                Snackbar { Text(data.visuals.message) }
            }
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar + aro degradado + 5 taps → biometría
                val ringBrush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF00D4FF),
                        Color(0xFF7C4DFF),
                        Color(0xFF00D4FF)
                    )
                )

                // 🔧 Escalas para controlar grosor de los aros en el avatar grande
                val outerRingScale = 32f / 36f   // subir hacia 1f → aro degradado más fino
                val innerContentScale = 26f / 30f // subir hacia 1f → aro blanco más fino

                Box(
                    Modifier
                        .size(120.dp)
                        .clickable {
                            tapCount += 1
                            if (tapCount % 5 == 0) askBiometric(ctx, owner) {
                                snack = "Desbloqueo admin OK"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Círculo exterior degradado
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(brush = ringBrush)
                    )

                    // Anillo blanco intermedio
                    Box(
                        modifier = Modifier
                            .fillMaxSize(outerRingScale)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        // Círculo interior con foto / iniciales
                        Box(
                            modifier = Modifier
                                .fillMaxSize(innerContentScale)
                                .clip(CircleShape)
                                .background(Color(0xFFECECEC)),
                            contentAlignment = Alignment.Center
                        ) {
                            val ctxLocal = LocalContext.current
                            val uidLocal = uid
                            val local = uidLocal?.let { avatarLocalFile(ctxLocal, it) }
                            val hasLocal = local?.exists() == true

                            val effUrl = profileUrl?.let {
                                if (bust.isNullOrBlank()) it else "$it?bust=$bust"
                            }

                            when {
                                // 1️⃣ Primero: si hay fichero local, usamos eso y listo
                                hasLocal -> {
                                    AsyncImage(
                                        model = local,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // 2️⃣ Si no hay local pero sí URL, cargamos remoto y cacheamos
                                !effUrl.isNullOrBlank() && uidLocal != null -> {
                                    val model = ImageRequest.Builder(ctxLocal)
                                        .data(effUrl)
                                        .apply {
                                            if (!bust.isNullOrBlank()) {
                                                setParameter(
                                                    "bust",
                                                    bust!!,
                                                    memoryCacheKey = bust
                                                )
                                            }
                                        }
                                        .build()

                                    AsyncImage(
                                        model = model,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        onSuccess = { success ->
                                            if (local != null) {
                                                saveDrawableToFile(success.result.drawable, local)
                                            }
                                        }
                                    )
                                }

                                // 3️⃣ Sin nada → iniciales
                                else -> {
                                    val initials = nameInitials(
                                        username.ifBlank {
                                            auth.currentUser?.displayName.orEmpty()
                                        }
                                    )

                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Acciones de foto
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { showPhotoSheet = true }) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = IOSLinkBlue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cambiar foto", color = IOSLinkBlue)
                    }
                    TextButton(onClick = {
                        if (uid != null) {
                            scope.launch {
                                try {
                                    loading = true
                                    repo.removeProfilePhoto(uid)
                                    profileUrl = null
                                    bust = System.currentTimeMillis().toString()
                                    snack = "Avatar eliminado"
                                } catch (e: Exception) {
                                    snack = "No se pudo eliminar"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Color(0xFFC62828)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Quitar foto", color = Color(0xFFC62828))
                    }
                }

                // Sheet para elegir cámara o galería
                if (showPhotoSheet) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showPhotoSheet = false },
                        sheetState = sheetState
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    showPhotoSheet = false

                                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                                        ctx,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasCameraPermission) {
                                        // Ya tenemos permiso → lanzar directamente
                                        takePhoto.launch(null)
                                    } else {
                                        // Pedir permiso → si acepta, en el callback se llama a takePhoto.launch(null)
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    tint = IOSLinkBlue
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Usar cámara", color = IOSLinkBlue)
                            }


                            TextButton(
                                onClick = {
                                    showPhotoSheet = false
                                    pickPhoto.launch("image/*")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    tint = IOSLinkBlue
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Elegir de galería", color = IOSLinkBlue)
                            }

                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Username + checker
                Column(Modifier.fillMaxWidth()) {
                    Text("Nombre de usuario", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { text ->
                            username = text
                            usernameOk = null
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                usernameBusy = true
                                delay(550)
                                val ok = repo.isUsernameAvailable(text)
                                // Si el username actual coincide con el doc, cuenta como OK
                                usernameOk =
                                    ok || text.trim().equals(
                                        auth.currentUser?.displayName ?: "",
                                        ignoreCase = true
                                    )
                                usernameBusy = false
                            }
                        },
                        trailingIcon = {
                            when {
                                usernameBusy -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )

                                usernameOk == true -> Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32)
                                )

                                usernameOk == false -> Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = (usernameOk == true),
                        onClick = {
                            scope.launch {
                                try {
                                    loading = true
                                    repo.updateUsername(username)
                                    snack = "Nombre actualizado"
                                } catch (e: Exception) {
                                    snack = "No se pudo actualizar el nombre"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IOSLinkBlue,
                            disabledContainerColor = Color(0xFFE5E5EA),
                            disabledContentColor = Color(0xFFB0B0B5)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar nombre", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Número de operador (solo local)
                Column(Modifier.fillMaxWidth()) {
                    Text("Número de operador", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = operatorNumber,
                        onValueChange = { text ->
                            val value = text.uppercase()
                            operatorNumber = value
                            // Guardar inmediatamente en SharedPreferences (solo local)
                            val prefs = ctx.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("operatorNumber", value.trim())
                                .apply()
                        },
                        placeholder = { Text("Ej.: ESP-XXXX-YYYY") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Este dato se guarda únicamente en tu dispositivo. No se sube a la nube ni a Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(Modifier.height(22.dp))

                // Notificaciones
                Column(Modifier.fillMaxWidth()) {

                Text("Notificaciones", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar notificaciones")
                        Switch(
                            checked = notifEnabled,
                            onCheckedChange = { v ->
                                notifEnabled = v

                                if (v) {
                                    // Encender master → pedir permiso si hace falta y registrar device
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        val granted =
                                            ContextCompat.checkSelfPermission(
                                                ctx,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED

                                        if (granted) {
                                            // Ya tenemos permiso → registrar directamente
                                            if (uid != null) {
                                                scope.launch {
                                                    try {
                                                        repo.registerDeviceForPushIfPossible()
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        } else {
                                            // Lanzar diálogo de permiso
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        // < Android 13 → no hay permiso de notificaciones
                                        if (uid != null) {
                                            scope.launch {
                                                try {
                                                    repo.registerDeviceForPushIfPossible()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }

                                    // Guardamos prefs con enabled = true
                                    saveNotif(
                                        uid,
                                        notifEnabled,
                                        notifMessages,
                                        notifComments
                                    )
                                } else {
                                    // Apagar master → solo guardamos prefs; no desregistramos device (igual que iOS)
                                    saveNotif(
                                        uid,
                                        notifEnabled,
                                        notifMessages,
                                        notifComments
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IOSLinkBlue
                            )
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mensajes")
                        Switch(
                            checked = notifMessages,
                            onCheckedChange = { v ->
                                notifMessages = v
                                saveNotif(
                                    uid,
                                    notifEnabled,
                                    notifMessages,
                                    notifComments
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IOSLinkBlue
                            )
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Comentarios")
                        Switch(
                            checked = notifComments,
                            onCheckedChange = { v ->
                                notifComments = v
                                saveNotif(
                                    uid,
                                    notifEnabled,
                                    notifMessages,
                                    notifComments
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IOSLinkBlue
                            )
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Navegación (app predeterminada)
                Column(Modifier.fillMaxWidth()) {
                    Text("Navegación", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    // Google Maps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                preferredNavApp = PreferredNavApp.GOOGLE_MAPS
                                val prefs = ctx.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
                                prefs.edit()
                                    .putString("preferredNavApp", "google_maps")
                                    .apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Google Maps", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Usar Google Maps para las rutas",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        if (preferredNavApp == PreferredNavApp.GOOGLE_MAPS) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Seleccionado",
                                tint = IOSLinkBlue
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Waze
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                preferredNavApp = PreferredNavApp.WAZE
                                val prefs = ctx.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
                                prefs.edit()
                                    .putString("preferredNavApp", "waze")
                                    .apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Waze", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Usar Waze para las rutas",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        if (preferredNavApp == PreferredNavApp.WAZE) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Seleccionado",
                                tint = IOSLinkBlue
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Navegación (paridad iOS)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onOpenMySpots() }) {
                        Text("Mis Spots")
                    }

                    OutlinedButton(onClick = { onOpenGuidelines() }) {
                        Text("Normas de la comunidad")
                    }
                }


                Spacer(Modifier.height(22.dp))
                // Privacidad: eliminar cuenta
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Privacidad",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !deletingAccount,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x1FFF3B30), // rojo suave de fondo
                            contentColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eliminar cuenta")
                    }
                }

                Spacer(Modifier.height(22.dp))
                // Logout
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // 1) Limpiar TODO lo local/caché
                            clearAllLocalData(ctx)

                            // 2) Cerrar sesión en Firebase
                            FirebaseAuth.getInstance().signOut()

                            // 3) Volver a la pantalla anterior
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFC62828)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cerrar sesión") }
            }

            // Confirmación de eliminación de cuenta
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = {
                        if (!deletingAccount) {
                            showDeleteConfirm = false
                        }
                    },
                    title = { Text("Eliminar cuenta") },
                    text = {
                        Text(
                            "Se enviará una solicitud para eliminar tu cuenta y tus datos. " +
                                    "Este proceso no se puede deshacer. ¿Quieres continuar?"
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (deletingAccount) return@TextButton
                                scope.launch {
                                    val functions = FirebaseFunctions.getInstance("europe-west1")
                                    deletingAccount = true
                                    try {
                                        functions
                                            .getHttpsCallable("requestAccountDeletion")
                                            .call(hashMapOf<String, Any>())
                                            .await()

                                        snack =
                                            "Solicitud enviada. Eliminaremos tu cuenta y datos."
                                        showDeleteConfirm = false

                                        // Misma lógica que logout: limpiar todo y salir
                                        clearAllLocalData(ctx)
                                        FirebaseAuth.getInstance().signOut()
                                        onBack()
                                    } catch (e: Exception) {
                                        snack =
                                            "No se pudo solicitar la eliminación. Inténtalo de nuevo."
                                    } finally {
                                        deletingAccount = false
                                    }
                                }
                            }
                        ) {
                            Text("Eliminar definitivamente", color = Color(0xFFD32F2F))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                if (!deletingAccount) {
                                    showDeleteConfirm = false
                                }
                            }
                        ) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }


            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Snackbar simple (placeholder)
            LaunchedEffect(snack) {
                if (!snack.isNullOrBlank()) {
                    // Aquí ya gestionas cómo mostrarlo (puedes usar un SnackbarHost real en el Scaffold)
                }
            }
        }
    }

}

// ---- Helpers ----

private val IOSLinkBlue = Color(0xFF007AFF)

enum class PreferredNavApp {
    GOOGLE_MAPS,
    WAZE
}

/**
 * Helper global para usar desde otras pantallas (SpotDetail, mapa, etc.)
 * Devuelve la opción elegida en Perfil.
 */
fun getPreferredNavApp(context: Context): PreferredNavApp {
    val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    val raw = prefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
    return when (raw) {
        "waze" -> PreferredNavApp.WAZE
        else -> PreferredNavApp.GOOGLE_MAPS
    }
}


private suspend fun clearAllLocalData(context: Context) {
    // 1) Borrar prefs de usuario (username, etc.)
    UserPrefs.clear(context)

    // 2) Borrar prefs de overlays (restricciones, urbano, infra, medio…)
    OverlayPrefs(context).clearAll()

    // 3) Borrar TODOS los SharedPreferences que usa la app para estado “de sesión”
    val prefsNames = listOf(
        "map_prefs",      // tipo de mapa
        "ratings_cache",  // medias/contadores de ratings
        "ratings_local",  // votos locales por usuario
        "spots_sync"      // marcas de sync en SpotsRepository
    )
    for (name in prefsNames) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // 4) Caché persistente de chats (nombres, avatares, fotos de grupo)
    runCatching {
        ChatsLocalCache.clearAll(context)
    }

    // 5) Caché JSON de previews de enlaces de chat
    runCatching {
        ChatLinkPreviewCache.clear(context)
    }

    // 6) Borrar TODA la base de datos Room (spots, chats, mensajes, participantes)
    runCatching {
        val db = AppDatabase.get(context.applicationContext)
        db.clearAllTables()
        // Por si acaso, borramos también el archivo físico de la base de datos
        context.deleteDatabase("spots.db")
    }

    // 7) Borrar ficheros locales (avatars, thumbs de chats, thumbs de spots, etc.)
    runCatching {
        fun deleteDirIfExists(dir: File?) {
            if (dir != null && dir.exists()) {
                dir.deleteRecursively()
            }
        }

        // Subcarpetas conocidas en filesDir
        val filesDir = context.filesDir
        if (filesDir != null && filesDir.exists()) {
            listOf("avatars", "chat_thumbnails", "spotImages", "link_previews").forEach { name ->
                deleteDirIfExists(File(filesDir, name))
            }
        }

        // Cache (incluye link_previews + temporales de cámara)
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            // Carpeta específica de previews
            deleteDirIfExists(File(cacheDir, "link_previews"))
            // Simular “Borrar caché”: vaciar todo cacheDir
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

@Composable
private fun ProfileTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shadowElevation = 2.dp, color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun nameInitials(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val first = parts.getOrNull(0)?.firstOrNull()?.uppercase() ?: ""
    val second = parts.getOrNull(1)?.firstOrNull()?.uppercase() ?: ""
    val initials = (first + second)
    return if (initials.isNotBlank()) initials else "?"
}

private fun saveNotif(uid: String?, enabled: Boolean, messages: Boolean, comments: Boolean) {
    if (uid == null) return
    val db = FirebaseFirestore.getInstance()
    db.collection("users").document(uid)
        .collection("meta").document("notifications")
        .set(
            mapOf(
                "enabled" to enabled,
                "messages" to messages,
                "comments" to comments,
                "lang" to Locale.getDefault().language,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
}

private fun askBiometric(
    ctx: android.content.Context,
    owner: androidx.lifecycle.LifecycleOwner,
    onSuccess: () -> Unit
) {
    val can = BiometricManager.from(ctx).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    if (can != BiometricManager.BIOMETRIC_SUCCESS) return

    val executor = ContextCompat.getMainExecutor(ctx)
    val prompt = BiometricPrompt(
        owner as FragmentActivity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloqueo administrador")
        .setSubtitle("Confirma con biometría")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(info)
}

// awaitOrNull helper (como en otros archivos)
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    try {
        await()
    } catch (_: Exception) {
        null
    }



