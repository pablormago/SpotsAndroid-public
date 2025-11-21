package com.spotitfly.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_participants",
    indices = [
        Index("chatId"),
        Index("userId")
    ]
)
data class ChatParticipantEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val chatId: String,
    val userId: String
)
