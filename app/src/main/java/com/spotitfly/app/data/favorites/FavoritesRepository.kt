package com.spotitfly.app.data.favorites

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gestión de favoritos del usuario actual.
 * Replica la lógica de iOS:
 *  - Ruta: users/{uid}/favorites/{spotId}
 *  - Campo: createdAt = serverTimestamp()
 */
class FavoritesRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Empieza a escuchar los favoritos del usuario.
     *
     * Cada cambio en users/{uid}/favorites actualiza el Set<String> de IDs.
     */
    fun listenFavorites(
        userId: String,
        onUpdate: (Set<String>) -> Unit
    ): ListenerRegistration {
        return db.collection("users")
            .document(userId)
            .collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Si quieres, aquí se puede hacer Log.e(...)
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: emptyList()
                val ids: Set<String> = docs.map { it.id }.toSet()
                onUpdate(ids)
            }
    }

    /**
     * Añade un favorito:
     *  Path: users/{uid}/favorites/{spotId}
     *  Data: { createdAt: serverTimestamp() }
     */
    suspend fun addFavorite(
        userId: String,
        spotId: String
    ) {
        val ref = db.collection("users")
            .document(userId)
            .collection("favorites")
            .document(spotId)

        ref.set(
            mapOf(
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Elimina un favorito del usuario.
     */
    suspend fun removeFavorite(
        userId: String,
        spotId: String
    ) {
        val ref = db.collection("users")
            .document(userId)
            .collection("favorites")
            .document(spotId)

        ref.delete().await()
    }
}

/* --- await helper local, igual que en RatingsRepository --- */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
