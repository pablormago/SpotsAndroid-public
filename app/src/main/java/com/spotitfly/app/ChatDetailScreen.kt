package com.spotitfly.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.spotitfly.app.data.chat.Chat
import com.spotitfly.app.data.chat.ChatFileKind
import com.spotitfly.app.data.chat.Message
import com.spotitfly.app.ui.chats.ChatDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.LinearProgressIndicator
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import com.spotitfly.app.data.chat.ChatReadOverrides
import com.spotitfly.app.data.chat.ChatsLocalCache
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImagePainter
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.spotitfly.app.data.chat.GroupApi
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.PersonRemove
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import kotlinx.coroutines.delay
import com.spotitfly.app.data.report.ReportService
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import com.spotitfly.app.data.linkpreview.UrlPreviewEntry
import com.spotitfly.app.data.linkpreview.UrlPreviewRepository
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.painterResource



private enum class MessageGroupPosition {
    SOLO,
    TOP,
    MIDDLE,
    BOTTOM
}

private enum class LinkHostKind {
    DEFAULT,
    YOUTUBE,
    INSTAGRAM
}

private const val SUPPORT_BOT_ID = "26CSxWS7R7eZlrvXUV1qJFyL7Oc2"

// Paleta de colores para nombres en grupos (clon de senderPalette de iOS)
private val senderPalette = listOf(
        Color(0xFF007AFF), // systemBlue
        Color(0xFFFF3B30), // systemRed
        Color(0xFF34C759), // systemGreen
        Color(0xFFFF9500), // systemOrange
        Color(0xFF5856D6), // systemIndigo
        Color(0xFFFF2D55), // systemPink
        Color(0xFFAF52DE), // systemPurple
        Color(0xFF5AC8FA)  // systemTeal
            )

private fun colorForSenderId(senderId: String): Color {
        var hash = 0L
        for (ch in senderId) {
                hash = ((hash shl 5) + hash) + ch.code.toLong()  // djb2
            }
        val index = (abs(hash) % senderPalette.size).toInt()
        return senderPalette[index]
    }

