package com.spotitfly.app.ui.comments

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.spotitfly.app.data.comments.CommentReadService
import com.spotitfly.app.data.model.SpotComment
import com.spotitfly.app.ui.design.SpotDetailTokens as T
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sheet de comentarios para un spot (equivalente a CommentsSheetView en iOS).
 *
 * - Lista comentarios visibles en tiempo real.
 * - Permite enviar comentarios nuevos.
 * - Cada fila replica layout de iOS: cabecera con autor + fecha + acciones
 *   y cuerpo con texto (links clicables) o modo edición inline.
 * - Notifica el número actual de comentarios a la pantalla de detalle (sin parpadeos).
 * - Marca comentarios como "vistos" en users/{uid}/spotReads/{spotId} (CommentReadService).
 */
@Composable
fun CommentsSheet(
    spotId: String,
    spotDescription: String,
    initialCount: Int,
    onUpdatedCount: (Int) -> Unit
) {
    val vm = remember(spotId) { CommentsViewModel(spotId) }

    val comments by vm.comments.collectAsState()
    val isSending by vm.isSending.collectAsState()

    val scrollState = rememberScrollState()

    var input by remember { mutableStateOf("") }
    var commentToDelete by remember { mutableStateOf<SpotComment?>(null) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUid = currentUser?.uid
    val currentName = currentUser?.displayName

    val readService = remember(spotId) { CommentReadService() }

    // Estado local para el contador que se envía al detalle
    var lastPublishedCount by remember(spotId) { mutableStateOf(initialCount.coerceAtLeast(0)) }
    var hasAppliedServerCount by remember(spotId) { mutableStateOf(false) }

    LaunchedEffect(spotId) {
        vm.start()
    }
    DisposableEffect(spotId) {
        onDispose { vm.stop() }
    }

    val trimmed = input.trim()

    // Autoscroll, actualización del contador sin 2→0→2 y markSeen en spotReads
    LaunchedEffect(comments.size, currentUid) {
        val newCount = comments.size

        if (!hasAppliedServerCount) {
            // Evitamos machacar el contador inicial con 0 si venimos de un valor > 0
            if (!(newCount == 0 && initialCount > 0)) {
                lastPublishedCount = newCount
                onUpdatedCount(newCount)
                hasAppliedServerCount = true
            }
        } else {
            if (newCount != lastPublishedCount) {
                lastPublishedCount = newCount
                onUpdatedCount(newCount)
            }
        }

        // MarkSeen equivalente a iOS CommentReadService.markSeen
        if (currentUid != null && spotId.isNotBlank()) {
            try {
                readService.markCommentsSeen(currentUid, spotId)
            } catch (_: Exception) {
                // Silencioso; no rompemos la UI si falla la escritura
            }
        }

        if (comments.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(T.gapM)
    ) {
        Text(
            text = "Comentarios",
            style = MaterialTheme.typography.titleLarge
        )

        if (spotDescription.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Descripción del spot",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = T.textPrimary
                )
                Spacer(Modifier.height(T.gapXS))
                Text(
                    text = spotDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = T.textBody
                )
            }
        }

        Divider()

        if (comments.isEmpty()) {
            Text(
                text = "Todavía no hay comentarios. Sé el primero en escribir uno.",
                style = MaterialTheme.typography.bodyMedium,
                color = T.textMuted
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(T.gapS)
            ) {
                comments.forEach { comment ->
                    val canEdit = currentUid != null && currentUid == comment.authorId
                    CommentRow(
                        comment = comment,
                        canEdit = canEdit,
                        currentUserName = currentName,
                        onEdit = { newText ->
                            vm.editComment(comment.id, newText)
                        },
                        onAskDelete = {
                            commentToDelete = comment
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(T.gapM))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un comentario…") },
                maxLines = 4
            )
            Spacer(Modifier.width(T.gapS))
            IconButton(
                onClick = {
                    vm.sendComment(trimmed, currentName)
                    input = ""
                },
                enabled = trimmed.isNotEmpty() && !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "Enviar comentario"
                    )
                }
            }
        }
    }

    val pendingDelete = commentToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text("Eliminar comentario") },
            text = { Text("¿Seguro que quieres eliminar este comentario? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteComment(pendingDelete.id)
                        commentToDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun CommentRow(
    comment: SpotComment,
    canEdit: Boolean,
    currentUserName: String?,
    onEdit: (String) -> Unit,
    onAskDelete: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(comment.text) }

    // 1:1 con iOS: si el comentario es tuyo y el doc aún no trae authorName,
    // usamos tu username local en vez de "Anónimo" para evitar el salto.
    val authorFromDoc = comment.authorName?.takeIf { it.isNotBlank() }
    val author = when {
        authorFromDoc != null -> authorFromDoc
        canEdit && !currentUserName.isNullOrBlank() -> currentUserName
        else -> "Anónimo"
    }

    val timeString = comment.createdAt?.let { formatRelativeTime(it) } ?: ""

    val bubbleShape: Shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(T.surface, bubbleShape)
            .border(
                width = 1.dp,
                color = T.textMuted.copy(alpha = 0.12f),
                shape = bubbleShape
            )
            .padding(10.dp)
    ) {
        // Cabecera: autor + fecha + acciones (editar/borrar)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = T.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            if (timeString.isNotEmpty()) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = T.textMuted
                )
            }

            if (canEdit) {
                Spacer(Modifier.width(T.gapS))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            draft = comment.text
                            isEditing = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Editar comentario"
                        )
                    }
                    IconButton(
                        onClick = { onAskDelete() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar comentario",
                            tint = T.danger
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(T.gapXS))

        if (isEditing) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Editar comentario") },
                    maxLines = 4
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            isEditing = false
                            draft = comment.text
                        }
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val newText = draft.trim()
                            if (newText.isNotEmpty()) {
                                onEdit(newText)
                                isEditing = false
                            }
                        }
                    ) {
                        Text("Guardar")
                    }
                }
            }
        } else {
            LinkifiedText(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = T.textBody
            )
        }
    }
}

private fun formatRelativeTime(ts: Timestamp): String {
    val timeMs = ts.toDate().time
    val nowMs = System.currentTimeMillis()
    val diffMs = nowMs - timeMs
    if (diffMs < 0) return "Ahora"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    return when {
        minutes < 1 -> "Ahora"
        minutes < 60 -> "hace ${minutes} min"
        hours < 24 -> "hace ${hours} h"
        days == 1L -> "ayer"
        days < 7 -> "hace ${days} días"
        else -> {
            val df = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            df.format(ts.toDate())
        }
    }
}

@Composable
private fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = T.textBody
) {
    val context = LocalContext.current

    val annotated = buildAnnotatedString {
        val matcher = Patterns.WEB_URL.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Texto normal antes del link
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            // Texto del link
            val url = text.substring(start, end)
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(url)
            }
            pop()
            lastIndex = end
        }

        // Resto del texto después del último link
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color)
    ) { offset ->
        val urlAnn = annotated.getStringAnnotations(
            tag = "URL",
            start = offset,
            end = offset
        ).firstOrNull()

        urlAnn?.let { ann ->
            runCatching {
                val rawUrl = ann.item
                val uri = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    Uri.parse(rawUrl)
                } else {
                    Uri.parse("https://$rawUrl")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            }
        }
    }
}
