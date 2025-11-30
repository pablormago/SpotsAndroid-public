package com.spotitfly.app.ui.chats

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.spotitfly.app.data.chat.Chat
import com.spotitfly.app.data.chat.GroupApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.GroupAdd



/**
 * Pantalla de creación de grupo:
 * - Selector de avatar (imagen de grupo).
 * - Nombre del grupo.
 * - Búsqueda y selección múltiple de usuarios.
 * - Crea el chat vía Cloud Function y sube la foto a Storage.
 */
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: (Chat) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid.orEmpty()

    var groupName by rememberSaveable { mutableStateOf("") }
    var searchText by rememberSaveable { mutableStateOf("") }

    var allUsers by remember { mutableStateOf<List<SelectableUser>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(true) }

    var selectedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isCreating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Avatar de grupo (solo galería por ahora, para no liarla con FileProvider/cámara)
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            avatarUri = uri
        }
    }

    LaunchedEffect(Unit) {
        if (currentUid.isBlank()) {
            isLoadingUsers = false
            return@LaunchedEffect
        }

        val db = FirebaseFirestore.getInstance()
        try {
            val snapshot = db.collection("users")
                .limit(200)
                .get()
                .await()

            val loaded = snapshot.documents.mapNotNull { doc ->
                val uid = ((doc.getString("uid") ?: doc.id).trim())
                if (uid.isBlank() || uid == currentUid) return@mapNotNull null

                val rawUsername = doc.getString("username")?.trim()
                val rawDisplayName = doc.getString("displayName")?.trim()
                val rawNickname = doc.getString("nickname")?.trim()
                val rawName = doc.getString("name")?.trim()
                val rawEmail = doc.getString("email")?.trim()

                // "username lógico": primero username real, luego displayName, etc.
                val username = when {
                    !rawUsername.isNullOrBlank() -> rawUsername
                    !rawDisplayName.isNullOrBlank() -> rawDisplayName
                    !rawNickname.isNullOrBlank() -> rawNickname
                    !rawName.isNullOrBlank() -> rawName
                    !rawEmail.isNullOrBlank() -> rawEmail
                    else -> uid
                }

                val photoUrl = doc.getString("profileImageUrl")

                SelectableUser(
                    id = uid,
                    username = username,
                    photoUrl = photoUrl
                )
            }.sortedBy { it.username.lowercase() }

            allUsers = loaded
        } catch (e: Exception) {
            errorText = e.message ?: "Error al cargar usuarios"
        } finally {
            isLoadingUsers = false
        }
    }



    // Filtro por texto de búsqueda (en memoria)
    val filteredUsers = remember(allUsers, searchText) {
        val q = searchText.trim().lowercase()
        if (q.isBlank()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.username.lowercase().contains(q)
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Cabecera simple (el AppBar viene de ChatsHomeScreen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Cancelar")
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // Avatar + nombre de grupo (paridad con iOS: avatar encima del nombre)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupAvatarSelector(
                avatarUri = avatarUri,
                onPickImage = { pickImageLauncher.launch("image/*") },
                onRemoveImage = { avatarUri = null }
            )

            Spacer(Modifier.width(16.dp))

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Nombre del grupo") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(Modifier.height(12.dp))

        // Búsqueda inline
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Buscar miembros…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        if (isLoadingUsers) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    MemberRow(
                        user = user,
                        selected = selectedUserIds.contains(user.id),
                        onToggle = {
                            selectedUserIds = if (selectedUserIds.contains(user.id)) {
                                selectedUserIds - user.id
                            } else {
                                selectedUserIds + user.id
                            }
                        }
                    )
                    Divider()
                }
            }
        }

        if (!errorText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorText.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (currentUid.isBlank()) return@Button
                if (groupName.trim().isEmpty() || selectedUserIds.isEmpty()) return@Button

                scope.launch {
                    if (isCreating) return@launch
                    isCreating = true
                    errorText = null

                    try {
                        val members = selectedUserIds.toList()
                        // 1) Crear el chat de grupo
                        val chatId = GroupApi.createGroup(
                            name = groupName.trim(),
                            memberIds = members
                        )

                        // 2) Subir avatar (opcional) y setear photoURL en Firestore
                        val avatar = avatarUri
                        if (avatar != null) {
                            val photoUrl = try {
                                uploadGroupAvatar(
                                    context = context,
                                    chatId = chatId,
                                    uri = avatar
                                )
                            } catch (e: Exception) {
                                errorText = "Error al subir la foto del grupo: ${e.message}"
                                ""
                            }

                            if (photoUrl.isNotBlank() && photoUrl != "null") {
                                GroupApi.setGroupPhoto(chatId, photoUrl)
                            }
                        }


                        // 3) Construir Chat local para empujar al detalle
                        val participantIds = (members + currentUid)
                            .distinct()
                            .sorted()

                        val chat = Chat(
                            id = chatId,
                            participantIds = participantIds,
                            isGroup = true,
                            displayName = groupName.trim()
                        )

                        onGroupCreated(chat)
                    } catch (e: Exception) {
                        errorText = e.message ?: "Error al crear grupo"
                    } finally {
                        isCreating = false
                    }
                }
            },
            enabled = !isCreating &&
                    groupName.trim().isNotEmpty() &&
                    selectedUserIds.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Creando grupo…")
            } else {
                Text("Crear grupo")
            }
        }
    }
}

