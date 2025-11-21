package com.spotitfly.app.data.context.overlays.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level delegate para DataStore (un único por nombre de archivo)
private val Context.dataStore by preferencesDataStore(name = "overlay_prefs")

class OverlayPrefs(private val context: Context) {

    private object K {
        val urbano: Preferences.Key<Boolean> = booleanPreferencesKey("overlay_urbano")
        val medio: Preferences.Key<Boolean> = booleanPreferencesKey("overlay_medio")
        val restr: Preferences.Key<Boolean> = booleanPreferencesKey("overlay_restr")
        val infra: Preferences.Key<Boolean> = booleanPreferencesKey("overlay_infra")
    }

    val flow: Flow<OverlayToggles> =
        context.dataStore.data.map { p: Preferences ->
            OverlayToggles(
                urbano = p[K.urbano] ?: false,
                medioambiente = p[K.medio] ?: true,
                restricciones = p[K.restr] ?: true,      // iOS: ON por defecto
                infraestructuras = p[K.infra] ?: true
            )
        }

    suspend fun set(value: OverlayToggles) {
        context.dataStore.edit { p ->
            p[K.urbano] = value.urbano
            p[K.medio] = value.medioambiente
            p[K.restr] = value.restricciones
            p[K.infra] = value.infraestructuras
        }
    }

    // ⬇️ NUEVO: limpiar todas las preferencias de overlays
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

}
