package com.spotitfly.app.data.rating

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RatingsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Guarda el voto (1..5) del usuario en spots/{spotId} con transacción:
     *  - ratings.{uid} = stars
     *  - ratingsCount = size(ratings)
     *  - averageRating = sum(ratings)/count
     *  - updatedAt = serverTimestamp()
     */
    suspend fun setUserRating(spotId: String, userId: String, stars: Int) {
        require(stars in 1..5) { "stars must be 1..5" }
        val ref = db.collection("spots").document(spotId)

        db.runTransaction { tx ->
            val snap = tx.get(ref)

            // Normaliza el mapa ratings -> MutableMap<String, Int>
            val currentMap: MutableMap<String, Int> = mutableMapOf()
            val raw = snap.get("ratings")
            if (raw is Map<*, *>) {
                raw.forEach { (k, v) ->
                    val key = k as? String ?: return@forEach
                    val value: Int? = when (v) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull()
                        else -> null
                    }
                    if (value != null) currentMap[key] = value
                }
            }

            // Aplica/actualiza el voto del usuario
            currentMap[userId] = stars

            // Recalcula agregados
            val values: Collection<Int> = currentMap.values
            val count = values.size
            val avg = if (count > 0) values.sum().toDouble() / count else 0.0

            // Actualiza doc
            tx.update(
                ref,
                mapOf(
                    "ratings" to currentMap,
                    "ratingsCount" to count,
                    "averageRating" to avg,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }
}

/* --- await helper local, sin dependencias extra --- */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
