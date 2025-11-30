package com.spotitfly.app.data.chat

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Caché local (en disco) para TODO lo relacionado con chats:
 * - Nombres de usuario (por uid)
 * - Avatares de usuario (por uid)
 * - Fotos de grupo (por chatId)
 *
 * Implementación simple usando SharedPreferences + JSON,
 * equivalente a los UserDefaults de iOS.
 */
object ChatsLocalCache {

    private const val PREFS_NAME = "chats_local_cache"
    private const val KEY_USERNAMES = "usernames"
    private const val KEY_USER_AVATARS = "user_avatars"
    private const val KEY_GROUP_PHOTOS = "group_photos"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ----------- USERNAMES -----------

    fun saveUserName(context: Context, uid: String, displayName: String?) {
        if (uid.isBlank() || displayName.isNullOrBlank()) return

        val p = prefs(context)
        val raw = p.getString(KEY_USERNAMES, "{}") ?: "{}"
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }

        obj.put(uid, displayName)
        p.edit().putString(KEY_USERNAMES, obj.toString()).apply()
    }

    fun getUserName(context: Context, uid: String): String? {
        if (uid.isBlank()) return null

        val raw = prefs(context).getString(KEY_USERNAMES, null) ?: return null

        return try {
            val obj = JSONObject(raw)
            if (obj.has(uid)) obj.optString(uid, null) else null
        } catch (_: Exception) {
            null
        }
    }

    // ----------- AVATARES DE USUARIO -----------

    fun saveUserAvatar(context: Context, uid: String, url: String?) {
        if (uid.isBlank() || url.isNullOrBlank()) return

        val p = prefs(context)
        val raw = p.getString(KEY_USER_AVATARS, "{}") ?: "{}"
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }

        obj.put(uid, url)
        p.edit().putString(KEY_USER_AVATARS, obj.toString()).apply()
    }

    fun getUserAvatar(context: Context, uid: String): String? {
        if (uid.isBlank()) return null

        val raw = prefs(context).getString(KEY_USER_AVATARS, null) ?: return null

        return try {
            val obj = JSONObject(raw)
            if (obj.has(uid)) obj.optString(uid, null) else null
        } catch (_: Exception) {
            null
        }
    }

    // ----------- FOTOS DE GRUPO (POR CHAT) -----------

    fun saveGroupPhoto(context: Context, chatId: String, url: String?) {
        if (chatId.isBlank() || url.isNullOrBlank()) return

        val p = prefs(context)
        val raw = p.getString(KEY_GROUP_PHOTOS, "{}") ?: "{}"
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }

        obj.put(chatId, url)
        p.edit().putString(KEY_GROUP_PHOTOS, obj.toString()).apply()
    }

    fun getGroupPhoto(context: Context, chatId: String): String? {
        if (chatId.isBlank()) return null

        val raw = prefs(context).getString(KEY_GROUP_PHOTOS, null) ?: return null

        return try {
            val obj = JSONObject(raw)
            if (obj.has(chatId)) obj.optString(chatId, null) else null
        } catch (_: Exception) {
            null
        }
    }

    // ----------- CLEAR (para logout) -----------

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
