package com.spotitfly.app.ui.context

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.spotitfly.app.data.context.Format

@Composable
fun HTMLText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val colorInt = MaterialTheme.colorScheme.onSurface.toArgb()
    val baseSp = if (style.fontSize.isUnspecified) 14.sp else style.fontSize
    val finalSp = ((baseSp.value - 1f).coerceAtLeast(10f)).sp
    val textSizePx = with(LocalDensity.current) { finalSp.toPx() }

    val normalized = Format.normalizeHtml(html)
    val linkBlue = Color.parseColor("#007AFF")

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
                setPadding(0, 0, 0, 0)
                // iOS: fuente 14 + line spacing 6pt
                setLineSpacing(6f, 1f)
            }
        },
        update = { tv ->
            tv.setTextColor(colorInt)
            val spanned = HtmlCompat.fromHtml(
                Format.linkifyEmailsAndPhones(normalized),
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
            val s = SpannableString(spanned)

            // Links azules sin subrayado (paridad iOS)
            val urls = s.getSpans(0, s.length, URLSpan::class.java)
            urls.forEach { span ->
                val start = s.getSpanStart(span)
                val end = s.getSpanEnd(span)
                s.removeSpan(span)
                val custom = object : URLSpan(span.url) {
                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                        ds.color = linkBlue
                    }
                }
                s.setSpan(custom, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            tv.text = s
            tv.textSize = (textSizePx / tv.resources.displayMetrics.scaledDensity)
            tv.linksClickable = true
        }
    )
}
