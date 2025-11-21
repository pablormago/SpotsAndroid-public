package com.spotitfly.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.spotitfly.app.data.local.dao.ChatDao
import com.spotitfly.app.data.local.dao.ChatParticipantDao
import com.spotitfly.app.data.local.dao.MessageDao
import com.spotitfly.app.data.local.dao.SpotDao
import com.spotitfly.app.data.local.entity.ChatEntity
import com.spotitfly.app.data.local.entity.ChatParticipantEntity
import com.spotitfly.app.data.local.entity.MessageEntity
import com.spotitfly.app.data.local.entity.SpotEntity

@Database(
    entities = [
        SpotEntity::class,
        ChatEntity::class,
        ChatParticipantEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun spotsDao(): SpotDao
    abstract fun chatsDao(): ChatDao
    abstract fun chatParticipantsDao(): ChatParticipantDao
    abstract fun messagesDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spots.db"
                )
                    // Permitimos lecturas en el hilo principal para que
                    // la carga inicial de chats/mensajes sea instantánea.
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }

    }
}