/**
 * Pequeño modelo local (equivalente a SelectableUser en iOS):
 * uid + nombre mostrado + foto opcional.
 */
private data class SelectableUser(
    val id: String,
    val username: String,
    val photoUrl: String?
)

@Composable
private fun GroupAvatarSelector(
    avatarUri: Uri?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    val ring = Brush.sweepGradient(
        listOf(
            Color(0xFF00D4FF),
            Color(0xFF7C4DFF),
            Color(0xFF00D4FF)
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clickable { onPickImage() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Transparent, CircleShape)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Transparent, CircleShape)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent, CircleShape)
                            .padding(1.dp)
                    ) {
                        // Aro degradado tipo iOS
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent, CircleShape)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    width = 2.dp,
                                    brush = ring,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Avatar de grupo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.GroupAdd,
                        contentDescription = "Seleccionar imagen de grupo",
                        tint = Color(0xFF546E7A)
                    )
                }
            }
        }

        if (avatarUri != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRemoveImage) {
                Text(
                    text = "Quitar foto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    user: SelectableUser,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (si hay foto)
        if (!user.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCFD8DC)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (selected) "✓" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


/**
 * Sube el avatar del grupo a Storage en `chats/{chatId}/avatar/v{ts}.jpg`
 * y devuelve la URL con `?bust={ts}` (o `&bust=` si ya hay query).
 */
private suspend fun uploadGroupAvatar(
    context: Context,
    chatId: String,
    uri: Uri
): String {
    // ⏱ mismo concepto que iOS: timestamp para versionar + bust
    val ts = System.currentTimeMillis()

    // 👤 uid del usuario actual (igual que en iOS, para cumplir reglas)
    val uid = FirebaseAuth.getInstance().currentUser?.uid
        ?: throw IllegalStateException("Sesión inválida")

    // 🛣 RUTA 1:1 CON iOS → chats/{chatId}/{uid}/avatar_v{ts}.jpg
    val path = "chats/$chatId/$uid/avatar_v$ts.jpg"

    val storage = FirebaseStorage.getInstance()
    val ref = storage.reference.child(path)

    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"

    val metadata = StorageMetadata.Builder()
        .setContentType(mimeType)
        .build()

    // Subimos por stream (como buen ciudadano) en vez de cargar todos los bytes en memoria
    resolver.openInputStream(uri)?.use { input ->
        ref.putStream(input, metadata).await()
    } ?: throw IllegalStateException("No se pudo abrir la imagen seleccionada")

    val url = ref.downloadUrl.await().toString()

    // Si la URL ya trae query (?alt=media&token=...), usamos &bust= igual que iOS
    val separator = if (url.contains("?")) "&" else "?"
    return "$url${separator}bust=$ts"
}


