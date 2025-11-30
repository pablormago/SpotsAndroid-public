package com.spotitfly.app

import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.spotitfly.app.data.chat.Chat
import com.spotitfly.app.data.chat.ChatsLocalCache
import com.spotitfly.app.data.chat.previewLabel
import com.spotitfly.app.ui.chats.ChatsHomeViewModel
import com.spotitfly.app.ui.chats.CreateGroupScreen
import com.spotitfly.app.ui.chats.NewChatScreen
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.spotitfly.app.data.chat.GroupApi
import android.widget.Toast
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults



@Composable
fun ChatsHomeScreen(
    onBack: () -> Unit,
    initialJoinInviteId: String? = null,
    initialChatId: String? = null,
    viewModel: ChatsHomeViewModel = viewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var initialChatHandled by remember { mutableStateOf(false) }

    // NUEVO: flags para flujos de creación
    var showNewChat by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showJoinWithInvite by remember { mutableStateOf(false) }

    // NUEVO: soporte para invitación inicial (deep link / botón externo)
    var initialInviteHandled by remember { mutableStateOf(false) }
    var initialInviteForJoin by remember { mutableStateOf<String?>(null) }

    val myUid = FirebaseAuth.getInstance().currentUser?.uid

    var showHidden by remember { mutableStateOf(false) }

    val visibleChats = remember(chats, myUid) {
        if (myUid.isNullOrBlank()) {
            chats
        } else {
            chats.filter { !it.isHiddenFor(myUid) }
        }
    }
    val hiddenChats = remember(chats, myUid) {
        if (myUid.isNullOrBlank()) {
            emptyList<Chat>()
        } else {
            chats.filter { it.isHiddenFor(myUid) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LaunchedEffect(initialJoinInviteId, initialInviteHandled) {
        if (!initialInviteHandled && !initialJoinInviteId.isNullOrBlank()) {
            initialInviteForJoin = initialJoinInviteId
            showJoinWithInvite = true
            initialInviteHandled = true
        }
    }

    LaunchedEffect(initialChatId, chats, initialChatHandled) {
        if (!initialChatHandled && !initialChatId.isNullOrBlank() && selectedChat == null) {
            val target = chats.firstOrNull { it.id == initialChatId }
            if (target != null) {
                selectedChat = target
                initialChatHandled = true
            }
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7)) // gris iOS agrupado
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

    when {
        // 👇 1) Estamos esperando abrir un chat inicial (deep link / volver de detalle)
        //    Mientras tanto NO mostramos la lista para evitar el flash.
        !initialChatHandled && !initialChatId.isNullOrBlank() && selectedChat == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        // 2) Pantalla principal: lista de chats

        selectedChat == null && !showNewChat && !showNewGroup && !showJoinWithInvite -> {
                ChatsTopBar(
                    onBack = onBack,
                    onNewChat = { showNewChat = true },
                    onNewGroup = { showNewGroup = true },
                    onJoinWithInvite = {
                        showJoinWithInvite = true
                    }
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Sin chats todavía",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cuando envíes un mensaje a otro usuario, aparecerá aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CommunityGuidelinesFooter()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header de sección "Chats" (igual que iOS)
                        item {
                            Text(
                                text = "Chats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Chats visibles
                        items(visibleChats) { chat ->
                            SwipeableChatRow(
                                chat = chat,
                                myUid = myUid,
                                onClick = { selectedChat = chat },
                                onMarkRead = { c -> viewModel.markChatAsReadFromHome(c.id) },
                                onToggleHidden = { c, hide -> viewModel.setHidden(c.id, hide) }
                            )
                        }

                        // Sección de chats ocultos
                        if (hiddenChats.isNotEmpty()) {
                            item {
                                HiddenChatsHeader(
                                    count = hiddenChats.size,
                                    expanded = showHidden,
                                    onToggle = { showHidden = !showHidden }
                                )
                            }

                            if (showHidden) {
                                items(hiddenChats) { chat ->
                                    SwipeableChatRow(
                                        chat = chat,
                                        myUid = myUid,
                                        onClick = { selectedChat = chat },
                                        onMarkRead = { c -> viewModel.markChatAsReadFromHome(c.id) },
                                        onToggleHidden = { c, hide -> viewModel.setHidden(c.id, hide) }
                                    )
                                }
                            }
                        }

                        // Footer Community Guidelines (siempre al final de la lista)
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            CommunityGuidelinesFooter()
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                }

            }

            // Flujo: nuevo chat 1-1
            showNewChat -> {
                SimpleTopBar(
                    title = "Nuevo chat",
                    onBack = { showNewChat = false }
                )
                NewChatScreen(
                    onBack = { showNewChat = false },
                    onChatCreated = { chat ->
                        showNewChat = false
                        selectedChat = chat
                    }
                )
            }

            // Flujo: nuevo grupo
            showNewGroup -> {
                SimpleTopBar(
                    title = "Nuevo grupo",
                    onBack = { showNewGroup = false }
                )
                CreateGroupScreen(
                    onBack = { showNewGroup = false },
                    onGroupCreated = { chat ->
                        showNewGroup = false
                        selectedChat = chat
                    }
                )
            }
        // Flujo: unirse con invitación
        showJoinWithInvite -> {
            SimpleTopBar(
                title = "Unirse por invitación",
                onBack = {
                    showJoinWithInvite = false
                    initialInviteForJoin = null
                }
            )
            JoinWithInviteScreen(
                onBack = {
                    showJoinWithInvite = false
                    initialInviteForJoin = null
                },
                onJoined = { chatId ->
                    showJoinWithInvite = false
                    initialInviteForJoin = null

                    // Si el chat ya está en la lista, lo abrimos directamente
                    val found = chats.firstOrNull { it.id == chatId }
                    if (found != null) {
                        selectedChat = found
                    } else {
                        // Si aún no ha llegado por el listener, al menos volvemos a la lista;
                        // el grupo aparecerá ahí en cuanto Firestore lo notifique.
                    }
                },
                initialInviteId = initialInviteForJoin
            )
        }

        // Detalle de chat
            else -> {
                selectedChat?.let { chat ->
                    ChatDetailScreen(
                        chat = chat,
                        onBack = { selectedChat = null }
                    )
                }
            }
        }
    }

}

@Composable
private fun SwipeableChatRow(
    chat: Chat,
    myUid: String?,
    onClick: () -> Unit,
    onMarkRead: (Chat) -> Unit,
    onToggleHidden: (Chat, Boolean) -> Unit
) {
    var offsetX by remember(chat.id) { mutableStateOf(0f) }
    val density = LocalDensity.current

    // Distancia mínima para disparar la acción
    val thresholdPx = with(density) { 80.dp.toPx() }
    // Ancho máximo que se muestra del "botón" de acción
    val maxActionWidthPx = with(density) { 120.dp.toPx() }
    // Límite máximo de desplazamiento de la fila (para que no se vaya a Cuenca)
    val maxSwipePx = maxActionWidthPx * 1.5f

    // Colores/textos/iconos según estado del swipe
    val (bgColor, label, alignment, icon) = remember(offsetX, myUid) {
        when {
            offsetX > 0f -> {
                // Arrastrando hacia la derecha → marcar leído
                Quadruple(
                    Color(0xFF2E7D32),        // verde
                    "Marcar leído",
                    Alignment.CenterStart,
                    Icons.Filled.Check
                )
            }
            offsetX < 0f -> {
                // Arrastrando hacia la izquierda → ocultar / mostrar
                val isHidden = chat.isHiddenFor(myUid ?: "")
                if (isHidden) {
                    // Ya está oculto → swipe para MOSTRAR (azul)
                    Quadruple(
                        Color(0xFF1565C0),    // azul
                        "Mostrar chat",
                        Alignment.CenterEnd,
                        Icons.Filled.Visibility
                    )
                } else {
                    // Está visible → swipe para OCULTAR (rojo)
                    Quadruple(
                        Color(0xFFC62828),    // rojo
                        "Ocultar chat",
                        Alignment.CenterEnd,
                        Icons.Filled.VisibilityOff
                    )
                }
            }
            else -> {
                Quadruple(
                    Color.Transparent,
                    "",
                    Alignment.CenterStart,
                    Icons.Filled.Check
                )
            }
        }
    }

    val absOffset = abs(offsetX)
    val actionVisible = absOffset > 0f && bgColor != Color.Transparent && label.isNotEmpty()
    val actionWidthPx = min(absOffset, maxActionWidthPx)
    val actionWidthDp = with(density) { actionWidthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Fondo de acción (franja) sólo en el lado expuesto, con ancho proporcional al swipe
        if (actionVisible && actionWidthPx > 1f) {
            Row(
                modifier = Modifier.matchParentSize(),
            ) {
                if (offsetX > 0f) {
                    // Izquierda → derecha → franja a la izquierda
                    Box(
                        modifier = Modifier
                            .width(actionWidthDp)
                            .fillMaxHeight()
                            .background(bgColor)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // El resto se queda transparente
                    Spacer(modifier = Modifier.weight(1f))
                } else if (offsetX < 0f) {
                    // Derecha → izquierda → franja a la derecha
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .width(actionWidthDp)
                            .fillMaxHeight()
                            .background(bgColor)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Fila desplazable encima del fondo (card con sombra como iOS)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .draggable(
                    state = rememberDraggableState { delta ->
                        // Solo movemos en horizontal
                        offsetX = (offsetX + delta)
                            .coerceIn(-maxSwipePx, maxSwipePx)
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            offsetX > thresholdPx -> {
                                // Izquierda → derecha: marcar leído
                                onMarkRead(chat)
                            }
                            offsetX < -thresholdPx -> {
                                // Derecha → izquierda: ocultar / mostrar
                                val hide = !chat.isHiddenFor(myUid ?: "")
                                onToggleHidden(chat, hide)
                            }
                        }
                        // Volver a posición neutra después del gesto
                        offsetX = 0f
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                ChatRow(
                    chat = chat,
                    onClick = onClick
                )
            }
        }

    }
}




// Pequeña helper para devolver 4 valores en el remember
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)


@Composable
private fun ChatRow(
    chat: Chat,
    onClick: () -> Unit
) {
    val title = when {
        chat.displayName != null -> chat.displayName
        chat.isSupport -> "Soporte"
        chat.isGroup -> "Chat de grupo"
        else -> "Chat"
    } ?: "Chat"

    val context = LocalContext.current
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val isUnread = myUid != null && chat.isUnreadFor(myUid)
    val preview = chat.previewLabel()

    val timeText = chat.updatedAtMillis?.let { millis ->
        val date = Date(millis)
        android.text.format.DateFormat.getTimeFormat(context).format(date)
    }

    val ringBrush = Brush.sweepGradient(
        listOf(
            Color(0xFF00D4FF),
            Color(0xFF7C4DFF),
            Color(0xFF00D4FF)
        )
    )

    val isGroup = chat.isGroup || chat.participantIds.size > 2
    val otherUserId = remember(myUid, chat.participantIds) {
        chat.participantIds.firstOrNull { it != myUid }
    }

    // Valor inicial: lo que haya en caché persistente (SharedPreferences)
    val initialAvatarUrl = remember(chat.id, isGroup, otherUserId) {
        try {
            if (isGroup) {
                ChatsLocalCache.getGroupPhoto(context, chat.id)
            } else if (!otherUserId.isNullOrBlank()) {
                ChatsLocalCache.getUserAvatar(context, otherUserId)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    var avatarUrl by remember(chat.id) { mutableStateOf<String?>(initialAvatarUrl) }

    LaunchedEffect(chat.id, isGroup, otherUserId) {
        try {
            // Refresco desde Firestore + guardado en caché local
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
            } else if (!otherUserId.isNullOrBlank()) {
                val userDoc = db.collection("users")
                    .document(otherUserId)
                    .get()
                    .await()

                val remote = userDoc.getString("profileImageUrl")
                avatarUrl = remote

                if (!remote.isNullOrBlank()) {
                    ChatsLocalCache.saveUserAvatar(context, otherUserId, remote)
                }
            } else {
                avatarUrl = null
            }
        } catch (_: Exception) {
            // Si algo falla, dejamos el valor anterior
        }
    }

    // --- Estado de línea / última vez / miembros (paridad iOS) ---
    var statusText by remember(chat.id) { mutableStateOf("") }
    var isOnline by remember(chat.id) { mutableStateOf(false) }

    DisposableEffect(chat.id, isGroup, otherUserId) {
        if (isGroup) {
            statusText = "${chat.participantIds.size} miembros"
            isOnline = false
            onDispose { }
        } else if (otherUserId.isNullOrBlank()) {
            statusText = "desconocido"
            isOnline = false
            onDispose { }
        } else {
            statusText = "conectando…"
            val db = FirebaseFirestore.getInstance()
            val registration = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        statusText = "desconocido"
                        isOnline = false
                        return@addSnapshotListener
                    }

                    val data = snapshot?.data ?: emptyMap<String, Any?>()
                    val online = (data["isOnline"] as? Boolean) == true
                    isOnline = online

                    if (online) {
                        statusText = "en línea"
                    } else {
                        val ts = (data["lastSeen"] as? Timestamp)
                            ?: (data["lastActiveAt"] as? Timestamp)

                        if (ts != null) {
                            val date = ts.toDate()
                            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val time = fmt.format(date)
                            statusText = "últ. vez a las $time"
                        } else {
                            statusText = "desconocido"
                        }
                    }
                }

            onDispose {
                registration.remove()
            }
        }
    }
    // --- FIN bloque estado ---

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Avatar con 3 círculos: degradado → blanco → contenido
// 🔧 Escalas para controlar grosor de aros
        val outerRingScale = 32f / 36f   // subir hacia 1f → aro degradado más fino
        val innerContentScale = 26f / 30f // subir hacia 1f → aro blanco más fino

        Box(
            modifier = Modifier
                .size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            // Círculo exterior degradado
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
                // Círculo interior con la foto / iniciales
                Box(
                    modifier = Modifier
                        .fillMaxSize(innerContentScale)
                        .clip(CircleShape)
                        .background(Color(0xFFECECEC)),
                    contentAlignment = Alignment.Center
                ) {
                    // Clave de caché: para 1:1 usamos el otro usuario;
                    // para grupos usamos el id del chat (como uid sintético).
                    val cacheKey = if (isGroup) {
                        chat.id
                    } else {
                        otherUserId ?: chat.id
                    }

                    val localFile = remember(cacheKey) {
                        cacheKey?.let { avatarLocalFile(context, it) }
                    }
                    val hasLocal = localFile?.exists() == true

                    val modelUrl = avatarUrl

                    when {
                        // 1️⃣ Fichero local
                        hasLocal -> {
                            val bitmap = remember(localFile?.path) {
                                try {
                                    BitmapFactory.decodeFile(localFile?.path ?: "")
                                        ?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!modelUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = modelUrl,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onSuccess = { state: AsyncImagePainter.State.Success ->
                                        if (localFile != null) {
                                            saveDrawableToFile(
                                                state.result.drawable,
                                                localFile
                                            )
                                        }
                                    }
                                )
                            } else {
                                // fallback iniciales
                                val initials = title
                                    .trim()
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") {
                                        it.first().uppercaseChar().toString()
                                    }

                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }

                        // 2️⃣ Solo URL remota
                        !modelUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = modelUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onSuccess = { state: AsyncImagePainter.State.Success ->
                                    if (localFile != null) {
                                        saveDrawableToFile(
                                            state.result.drawable,
                                            localFile
                                        )
                                    }
                                }
                            )
                        }

                        else -> {
                            // Sin nada → iniciales
                            val initials = title
                                .trim()
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .joinToString("") {
                                    it.first().uppercaseChar().toString()
                                }

                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Primera línea: título + hora
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        color = Color(0xFF000000)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))
                if (timeText != null) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Segunda línea: estado (en línea / última vez / miembros)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF8E8E93)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tercera línea: preview + punto rojo de no leído
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = Color(0xFF6D6D72)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
            }
        }
    }
}


@Composable
private fun HiddenChatsHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Chats ocultos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Ocultar chats ocultos" else "Mostrar chats ocultos",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



@Composable
private fun CommunityGuidelinesFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = "Recuerda seguir las normas de la comunidad: nada de spam, insultos ni contenido inapropiado. Puedes reportar mensajes y usuarios desde su perfil.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
        )
    }
}


