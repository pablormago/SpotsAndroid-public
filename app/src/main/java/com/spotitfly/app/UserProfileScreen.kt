package com.spotitfly.app

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spotitfly.app.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import androidx.compose.foundation.border
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.tasks.await
import android.content.Context
import com.spotitfly.app.UserPrefs
import com.spotitfly.app.data.context.overlays.prefs.OverlayPrefs
import com.spotitfly.app.data.local.AppDatabase
import com.spotitfly.app.data.chat.ChatsLocalCache
import com.spotitfly.app.data.chat.ChatLinkPreviewCache
import java.io.File


@Composable
fun UserProfileScreen(onBack: () -> Unit) {
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

    var profileUrl by remember { mutableStateOf<String?>(null) }
    var bust by remember { mutableStateOf<String?>(null) }

    var snack by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Notificaciones (paridad iOS: users/{uid}/meta/notifications)
    var notifEnabled by remember { mutableStateOf(false) }
    var notifMessages by remember { mutableStateOf(true) }
    var notifComments by remember { mutableStateOf(true) }

    // Tap admin (5 taps abre biometría)
    var tapCount by remember { mutableStateOf(0) }

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

    // ---- Carga inicial (paridad iOS) ----
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val doc = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get().awaitOrNull()

        username = doc?.getString("username").orEmpty()
        profileUrl = doc?.getString("profileImageUrl")
        bust = doc?.getString("avatarBustToken")

        // notificaciones
        val notif = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("meta").document("notifications")
            .get().awaitOrNull()
        notifEnabled = notif?.getBoolean("enabled") ?: false
        notifMessages = notif?.getBoolean("messages") ?: true
        notifComments = notif?.getBoolean("comments") ?: true
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
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(
                Modifier
                    .fillMaxSize()
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
                        .size(108.dp)
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
                            val effUrl = profileUrl?.let {
                                if (bust.isNullOrBlank()) it else "$it?bust=$bust"
                            }

                            if (!effUrl.isNullOrBlank() && uid != null) {
                                val local = avatarLocalFile(ctxLocal, uid)
                                val model = ImageRequest.Builder(ctxLocal)
                                    .data(if (local.exists()) local else effUrl)
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
                                        saveDrawableToFile(success.result.drawable, local)
                                    }
                                )
                            } else {
                                // Iniciales si no hay foto
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

                Spacer(Modifier.height(14.dp))


                // Acciones de foto
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { takePhoto.launch(null) }) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cámara")
                    }
                    FilledTonalButton(onClick = { pickPhoto.launch("image/*") }) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Galería")
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
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Quitar foto")
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
                                usernameOk = ok || text.trim().equals(auth.currentUser?.displayName ?: "", ignoreCase = true)
                                usernameBusy = false
                            }
                        },
                        trailingIcon = {
                            when {
                                usernameBusy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                usernameOk == true -> Icon(Icons.Outlined.Check, contentDescription = null, tint = Color(0xFF2E7D32))
                                usernameOk == false -> Icon(Icons.Outlined.Close, contentDescription = null, tint = Color(0xFFC62828))
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
                        }
                    ) { Text("Guardar nombre") }
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
                        Switch(checked = notifEnabled, onCheckedChange = { v ->
                            notifEnabled = v
                            saveNotif(uid, notifEnabled, notifMessages, notifComments)
                        })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mensajes")
                        Switch(checked = notifMessages, onCheckedChange = { v ->
                            notifMessages = v
                            saveNotif(uid, notifEnabled, notifMessages, notifComments)
                        })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Comentarios")
                        Switch(checked = notifComments, onCheckedChange = { v ->
                            notifComments = v
                            saveNotif(uid, notifEnabled, notifMessages, notifComments)
                        })
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Navegación (paridad iOS; enlazaremos cuando estén esas pantallas)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { /* TODO: Mis Spots */ }) { Text("Mis Spots") }
                    OutlinedButton(onClick = { /* TODO: Normas comunidad */ }) { Text("Normas de la comunidad") }
                }

                Spacer(Modifier.height(22.dp))

                // Logout
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // 1) Limpiar TODO lo local/caché
                            clearAllLocalData(ctx)   // o 'context' si tu variable se llama así

                            // 2) Cerrar sesión en Firebase
                            FirebaseAuth.getInstance().signOut()

                            // 3) Volver a la pantalla anterior
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                ) { Text("Cerrar sesión") }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Snackbar simple
            LaunchedEffect(snack) {
                if (!snack.isNullOrBlank()) {
                    // Compose M3 simple: mostramos un Snack "manual"
                    // Aquí usamos un diálogo simple por brevedad
                }
            }
        }
    }
}

// ---- Helpers ----

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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
    val prompt = BiometricPrompt(owner as androidx.fragment.app.FragmentActivity,
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
    try { await() } catch (_: Exception) { null }
