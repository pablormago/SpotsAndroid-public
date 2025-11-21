package com.spotitfly.app.data.comments

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.spotitfly.app.data.model.Spot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Servicio equivalente a CommentReadService en iOS.
 *
 * - markCommentsSeen: marca los comentarios de un spot como "vistos" para un usuario.
 *   Escribe en users/{uid}/spotReads/{spotId} el campo lastSeenAt.
 *
 * - getUnreadSpotIds: devuelve los IDs de spots que tienen comentarios nuevos
 *   (lastCommentAt > lastSeenAt y commentCount > 0).
 */
class CommentReadService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun markCommentsSeen(userId: String, spotId: String) {
        if (userId.isBlank() || spotId.isBlank()) return

        val docRef = db.collection("users")
            .document(userId)
            .collection("spotReads")
            .document(spotId)

        val data = mapOf(
            "lastSeenAt" to FieldValue.serverTimestamp()
        )

        docRef.set(data, SetOptions.merge()).await()
    }

    /**
     * Calcula qué spots tienen comentarios sin leer para este usuario.
     *
     * - spots: lista de spots (de tu usuario, típicamente) con commentCount y lastCommentAt.
     * - Devuelve IDs donde:
     *      commentCount > 0 && lastCommentAt > lastSeenAt (o sin lastSeenAt).
     */
    suspend fun getUnreadSpotIds(
        userId: String,
        spots: List<Spot>
    ): Set<String> {
        if (userId.isBlank() || spots.isEmpty()) return emptySet()

        val result = mutableSetOf<String>()
        val readsCol = db.collection("users")
            .document(userId)
            .collection("spotReads")

        for (spot in spots) {
            val count = (spot.commentCount ?: 0)
            if (count <= 0) continue

            val lastCommentAt: Timestamp = spot.lastCommentAt ?: continue

            val readSnap = readsCol.document(spot.id).get().await()
            val lastSeen = readSnap.getTimestamp("lastSeenAt")

            val hasUnread = lastSeen == null || lastCommentAt.toDate().after(lastSeen.toDate())
            if (hasUnread) {
                result.add(spot.id)
            }
        }

        return result
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