private val IOSActionBlue = Color(0xFF007AFF)
@Composable
fun ChatDetailScreen(
    chat: Chat,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {

    // ViewModel escopado al chat: evita que veas mensajes del chat A al abrir el B
    val scopedViewModel: ChatDetailViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            key = "chat-${chat.id}"
        )

    val messages by scopedViewModel.messages.collectAsState()
    val auth = FirebaseAuth.getInstance()
    val isReady by scopedViewModel.isReady.collectAsState()
    val userStatus by scopedViewModel.userStatus.collectAsState()


    // Gating de UI para evitar parpadeo de mensajes de otro chat
    var uiIsReady by remember(chat.id) { mutableStateOf(false) }

    val myUid = auth.currentUser?.uid
    var inputText by rememberSaveable { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<Message?>(null) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<Message?>(null) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }
    var forwardSource by remember { mutableStateOf<Message?>(null) }
    var forwardTargetId by remember { mutableStateOf("") }
    var showForwardDialog by remember { mutableStateOf(false) }
    var showAttachDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val isGroupChat = chat.isGroup || chat.participantIds.size > 2

    // Overrides locales para nombre y avatar de grupo (renombrar/cambiar foto en vivo)
    var groupTitleOverride by remember(chat.id) { mutableStateOf<String?>(null) }
    var groupAvatarOverride by remember(chat.id) { mutableStateOf<String?>(null) }
    var groupInviteUrlOverride by remember(chat.id) { mutableStateOf<String?>(null) }

    var groupMembers by remember(chat.id) {

    mutableStateOf<Map<String, ChatMemberUiModel>>(
            if (isGroupChat) {
                buildInitialGroupMembersFromCache(chat, myUid, context)
            } else {
                emptyMap()
            }
        )
    }

    LaunchedEffect(chat.id, isGroupChat) {
        if (!isGroupChat) {
            groupMembers = emptyMap()
            return@LaunchedEffect
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val ids = chat.participantIds

            val fetched = ids.mapNotNull { uid ->
                try {
                    val doc = db.collection("users")
                        .document(uid)
                        .get()
                        .await()

                    if (!doc.exists()) return@mapNotNull null

                    val name = doc.getString("displayName")
                        ?: doc.getString("username")
                        ?: doc.getString("email")
                        ?: ChatsLocalCache.getUserName(context, uid)
                        ?: "Usuario"

                    val avatarFromDoc = doc.getString("profileImageUrl")
                    val avatar = avatarFromDoc ?: ChatsLocalCache.getUserAvatar(context, uid)

                    // Persistimos en caché para próximas aperturas
                    ChatsLocalCache.saveUserName(context, uid, name)
                    if (!avatar.isNullOrBlank()) {
                        ChatsLocalCache.saveUserAvatar(context, uid, avatar)
                    }

                    ChatMemberUiModel(
                        uid = uid,
                        displayName = name,
                        avatarUrl = avatar
                    )
                } catch (_: Exception) {
                    null
                }
            }

            // Refrescamos el mapa (local + red)
            groupMembers = fetched.associateBy { it.uid }
        } catch (_: Exception) {
            // Si falla la red, mantenemos lo que haya en caché
            // (no vaciamos para no perder los avatares)
        }
    }

    // Prefetch agresivo de avatares de grupo:
    // en cuanto tengamos groupMembers (de caché o de red),
    // calentamos el imageLoader de Coil con todas las URLs.
    LaunchedEffect(chat.id, groupMembers) {
        if (!isGroupChat) return@LaunchedEffect

        try {
            val imageLoader = context.imageLoader

            val urls = groupMembers.values
                .mapNotNull { it.avatarUrl }
                .distinct()

            urls.forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()

                // No muestra nada en pantalla, solo calienta cache.
                imageLoader.enqueue(request)
            }
        } catch (_: Exception) {
            // Si falla, no rompemos nada: simplemente no hay prefetch.
        }
    }

    // Al entrar al chat: empezar a escuchar mensajes, estado de usuario
    // y marcar como leído (equivalente a iOS cuando abres el hilo).
    // Suscribimos el viewModel al chat cuando cambia el chatId
    LaunchedEffect(chat.id) {
        // Reset de estado de UI al cambiar de chat para evitar arrastres/parpadeos
        uiIsReady = false
        inputText = ""
        replyTarget = null
        editingTarget = null
        pendingDelete = null
        forwardSource = null
        showForwardDialog = false
        showAttachDialog = false

        // Mini-override local inmediatamente (equivalente a iOS)
        val uid = myUid
        if (uid != null) {
            ChatReadOverrides.markAsReadLocally(
                chatId = chat.id,
                uid = uid,
                lastMessageAtMillis = chat.updatedAtMillis
            )
        }

        // Y además el flujo normal: listener + write al servidor
        scopedViewModel.start(chat.id)
        scopedViewModel.observeUserStatus(chat)
        scopedViewModel.markChatAsRead(chat.id)
    }

    // Cuando el VM declara que está listo para este chat → liberamos la UI
    LaunchedEffect(chat.id, isReady) {
        if (isReady) {
            uiIsReady = true
        }
    }

    // Al ir a background / cerrar / hacer back:
    // marcamos de nuevo como leído y paramos el VM.
    DisposableEffect(lifecycleOwner, chat.id) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Refuerzo del mini-override local al salir del chat
                val uid = myUid
                if (uid != null) {
                    ChatReadOverrides.markAsReadLocally(
                        chatId = chat.id,
                        uid = uid,
                        lastMessageAtMillis = chat.updatedAtMillis
                    )
                }

                // Y write normal al servidor
                scopedViewModel.markChatAsRead(chat.id)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)

            // Último refuerzo por si el onStop no se ha disparado
            val uid = myUid
            if (uid != null) {
                ChatReadOverrides.markAsReadLocally(
                    chatId = chat.id,
                    uid = uid,
                    lastMessageAtMillis = chat.updatedAtMillis
                )
            }
            viewModel.markChatAsRead(chat.id)
        }
    }

    val listState = rememberLazyListState()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scopedViewModel.onMediaPicked(
                uri = uri,
                replyToMessageId = replyTarget?.id
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onDocumentPicked(
                uri = uri,
                replyToMessageId = replyTarget?.id
            )
        }
    }

    // Auto-scroll al final cuando cambie el número de mensajes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Cálculo de primer mensaje no leído y total no leídos
    val lastReadMillisForMe = remember(chat.id, myUid) {
        if (myUid == null) 0L else chat.lastReadMillis?.get(myUid) ?: 0L
    }

    val (firstUnreadIndex, unreadCount) = remember(messages, myUid, lastReadMillisForMe) {
        var firstIndex = -1
        var count = 0
        if (myUid != null && messages.isNotEmpty()) {
            messages.forEachIndexed { index, msg ->
                if (msg.senderId != myUid && msg.createdAtMillis > lastReadMillisForMe) {
                    if (firstIndex == -1) firstIndex = index
                    count++
                }
            }
        }
        firstIndex to count
    }

    // Fondo estilo iOS: imagen distinta para claro / oscuro
    val isDark = isSystemInDarkTheme()
    val backgroundPainter = if (isDark) {
        painterResource(R.drawable.chat_background_dark)
    } else {
        painterResource(R.drawable.chat_background_light)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // fallback por si falla el drawable
    ) {
        Image(
            painter = backgroundPainter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (isDark) 1f else 0.9f // ligero tint en claro
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {

        ChatDetailTopBar(
            chat = chat,
            userStatus = userStatus,
            overrideTitle = if (isGroupChat) groupTitleOverride else null,
            onBack = onBack,
            onHeaderClick = {
                val isGroup = chat.isGroup || chat.participantIds.size > 2
                if (isGroup) {
                    showGroupSheet = true
                } else {
                    showProfileSheet = true
                }
            },
            overrideAvatarUrl = if (isGroupChat) groupAvatarOverride else null
        )


        if (!uiIsReady) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cargando mensajes…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay mensajes en este chat.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                state = listState
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                    val isMine = myUid != null && message.senderId == myUid
                    val isSystem = message.type == "system"

                    // Separador de fecha
                    val showDateSeparator = index == 0 || !isSameDay(
                        messages[index - 1].createdAtMillis,
                        message.createdAtMillis
                    )
                    if (showDateSeparator) {
                        DateSeparator(message.createdAtMillis)
                    }

                    // Separador de "n mensajes no leídos"
                    if (firstUnreadIndex == index && unreadCount > 0) {
                        UnreadSeparator(unreadCount)
                    }

                    // Mensaje al que estamos respondiendo, si existe
                    val replyMessage = message.replyToMessageId?.let { replyId ->
                        messages.find { it.id == replyId }
                    }

                        if (isSystem) {
                            // Para mensajes de sistema (ej. "salió del grupo") intentamos
                            // resolver el nombre del usuario a partir de groupMembers
                            val actorName = if (isGroupChat) {
                                groupMembers[message.senderId]?.displayName
                            } else {
                                null
                            }

                            SystemMessageRow(
                                message = message,
                                actorDisplayName = actorName
                            )
                        } else {
                            val groupPosition = calculateGroupPosition(messages, index)

                            val senderInfo = if (isGroupChat && !isMine) {
                                groupMembers[message.senderId]
                            } else {
                                null
                            }

                            MessageRow(
                                message = message,
                                isMine = isMine,
                                groupPosition = groupPosition,
                                sender = senderInfo,
                                replyTo = replyMessage,
                                onReply = { replyTarget = message },
                                onEdit = { msg ->
                                    editingTarget = msg
                                    replyTarget = null
                                    inputText = msg.text
                                },
                                onForward = { msg ->
                                    forwardSource = msg
                                    forwardTargetId = ""
                                    showForwardDialog = true
                                },
                                onDelete = { msg ->
                                    pendingDelete = msg
                                }
                            )
                        }
                }
            }
        }

        if (editingTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                EditingBar(
                    editing = editingTarget!!,
                    onClear = {
                        editingTarget = null
                        inputText = ""
                    }
                )
            }
        }

        if (replyTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                ReplyingBar(
                    replyTo = replyTarget!!,
                    onClear = { replyTarget = null }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // respeta safe area inferior (gestos / barra)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = {
                    showAttachDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "Adjuntar archivo"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 140.dp),
                placeholder = {
                    Text(
                        text = "Escribe un mensaje…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                singleLine = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty() && myUid != null) {
                        val currentEditing = editingTarget
                        if (currentEditing != null) {
                            // Modo edición → actualizar mensaje existente
                            viewModel.editTextMessage(
                                chatId = chat.id,
                                messageId = currentEditing.id,
                                newText = trimmed
                            )
                            editingTarget = null
                        } else {
                            // Modo normal → enviar mensaje nuevo
                            val replyId = replyTarget?.id
                            viewModel.sendTextMessage(
                                chat = chat,
                                text = trimmed,
                                replyToMessageId = replyId
                            )
                        }
                        inputText = ""
                        replyTarget = null
                    }
                },
                enabled = inputText.isNotBlank() && myUid != null
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Enviar mensaje",
                    tint = if (inputText.isNotBlank() && myUid != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
        }

        if (showAttachDialog) {
            AlertDialog(
                onDismissRequest = { showAttachDialog = false },
                title = { Text("Adjuntar") },
                text = {
                    Column {
                        Text("Elige qué quieres adjuntar")
                    }
                },
                confirmButton = {
                    Column {
                        TextButton(
                            onClick = {
                                showAttachDialog = false
                                // A8.2: por ahora lanzamos un selector simple de foto/vídeo
                                mediaPickerLauncher.launch("image/*")
                            }
                        ) {
                            Text("Foto o vídeo")
                        }
                        TextButton(
                            onClick = {
                                showAttachDialog = false
                                // A8.2: por ahora lanzamos un selector genérico de archivo
                                documentPickerLauncher.launch("*/*")
                            }
                        ) {
                            Text("Archivo")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAttachDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Confirmación de borrado
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Borrar mensaje") },
                text = {
                    Text("¿Seguro que quieres borrar este mensaje? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toDelete = pendingDelete
                            if (toDelete != null) {
                                viewModel.deleteMessage(
                                    chatId = chat.id,
                                    messageId = toDelete.id
                                )
                            }
                            pendingDelete = null
                        }
                    ) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Picker real de chats para reenviar (sheet)
        if (showForwardDialog && forwardSource != null && myUid != null) {
            ForwardChatPickerSheet(
                currentChatId = chat.id,
                myUid = myUid,
                onDismiss = {
                    showForwardDialog = false
                    forwardSource = null
                },
                onChatSelected = { targetChatId ->
                    viewModel.forwardMessageToChat(
                        chatId = targetChatId,
                        message = forwardSource!!
                    )
                    showForwardDialog = false
                    forwardSource = null
                }
            )
        }
    }

    if (showProfileSheet) {
        ChatProfileInfoBottomSheet(
            chat = chat,
            onClose = { showProfileSheet = false }
        )
    }

    if (showGroupSheet) {
        ChatGroupInfoBottomSheet(
            chat = chat,
            onClose = { showGroupSheet = false },
            onMetaChanged = { newName, newPhotoUrl ->
                // Reflejar cambios al instante en la cabecera del chat
                if (!newName.isNullOrBlank()) {
                    groupTitleOverride = newName
                }
                if (!newPhotoUrl.isNullOrBlank()) {
                    groupAvatarOverride = newPhotoUrl
                }
            },
            initialInviteUrl = groupInviteUrlOverride,
            onInviteUrlChanged = { updatedUrl ->
                groupInviteUrlOverride = updatedUrl
            },
            onLeftGroup = {
                // Cerrar sheet y volver a la lista de chats
                showGroupSheet = false
                onBack()
            }
        )
    }

}

}

@Composable
private fun MessageRow(
    message: Message,
    isMine: Boolean,
    groupPosition: MessageGroupPosition,
    sender: ChatMemberUiModel?,
    replyTo: Message?,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onForward: (Message) -> Unit,
    onDelete: (Message) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // Paleta de colores alineada con iOS (TG.incoming/outgoing + text)
    val bubbleColor = when {
        isMine && isDark -> Color(red = 0.18f, green = 0.46f, blue = 0.77f) // outgoingDark
        isMine && !isDark -> Color(red = 0.80f, green = 0.90f, blue = 1.00f) // outgoingLight
        !isMine && isDark -> Color(red = 0.17f, green = 0.19f, blue = 0.21f) // incomingDark
        else -> Color.White // incomingLight
    }

    // En iOS el texto de la burbuja es siempre negro en claro / blanco en oscuro
    val textColor = if (isDark) Color.White else Color.Black

    val hasFile = message.hasFile
    val caption = message.text

    // Si es SOLO un link http(s) → preview tipo tarjeta
    val linkPreviewUrl = if (!hasFile) extractSingleHttpUrlForPreview(caption) else null

    // Ancho máximo de la burbuja (≈ iOS: min(320pt, 70% del ancho))
    val bubbleMaxWidth = with(LocalConfiguration.current) {
        320.dp.coerceAtMost(screenWidthDp.dp * 0.70f)
    }

    val shape = bubbleShape(isMine, groupPosition)

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Nombre inline solo en grupos y solo en TOP / SOLO

    // Nombre inline solo en grupos y solo en TOP / SOLO
    val inlineSenderName =
        if (!isMine && sender != null &&
            (groupPosition == MessageGroupPosition.SOLO || groupPosition == MessageGroupPosition.TOP)
        ) {
            sender.displayName
        } else {
            null
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            // Columna de avatar (solo en grupos, mensajes de otro)
            if (!isMine && sender != null) {
                val showAvatar = groupPosition == MessageGroupPosition.BOTTOM ||
                        groupPosition == MessageGroupPosition.SOLO

                if (showAvatar) {
                    // 🔧 Paddings para controlar el grosor de los aros
                    val outerRingPadding = 1.5.dp   // → aro degradado. Menos dp = más gordo, más dp = más fino
                    val whiteRingPadding = 1.5.dp   // → aro blanco. Menos dp = más gordo, más dp = más fino

                    val ringBrushSmall = Brush.sweepGradient(
                        listOf(
                            Color(0xFF00D4FF),
                            Color(0xFF7C4DFF),
                            Color(0xFF00D4FF)
                        )
                    )

                    val context = LocalContext.current
                    val localFile = avatarLocalFile(context, sender.uid)
                    val hasLocal = localFile.exists()
                    val modelUrl = sender.avatarUrl

                    Box(
                        modifier = Modifier
                            .size(50.dp) // tamaño total del avatar
                            .background(ringBrushSmall, CircleShape)           // aro degradado
                            .padding(outerRingPadding)                         // grosor aro degradado
                            .background(Color.White, CircleShape)              // aro blanco intermedio
                            .padding(whiteRingPadding)                         // grosor aro blanco
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            // 1️⃣ Fichero local ya guardado para este uid
                            hasLocal -> {
                                val bitmap = remember(localFile.path) {
                                    try {
                                        BitmapFactory.decodeFile(localFile.path)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Avatar remitente",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (!modelUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = modelUrl,
                                        contentDescription = "Avatar remitente",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        onSuccess = { state: AsyncImagePainter.State.Success ->
                                            saveDrawableToFile(state.result.drawable, localFile)
                                        }
                                    )
                                }
                            }

                            // 2️⃣ No hay fichero local pero sí URL → pasamos por Coil y guardamos
                            !modelUrl.isNullOrEmpty() -> {
                                AsyncImage(
                                    model = modelUrl,
                                    contentDescription = "Avatar remitente",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onSuccess = { state: AsyncImagePainter.State.Success ->
                                        saveDrawableToFile(state.result.drawable, localFile)
                                    }
                                )
                            }

                            else -> {
                                // Sin foto → iniciales del nombre del remitente
                                val initials = sender.displayName
                                    .trim()
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.first().uppercaseChar().toString() }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Placeholder transparente para TOP/MIDDLE (mantiene alineación 1:1 con iOS)
                    Spacer(modifier = Modifier.size(40.dp))
                }


                Spacer(modifier = Modifier.width(8.dp))
            } else if (isMine) {
                // En mensajes propios empujamos la burbuja a la derecha
                Spacer(modifier = Modifier.weight(1f))
            }

            // Columna de burbuja (texto, enlaces, archivos, hora, menú)
            Surface(
                modifier = Modifier
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                showMenu = true
                            }
                        )
                    },
                color = bubbleColor,
                shape = shape
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Nombre del remitente inline dentro de la burbuja (solo grupos)
                        if (inlineSenderName != null && sender != null) {
                            Text(
                                text = inlineSenderName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colorForSenderId(sender.uid),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Bloque de reply/quote (si existe)
                        if (replyTo != null) {
                            ReplyPreview(reply = replyTo)
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Contenido principal (archivo / texto / preview de enlace)
                        if (hasFile) {
                            // 1️⃣ Mensajes con archivo → mantenemos comportamiento actual
                            FileMessagePreview(
                                message = message,
                                textColor = textColor
                            )
                            if (caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinkedText(
                                    text = caption,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    onLongPress = { showMenu = true }
                                )
                            }
                        } else {
                            // 2️⃣ Mensajes SIN archivo → imitamos lógica de iOS:
                            //    - si el texto NO es solo un enlace → mostramos texto
                            //    - si hay http(s) → mostramos preview del primer enlace
                            val httpUrlForPreview = firstHttpUrl(caption)
                            val isOnlyHttpLink = extractSingleHttpUrlForPreview(caption) != null

                            if (caption.isNotBlank() && !isOnlyHttpLink) {
                                LinkedText(
                                    text = caption,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    onLongPress = { showMenu = true }
                                )
                            }

                            if (httpUrlForPreview != null) {
                                if (caption.isNotBlank() && !isOnlyHttpLink) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                LinkPreview(
                                    url = httpUrlForPreview,
                                    textColor = textColor
                                )
                            }
                        }

                        // Hora + " · editado" (si aplica) con color 1:1 iOS
                        val meta = formatTime(message.createdAtMillis).let { base ->
                            if (message.editedAtMillis != null) "$base · editado" else base
                        }

                        val timeColor =
                            if (isDark && isMine) {
                                Color.White.copy(alpha = 0.9f)
                            } else {
                                // TG.timeGrey = (0.56, 0.56, 0.60)
                                Color(red = 0.56f, green = 0.56f, blue = 0.60f)
                            }

                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                                .padding(top = 2.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Editar: solo si es mío y hay texto sin archivo
                        if (isMine && !hasFile && caption.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                onClick = {
                                    showMenu = false
                                    onEdit(message)
                                }
                            )
                        }

                        // Responder: siempre disponible
                        DropdownMenuItem(
                            text = { Text("Responder") },
                            onClick = {
                                showMenu = false
                                onReply(message)
                            }
                        )

                        // Reenviar: disponible siempre (como en iOS)
                        DropdownMenuItem(
                            text = { Text("Reenviar…") },
                            onClick = {
                                showMenu = false
                                onForward(message)
                            }
                        )

                        // Copiar: solo si hay texto
                        if (caption.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Copiar") },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(caption))
                                    showMenu = false
                                }
                            )
                        }

                        // Borrar: solo si es mío
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Borrar") },
                                onClick = {
                                    showMenu = false
                                    onDelete(message)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatProfileInfoBottomSheet(
    chat: Chat,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true       // 👈 hoja grande, no a media pantalla
    )
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val myUid = auth.currentUser?.uid
    val scope = rememberCoroutineScope()

    // Usuario al otro lado del 1:1 (ignorando bot de soporte)
    val otherUserId = remember(chat.id, myUid) {
        chat.participantIds.firstOrNull { it != myUid && it != SUPPORT_BOT_ID }
    }

    // Perfil + estado
    var displayName by remember(chat.id) { mutableStateOf<String?>(null) }
    var statusText by remember(chat.id) { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember(chat.id) { mutableStateOf(true) }

    // Silenciar chat
    var isMuted by remember(chat.id) { mutableStateOf(false) }
    var isLoadingMute by remember(chat.id) { mutableStateOf(true) }
    var isUpdatingMute by remember(chat.id) { mutableStateOf(false) }
    var muteError by remember(chat.id) { mutableStateOf<String?>(null) }

    // Reporte
    var showReasonDialog by remember { mutableStateOf(false) }
    var isReporting by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }

    // 🔹 Carga de perfil + estado (equivalente a loadUserStatus + nombre de iOS)
    LaunchedEffect(chat.id, otherUserId) {
        isLoadingProfile = true
        try {
            if (!otherUserId.isNullOrEmpty()) {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users")
                    .document(otherUserId)
                    .get()
                    .await()

                if (doc.exists()) {
                    val name = doc.getString("displayName")
                        ?: doc.getString("username")
                        ?: doc.getString("email")
                        ?: "Usuario"

                    val isOnline = doc.getBoolean("online") ?: false
                    val lastSeenTs = doc.getTimestamp("lastSeenAt")

                    val status = when {
                        isOnline -> "En línea"
                        lastSeenTs != null -> {
                            val date = lastSeenTs.toDate()
                            val timeStr = android.text.format.DateFormat
                                .getTimeFormat(context)
                                .format(date)
                            "Últ. vez a las $timeStr"
                        }
                        else -> null
                    }

                    displayName = name
                    statusText = status
                } else {
                    displayName = null
                    statusText = null
                }
            } else {
                displayName = null
                statusText = null
            }
        } catch (_: Exception) {
            displayName = null
            statusText = null
        } finally {
            isLoadingProfile = false
        }
    }

    // 🔹 Carga de estado de mute (equivalente a ChatPrefsService.getMute en iOS)
    LaunchedEffect(chat.id) {
        isLoadingMute = true
        muteError = null
        try {
            val muted = ChatPrefsRepository.getMute(chat.id)
            isMuted = muted
        } catch (_: Exception) {
            muteError = "No se pudo cargar el estado de silencio."
        } finally {
            isLoadingMute = false
        }
    }

    // 🔹 Diálogo de motivos de reporte (equivalente a confirmationDialog de iOS)
    if (showReasonDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isReporting) {
                    showReasonDialog = false
                    reportError = null
                }
            },
            title = { Text("Motivo del reporte") },
            text = {
                Column {
                    if (reportError != null) {
                        Text(
                            text = reportError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val reasons = listOf(
                        "Spam",
                        "Contenido falso/incorrecto",
                        "Inapropiado",
                        "Discurso de odio/Insultos",
                        "Datos personales"
                    )

                    reasons.forEach { reason ->
                        TextButton(
                            onClick = {
                                if (isReporting) return@TextButton
                                scope.launch {
                                    isReporting = true
                                    reportError = null
                                    try {
                                        val success = if (!otherUserId.isNullOrEmpty()) {
                                            // iOS → ReportService.reportUser(otherUserId: reason)
                                            ReportService.reportUser(otherUserId, reason)
                                        } else {
                                            // Fallback por si acaso (no deberíamos llegar aquí)
                                            ReportService.reportChat(chat.id, reason)
                                        }

                                        if (success) {
                                            showReasonDialog = false
                                            onClose()
                                        } else {
                                            reportError = "No se pudo enviar el reporte. Inténtalo de nuevo."
                                        }
                                    } catch (_: Exception) {
                                        reportError = "No se pudo enviar el reporte. Inténtalo de nuevo."
                                    } finally {
                                        isReporting = false
                                    }
                                }
                            },
                            enabled = !isReporting
                        ) {
                            Text(reason)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isReporting) {
                            showReasonDialog = false
                            reportError = null
                        }
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 🔹 Contenido principal de la sheet
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Avatar grande con el mismo aro degradado que la cabecera
            // Avatar grande 1:1 con iOS (mismo aro degradado y foto real)
// 36.dp en cabecera → aquí lo hacemos ~3× más grande
            ChatAvatarPlaceholder(
                chat = chat,
                modifier = Modifier.size(108.dp)
            )


            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = displayName ?: (chat.displayName ?: "Usuario"),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Estado ("en línea" / "últ. vez a las …")
            if (isLoadingProfile) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else if (!statusText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val dotColor = if (statusText == "En línea") {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🔹 Toggle de "Silenciar chat" (solo si no es bot de soporte)
            if (otherUserId != null && otherUserId != SUPPORT_BOT_ID) {
                if (isLoadingMute) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cargando estado de silencio…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Silenciar chat",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (muteError != null) {
                                Text(
                                    text = muteError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = if (isMuted) {
                                        "Notificaciones desactivadas"
                                    } else {
                                        "Recibirás notificaciones nuevas"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isMuted,
                            onCheckedChange = { newValue ->
                                if (isUpdatingMute || isLoadingMute) return@Switch
                                isMuted = newValue
                                muteError = null

                                scope.launch {
                                    isUpdatingMute = true
                                    try {
                                        ChatPrefsRepository.setMute(chat.id, newValue)
                                    } catch (_: Exception) {
                                        muteError = "No se pudo actualizar el silencio."
                                        isMuted = !newValue
                                    } finally {
                                        isUpdatingMute = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔹 Botón "Reportar usuario" (igual texto que iOS)
            if (otherUserId != null && otherUserId != SUPPORT_BOT_ID) {
                Button(
                    onClick = { showReasonDialog = true },
                    enabled = !isReporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reportar usuario")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🔹 Botón Cerrar (como en iOS)
            TextButton(
                onClick = { onClose() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


@Composable
private fun LinkedText(
    text: String,
    color: Color,
    style: TextStyle,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val annotated = remember(text, color) {
        buildLinkAnnotatedString(text, color)
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures(
                onTap = { pos ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)

                    annotated.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()
                        ?.let { ann ->
                            openExternalUri(context, ann.item)
                            return@detectTapGestures
                        }

                    annotated.getStringAnnotations("DEEP_LINK", offset, offset)
                        .firstOrNull()
                        ?.let { ann ->
                            openExternalUri(context, ann.item)
                            return@detectTapGestures
                        }
                },
                onLongPress = {
                    // Long press directo sobre el texto → menú contextual
                    onLongPress?.invoke()
                }
            )
        },
        onTextLayout = { layout ->
            textLayoutResult = layout
        }
    )
}

@Composable
private fun LinkPreview(
    url: String,
    textColor: Color
) {
    val context = LocalContext.current

    var entry by remember(url) { mutableStateOf<UrlPreviewEntry?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        hasError = false
        val result = UrlPreviewRepository.loadForUi(context, url)
        if (result != null) {
            entry = result
        } else {
            hasError = true
        }
        isLoading = false
    }

    val effectiveEntry = entry
    val finalUrl = effectiveEntry?.finalUrl ?: url
    val uri = remember(finalUrl) {
        runCatching { Uri.parse(finalUrl) }.getOrNull()
    }

    val host = effectiveEntry?.host
        ?: uri?.host
        ?: finalUrl

    val titleText = effectiveEntry?.title
        ?: host

    val imagePath = effectiveEntry?.imageLocalPath
    val aspect = effectiveEntry?.imageAspectRatio ?: (16f / 9f)

    val clickable = uri != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) {
                uri?.toString()?.let { openExternalUri(context, it) }
            }
    ) {
        // Imagen o placeholder, tarjeta tipo iOS
        if (imagePath != null && File(imagePath).exists()) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder mientras carga o si no hay imagen OG
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = textColor.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cargando vista previa…",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }
                    }

                    hasError -> {
                        Text(
                            text = "No se pudo cargar la vista previa",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }

                    else -> {
                        // Sin imagen pero sin error → solo bloque gris
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Título en negrita (como iOS)
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Host debajo, más pequeño
        Text(
            text = host,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


private fun extractYouTubeIdFromUri(uri: Uri): String? {
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null

    return when {
        host.contains("youtu.be") -> {
            uri.lastPathSegment
        }
        host.contains("youtube.com") -> {
            // Primero intentamos con el parámetro v, si no, último segmento del path
            uri.getQueryParameter("v")
                ?: uri.pathSegments.lastOrNull()
        }
        else -> null
    }
}


@Composable
private fun FileMessagePreview(
    message: Message,
    textColor: Color
) {
    val context = LocalContext.current

    // Texto base según tipo de archivo
    val label = when (message.fileKind) {
        ChatFileKind.IMAGE -> "📷 Foto"
        ChatFileKind.VIDEO -> "🎬 Vídeo"
        ChatFileKind.AUDIO -> "🎵 Audio"
        ChatFileKind.DOCUMENT -> {
            val name = message.fileName.orEmpty()
            if (name.isNotBlank()) "📎 $name" else "Archivo"
        }

        ChatFileKind.NONE -> {
            val name = message.fileName.orEmpty()
            if (name.isNotBlank()) "📎 $name" else "Archivo"
        }
    }

    val sizePart = message.fileSizeBytes?.let { " · ${formatFileSize(it)}" }.orEmpty()
    val fileUrl = message.fileUrl
    val remoteThumbUrl = message.thumbnailUrl ?: fileUrl

    // Fichero local persistente para el thumb de este mensaje (IMAGE / VIDEO)
    val localThumbFile = remember(message.id) {
        chatFileThumbnailLocalFile(context, message.id)
    }
    val hasLocalThumb = localThumbFile.exists()

    when (message.fileKind) {
        ChatFileKind.IMAGE,
        ChatFileKind.VIDEO -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !fileUrl.isNullOrBlank()) {
                        fileUrl?.let { openExternalUri(context, it) }
                    }
            ) {
                val model = when {
                    hasLocalThumb -> localThumbFile
                    !remoteThumbUrl.isNullOrBlank() -> remoteThumbUrl
                    else -> null
                }

                if (model != null) {
                    if (hasLocalThumb) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                            onSuccess = { state: AsyncImagePainter.State.Success ->
                                saveDrawableToFile(state.result.drawable, localThumbFile)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = label + sizePart,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }

        ChatFileKind.AUDIO,
        ChatFileKind.DOCUMENT,
        ChatFileKind.NONE -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !fileUrl.isNullOrBlank()) {
                        fileUrl?.let { openExternalUri(context, it) }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconEmoji = when (message.fileKind) {
                    ChatFileKind.AUDIO -> "🎵"
                    ChatFileKind.DOCUMENT,
                    ChatFileKind.NONE -> "📎"
                    else -> "📎"
                }

                Text(
                    text = iconEmoji,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = label + sizePart,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }
    }

    if (message.fileUrl != null) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Abrir",
                modifier = Modifier.clickable {
                    openChatAttachment(context, message)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    val progress = message.uploadProgress

    if (progress != null && progress in 0.0..0.999) {
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}


@Composable
private fun SystemMessageRow(
    message: Message,
    actorDisplayName: String? = null
) {
    val baseText = message.text.ifBlank { "Mensaje del sistema" }

    // Caso específico: mensaje de sistema "salió del grupo" en grupos
    val text = if (
        message.type == "system" &&
        baseText.trim().equals("salió del grupo", ignoreCase = true) &&
        !actorDisplayName.isNullOrBlank()
    ) {
        "${actorDisplayName} salió del grupo"
    } else {
        baseText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



@Composable
private fun ReplyPreview(
    reply: Message
) {
    val previewText = when {
        reply.text.isNotBlank() -> reply.text
        reply.fileName != null -> reply.fileName
        reply.hasFile -> "Archivo"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 24.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun EditingBar(
    editing: Message,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Editando mensaje",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = editing.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancelar edición"
                )
            }
        }
    }
}

@Composable
private fun ReplyingBar(
    replyTo: Message,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                ReplyPreview(reply = replyTo)
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancelar respuesta"
                )
            }
        }
    }
}


@Composable
private fun ChatDetailTopBar(
    chat: Chat,
    userStatus: String,
    overrideTitle: String? = null,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    overrideAvatarUrl: String? = null
) {
    val isGroup = chat.isGroup || chat.participantIds.size > 2

    val baseTitle = chat.displayName
        ?: if (chat.isSupport) "Soporte"
        else if (isGroup) "Chat de grupo"
        else "Chat"

    val title = overrideTitle ?: baseTitle


    val subtitle = if (isGroup) {
        val count = chat.participantIds.size
        if (count > 0) "$count miembros" else ""
    } else {
        userStatus
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // respeta safe area superior
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón back "Chats" como en iOS
        Row(
            modifier = Modifier.clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = IOSActionBlue
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "Chats",
                style = MaterialTheme.typography.bodyMedium,
                color = IOSActionBlue,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Título + subtítulo centrados (tap → hoja de info)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onHeaderClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            if (subtitle.isNotBlank()) {
                if (isGroup) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val online = userStatus.trim()
                            .lowercase(Locale.getDefault()) == "en línea"

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (online) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Avatar placeholder con aro degradado (tap → hoja de info)
        ChatAvatarPlaceholder(
            chat = chat,
            modifier = Modifier
                .size(50.dp)
                .clickable(onClick = onHeaderClick),
            overrideAvatarUrl = overrideAvatarUrl
        )

    }
}

@Composable
private fun ChatAvatarPlaceholder(
    chat: Chat,
    modifier: Modifier = Modifier,
    overrideAvatarUrl: String? = null
) {
    val auth = FirebaseAuth.getInstance()
    val myUid = auth.currentUser?.uid
    val isGroup = chat.isGroup || chat.participantIds.size > 2

    val context = LocalContext.current

    // 👇 NUEVO: id del otro usuario para 1:1 (excluyendo soporte)
    val otherUserId = remember(chat.id, myUid) {
        chat.participantIds.firstOrNull { it != myUid && it != SUPPORT_BOT_ID }
    }

    // Valor inicial desde override (si existe) o desde caché persistente
    val initialAvatarUrl = remember(chat.id, isGroup, myUid, overrideAvatarUrl) {
        overrideAvatarUrl ?: run {
            try {
                if (isGroup) {
                    ChatsLocalCache.getGroupPhoto(context, chat.id)
                } else {
                    val otherUserIdLocal = chat.participantIds.firstOrNull { it != myUid }
                    if (!otherUserIdLocal.isNullOrEmpty()) {
                        ChatsLocalCache.getUserAvatar(context, otherUserIdLocal)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    var avatarUrl by remember(chat.id) { mutableStateOf<String?>(initialAvatarUrl) }


    // Iniciales de fallback si no hay avatar remoto
    val initials = remember(chat.displayName) {
        val source = chat.displayName
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: "Chat"

        source
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
    }

    LaunchedEffect(chat.id, isGroup, myUid, overrideAvatarUrl) {
        // Si viene override explícito, no tocamos Firestore aquí
        if (overrideAvatarUrl != null) {
            avatarUrl = overrideAvatarUrl
            return@LaunchedEffect
        }

        try {
            // 2) Refresco desde Firestore + actualización de caché
            val db = FirebaseFirestore.getInstance()


            if (isGroup) {
                val doc = db.collection("chats")
                    .document(chat.id)
                    .get()
                    .await()

                val remote = doc.getString("photoURL")
                avatarUrl = remote

                if (!remote.isNullOrBlank()) {
                    ChatsLocalCache.saveGroupPhoto(context, chat.id, remote)
                }
            } else {
                val otherUserIdLocal = chat.participantIds.firstOrNull { it != myUid }
                if (!otherUserIdLocal.isNullOrEmpty()) {
                    val userDoc = db.collection("users")
                        .document(otherUserIdLocal)
                        .get()
                        .await()

                    val remote = userDoc.getString("profileImageUrl")
                    avatarUrl = remote

                    if (!remote.isNullOrBlank()) {
                        ChatsLocalCache.saveUserAvatar(context, otherUserIdLocal, remote)
                    }
                } else {
                    avatarUrl = null
                }
            }
        } catch (_: Exception) {
            // Si falla, mantenemos lo que hubiera (cache o null)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Aro exterior degradado: mismos colores que los avatares de miembros en las burbujas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF00D4FF),
                            Color(0xFF7C4DFF),
                            Color(0xFF00D4FF)
                        )
                    )
                )
        )

        // Anillo blanco (30/36 del tamaño total)
        Box(
            modifier = Modifier
                .fillMaxSize(30f / 36f)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            // Círculo interior (26/30 del anillo blanco → mismo ratio que 26dp sobre 30dp)
            Box(
                modifier = Modifier
                    .fillMaxSize(26f / 30f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // 🔑 Clave para cache en disco:
                // - Grupos: chat.id
                // - 1:1: uid del otro usuario (si lo tenemos), fallback a chat.id
                val avatarKey = if (isGroup) {
                    chat.id
                } else {
                    otherUserId ?: chat.id
                }

                val localFile = remember(avatarKey) {
                    avatarKey?.let { avatarLocalFile(context, it) }
                }
                val hasLocal = localFile?.exists() == true
                val modelUrl = avatarUrl

                when {
                    hasLocal -> {
                        val bitmap = remember(localFile!!.path) {
                            try {
                                BitmapFactory.decodeFile(localFile.path)?.asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Avatar chat",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!modelUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = modelUrl,
                                contentDescription = "Avatar chat",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onSuccess = { state: AsyncImagePainter.State.Success ->
                                    saveDrawableToFile(state.result.drawable, localFile!!)
                                }
                            )
                        } else {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    !modelUrl.isNullOrEmpty() -> {
                        val safeFile = localFile
                        AsyncImage(
                            model = modelUrl,
                            contentDescription = "Avatar chat",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onSuccess = { state: AsyncImagePainter.State.Success ->
                                if (safeFile != null) {
                                    saveDrawableToFile(state.result.drawable, safeFile)
                                }
                            }
                        )
                    }

                    else -> {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatGroupInfoBottomSheet(
    chat: Chat,
    onClose: () -> Unit,
    onMetaChanged: (String?, String?) -> Unit,
    initialInviteUrl: String?,
    onInviteUrlChanged: (String?) -> Unit,
    onLeftGroup: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // 👉 se abre siempre grande
    )
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val myUid = auth.currentUser?.uid
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var members by remember(chat.id) { mutableStateOf<List<ChatMemberUiModel>>(emptyList()) }
    var isLoading by remember(chat.id) { mutableStateOf(true) }

    var chatListener by remember(chat.id) { mutableStateOf<ListenerRegistration?>(null) }

    // 🔹 Meta del grupo (nombre, foto, límite, permisos)
    var groupName by remember(chat.id) { mutableStateOf(chat.displayName ?: "Grupo") }
    var groupPhotoUrl by remember(chat.id) { mutableStateOf<String?>(null) }
    var maxMembers by remember(chat.id) { mutableStateOf(64) }
    var canManageGroup by remember(chat.id) { mutableStateOf(false) }
    var showAddMembersPicker by remember(chat.id) { mutableStateOf(false) }

    // 🔹 Roles (propietario, admins)
    var ownerId by remember(chat.id) { mutableStateOf<String?>(null) }
    var adminIds by remember(chat.id) { mutableStateOf<Set<String>>(emptySet()) }

    // 🔹 Invitaciones
    var inviteUrl by remember(chat.id, initialInviteUrl) { mutableStateOf(initialInviteUrl) }
    var isCreatingInvite by remember { mutableStateOf(false) }
    var isRevokingInvite by remember { mutableStateOf(false) }
    var showRevokeInviteDialog by remember { mutableStateOf(false) }

    // 🔹 Renombrar
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isRenaming by remember { mutableStateOf(false) }

    // 🔹 Cambiar foto
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // 🔹 Acciones por miembro (promover / degradar / expulsar)
    var memberActionTarget by remember(chat.id) { mutableStateOf<ChatMemberUiModel?>(null) }
    var isMemberActionBusy by remember { mutableStateOf(false) }

    // 🔹 Silenciar chat (mutedFor[])
    var isMuted by remember(chat.id) { mutableStateOf(false) }
    var isUpdatingMute by remember { mutableStateOf(false) }

    // 🔹 Salir del grupo
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var isLeavingGroup by remember { mutableStateOf(false) }

    // Azul estilo iOS para las acciones dentro de las cards
    val actionBlue = Color(0xFF007AFF)

    // Cargar mute 1:1 con iOS (ChatPrefsService)
    LaunchedEffect(chat.id, myUid) {
        val uid = myUid ?: return@LaunchedEffect
        // getMute devuelve false por defecto si no existe doc
        isMuted = ChatPrefsRepository.getMute(chat.id)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && myUid != null) {
            scope.launch {
                isUploadingPhoto = true
                try {
                    val url = uploadGroupPhoto(
                        chatId = chat.id,
                        ownerUid = myUid,
                        uri = uri,
                        context = context
                    )
                    if (url != null) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("chats")
                            .document(chat.id)
                            .update("photoURL", url)
                            .await()

                        groupPhotoUrl = url
                        ChatsLocalCache.saveGroupPhoto(context, chat.id, url)
                        onMetaChanged(groupName, url)
                    }
                } catch (e: Exception) {
                    Log.e("ChatGroupInfo", "Error al cambiar foto de grupo", e)
                    Toast.makeText(
                        context,
                        "No se pudo cambiar la foto del grupo",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isUploadingPhoto = false
                }
            }
        }
    }

    // 🔹 Cargar meta + miembros + invitación activa desde Firestore
    LaunchedEffect(chat.id) {
        try {
            val db = FirebaseFirestore.getInstance()

            // 1️⃣ Metadatos del grupo (name, photo, maxMembers, owner/admins, mutedFor)
            try {
                val chatSnap = db.collection("chats")
                    .document(chat.id)
                    .get()
                    .await()

                val data = chatSnap.data
                if (data != null) {
                    val name = data["name"] as? String
                    if (!name.isNullOrBlank()) {
                        groupName = name
                    }

                    val photo = (data["photoURL"] as? String)
                        ?: (data["groupPhotoURL"] as? String)
                        ?: (data["avatarURL"] as? String)
                    groupPhotoUrl = photo

                    val limit = (data["maxMembers"] as? Long)?.toInt()
                    if (limit != null && limit > 0) {
                        maxMembers = limit
                    }

                    val ownerFromDoc = (data["ownerId"] as? String)
                        ?: (data["createdBy"] as? String)
                        ?: ""
                    val adminsFromDoc =
                        (data["admins"] as? List<*>)?.mapNotNull { it as? String }?.toSet()
                            ?: emptySet()

                    val mutedFor =
                        (data["mutedFor"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    ownerId = ownerFromDoc
                    adminIds = adminsFromDoc

                    val me = myUid
                    canManageGroup = me != null &&
                            (me == ownerFromDoc || adminsFromDoc.contains(me!!))

                    isMuted = me != null && mutedFor.contains(me)
                }
            } catch (_: Exception) {
                // seguimos con valores por defecto
            }

            // 1️⃣ bis – Cargar invitación ACTIVA desde Functions (vía GroupApi ya lo hace create/revoke)
            try {
                val invitesSnap = db.collection("groupInvites")
                    .whereEqualTo("chatId", chat.id)
                    .get()
                    .await()

                val inviteDoc = invitesSnap.documents.firstOrNull { doc ->
                    doc.getBoolean("active") == true
                }

                if (inviteDoc != null) {
                    val code = inviteDoc.id
                    val url = "spots://invite/$code"
                    inviteUrl = url
                    onInviteUrlChanged(url)
                } else {
                    inviteUrl = null
                    onInviteUrlChanged(null)
                }
            } catch (e: Exception) {
                Log.e("ChatGroupInfo", "Error al cargar invitación activa", e)
            }

            // 2️⃣ Lista de miembros + ROLES
            val ids = chat.participantIds

            val fetched = ids.mapNotNull { uid ->
                try {
                    val doc = db.collection("users")
                        .document(uid)
                        .get()
                        .await()

                    if (!doc.exists()) return@mapNotNull null

                    val name = doc.getString("displayName")
                        ?: doc.getString("username")
                        ?: doc.getString("email")
                        ?: "Usuario"

                    val avatar = doc.getString("profileImageUrl")

                    val isOwnerMember = ownerId != null && ownerId == uid
                    val isAdminMember = adminIds.contains(uid)
                    val isMeMember = myUid != null && myUid == uid

                    ChatMemberUiModel(
                        uid = uid,
                        displayName = name,
                        avatarUrl = avatar,
                        isOwner = isOwnerMember,
                        isAdmin = isAdminMember,
                        isMe = isMeMember
                    )
                } catch (_: Exception) {
                    null
                }
            }

            val sorted = fetched.sortedWith(
                compareBy<ChatMemberUiModel> { !it.isOwner }
                    .thenBy { !it.isAdmin }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
            )

            members = sorted

        } finally {
            isLoading = false
        }
    }

    // 🔹 Listener en tiempo real de meta + participantes (owner/admins/miembros/mutedFor)
    DisposableEffect(chat.id) {
        val db = FirebaseFirestore.getInstance()

        chatListener?.remove()

        val registration = db.collection("chats")
            .document(chat.id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatGroupInfo", "Error en listener de participantes", error)
                    return@addSnapshotListener
                }

                val data = snapshot?.data ?: return@addSnapshotListener

                val name = data["name"] as? String
                val photo = (data["photoURL"] as? String)
                    ?: (data["groupPhotoURL"] as? String)
                    ?: (data["avatarURL"] as? String)

                val limit = (data["maxMembers"] as? Long)?.toInt()

                val ownerFromDoc = (data["ownerId"] as? String)
                    ?: (data["createdBy"] as? String)
                    ?: ""
                val adminsFromDoc =
                    (data["admins"] as? List<*>)?.mapNotNull { it as? String }?.toSet()
                        ?: emptySet()

                val mutedFor =
                    (data["mutedFor"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                val participants =
                    (data["participants"] as? List<*>)?.mapNotNull { it as? String }
                        ?: chat.participantIds

                val me = myUid

                scope.launch {
                    try {
                        val fetched = participants.mapNotNull { uid ->
                            try {
                                val userDoc = db.collection("users")
                                    .document(uid)
                                    .get()
                                    .await()

                                if (!userDoc.exists()) return@mapNotNull null

                                val displayName = userDoc.getString("displayName")
                                    ?: userDoc.getString("username")
                                    ?: userDoc.getString("email")
                                    ?: "Usuario"

                                val avatar = userDoc.getString("profileImageUrl")

                                val isOwnerMember =
                                    ownerFromDoc.isNotEmpty() && ownerFromDoc == uid
                                val isAdminMember = adminsFromDoc.contains(uid)
                                val isMeMember = me != null && me == uid

                                ChatMemberUiModel(
                                    uid = uid,
                                    displayName = displayName,
                                    avatarUrl = avatar,
                                    isOwner = isOwnerMember,
                                    isAdmin = isAdminMember,
                                    isMe = isMeMember
                                )
                            } catch (_: Exception) {
                                null
                            }
                        }

                        val sorted = fetched.sortedWith(
                            compareBy<ChatMemberUiModel> { !it.isOwner }
                                .thenBy { !it.isAdmin }
                                .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                        )

                        members = sorted
                        ownerId = ownerFromDoc.ifBlank { null }
                        adminIds = adminsFromDoc

                        if (!name.isNullOrBlank()) {
                            groupName = name
                        }
                        if (!photo.isNullOrBlank()) {
                            groupPhotoUrl = photo
                        }
                        if (limit != null && limit > 0) {
                            maxMembers = limit
                        }

                        canManageGroup = me != null &&
                                (me == ownerFromDoc || adminsFromDoc.contains(me!!))

                    } catch (e: Exception) {
                        Log.e("ChatGroupInfo", "Error actualizando miembros en tiempo real", e)
                    } finally {
                        isLoading = false
                    }
                }
            }

        chatListener = registration

        onDispose {
            chatListener?.remove()
            chatListener = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 👇👇👇 CABECERA: avatar reutilizando ChatAvatarPlaceholder (mismo diseño que cabecera)
            val avatarChat = remember(chat.id, groupName) {
                if (groupName.isBlank()) chat else chat.copy(displayName = groupName)
            }

            ChatAvatarPlaceholder(
                chat = avatarChat,
                modifier = Modifier.size(72.dp),
                overrideAvatarUrl = groupPhotoUrl
            )
            // ☝️☝️☝️

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = groupName.ifBlank { "Grupo" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${members.size} / $maxMembers miembros",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 🔹 Configuración del grupo (solo owner/admin)
            if (canManageGroup) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CONFIGURACIÓN DEL GRUPO",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Renombrar grupo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    newName = groupName
                                    showRenameDialog = true
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Renombrar grupo",
                                tint = actionBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Renombrar grupo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = actionBlue
                            )
                        }

                        // Cambiar foto
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isUploadingPhoto) {
                                    if (!isUploadingPhoto) {
                                        imagePickerLauncher.launch("image/*")
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Cambiar foto",
                                tint = actionBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Cambiar foto",
                                style = MaterialTheme.typography.bodyMedium,
                                color = actionBlue
                            )

                            if (isUploadingPhoto) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                // 🔹 Invitaciones
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "INVITACIONES",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = "Miembros",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Miembros",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${members.size} / $maxMembers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (inviteUrl.isNullOrBlank()) {
                            // Crear enlace
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCreatingInvite) {
                                        if (!isCreatingInvite) {
                                            scope.launch {
                                                isCreatingInvite = true
                                                try {
                                                    val url = GroupApi.createInviteLink(chat.id)
                                                    inviteUrl = url
                                                    onInviteUrlChanged(url)
                                                    Toast.makeText(
                                                        context,
                                                        "Enlace de invitación creado",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "ChatGroupInfo",
                                                        "Error al crear enlace de invitación",
                                                        e
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "No se pudo crear el enlace de invitación",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isCreatingInvite = false
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = "Crear enlace de invitación",
                                    tint = actionBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Crear enlace de invitación",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = actionBlue
                                )

                                if (isCreatingInvite) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Enlace activo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = inviteUrl ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = actionBlue,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Copiar enlace
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val current = inviteUrl ?: return@clickable
                                        clipboardManager.setText(AnnotatedString(current))
                                        Toast.makeText(
                                            context,
                                            "Enlace de invitación copiado al portapapeles",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Copiar enlace",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = actionBlue
                                )
                            }

                            // Revocar enlace
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isRevokingInvite) {
                                        if (!isRevokingInvite) {
                                            showRevokeInviteDialog = true
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Revocar enlace",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (members.size >= maxMembers) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Este grupo ha alcanzado el límite de miembros.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 🔹 Miembros del grupo
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIEMBROS DEL GRUPO",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (canManageGroup && members.size < maxMembers) {
                    TextButton(
                        onClick = { showAddMembersPicker = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = "Añadir miembros",
                            tint = actionBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Añadir",
                            style = MaterialTheme.typography.labelSmall,
                            color = actionBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val amOwner = myUid != null && ownerId == myUid

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            members.forEach { member ->
                                val canOpenActions =
                                    amOwner && !member.isOwner && !member.isMe

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GroupMemberAvatar(member)
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = member.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val roleLabel = when {
                                            member.isOwner -> "Propietario"
                                            member.isAdmin -> "Administrador"
                                            else -> "Miembro"
                                        }
                                        val meLabel = if (member.isMe) " · Tú" else ""
                                        Text(
                                            text = roleLabel + meLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (canOpenActions) {
                                        IconButton(
                                            onClick = { memberActionTarget = member }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "Más acciones",
                                                tint = actionBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🔹 Ajustes personales: silenciar + salir del grupo
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AJUSTES PERSONALES",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Silenciar chat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Silenciar chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Silenciar notificaciones",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isMuted,
                            onCheckedChange = { checked ->
                                if (myUid == null || isUpdatingMute) return@Switch

                                // Optimista: actualizamos UI y revertimos si falla
                                isMuted = checked
                                scope.launch {
                                    isUpdatingMute = true
                                    try {
                                        // 1:1 con iOS → guardar en users/{uid}/meta/chatPrefs/prefs/{chatId}
                                        ChatPrefsRepository.setMute(chat.id, checked)
                                    } catch (e: Exception) {
                                        Log.e("ChatGroupInfo", "Error actualizando silencio", e)
                                        isMuted = !checked
                                        Toast.makeText(
                                            context,
                                            "No se pudo actualizar el silencio",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isUpdatingMute = false
                                    }
                                }
                            }
                        )

                    }

                    // Salir del grupo (solo si hay usuario)
                    if (myUid != null) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLeaveGroupDialog = true
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonRemove,
                                contentDescription = "Salir del grupo",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Salir del grupo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // 🔹 Diálogo de renombrar
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRenaming) {
                    showRenameDialog = false
                }
            },
            title = { Text("Renombrar grupo") },
            text = {
                Column {
                    Text(
                        text = "Introduce un nuevo nombre para el grupo",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isEmpty()) return@TextButton

                        scope.launch {
                            isRenaming = true
                            try {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("chats")
                                    .document(chat.id)
                                    .update("name", trimmed)
                                    .await()

                                groupName = trimmed
                                showRenameDialog = false
                                onMetaChanged(groupName, groupPhotoUrl)
                            } catch (e: Exception) {
                                Log.e("ChatGroupInfo", "Error al renombrar grupo", e)
                                Toast.makeText(
                                    context,
                                    "No se pudo renombrar el grupo",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isRenaming = false
                            }
                        }
                    },
                    enabled = !isRenaming && newName.trim().isNotEmpty()
                ) {
                    if (isRenaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Guardar",
                            color = actionBlue
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isRenaming) {
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text(
                        text = "Cancelar",
                        color = actionBlue
                    )
                }
            }
        )
    }

    // 🔹 Diálogo de revocar enlace
    if (showRevokeInviteDialog && inviteUrl != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isRevokingInvite) {
                    showRevokeInviteDialog = false
                }
            },
            title = { Text("Revocar enlace de invitación") },
            text = {
                Text("Nadie podrá usar el enlace actual para unirse.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isRevokingInvite) {
                            scope.launch {
                                isRevokingInvite = true
                                try {
                                    GroupApi.revokeInviteLink(chat.id)
                                    inviteUrl = null
                                    onInviteUrlChanged(null)
                                    showRevokeInviteDialog = false
                                    Toast.makeText(
                                        context,
                                        "Enlace de invitación revocado",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Log.e(
                                        "ChatGroupInfo",
                                        "Error al revocar enlace de invitación",
                                        e
                                    )
                                    Toast.makeText(
                                        context,
                                        "No se pudo revocar el enlace de invitación",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isRevokingInvite = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Revocar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isRevokingInvite) {
                            showRevokeInviteDialog = false
                        }
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 🔹 Menú de acciones por miembro (solo propietario)
    if (memberActionTarget != null) {
        val target = memberActionTarget!!
        val amOwner = myUid != null && ownerId == myUid

        if (!amOwner) {
            memberActionTarget = null
        } else {
            AlertDialog(
                onDismissRequest = {
                    if (!isMemberActionBusy) {
                        memberActionTarget = null
                    }
                },
                title = { Text(target.displayName) },
                text = {
                    Column {
                        Text(
                            text = "Acciones para este miembro del grupo.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (target.isOwner) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Este miembro es el propietario del grupo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (target.isMe) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Para salir del grupo, usa la opción de salir del grupo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Column {
                        if (!target.isMe && !target.isOwner) {
                            if (target.isAdmin) {
                                TextButton(
                                    enabled = !isMemberActionBusy,
                                    onClick = {
                                        if (isMemberActionBusy) return@TextButton
                                        scope.launch {
                                            isMemberActionBusy = true
                                            try {
                                                GroupApi.revokeAdmin(chat.id, target.uid)
                                                adminIds = adminIds - target.uid
                                                members = members.map {
                                                    if (it.uid == target.uid) it.copy(isAdmin = false)
                                                    else it
                                                }
                                                Toast.makeText(
                                                    context,
                                                    "Administrador revocado",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "ChatGroupInfo",
                                                    "Error al revocar admin",
                                                    e
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "No se pudo revocar admin",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isMemberActionBusy = false
                                                memberActionTarget = null
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.StarBorder,
                                        contentDescription = "Quitar admin",
                                        tint = actionBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Quitar admin",
                                        color = actionBlue
                                    )
                                }
                            } else {
                                TextButton(
                                    enabled = !isMemberActionBusy,
                                    onClick = {
                                        if (isMemberActionBusy) return@TextButton
                                        scope.launch {
                                            isMemberActionBusy = true
                                            try {
                                                GroupApi.grantAdmin(chat.id, target.uid)
                                                adminIds = adminIds + target.uid
                                                members = members.map {
                                                    if (it.uid == target.uid) it.copy(isAdmin = true)
                                                    else it
                                                }
                                                Toast.makeText(
                                                    context,
                                                    "Miembro ascendido a admin",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "ChatGroupInfo",
                                                    "Error al conceder admin",
                                                    e
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "No se pudo hacer admin",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isMemberActionBusy = false
                                                memberActionTarget = null
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Hacer admin",
                                        tint = actionBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hacer admin",
                                        color = actionBlue
                                    )
                                }
                            }

                            TextButton(
                                enabled = !isMemberActionBusy,
                                onClick = {
                                    if (isMemberActionBusy) return@TextButton
                                    scope.launch {
                                        isMemberActionBusy = true
                                        try {
                                            GroupApi.removeMember(chat.id, target.uid)
                                            members = members.filterNot { it.uid == target.uid }
                                            Toast.makeText(
                                                context,
                                                "Miembro eliminado del grupo",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            Log.e(
                                                "ChatGroupInfo",
                                                "Error al quitar miembro",
                                                e
                                            )
                                            Toast.makeText(
                                                context,
                                                "No se pudo quitar al miembro",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } finally {
                                            isMemberActionBusy = false
                                            memberActionTarget = null
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonRemove,
                                    contentDescription = "Quitar del grupo",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Quitar del grupo",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isMemberActionBusy,
                        onClick = { memberActionTarget = null }
                    ) {
                        Text(
                            text = "Cancelar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }

    // 🔹 Diálogo “Salir del grupo”
    if (showLeaveGroupDialog && myUid != null) {
        val amOwner = ownerId == myUid

        AlertDialog(
            onDismissRequest = {
                if (!isLeavingGroup) {
                    showLeaveGroupDialog = false
                }
            },
            title = {
                Text(
                    text = if (amOwner) "No puedes salir del grupo" else "Salir del grupo"
                )
            },
            text = {
                Text(
                    text = if (amOwner) {
                        "Eres el propietario de este grupo y no puedes salir mientras seas el propietario. " +
                                "Asigna la propiedad a otro miembro o gestiona el grupo desde la administración."
                    } else {
                        "¿Seguro que quieres salir de este grupo? No recibirás más mensajes ni notificaciones."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                if (amOwner) {
                    TextButton(
                        onClick = { showLeaveGroupDialog = false }
                    ) {
                        Text("Entendido")
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (isLeavingGroup) return@TextButton
                            scope.launch {
                                isLeavingGroup = true
                                try {
                                    GroupApi.removeMember(chat.id, myUid)
                                    Toast.makeText(
                                        context,
                                        "Has salido del grupo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showLeaveGroupDialog = false
                                    onLeftGroup()
                                } catch (e: Exception) {
                                    Log.e("ChatGroupInfo", "Error al salir del grupo", e)
                                    Toast.makeText(
                                        context,
                                        "No se pudo salir del grupo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isLeavingGroup = false
                                }
                            }
                        },
                        enabled = !isLeavingGroup
                    ) {
                        if (isLeavingGroup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Salir",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            dismissButton = {
                if (!amOwner) {
                    TextButton(
                        onClick = {
                            if (!isLeavingGroup) {
                                showLeaveGroupDialog = false
                            }
                        },
                        enabled = !isLeavingGroup
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // 🔹 Sheet "Añadir miembros"
    if (showAddMembersPicker) {
        AddMembersPickerSheet(
            chatId = chat.id,
            exclude = members.map { it.uid }.toSet(),
            currentMembersCount = members.size,
            maxMembers = maxMembers,
            onDismiss = { showAddMembersPicker = false }
        )
    }
}


private fun buildInitialGroupMembersFromCache(
    chat: Chat,
    myUid: String?,
    context: Context
): Map<String, ChatMemberUiModel> {
    val ids = chat.participantIds
    if (ids.isEmpty()) return emptyMap()

    val result = mutableMapOf<String, ChatMemberUiModel>()

    ids.forEach { uid ->
        // No hacemos lógica rara: si hay algo en caché, lo usamos.
        val cachedName = ChatsLocalCache.getUserName(context, uid)?.trim()
        val name = if (!cachedName.isNullOrEmpty()) cachedName else "Usuario"

        val avatar = ChatsLocalCache.getUserAvatar(context, uid)

        // Solo creamos modelo si hay algo útil (nombre o avatar).
        if (!avatar.isNullOrBlank() || !cachedName.isNullOrEmpty()) {
            result[uid] = ChatMemberUiModel(
                uid = uid,
                displayName = name,
                avatarUrl = avatar
            )
        }
    }

    return result
}

@Composable
private fun GroupMemberAvatar(member: ChatMemberUiModel) {
    val ringBrush = Brush.sweepGradient(
        listOf(
            Color(0xFF00D4FF),
            Color(0xFF7C4DFF),
            Color(0xFF00D4FF)
        )
    )

    // 🔧 Escalas para controlar grosor de aros
    val outerRingScale = 32f / 36f   // subir hacia 1f → aro degradado más fino
    val innerContentScale = 26f / 30f // subir hacia 1f → aro blanco más fino

    Box(
        modifier = Modifier
            .size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        // Aro exterior degradado
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(brush = ringBrush)
        )

        // Anillo blanco
        Box(
            modifier = Modifier
                .fillMaxSize(outerRingScale)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            // Círculo interior donde va la foto / iniciales
            Box(
                modifier = Modifier
                    .fillMaxSize(innerContentScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val localFile = avatarLocalFile(context, member.uid)
                val hasLocal = localFile.exists()
                val modelUrl = member.avatarUrl

                when {
                    hasLocal -> {
                        val bitmap = remember(localFile.path) {
                            try {
                                BitmapFactory.decodeFile(localFile.path)?.asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Avatar miembro grupo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!modelUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = modelUrl,
                                contentDescription = "Avatar miembro grupo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onSuccess = { state: AsyncImagePainter.State.Success ->
                                    saveDrawableToFile(state.result.drawable, localFile)
                                }
                            )
                        }
                    }

                    !modelUrl.isNullOrEmpty() -> {
                        AsyncImage(
                            model = modelUrl,
                            contentDescription = "Avatar miembro grupo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onSuccess = { state: AsyncImagePainter.State.Success ->
                                saveDrawableToFile(state.result.drawable, localFile)
                            }
                        )
                    }

                    else -> {
                        // Sin foto → iniciales del nombre del miembro
                        val initials = member.displayName
                            .trim()
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString("") { it.first().uppercaseChar().toString() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}



private data class ChatMemberUiModel(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val isMe: Boolean = false
)

// ⬇️ NUEVO: modelo local para candidatos de "Añadir miembros"
private data class AddMemberCandidate(
    val uid: String,
    val displayName: String,
    val username: String?,
    val avatarUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMembersPickerSheet(
    chatId: String,
    exclude: Set<String>,
    currentMembersCount: Int,
    maxMembers: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val myUid = auth.currentUser?.uid

    val actionBlue = Color(0xFF007AFF)

    val effectiveMax = if (maxMembers > 0) maxMembers else 64
    val remainingSlots = (effectiveMax - currentMembersCount).coerceAtLeast(0)

    var query by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Lista inicial (todos los usuarios que cargamos al abrir)
    var initialResults by remember { mutableStateOf<List<AddMemberCandidate>>(emptyList()) }
    // Lista visible actual (inicial o filtrada)
    var results by remember { mutableStateOf<List<AddMemberCandidate>>(emptyList()) }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val effectiveExclude = remember(exclude, myUid) {
        buildSet {
            addAll(exclude)
            myUid?.let { add(it) }
        }
    }

    // 🔹 Carga inicial: lista "general" de usuarios para ir ticando (como iOS)
    LaunchedEffect(chatId) {
        if (remainingSlots <= 0) {
            initialResults = emptyList()
            results = emptyList()
            return@LaunchedEffect
        }

        isSearching = true
        errorMessage = null
        try {
            // Intentamos ordenar por displayNameLower; si no, por usernameLower
            val snap = try {
                db.collection("users")
                    .orderBy("displayNameLower")
                    .limit(50)
                    .get()
                    .await()
            } catch (_: Exception) {
                db.collection("users")
                    .orderBy("usernameLower")
                    .limit(50)
                    .get()
                    .await()
            }

            val list = snap.documents.mapNotNull { doc ->
                val uid = doc.id
                if (effectiveExclude.contains(uid)) return@mapNotNull null

                // Igual que iOS: displayName ?? username ?? email
                val displayName = (
                        doc.getString("displayName")
                            ?: doc.getString("username")
                            ?: doc.getString("email")
                        )?.trim().orEmpty()

                if (displayName.isEmpty()) return@mapNotNull null

                val username = doc.getString("username")
                val avatarUrl = doc.getString("profileImageUrl")
                    ?: doc.getString("photoUrl")

                AddMemberCandidate(
                    uid = uid,
                    displayName = displayName,
                    username = username,
                    avatarUrl = avatarUrl
                )
            }

            val sorted = list.sortedWith(
                compareBy<AddMemberCandidate> {
                    it.displayName.lowercase(Locale.getDefault())
                }.thenBy {
                    it.username?.lowercase(Locale.getDefault()) ?: ""
                }
            )

            initialResults = sorted
            results = sorted
        } catch (e: Exception) {
            Log.e("AddMembersPickerSheet", "Error cargando usuarios iniciales", e)
            errorMessage = "No se pudieron cargar los usuarios. Inténtalo de nuevo."
            initialResults = emptyList()
            results = emptyList()
        } finally {
            isSearching = false
        }
    }

    // 🔹 Búsqueda reactiva: al escribir en el campo
    LaunchedEffect(query) {
        val text = query.trim().lowercase(Locale.getDefault())
        if (remainingSlots <= 0) return@LaunchedEffect

        // Sin texto → mostramos la lista inicial
        if (text.isEmpty()) {
            results = initialResults
            errorMessage = null
            return@LaunchedEffect
        }

        // 1 letra → filtro local sobre initialResults (rápido)
        if (text.length < 2) {
            results = initialResults.filter {
                it.displayName.lowercase(Locale.getDefault()).contains(text) ||
                        (it.username?.lowercase(Locale.getDefault())?.contains(text) == true)
            }
            errorMessage = null
            return@LaunchedEffect
        }

        // ≥2 letras → igual que iOS: doble query usernameLower + displayNameLower
        isSearching = true
        errorMessage = null

        try {
            val limit = 24L

            suspend fun runQuery(field: String): List<AddMemberCandidate> {
                val snap = db.collection("users")
                    .whereGreaterThanOrEqualTo(field, text)
                    .whereLessThan(field, text + "\uf8ff")
                    .limit(limit)
                    .get()
                    .await()

                return snap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (effectiveExclude.contains(uid)) return@mapNotNull null

                    val displayName = (
                            doc.getString("displayName")
                                ?: doc.getString("username")
                                ?: doc.getString("email")
                            )?.trim().orEmpty()
                    if (displayName.isEmpty()) return@mapNotNull null

                    val username = doc.getString("username")
                    val avatarUrl = doc.getString("profileImageUrl")
                        ?: doc.getString("photoUrl")

                    AddMemberCandidate(
                        uid = uid,
                        displayName = displayName,
                        username = username,
                        avatarUrl = avatarUrl
                    )
                }
            }

            val usernameResults = runQuery("usernameLower")
            val displayNameResults = runQuery("displayNameLower")

            val map = LinkedHashMap<String, AddMemberCandidate>()
            (usernameResults + displayNameResults).forEach { candidate ->
                map.putIfAbsent(candidate.uid, candidate)
            }

            val list = map.values.sortedWith(
                compareBy<AddMemberCandidate> {
                    it.displayName.lowercase(Locale.getDefault())
                }.thenBy {
                    it.username?.lowercase(Locale.getDefault()) ?: ""
                }
            )

            results = list
        } catch (e: Exception) {
            Log.e("AddMembersPickerSheet", "Error buscando usuarios", e)
            errorMessage = "No se pudieron cargar los usuarios. Inténtalo de nuevo."
            results = emptyList()
        } finally {
            isSearching = false
        }
    }

    val hasSelection = selectedIds.isNotEmpty()

    fun toggleSelection(uid: String) {
        val already = selectedIds.contains(uid)
        val currentCount = selectedIds.size
        val canAddMore = currentCount < remainingSlots

        selectedIds = if (already) {
            selectedIds - uid
        } else if (canAddMore) {
            selectedIds + uid
        } else {
            selectedIds
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)          // 👈 sheet alto, casi pantalla completa
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 🔹 Cabecera estilo iOS: Cancelar | Título | Añadir(x)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    Text("Cancelar", color = actionBlue)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Añadir miembros",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        if (!hasSelection || isSubmitting || remainingSlots <= 0) return@TextButton
                        scope.launch {
                            try {
                                isSubmitting = true
                                GroupApi.addMembersRespectingLimit(
                                    chatId = chatId,
                                    userIds = selectedIds.toList()
                                )
                                sheetState.hide()
                                onDismiss()
                            } catch (e: Exception) {
                                Log.e("AddMembersPickerSheet", "Error añadiendo miembros", e)
                                errorMessage = "No se pudieron añadir los miembros. Inténtalo de nuevo."
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = hasSelection && !isSubmitting && remainingSlots > 0
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        val suffix =
                            if (selectedIds.isEmpty()) "" else " (${selectedIds.size})"
                        Text("Añadir$suffix", color = actionBlue)
                    }
                }
            }

            if (remainingSlots <= 0) {
                Text(
                    text = "Este grupo ya está completo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    text = "Puedes añadir hasta $remainingSlots miembro(s) más.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 🔹 Buscador
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting && remainingSlots > 0,
                label = { Text("Buscar por nombre o @usuario") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                errorMessage = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Borrar búsqueda"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { /* ya reaccionamos con LaunchedEffect(query) */ }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 🔹 Resultados
            when {
                isSearching && initialResults.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                errorMessage != null && results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isEmpty()) {
                                "No hay usuarios disponibles para añadir."
                            } else {
                                "No se han encontrado usuarios para \"$query\"."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(results, key = { it.uid }) { candidate ->
                            val isSelected = selectedIds.contains(candidate.uid)
                            val canSelectMore =
                                selectedIds.size < remainingSlots || isSelected

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !isSubmitting &&
                                                remainingSlots > 0 &&
                                                canSelectMore
                                    ) {
                                        toggleSelection(candidate.uid)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GroupMemberAvatar(
                                    member = ChatMemberUiModel(
                                        uid = candidate.uid,
                                        displayName = candidate.displayName,
                                        avatarUrl = candidate.avatarUrl
                                    )
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = candidate.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    candidate.username
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { username ->
                                            Text(
                                                text = "@$username",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Seleccionado",
                                        tint = actionBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInfoProfileSheet(
    chat: Chat,
    userStatus: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = chat.displayName ?: "Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (userStatus.isNotBlank()) {
                Text(
                    text = userStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Participantes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            chat.participantIds.forEach { id ->
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInfoGroupSheet(
    chat: Chat,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memberCount = chat.participantIds.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = chat.displayName ?: "Grupo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (memberCount > 0) {
                Text(
                    text = "$memberCount miembros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Participantes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            chat.participantIds.forEach { id ->
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardChatPickerSheet(
    currentChatId: String,
    myUid: String,
    onDismiss: () -> Unit,
    onChatSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isLoading by remember { mutableStateOf(true) }
    var chats by remember { mutableStateOf<List<ForwardChatUiModel>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentChatId, myUid) {
        isLoading = true
        loadError = null
        try {
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("chats")
                .whereArrayContains("participants", myUid)
                .get()
                .await()

            val items = snapshot.documents
                .filter { it.id != currentChatId }
                .map { doc ->
                    val isSupport = doc.getBoolean("isSupport") ?: false

                    val participantsAny = doc.get("participants") as? List<*>
                    val participants = participantsAny
                        ?.mapNotNull { it as? String }
                        ?: emptyList()

                    val isGroup = !isSupport && participants.size > 2

                    val title: String = when {
                        isSupport -> {
                            // soporte: usa título si existe, si no "Soporte"
                            doc.getString("title")
                                ?: doc.getString("name")
                                ?: doc.getString("displayName")
                                ?: "Soporte"
                        }

                        isGroup -> {
                            // grupo: usa name/displayName del doc o un fallback
                            doc.getString("title")
                                ?: doc.getString("name")
                                ?: doc.getString("displayName")
                                ?: "Grupo"
                        }

                        else -> {
                            // 1:1 → nombre del otro usuario (igual que ChatsHomeViewModel)
                            val me = myUid
                            val otherId = participants.firstOrNull { it != me && it != SUPPORT_BOT_ID }

                            if (otherId != null) {
                                try {
                                    val userSnap = db.collection("users")
                                        .document(otherId)
                                        .get()
                                        .await()

                                    val data = userSnap.data
                                    if (data != null) {
                                        preferUsername(data, otherId)
                                    } else {
                                        stubName(otherId)
                                    }
                                } catch (_: Exception) {
                                    stubName(otherId)
                                }
                            } else {
                                "Chat"
                            }
                        }
                    }

                    ForwardChatUiModel(
                        id = doc.id,
                        title = title,
                        isGroup = isGroup,
                        isSupport = isSupport
                    )
                }

            chats = items
        } catch (e: Exception) {
            loadError = "No se pudieron cargar tus chats."
            chats = emptyList()
        } finally {
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Reenviar mensaje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Elige un chat de destino",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                loadError != null -> {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                chats.isEmpty() -> {
                    Text(
                        text = "No hay otros chats disponibles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChatSelected(chat.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when {
                                    chat.isSupport -> "🛟"
                                    chat.isGroup -> "👥"
                                    else -> "👤"
                                }

                                Text(
                                    text = icon,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private data class ForwardChatUiModel(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val isSupport: Boolean
)

@Composable
private fun DateSeparator(dateMillis: Long) {
    val date = remember(dateMillis) { Date(dateMillis) }

    // Igual que iOS: Locale fijo es_ES y formato "d MMMM yyyy"
    val formatter = remember {
        SimpleDateFormat("d MMMM yyyy", Locale("es", "ES"))
    }
    val baseLabel = formatter.format(date)

    val label = when {
        isToday(dateMillis) -> "Hoy"
        isYesterday(dateMillis) -> "Ayer"
        else -> baseLabel
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        // Línea izquierda
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
        )

        // "Píldora" central con la fecha
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Línea derecha
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
        )
    }
}

@Composable
private fun UnreadSeparator(count: Int) {
    val text = if (count == 1) {
        "1 mensaje no leído"
    } else {
        "$count mensajes no leídos"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun bubbleShape(
    isMine: Boolean,
    groupPosition: MessageGroupPosition
): RoundedCornerShape {
    val radius = 20.dp
    val small = 4.dp

    return if (isMine) {
        when (groupPosition) {
            MessageGroupPosition.SOLO ->
                RoundedCornerShape(radius)

            MessageGroupPosition.TOP ->
                RoundedCornerShape(
                    topStart = radius,
                    topEnd = radius,
                    bottomEnd = small,
                    bottomStart = radius
                )

            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(
                    topStart = radius,
                    topEnd = small,
                    bottomEnd = small,
                    bottomStart = radius
                )

            MessageGroupPosition.BOTTOM ->
                RoundedCornerShape(
                    topStart = radius,
                    topEnd = small,
                    bottomEnd = radius,
                    bottomStart = radius
                )
        }
    } else {
        when (groupPosition) {
            MessageGroupPosition.SOLO ->
                RoundedCornerShape(radius)

            MessageGroupPosition.TOP ->
                RoundedCornerShape(
                    topStart = radius,
                    topEnd = radius,
                    bottomEnd = radius,
                    bottomStart = small
                )

            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(
                    topStart = small,
                    topEnd = radius,
                    bottomEnd = radius,
                    bottomStart = small
                )

            MessageGroupPosition.BOTTOM ->
                RoundedCornerShape(
                    topStart = radius,
                    topEnd = radius,
                    bottomEnd = radius,
                    bottomStart = small
                )
        }
    }
}

private fun openChatAttachment(
    context: Context,
    message: Message
) {
    val url = message.fileUrl ?: return

    try {
        val uri = Uri.parse(url)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val mime = message.fileType?.trim()

            // Si tenemos un MIME real (no application/octet-stream), lo usamos.
            // Si no, dejamos que Android lo resuelva a partir de la URL.
            if (!mime.isNullOrEmpty() && mime != "application/octet-stream") {
                setDataAndType(uri, mime)
            } else {
                data = uri
            }

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ChatDetailScreen", "Error abriendo adjunto", e)
        Toast.makeText(
            context,
            "No se pudo abrir el archivo",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun calculateGroupPosition(
    messages: List<Message>,
    index: Int
): MessageGroupPosition {
    val current = messages[index]
    val prev = messages.getOrNull(index - 1)
    val next = messages.getOrNull(index + 1)

    fun sameSender(a: Message?, b: Message): Boolean {
        if (a == null) return false
        return a.senderId == b.senderId
    }

    fun closeInTime(a: Message?, b: Message): Boolean {
        if (a == null) return false
        val diff = kotlin.math.abs(a.createdAtMillis - b.createdAtMillis)
        return diff <= 5 * 60 * 1000L // 5 minutos
    }

    val prevSame = sameSender(prev, current) && closeInTime(prev, current)
    val nextSame = sameSender(next, current) && closeInTime(next, current)

    return when {
        !prevSame && !nextSame -> MessageGroupPosition.SOLO
        !prevSame && nextSame -> MessageGroupPosition.TOP
        prevSame && nextSame -> MessageGroupPosition.MIDDLE
        prevSame && !nextSame -> MessageGroupPosition.BOTTOM
        else -> MessageGroupPosition.SOLO
    }
}

private val linkRegex = Regex("(https?://\\S+|spots://\\S+)")

// Devuelve el primer http(s):// que encuentre en el texto, si existe
private fun firstHttpUrl(text: String): String? {
    val match = linkRegex.find(text) ?: return null
    val url = match.value
    return if (url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
    ) {
        url
    } else {
        null
    }
}


private suspend fun uploadGroupPhoto(
    chatId: String,
    ownerUid: String,
    uri: Uri,
    context: Context
): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.use { it.readBytes() }
        if (bytes.isEmpty()) return null

        val ts = System.currentTimeMillis()
        val path = "chats/$chatId/$ownerUid/avatar_v$ts.jpg"

        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child(path)

        storageRef.putBytes(bytes).await()

        val rawUrl = storageRef.downloadUrl.await().toString()

        val finalUrl = if (rawUrl.contains("alt=media")) {
            val sep = if (rawUrl.contains("?")) "&" else "?"
            rawUrl + sep + "bust=$ts"
        } else {
            rawUrl
        }

        // ✅ Actualizamos caché de grupo para futuras aperturas
        try {
            ChatsLocalCache.saveGroupPhoto(context, chatId, finalUrl)

            // Y sobrescribimos el fichero local usado por ChatAvatarPlaceholder
            val localFile = avatarLocalFile(context, chatId)
            localFile.parentFile?.mkdirs()
            localFile.outputStream().use { out ->
                out.write(bytes)
            }
        } catch (e: Exception) {
            Log.e("ChatGroupInfo", "Error escribiendo caché local de avatar", e)
        }

        finalUrl
    } catch (e: Exception) {
        Log.e("ChatGroupInfo", "uploadGroupPhoto error", e)
        null
    }
}




private fun senderHeaderLabel(senderId: String): String {
    if (senderId.isBlank()) return ""
    return senderId
}

private fun buildLinkAnnotatedString(
    text: String,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        for (match in linkRegex.findAll(text)) {
            val range = match.range

            // Texto normal antes del link
            if (range.first > currentIndex) {
                append(text.substring(currentIndex, range.first))
            }

            val url = match.value
            val start = length
            append(url)
            val end = length

            val tag = if (url.startsWith("spots://")) "DEEP_LINK" else "URL"

            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = tag,
                annotation = url,
                start = start,
                end = end
            )

            currentIndex = range.last + 1
        }

        // Resto del texto después del último link
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

private fun openExternalUri(context: Context, rawUrl: String) {
    val uri = try {
        Uri.parse(rawUrl)
    } catch (e: Exception) {
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No hay ninguna actividad que pueda manejar este intent, lo ignoramos
    }
}

private fun extractSingleHttpUrlForPreview(text: String): String? {
    val matches = linkRegex
        .findAll(text)
        .map { it.value }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .toList()

    if (matches.size != 1) return null

    val only = matches.first()
    return if (text.trim() == only) only else null
}

private fun isToday(millis: Long): Boolean {
    return isSameDay(millis, System.currentTimeMillis())
}

private fun isYesterday(millis: Long): Boolean {
    val dayMs = 24L * 60 * 60 * 1000
    return isSameDay(millis, System.currentTimeMillis() - dayMs)
}

private fun isSameDay(millis1: Long, millis2: Long): Boolean {
    val dayMs = 24L * 60 * 60 * 1000
    val tz = TimeZone.getDefault()
    val d1 = (millis1 + tz.getOffset(millis1)) / dayMs
    val d2 = (millis2 + tz.getOffset(millis2)) / dayMs
    return d1 == d2
}

private fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024) {
        return String.format(Locale.getDefault(), "%.1f KB", kb)
    }

    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

private fun preferUsername(data: Map<String, Any>, uid: String): String {
    fun field(key: String): String =
        (data[key] as? String)?.trim().orEmpty()

    val username = field("username")
    if (username.isNotEmpty()) return username

    val usernameLower = field("usernameLower")
    if (usernameLower.isNotEmpty()) return usernameLower

    val displayName = field("displayName")
    if (displayName.isNotEmpty()) return displayName

    val name = field("name")
    if (name.isNotEmpty()) return name

    val fullName = field("fullName")
    if (fullName.isNotEmpty()) return fullName

    val email = field("email")
    if (email.isNotEmpty()) {
        val local = email.substringBefore("@").trim()
        if (local.isNotEmpty()) return local
    }

    return stubName(uid)
}

private fun stubName(uid: String): String =
    "usuario-" + uid.take(6)

private fun classifyLinkHost(host: String?): LinkHostKind {
    val h = host?.lowercase(Locale.getDefault()) ?: return LinkHostKind.DEFAULT
    return when {
        "youtube.com" in h || "youtu.be" in h -> LinkHostKind.YOUTUBE
        "instagram.com" in h -> LinkHostKind.INSTAGRAM
        else -> LinkHostKind.DEFAULT
    }
}

private data class UrlPreviewCacheEntry(
    val title: String?,
    val aspectRatio: Float?
)

private object UrlPreviewDiskCache {
    private const val PREFS_NAME = "url_preview_meta"

    fun loadMeta(context: Context, url: String): UrlPreviewCacheEntry? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasTitle = prefs.contains("${url}#title")
        val hasRatio = prefs.contains("${url}#ratio")

        if (!hasTitle && !hasRatio) return null

        val title = prefs.getString("${url}#title", null)
        val ratio = if (hasRatio) {
            prefs.getFloat("${url}#ratio", 0f).takeIf { it > 0f }
        } else {
            null
        }

        return UrlPreviewCacheEntry(
            title = title,
            aspectRatio = ratio
        )
    }

    fun saveMeta(context: Context, url: String, entry: UrlPreviewCacheEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            entry.title?.let { putString("${url}#title", it) }
            entry.aspectRatio?.let { putFloat("${url}#ratio", it) }
        }.apply()
    }

    fun updateRatio(context: Context, url: String, ratio: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("${url}#ratio", ratio)
            .apply()
    }

    fun localImageFile(context: Context, url: String): File {
        val dir = File(context.filesDir, "url_previews")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Hash simple para nombre de fichero estable
        val safeName = url.hashCode().toString()
        return File(dir, "$safeName.jpg")
    }
}

private data class UrlPreviewNetworkMeta(
    val title: String?,
    val imageUrl: String?
)

private object UrlPreviewNetwork {

    suspend fun fetchMeta(url: String): UrlPreviewNetworkMeta? =
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }

                conn.inputStream.use { input ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val sb = StringBuilder()
                    var line: String?

                    // Leemos sólo el <head> o unos cuantos KB para no matar la red
                    while (reader.readLine().also { line = it } != null && sb.length < 64_000) {
                        sb.append(line).append('\n')
                        if (line!!.contains("</head>", ignoreCase = true)) break
                    }

                    val html = sb.toString()

                    val ogTitle = Regex(
                        """<meta[^>]+property=["']og:title["'][^>]+content=["'](.*?)["']""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(html)?.groupValues?.getOrNull(1)

                    val ogImage = Regex(
                        """<meta[^>]+property=["']og:image["'][^>]+content=["'](.*?)["']""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(html)?.groupValues?.getOrNull(1)

                    val titleFromTag = Regex(
                        """<title[^>]*>(.*?)</title>""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(html)?.groupValues?.getOrNull(1)

                    UrlPreviewNetworkMeta(
                        title = (ogTitle ?: titleFromTag)?.trim()?.takeIf { it.isNotEmpty() },
                        imageUrl = ogImage?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
}

private fun buildYoutubeThumbnailUrl(uri: Uri?): String? {
    if (uri == null) return null
    val host = uri.host?.lowercase() ?: return null

    return when {
        "youtu.be" in host -> {
            val id = uri.path?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
            "https://img.youtube.com/vi/$id/maxresdefault.jpg"
        }

        "youtube.com" in host -> {
            val id = uri.getQueryParameter("v")?.takeIf { it.isNotBlank() } ?: return null
            "https://img.youtube.com/vi/$id/maxresdefault.jpg"
        }

        else -> null
    }
}


private object ChatPrefsRepository {
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Guarda mute por chat en:
     * users/{uid}/meta/chatPrefs/prefs/{chatId} { mute: Boolean }
     * Igual que ChatPrefsService en iOS.
     */
    suspend fun setMute(chatId: String, mute: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf("mute" to mute)

        db.collection("users")
            .document(uid)
            .collection("meta")
            .document("chatPrefs")
            .collection("prefs")
            .document(chatId)
            .set(data, SetOptions.merge())
            .await()
    }

    /**
     * Lee mute por chat (default = false) desde la misma ruta que iOS.
     */
    suspend fun getMute(chatId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false

        return try {
            val snap = db.collection("users")
                .document(uid)
                .collection("meta")
                .document("chatPrefs")
                .collection("prefs")
                .document(chatId)
                .get()
                .await()

            (snap.get("mute") as? Boolean) ?: false
        } catch (_: Exception) {
            false
        }
    }
}

