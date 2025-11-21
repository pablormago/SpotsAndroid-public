package com.spotitfly.app.data.model

import com.google.firebase.Timestamp
import java.util.Locale

/**
 * Modelo de comentario de spot (paridad con iOS SpotComment).
 *
 * - id: ID del documento en spots/{spotId}/comments/{commentId}
 * - text: texto del comentario
 * - authorId / authorName: autor
 * - createdAt: timestamp de creación (Firestore Timestamp)
 * - status: "hidden", "review", "visible", etc. (opcional)
 */
data class SpotComment(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String?,
    val createdAt: Timestamp?,
    val status: String?
) {

    /**
     * visible por defecto si no hay status
     * (igual que en iOS: si status es nil → visible)
     */
    val isVisible: Boolean
        get() {
            val s = status?.lowercase(Locale.getDefault()) ?: return true
            return s == "visible" || s == "public"
        }

    /**
     * true si el comentario está oculto o en revisión.
     * Útil si alguna vista quiere filtrar "hidden"/"review".
     */
    val isHiddenOrReview: Boolean
        get() {
            val s = status?.lowercase(Locale.getDefault()) ?: return false
            return s == "hidden" || s == "review"
        }
}
