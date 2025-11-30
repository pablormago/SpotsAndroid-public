package com.spotitfly.app

import android.content.Context

/**
 * Equivalente sencillo a UserDefaults de iOS para la sesión de usuario.
 * De momento solo cacheamos el username (asociado a un uid).
 */
object UserPrefs {

    private const val PREFS_NAME = "user_session_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_USERNAME_UID = "username_uid"

    /**
     * Guarda username asociado a un uid concreto.
     */
    fun saveUsername(context: Context, uid: String?, username: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!uid.isNullOrBlank() && !username.isNullOrBlank()) {
                putString(KEY_USERNAME, username.trim())
                putString(KEY_USERNAME_UID, uid)
            } else {
                remove(KEY_USERNAME)
                remove(KEY_USERNAME_UID)
            }
        }.apply()
    }

    /**
     * Compat: mantiene la firma antigua, pero sin uid no cachea nada útil.
     * (Si se usa en otro sitio, simplemente no guardará el username).
     */
    fun saveUsername(context: Context, username: String?) {
        saveUsername(context, null, username)
    }

    /**
     * Carga el username cacheado para un uid concreto.
     * Si el uid no coincide con el que había guardado, devuelve null.
     */
    fun loadUsername(context: Context, uid: String?): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUid = prefs.getString(KEY_USERNAME_UID, null)
        if (uid.isNullOrBlank() || cachedUid == null || cachedUid != uid) {
            return null
        }
        return prefs.getString(KEY_USERNAME, null)
    }

    /**
     * Compat: versión antigua; sin uid no tiene sentido usar el caché.
     */
    fun loadUsername(context: Context): String? {
        return loadUsername(context, null)
    }
    // ⬇️ NUEVO: borrar todas las claves de user_prefs
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .apply()
    }
}
