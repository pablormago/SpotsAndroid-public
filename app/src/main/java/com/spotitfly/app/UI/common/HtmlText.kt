package com.spotitfly.app.ui.common

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun HtmlText(html: String) {
    AndroidView(factory = { context ->
        TextView(context).apply {
            // Habilitar clicks en <a href=...>
            movementMethod = LinkMovementMethod.getInstance()
        }
    }, update = { tv ->
        // FROM_HTML_MODE_LEGACY conserva <b>, <br>, etc. y decodifica entidades.
        tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        // (color/estilo de enlaces: el del tema por defecto; igual que iOS usa el del sistema)
        tv.linksClickable = true
    })
}
