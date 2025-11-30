package com.spotitfly.app.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.spotitfly.app.data.model.SpotComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ViewModel de comentarios por spot (paridad con iOS CommentsViewModel).
 *
 * Responsabilidades:
 *  - Escuchar en tiempo real spots/{spotId}/comments ordenados por createdAt.
 *  - Mapear cada doc a SpotComment.
 *  - Filtrar en cliente los status "hidden"/"review".
 *  - Enviar/editar/borrar comentarios.
 *  - Mantener commentCount y lastCommentAt en el doc del spot.
 *
 * NOTA: De momento no está conectado a ninguna UI; se usará en la Fase 2
 * desde SpotDetailScreen (sheet de comentarios).
 */
class CommentsViewModel(
    private val spotId: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _comments = MutableStateFlow<List<SpotComment>>(emptyList())
    val comments: StateFlow<List<SpotComment>> = _comments.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private var listener: ListenerRegistration? = null

    /**
     * Arranca el listener en Firestore para spots/{spotId}/comments.
     * Equivalente a start() en iOS.
     */
    fun start() {
        if (listener != null) return
        if (spotId.isBlank()) return

        val ref = db.collection("spots")
            .document(spotId)
            .collection("comments")
            .orderBy("createdAt") // ASC, como en iOS

        listener = ref.addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: emptyList()
            val out = mutableListOf<SpotComment>()

            for (doc in docs) {
                val text = doc.getString("text") ?: ""
                val authorId = doc.getString("authorId") ?: ""
                val authorName = doc.getString("authorName")
                val ts: Timestamp? = doc.getTimestamp("createdAt")
                val status = doc.getString("status")

                // 🔎 Filtrado en cliente:
                // - Oculta "hidden" y "review"
                // - Trata status null como visible (compat), igual que en iOS
                val s = status?.lowercase(Locale.getDefault())
                if (s == "hidden" || s == "review") {
                    continue
                }

                val comment = SpotComment(
                    id = doc.id,
                    text = text,
                    authorId = authorId,
                    authorName = authorName,
                    createdAt = ts,
                    status = status
                )
                out += comment
            }

            _comments.value = out
        }
    }

    /**
     * Detiene el listener y limpia la lista local.
     * Equivalente a stop() en iOS.
     */
    fun stop() {
        listener?.remove()
        listener = null
        _comments.value = emptyList()
    }

    /**
     * Enviar un comentario nuevo:
     *  - Crea doc en spots/{spotId}/comments.
     *  - Incrementa commentCount en spots/{spotId}.
     *  - Actualiza lastCommentAt en el spot.
     *
     * En la Fase 2 se llamará desde el sheet de comentarios.
     */
    fun sendComment(text: String, authorName: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (spotId.isBlank()) return

        viewModelScope.launch {
            _isSending.value = true
            try {
                val commentsRef = db.collection("spots")
                    .document(spotId)
                    .collection("comments")

                val data = hashMapOf<String, Any?>(
                    "text" to trimmed,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Crear el comentario
                val newRef = commentsRef.document()
                newRef.set(data).await()

                // 🔼 Mantener contador en el doc del spot
                val spotRef = db.collection("spots").document(spotId)
                spotRef.update(
                    mapOf(
                        "commentCount" to FieldValue.increment(1L),
                        "lastCommentAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
                // TODO: opcional → exponer error a la UI si lo necesitamos
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * Editar un comentario existente (solo cambia el texto).
     * Paridad con edit(commentId:newText:) en iOS.
     */
    fun editComment(commentId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        if (spotId.isBlank()) return

        viewModelScope.launch {
            try {
                val ref = db.collection("spots")
                    .document(spotId)
                    .collection("comments")
                    .document(commentId)

                ref.update(
                    mapOf(
                        "text" to trimmed,
                        "editedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
                // Silencioso por ahora; más adelante podemos añadir logs/snackbar
            }
        }
    }

    /**
     * Borrar un comentario:
     *  - Borra el doc en spots/{spotId}/comments/{commentId}
     *  - Decrementa commentCount en el spot.
     *
     * Igual que delete(commentId:) en iOS.
     */
    fun deleteComment(commentId: String) {
        if (commentId.isBlank()) return
        if (spotId.isBlank()) return

        viewModelScope.launch {
            try {
                val ref = db.collection("spots")
                    .document(spotId)
                    .collection("comments")
                    .document(commentId)

                // Borrar comentario
                ref.delete().await()

                // 🔽 Mantener contador en el doc del spot
                val spotRef = db.collection("spots").document(spotId)
                spotRef.update(
                    mapOf(
                        "commentCount" to FieldValue.increment(-1L)
                    )
                ).await()
            } catch (_: Exception) {
                // Igual: silencioso, más adelante podemos mejorar UX de error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        listener = null
    }
}

/**
 * Pequeño helper local para convertir Task<T> en suspensión (igual que en SpotsRepository).
 */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> cont.resume(res) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
