package com.spotitfly.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.spotitfly.app.data.favorites.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de favoritos (paridad con iOS FavoritesViewModel).
 *
 * - Mantiene un Set<String> de IDs de spots favoritos.
 * - Escucha Firestore en tiempo real: users/{uid}/favorites/{spotId}
 * - Ofrece toggle con actualización optimista (como en iOS).
 */
class FavoritesViewModel(
    private val repo: FavoritesRepository = FavoritesRepository()
) : ViewModel() {

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private var listener: ListenerRegistration? = null

    /**
     * Comienza a escuchar los favoritos del usuario actual.
     * Equivalente a favoritesVM.listenFavorites(userId:) en iOS.
     */
    fun startListening(userId: String?) {
        // Limpia cualquier listener anterior
        listener?.remove()
        listener = null

        if (userId.isNullOrBlank()) {
            _favoriteIds.value = emptySet()
            return
        }

        listener = repo.listenFavorites(userId) { ids ->
            _favoriteIds.value = ids
        }
    }

    /**
     * Limpia estado cuando se cierra sesión.
     * Igual que en iOS cuando userSession.uid pasa a nil.
     */
    fun clearForLogout() {
        listener?.remove()
        listener = null
        _favoriteIds.value = emptySet()
    }

    /**
     * Versión suspend de toggleFavorite, igual que en iOS:
     * - usa Auth.currentUser?.uid
     * - hace UI optimista sobre _favoriteIds
     */
    suspend fun toggleFavorite(spotId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val current = _favoriteIds.value
        val isFav = current.contains(spotId)

        if (isFav) {
            // Optimista: quitar local antes de borrar en Firestore
            val before = current
            _favoriteIds.value = before - spotId
            try {
                repo.removeFavorite(uid, spotId)
            } catch (e: Exception) {
                // Revertir si falla
                _favoriteIds.value = before
            }
        } else {
            // Optimista: añadir local antes de escribir en Firestore
            val before = current
            _favoriteIds.value = before + spotId
            try {
                repo.addFavorite(uid, spotId)
            } catch (e: Exception) {
                // Revertir si falla
                _favoriteIds.value = before
            }
        }
    }

    /**
     * Versión cómoda para llamar desde Composables:
     *
     *   onClick = { favoritesVM.toggleFavoriteAsync(spot.id) }
     */
    fun toggleFavoriteAsync(spotId: String) {
        viewModelScope.launch {
            toggleFavorite(spotId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        listener = null
    }
}