@Composable
private fun JoinWithInviteScreen(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    initialInviteId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf(initialInviteId.orEmpty()) }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Si el código cambia (por ejemplo, al llegar desde un deep link),
    // actualizamos el campo de texto una sola vez.
    LaunchedEffect(initialInviteId) {
        if (!initialInviteId.isNullOrBlank()) {
            input = initialInviteId
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Pega el enlace o el código de invitación del grupo al que quieres unirte.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enlace o código de invitación") },
            singleLine = true
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val trimmed = input.trim()
                if (trimmed.isEmpty()) {
                    errorMessage = "Introduce un código o enlace válido"
                    return@Button
                }

                scope.launch {
                    isJoining = true
                    errorMessage = null
                    try {
                        // Llamamos a la Cloud Function vía GroupApi
                        val chatId = GroupApi.joinByInvite(trimmed)
                        Toast.makeText(
                            context,
                            "Te has unido al grupo",
                            Toast.LENGTH_SHORT
                        ).show()
                        onJoined(chatId)
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "No se pudo usar esta invitación"
                    } finally {
                        isJoining = false
                    }
                }
            },
            enabled = !isJoining && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF007AFF), // Action Blue iOS
                contentColor = Color.White
            )
        ) {
            if (isJoining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("Unirse al grupo")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Cancelar",
                color = Color(0xFF007AFF),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
private fun ChatsTopBar(
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onJoinWithInvite: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Botón "Atrás" estilo iOS (chevron + texto azul)
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Atrás",
                color = Color(0xFF007AFF),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Título centrado como en iOS
        Text(
            text = "Chats",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Icono de “square.and.pencil” → aproximado con lápiz Material
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Menú de chats",
                    tint = Color(0xFF007AFF)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Nuevo chat") },
                    onClick = {
                        showMenu = false
                        onNewChat()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Nuevo grupo") },
                    onClick = {
                        showMenu = false
                        onNewGroup()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Unirse con enlace de invitación") },
                    onClick = {
                        showMenu = false
                        onJoinWithInvite()
                    }
                )
            }
        }
    }
}


@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Botón "Atrás" estilo iOS (chevron + texto azul)
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Atrás",
                color = Color(0xFF007AFF),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Título centrado y alineado verticalmente con el botón
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

