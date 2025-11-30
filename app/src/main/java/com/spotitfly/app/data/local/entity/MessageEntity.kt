package com.spotitfly.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spotitfly.app.data.chat.ChatFileKind
import com.spotitfly.app.data.chat.Message

@Entity(
    tableName = "messages",
    indices = [
        Index("chatId"),
        Index("createdAtMillis")
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val type: String?,
    val createdAtMillis: Long,
    val readByJson: String?,
    val mentionsJson: String?,
    val replyToMessageId: String?,
    val editedAtMillis: Long?,
    val fileUrl: String?,
    val fileName: String?,
    val fileSizeBytes: Long?,
    val fileType: String?,
    val thumbnailUrl: String?,
    val uploadProgress: Double?,
    // Campos solo locales para caché offline
    val localPath: String?,
    val thumbnailLocalPath: String?
) {

    val hasFile: Boolean
        get() = !fileUrl.isNullOrEmpty() || !fileName.isNullOrEmpty() || !fileType.isNullOrEmpty()

    val fileKind: ChatFileKind
        get() = ChatFileKindDetector.detect(fileType, fileName, fileUrl)

    companion object {

        fun fromDomain(message: Message): MessageEntity {
            return MessageEntity(
                id = message.id,
                chatId = message.chatId,
                senderId = message.senderId,
                text = message.text,
                type = message.type,
                createdAtMillis = message.createdAtMillis,
                readByJson = ChatLocalJson.listToJson(message.readBy),
                mentionsJson = ChatLocalJson.listToJson(message.mentions),
                replyToMessageId = message.replyToMessageId,
                editedAtMillis = message.editedAtMillis,
                fileUrl = message.fileUrl,
                fileName = message.fileName,
                fileSizeBytes = message.fileSizeBytes,
                fileType = message.fileType,
                thumbnailUrl = message.thumbnailUrl,
                uploadProgress = message.uploadProgress,
                localPath = null,
                thumbnailLocalPath = null
            )
        }

        fun toDomain(entity: MessageEntity): Message {
            return Message(
                id = entity.id,
                chatId = entity.chatId,
                senderId = entity.senderId,
                text = entity.text,
                type = entity.type,
                createdAtMillis = entity.createdAtMillis,
                readBy = ChatLocalJson.jsonToStringList(entity.readByJson),
                mentions = ChatLocalJson.jsonToStringList(entity.mentionsJson),
                replyToMessageId = entity.replyToMessageId,
                editedAtMillis = entity.editedAtMillis,
                fileUrl = entity.fileUrl,
                fileName = entity.fileName,
                fileSizeBytes = entity.fileSizeBytes,
                fileType = entity.fileType,
                thumbnailUrl = entity.thumbnailUrl,
                uploadProgress = entity.uploadProgress
            )
        }
    }
}

/**
 * Pequeño helper local para reutilizar la lógica de detección de tipo de archivo.
 */
internal object ChatFileKindDetector {
    fun detect(
        fileType: String?,
        fileName: String?,
        fileUrl: String?
    ): ChatFileKind {
        // Delegamos en el mismo helper que usa Message.fileKind
        return com.spotitfly.app.data.chat.detectFileKind(fileType, fileName, fileUrl)
    }
}
