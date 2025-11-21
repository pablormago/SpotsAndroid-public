package com.spotitfly.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spotitfly.app.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAtMillis DESC")
    fun getAllOnce(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getById(chatId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChatEntity>)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

