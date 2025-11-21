package com.spotitfly.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.spotitfly.app.data.chat.Chat
import com.spotitfly.app.data.chat.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.util.Log
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import android.webkit.MimeTypeMap
import android.content.Context
import com.google.firebase.FirebaseApp
import com.spotitfly.app.data.local.AppDatabase
import com.spotitfly.app.data.local.dao.MessageDao
import com.spotitfly.app.data.local.entity.MessageEntity
import kotlinx.coroutines.launch



class ChatDetailViewModel : ViewModel() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private val appContext: Context = FirebaseApp.getInstance().applicationContext
    private val messageDao: MessageDao by lazy { AppDatabase.get(appContext).messagesDao() }

    private var messagesListener: ListenerRegistration? = null
    private var statusListener: ListenerRegistration? = null
    private var currentChatId: String? = null


    private val _messages: MutableStateFlow<List<Message>> =
        MutableStateFlow(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isReady: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _userStatus: MutableStateFlow<String> =
        MutableStateFlow("conectando…")
    val userStatus: StateFlow<String> = _userStatus.asStateFlow()

    /**
     * Listener principal de mensajes del chat.
     */
    fun start(chatId: String) {
        if (chatId == currentChatId && messagesListener != null) return

        stop()
        currentChatId = chatId
        _isReady.value = false
        loadMessagesFromLocal(chatId)
        val ref = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        messagesListener = ref.addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
            if (error != null) {
                Log.e("ChatDetailViewModel", "Error en listener de mensajes", error)
                return@addSnapshotListener
            }

            if (snapshot == null) {
                _messages.value = emptyList()
                _isReady.value = true
                return@addSnapshotListener
            }

            val out = mutableListOf<Message>()

            for (doc in snapshot.documents) {
                val id = doc.id
                val chatIdField = doc.getString("chatId") ?: currentChatId ?: ""
                val senderId = doc.getString("senderId") ?: ""
                val text = doc.getString("text") ?: ""
                val type = doc.getString("type")

                val createdAtTs: Timestamp? = doc.getTimestamp("createdAt")
                val createdAtMillis = createdAtTs?.toDate()?.time ?: 0L

                val readByAny = doc.get("readBy")
                val readBy = (readByAny as? List<*>)?.mapNotNull { it as? String }

                val mentionsAny = doc.get("mentions")
                val mentions = mentionsAny?.let { raw ->
                    (raw as? List<*>)?.mapNotNull { it as? String }
                }

                val replyToMessageId = doc.getString("replyToMessageId")

                val editedTs: Timestamp? = doc.getTimestamp("editedAt")
                val editedAtMillis = editedTs?.toDate()?.time

                val fileUrl = doc.getString("fileUrl")
                val fileName = doc.getString("fileName")
                val fileType = doc.getString("fileType")
                val thumbnailUrl = doc.getString("thumbnailUrl")

                val fileSizeAny = doc.get("fileSize")
                val fileSizeBytes = when (fileSizeAny) {
                    is Number -> fileSizeAny.toLong()
                    else -> null
                }

                val message = Message(
                    id = id,
                    chatId = chatIdField,
                    senderId = senderId,
                    text = text,
                    type = type,
                    createdAtMillis = createdAtMillis,
                    readBy = readBy,
                    mentions = mentions,
                    replyToMessageId = replyToMessageId,
                    editedAtMillis = editedAtMillis,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    fileType = fileType,
                    thumbnailUrl = thumbnailUrl,
                    uploadProgress = null // el progreso de subida se gestiona localmente (no en Firestore)
                )

                out.add(message)
            }

            _messages.value = out
            cacheMessages(chatId, out)
            _isReady.value = true
        }
    }

    private fun loadMessagesFromLocal(chatId: String) {
        val entities = messageDao.getForChatOnce(chatId)
        if (entities.isEmpty()) return

        // Si ya hay mensajes en memoria (por Firestore), no machacamos.
        if (_messages.value.isNotEmpty()) return

        val messages = entities.map { entity -> MessageEntity.toDomain(entity) }
        _messages.value = messages
        _isReady.value = true
    }


    /**
     * Listener del estado del otro usuario (solo 1:1).
     * Equivalente al loadUserStatus() de iOS.
     */
    fun observeUserStatus(chat: Chat) {
        statusListener?.remove()
        _userStatus.value = "conectando…"

        // Solo tiene sentido en chats directos (2 participantes)
        if (chat.participantIds.size != 2) {
            _userStatus.value = ""
            return
        }

        val currentUserId = auth.currentUser?.uid
        val otherUserId = chat.participantIds.firstOrNull { it != currentUserId }

        if (otherUserId == null) {
            _userStatus.value = ""
            return
        }

        val userRef = db.collection("users").document(otherUserId)

        statusListener = userRef.addSnapshotListener { snapshot: DocumentSnapshot?, error: FirebaseFirestoreException? ->
            if (error != null) {
                Log.e("ChatDetailViewModel", "Error escuchando estado de usuario", error)
                _userStatus.value = ""
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                _userStatus.value = ""
                return@addSnapshotListener
            }

            val data = snapshot.data ?: emptyMap<String, Any?>()
            _userStatus.value = formatUserStatus(data)
        }
    }

    private fun cacheMessages(chatId: String, messages: List<Message>) {
        if (messages.isEmpty()) return

        viewModelScope.launch {
            val entities = messages.map { MessageEntity.fromDomain(it) }
            messageDao.upsertAll(entities)
        }
    }


    /**
     * Para dejar de escuchar mensajes y estado.
     */
    fun stop() {
        messagesListener?.remove()
        messagesListener = null
        statusListener?.remove()
        statusListener = null
        currentChatId = null
        _isReady.value = false
    }

    /**
     * Envío de mensaje de texto (nuevo).
     * Equivalente a sendText en iOS, incluyendo reply.
     */
    fun sendTextMessage(
        chat: Chat,
        text: String,
        replyToMessageId: String? = null
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val chatId = chat.id
        val senderId = auth.currentUser?.uid ?: return
        val now = Timestamp.now()

        val msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        val data = hashMapOf<String, Any?>(
            "id" to msgRef.id,
            "chatId" to chatId,
            "senderId" to senderId,
            "text" to trimmed,
            "type" to "text",
            "createdAtClient" to now,
            "createdAt" to FieldValue.serverTimestamp()
        )

        if (replyToMessageId != null) {
            data["replyToMessageId"] = replyToMessageId
        }

        msgRef
            .set(data)
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error enviando mensaje de texto", e)
            }

        // Actualizar el resumen del chat (lastMessage / lastSenderId / lastMessageAt / updatedAt)
        val lastMessageLabel = trimmed.take(200)

        val chatUpdate = hashMapOf<String, Any?>(
            "lastMessage" to lastMessageLabel,
            "lastSenderId" to senderId,
            "lastMessageAt" to now,
            "updatedAt" to now
        )

        db.collection("chats")
            .document(chatId)
            .set(chatUpdate, SetOptions.merge())
    }

    /**
     * Editar un mensaje de texto (solo texto + editedAt).
     * Cumple reglas: solo se permite update de ["text","editedAt","mentions"].
     */
    fun editTextMessage(
        chatId: String,
        messageId: String,
        newText: String
    ) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        val senderId = auth.currentUser?.uid ?: return

        val msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        val now = Timestamp.now()

        val update = hashMapOf<String, Any?>(
            "text" to trimmed,
            "editedAt" to now
        )

        msgRef
            .get()
            .addOnSuccessListener { doc ->
                val existingSenderId = doc.getString("senderId")
                if (existingSenderId != senderId) {
                    return@addOnSuccessListener
                }

                msgRef
                    .update(update)
                    .addOnFailureListener { e ->
                        Log.e("ChatDetailViewModel", "Error editando mensaje", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error leyendo mensaje para editar", e)
            }
    }

    fun forwardMessageToChat(
        chatId: String,
        message: Message
    ) {
        val senderId = auth.currentUser?.uid ?: return
        val now = Timestamp.now()

        val msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        val data = hashMapOf<String, Any?>(
            "id" to msgRef.id,
            "chatId" to chatId,
            "senderId" to senderId,
            "createdAtClient" to now,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val text = message.text?.trim().orEmpty()
        val hasFile =
            !message.fileUrl.isNullOrEmpty() ||
                    !message.fileName.isNullOrEmpty()

        if (hasFile) {
            data["type"] = "file"
            data["text"] = text

            message.fileUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { data["fileUrl"] = it }

            message.fileName
                ?.takeIf { it.isNotBlank() }
                ?.let { data["fileName"] = it }

            message.fileSizeBytes
                ?.let { data["fileSize"] = it }

            message.fileType
                ?.takeIf { it.isNotBlank() }
                ?.let { data["fileType"] = it }

            message.thumbnailUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { data["thumbnailUrl"] = it }
        } else {
            data["type"] = "text"
            data["text"] = text
        }

        msgRef
            .set(data)
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error reenviando mensaje", e)
            }

        val label = labelForMessageData(data)

        val chatUpdate = hashMapOf<String, Any?>(
            "lastMessage" to label,
            "lastSenderId" to senderId,
            "lastMessageAt" to now,
            "updatedAt" to now
        )

        db.collection("chats")
            .document(chatId)
            .set(chatUpdate, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error actualizando resumen de chat tras forward", e)
            }
    }


    /**
     * Borrar un mensaje (delete) y reconstruir el lastMessage del chat.
     */
    fun deleteMessage(chatId: String, messageId: String) {
        val senderId = auth.currentUser?.uid ?: return

        val msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        msgRef
            .get()
            .addOnSuccessListener { doc ->
                val existingSenderId = doc.getString("senderId")
                if (existingSenderId != senderId) {
                    return@addOnSuccessListener
                }

                msgRef
                    .delete()
                    .addOnSuccessListener {
                        rebuildChatLastMessage(chatId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatDetailViewModel", "Error borrando mensaje", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error leyendo mensaje para borrar", e)
            }
    }

    /**
     * Marcar como leído (equivalente a onChatRead de iOS).
     */
    /**
     * Marcar como leído (equivalente a onChatRead de iOS).
     */
    fun markChatAsRead(chatId: String) {
        val uid = auth.currentUser?.uid ?: return

        // 1) Registro de lectura por usuario: users/{uid}/chatsReads/{chatId}
        val userReadsRef = db.collection("users")
            .document(uid)
            .collection("chatsReads")
            .document(chatId)

        val userReadsUpdate = hashMapOf<String, Any>(
            "lastReadAt" to FieldValue.serverTimestamp()
        )

        userReadsRef
            .set(userReadsUpdate, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error actualizando chatsReads", e)
            }

        // 2) Actualizar SOLO nuestra entrada en el mapa lastRead del chat,
        //    sin tocar updatedAt (que indica la hora del último mensaje).
        val chatRef = db.collection("chats").document(chatId)

        // Intento principal: ruta anidada lastRead.<uid> (igual que en iOS)
        chatRef
            .update("lastRead.$uid", FieldValue.serverTimestamp())
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error actualizando resumen de chat (lastRead)", e)

                // Fallback de compatibilidad:
                // si lastRead estaba mal tipado (Timestamp plano), lo migramos a mapa.
                val fallbackData = hashMapOf<String, Any>(
                    "lastRead" to hashMapOf<String, Any>(
                        uid to Timestamp.now()
                    )
                )

                chatRef
                    .set(fallbackData, SetOptions.merge())
                    .addOnFailureListener { e2 ->
                        Log.e("ChatDetailViewModel", "Error en fallback de lastRead", e2)
                    }
            }
    }



    /**
     * Envío de media (foto desde cámara/galería).
     */
    fun onMediaPicked(uri: Uri, replyToMessageId: String? = null) {
        uploadAttachment(uri = uri, isMedia = true, replyToMessageId = replyToMessageId)
    }

    /**
     * Envío de documento (archivo genérico).
     */
    fun onDocumentPicked(uri: Uri, replyToMessageId: String? = null) {
        uploadAttachment(uri = uri, isMedia = false, replyToMessageId = replyToMessageId)
    }

    /**
     * Subida de adjunto a Storage + creación de mensaje de archivo.
     * Importante: NO usamos update() sobre el mensaje para cumplir reglas.
     */
    /**
     * Subida de adjunto a Storage + creación de mensaje de archivo.
     * Importante: NO usamos update() sobre el mensaje para cumplir reglas.
     */
    private fun uploadAttachment(
        uri: Uri,
        isMedia: Boolean,
        replyToMessageId: String?
    ) {
        val chatId = currentChatId ?: return
        val senderId = auth.currentUser?.uid ?: return

        val now = Timestamp.now()

        // ID de mensaje
        val msgRef = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        val context = storage.app.applicationContext
        val resolver = context.contentResolver

        // === 1) Nombre REAL del archivo + MIME real ===
        var displayName: String? = null
        var mimeFromResolver: String? = null

        try {
            mimeFromResolver = resolver.getType(uri)

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatDetailViewModel", "Error leyendo metadata del archivo", e)
        }

        val rawName = displayName ?: (uri.lastPathSegment ?: "")
        val extFromName = rawName
            .substringAfterLast('.', "")
            .lowercase(Locale.getDefault())
            .ifBlank { null }

        val extFromMime = mimeFromResolver?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }?.lowercase(Locale.getDefault())

        val extFromUri = if (rawName.contains('.')) {
            rawName
                .substringAfterLast('.', "")
                .lowercase(Locale.getDefault())
                .ifBlank { null }
        } else {
            null
        }

        // Decidir extensión final + MIME final con mejores esfuerzos
        val (finalExt, finalMime) = when {
            // Tenemos MIME del contentResolver → caso ideal (fotos, vídeos, docs…)
            mimeFromResolver != null -> {
                val ext = extFromName ?: extFromMime ?: extFromUri
                val mime = mimeFromResolver!!
                ext to mime
            }

            // No hay MIME, pero sí nombre con extensión
            extFromName != null -> {
                val mimeFromExt = MimeTypeMap
                    .getSingleton()
                    .getMimeTypeFromExtension(extFromName)
                val mime = mimeFromExt ?: if (isMedia) "image/jpeg" else "application/octet-stream"
                extFromName to mime
            }

            // Solo tenemos algo de la URI
            extFromUri != null -> {
                val mimeFromExt = MimeTypeMap
                    .getSingleton()
                    .getMimeTypeFromExtension(extFromUri)
                val mime = mimeFromExt ?: if (isMedia) "image/jpeg" else "application/octet-stream"
                extFromUri to mime
            }

            // Media sin extensión detectable → asumimos foto
            isMedia -> {
                "jpg" to "image/jpeg"
            }

            // Fallback total
            else -> {
                null to "application/octet-stream"
            }
        }

        // Nombre en STORAGE (seguro, sin espacios raros)
        val fileNameForStorage = if (finalExt != null) {
            "file_${msgRef.id}.$finalExt"
        } else {
            "file_${msgRef.id}"
        }

        // Nombre que verá el usuario (intento de nombre real)
        val displayNameForMessage = displayName
            ?: rawName
                .takeIf { it.isNotBlank() }
            ?: fileNameForStorage

        // === 2) Thumbnail para VÍDEO (si aplica) ===
        var thumbnailBytes: ByteArray? = null

        if (finalMime.startsWith("video/")) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.frameAtTime // frame por defecto
                if (frame != null) {
                    val targetWidth = 480
                    val ratio = targetWidth.toDouble() / frame.width.toDouble()
                    val targetHeight = (frame.height * ratio).toInt().coerceAtLeast(1)

                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        targetWidth,
                        targetHeight,
                        true
                    )

                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    thumbnailBytes = baos.toByteArray()

                    frame.recycle()
                    if (scaled != frame) {
                        scaled.recycle()
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e("ChatDetailViewModel", "Error generando thumbnail de vídeo", e)
            }
        }

        // === 3) Mensaje local con progreso 0% para la UI ===
        val localMessage = Message(
            id = msgRef.id,
            chatId = chatId,
            senderId = senderId,
            text = "",
            type = "file",
            createdAtMillis = now.toDate().time,
            readBy = emptyList(),
            mentions = null,
            replyToMessageId = replyToMessageId,
            editedAtMillis = null,
            fileUrl = null,
            fileName = displayNameForMessage,
            fileSizeBytes = null,
            fileType = finalMime,
            thumbnailUrl = null,
            uploadProgress = 0.0
        )

        _messages.value = _messages.value + localMessage

        // === 4) Subida a Storage con contentType correcto ===
        val storageRef = storage.reference
            .child("chats")
            .child(chatId)
            .child(senderId)
            .child(fileNameForStorage)

        val metadata = StorageMetadata.Builder()
            .setContentType(finalMime)
            .build()

        val uploadTask = storageRef.putFile(uri, metadata)

        // Progreso de subida → actualiza uploadProgress del mensaje local
        uploadTask.addOnProgressListener { taskSnapshot ->
            val total = taskSnapshot.totalByteCount
            val transferred = taskSnapshot.bytesTransferred
            val progress = if (total > 0L) {
                transferred.toDouble() / total.toDouble()
            } else {
                0.0
            }

            _messages.value = _messages.value.map { m ->
                if (m.id == msgRef.id) m.copy(uploadProgress = progress) else m
            }
        }

        uploadTask
            .addOnSuccessListener { taskSnapshot ->
                storageRef.downloadUrl
                    .addOnSuccessListener { url ->
                        val sizeFromMeta = taskSnapshot.metadata?.sizeBytes
                        val sizeFromTask = taskSnapshot.totalByteCount
                            .takeIf { it > 0L }

                        val sizeBytes = sizeFromMeta ?: sizeFromTask

                        fun finishWithMessage(thumbnailUrl: String?) {
                            val data = hashMapOf<String, Any?>(
                                "id" to msgRef.id,
                                "chatId" to chatId,
                                "senderId" to senderId,
                                "text" to "",
                                "type" to "file",
                                "fileUrl" to url.toString(),
                                "fileName" to displayNameForMessage,
                                "fileSize" to sizeBytes,
                                "fileType" to finalMime,
                                "createdAtClient" to now,
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            if (thumbnailUrl != null) {
                                data["thumbnailUrl"] = thumbnailUrl
                            }

                            if (replyToMessageId != null) {
                                data["replyToMessageId"] = replyToMessageId
                            }

                            // Crear el mensaje (una sola escritura, cumpliendo reglas)
                            msgRef
                                .set(data)
                                .addOnFailureListener { e ->
                                    Log.e(
                                        "ChatDetailViewModel",
                                        "Error creando mensaje de archivo tras subirlo",
                                        e
                                    )
                                }

                            // Actualizar agregados del chat (lastMessage / lastSenderId / lastMessageAt / updatedAt)
                            val label = labelForMessageData(data)

                            val chatUpdate = hashMapOf<String, Any?>(
                                "lastMessage" to label,
                                "lastSenderId" to senderId,
                                "lastMessageAt" to now,
                                "updatedAt" to now
                            )

                            db.collection("chats")
                                .document(chatId)
                                .set(chatUpdate, SetOptions.merge())

                            // Actualizar copia local: ya sin progreso y con URLs reales
                            _messages.value = _messages.value.map { m ->
                                if (m.id == msgRef.id) {
                                    m.copy(
                                        fileUrl = url.toString(),
                                        fileName = displayNameForMessage,
                                        fileSizeBytes = sizeBytes,
                                        fileType = finalMime,
                                        thumbnailUrl = thumbnailUrl,
                                        uploadProgress = null
                                    )
                                } else m
                            }
                        }

                        // Si hay thumbnail de vídeo → subirla primero, luego crear mensaje con thumbnailUrl
                        if (thumbnailBytes != null) {
                            val thumbRef = storage.reference
                                .child("chats")
                                .child(chatId)
                                .child(senderId)
                                .child("thumb_${msgRef.id}.jpg")

                            thumbRef
                                .putBytes(thumbnailBytes!!)
                                .addOnSuccessListener {
                                    thumbRef.downloadUrl
                                        .addOnSuccessListener { turl ->
                                            finishWithMessage(turl.toString())
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("ChatDetailViewModel", "Error obteniendo URL de thumbnail", e)
                                            finishWithMessage(null)
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChatDetailViewModel", "Error subiendo thumbnail", e)
                                    finishWithMessage(null)
                                }
                        } else {
                            // No hay thumbnail (fotos, docs, o fallo generando miniatura)
                            finishWithMessage(null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatDetailViewModel", "Error obteniendo downloadUrl", e)
                        _messages.value = _messages.value.map { m ->
                            if (m.id == msgRef.id) m.copy(uploadProgress = -1.0) else m
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error subiendo archivo", e)
                _messages.value = _messages.value.map { m ->
                    if (m.id == msgRef.id) m.copy(uploadProgress = -1.0) else m
                }
            }
    }


    // === Helpers privados para agregados del chat (paridad con iOS) ===

    private fun labelForMessageData(data: Map<String, Any?>): String {
        val fileUrl = (data["fileUrl"] as? String)?.takeIf { it.isNotBlank() }
        val fileName = (data["fileName"] as? String)?.takeIf { it.isNotBlank() }
        val fileType = (data["fileType"] as? String)?.lowercase().orEmpty()

        val hasFile = !fileUrl.isNullOrEmpty() || !fileName.isNullOrEmpty()

        if (hasFile) {
            return when {
                fileType.startsWith("image/") -> "📷 Foto"
                fileType.startsWith("video/") -> "🎬 Vídeo"
                fileType.startsWith("audio/") -> "🎵 Audio"
                else -> {
                    val name = fileName ?: "Archivo"
                    "📎 $name"
                }
            }
        }

        val text = (data["text"] as? String).orEmpty().trim()
        return text
    }

    private fun rebuildChatLastMessage(chatId: String) {
        val chatRef = db.collection("chats").document(chatId)
        val messagesRef = chatRef.collection("messages")

        messagesRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents.first()
                    val data = doc.data ?: emptyMap<String, Any?>()
                    val label = labelForMessageData(data)
                    val sender = data["senderId"] as? String ?: ""
                    val createdAtTs = data["createdAt"] as? Timestamp

                    val update = hashMapOf<String, Any?>(
                        "lastMessage" to label,
                        "lastSenderId" to sender,
                        "lastMessageAt" to createdAtTs,
                        "updatedAt" to createdAtTs
                    )

                    chatRef
                        .set(update, SetOptions.merge())
                        .addOnFailureListener { e ->
                            Log.e("ChatDetailViewModel", "Error actualizando lastMessage en rebuild", e)
                        }
                } else {
                    val update = hashMapOf<String, Any?>(
                        "lastMessage" to "",
                        "lastSenderId" to null,
                        "lastMessageAt" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    chatRef
                        .set(update, SetOptions.merge())
                        .addOnFailureListener { e ->
                            Log.e("ChatDetailViewModel", "Error limpiando lastMessage en rebuild", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatDetailViewModel", "Error obteniendo mensajes para rebuild", e)
            }
    }

    /**
     * Formatea el estado de usuario (online / última vez…).
     */
    private fun formatUserStatus(data: Map<String, Any?>): String {
        val isOnline = (data["isOnline"] as? Boolean) == true
        if (isOnline) return "en línea"

        val ts = (data["lastSeen"] as? Timestamp)
            ?: (data["lastActiveAt"] as? Timestamp)

        if (ts != null) {
            val date = ts.toDate()
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = fmt.format(date)
            return "últ. vez a las $time"
        }

        return "desconocido"
    }

    companion object {
        // UID del bot de soporte (igual que en iOS)
        private const val SUPPORT_BOT_ID = "26CSxWS7R7eZlrvXUV1qJFyL7Oc2"
    }
}
