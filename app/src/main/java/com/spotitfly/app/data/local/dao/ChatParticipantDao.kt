package com.spotitfly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spotitfly.app.data.local.entity.ChatParticipantEntity

@Dao
interface ChatParticipantDao {

    @Query("SELECT * FROM chat_participants WHERE chatId = :chatId")
    fun getForChat(chatId: String): List<ChatParticipantEntity>


    @Query("DELETE FROM chat_participants WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChatParticipantEntity>)
}
