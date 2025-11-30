package com.spotitfly.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotitfly.app.data.chat.Chat

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val isSupport: Boolean,
    val isGroup: Boolean,
    val displayName: String?,
    val lastMessageText: String?,
    val lastMessageSenderId: String?,
    val updatedAtMillis: Long?,
    val lastReadMillisJson: String?,
    val hiddenForJson: String?,
    val isHiddenJson: String?
) {

    companion object {

        fun fromDomain(chat: Chat): ChatEntity {
            return ChatEntity(
                id = chat.id,
                isSupport = chat.isSupport,
                isGroup = chat.isGroup,
                displayName = chat.displayName,
                lastMessageText = chat.lastMessageText,
                lastMessageSenderId = chat.lastMessageSenderId,
                updatedAtMillis = chat.updatedAtMillis,
                lastReadMillisJson = ChatLocalJson.mapLongToJson(chat.lastReadMillis),
                hiddenForJson = ChatLocalJson.listToJson(chat.hiddenFor),
                isHiddenJson = ChatLocalJson.mapBooleanToJson(chat.isHidden)
            )
        }

        fun toDomain(
            entity: ChatEntity,
            participantIds: List<String>
        ): Chat {
            return Chat(
                id = entity.id,
                participantIds = participantIds,
                isSupport = entity.isSupport,
                isGroup = entity.isGroup,
                displayName = entity.displayName,
                lastMessageText = entity.lastMessageText,
                lastMessageSenderId = entity.lastMessageSenderId,
                updatedAtMillis = entity.updatedAtMillis,
                lastReadMillis = ChatLocalJson.jsonToLongMap(entity.lastReadMillisJson),
                hiddenFor = ChatLocalJson.jsonToStringList(entity.hiddenForJson),
                isHidden = ChatLocalJson.jsonToBooleanMap(entity.isHiddenJson)
            )
        }
    }
}
