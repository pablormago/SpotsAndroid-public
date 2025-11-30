package com.spotitfly.app.data.chat

import java.util.Locale

enum class ChatFileKind {
    NONE,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT
}

data class Chat(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val isSupport: Boolean = false,
    val isGroup: Boolean = false,
    val displayName: String? = null,
    val lastMessageText: String? = null,
    val lastMessageSenderId: String? = null,
    val updatedAtMillis: Long? = null,
    val lastReadMillis: Map<String, Long>? = null,
    val hiddenFor: List<String>? = null,
    val isHidden: Map<String, Boolean>? = null
) {

    /**
     * True si hay mensajes no leídos para el usuario dado.
     * Si el último mensaje lo envié yo, nunca se considera no leído.
     */
    fun isUnreadFor(userId: String): Boolean {
        if (userId.isBlank()) return false
        val updated = updatedAtMillis ?: return false

        // Último "leído" que conocemos desde Firestore
        val serverLastRead = lastReadMillis?.get(userId) ?: 0L

        // Override local en este dispositivo (equivalente a `localOverrides` en iOS)
        val localOverride = ChatReadOverrides.getOverride(id, userId) ?: 0L

        val effectiveLastRead = maxOf(serverLastRead, localOverride)

        return updated > effectiveLastRead
    }


    /**
     * True si este chat está oculto para un usuario concreto.
     * Usa isHidden[uid] y hace fallback a hiddenFor (legacy).
     */
    fun isHiddenFor(uid: String?): Boolean {
        val u = uid ?: return false
        if (isHidden?.get(u) == true) return true
        if (hiddenFor?.contains(u) == true) return true
        return false
    }
}

/**
 * Mini-override local para las marcas de leído, equivalente a `localOverrides` en iOS.
 *
 * Se usa para que, en cuanto el usuario abre un chat en este dispositivo,
 * el listado deje de mostrar "mensajes nuevos" aunque el write al servidor aún no haya llegado.
 */
object ChatReadOverrides {

    // chatId -> (uid -> lastReadAtMillis)
    private val overrides: MutableMap<String, MutableMap<String, Long>> = mutableMapOf()

    /**
     * Marca un chat como leído localmente para un usuario.
     *
     * @param chatId ID del chat
     * @param uid ID del usuario
     * @param lastMessageAtMillis timestamp del último mensaje conocido (opcional)
     */
    fun markAsReadLocally(
        chatId: String,
        uid: String,
        lastMessageAtMillis: Long?
    ) {
        if (chatId.isBlank() || uid.isBlank()) return

        val now = System.currentTimeMillis()
        val candidate = maxOf(lastMessageAtMillis ?: 0L, now)

        val perChat = overrides.getOrPut(chatId) { mutableMapOf() }
        val current = perChat[uid]

        if (current == null || candidate > current) {
            perChat[uid] = candidate
        }
    }

    fun getOverride(chatId: String, uid: String): Long? {
        return overrides[chatId]?.get(uid)
    }

    /**
     * Por si alguna vez quieres limpiar (no es obligatorio usarlo).
     */
    fun clear(chatId: String? = null, uid: String? = null) {
        when {
            chatId == null && uid == null -> {
                overrides.clear()
            }
            chatId != null && uid == null -> {
                overrides.remove(chatId)
            }
            chatId != null && uid != null -> {
                overrides[chatId]?.remove(uid)
            }
        }
    }
}


data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val type: String? = null,
    val createdAtMillis: Long = 0L,
    val readBy: List<String>? = null,
    val mentions: List<String>? = null,
    val replyToMessageId: String? = null,
    val editedAtMillis: Long? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long? = null,
    val fileType: String? = null,
    val thumbnailUrl: String? = null,
    val uploadProgress: Double? = null
) {
    val hasFile: Boolean
        get() = !fileUrl.isNullOrEmpty() || !fileName.isNullOrEmpty() || !fileType.isNullOrEmpty()

    val fileKind: ChatFileKind
        get() = detectFileKind(fileType, fileName, fileUrl)
}


fun detectFileKind(
    fileType: String?,
    fileName: String?,
    fileUrl: String?
): ChatFileKind {
    val type = fileType?.lowercase(Locale.ROOT) ?: ""
    val name = fileName?.lowercase(Locale.ROOT) ?: ""
    val url = fileUrl?.lowercase(Locale.ROOT) ?: ""

    val extFromName = name.substringAfterLast('.', "")
    val extFromUrl = url.substringAfterLast('.', "")
    val ext = (extFromName.ifEmpty { extFromUrl }).lowercase(Locale.ROOT)

    val isImageMime = type.startsWith("image/")
    val isVideoMime = type.startsWith("video/")
    val isAudioMime = type.startsWith("audio/")

    val isImageExt = ext in listOf("jpg", "jpeg", "png", "gif", "heic", "heif", "webp")
    val isVideoExt = ext in listOf("mp4", "mov", "m4v", "avi", "mkv")
    val isAudioExt = ext in listOf("mp3", "aac", "m4a", "wav", "ogg", "flac")

    return when {
        isImageMime || isImageExt -> ChatFileKind.IMAGE
        isVideoMime || isVideoExt -> ChatFileKind.VIDEO
        isAudioMime || isAudioExt -> ChatFileKind.AUDIO
        type.isNotEmpty() || ext.isNotEmpty() -> ChatFileKind.DOCUMENT
        else -> ChatFileKind.NONE
    }



}
fun Chat.previewLabel(): String {
    val raw = (lastMessageText ?: "").trim()
    if (raw.isEmpty()) return "📎 Archivo"

    val s = raw
    val lower = s.lowercase(Locale.ROOT)

    fun hasAnySuffix(exts: List<String>): Boolean =
        exts.any { lower.endsWith(it) }

    // Heurística por extensión (cuando lastMessage = fileName)
    if (hasAnySuffix(listOf(".jpg", ".jpeg", ".png", ".gif", ".heic"))) {
        return "📷 Foto"
    }
    if (hasAnySuffix(listOf(".mp4", ".mov", ".avi", ".mkv"))) {
        return "🎬 Vídeo"
    }
    if (hasAnySuffix(listOf(".mp3", ".m4a", ".wav"))) {
        return "🎵 Audio"
    }
    if (hasAnySuffix(listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar"))) {
        return "📎 $s"
    }

    // Enlaces
    if (s.contains("http://") || s.contains("https://")) {
        val host = try {
            val uri = java.net.URI(s)
            uri.host
        } catch (_: Exception) {
            null
        }
        return if (!host.isNullOrBlank()) {
            "🔗 $host"
        } else {
            "🔗 Enlace"
        }
    }

    // Texto normal
    return s
}