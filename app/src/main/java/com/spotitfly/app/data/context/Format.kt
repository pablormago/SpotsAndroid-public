package com.spotitfly.app.data.context

import androidx.core.text.HtmlCompat

internal object Format {

    /** Decodifica entidades escapadas tipo \u003c \u003e \" &nbsp; */
    fun decodeEntities(src: String?): String {
        if (src.isNullOrBlank()) return ""
        return src
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")
            .replace("\\\"", "\"")
            .replace("&nbsp;", " ")
    }

    /** Normaliza etiquetas propietarias a HTML estándar (iOS usa <elem> como <b>) */
    fun normalizeHtml(src: String?): String {
        val s = decodeEntities(src)
        if (s.isBlank()) return ""
        return s
            .replace("<elem>", "<b>", ignoreCase = true)
            .replace("</elem>", "</b>", ignoreCase = true)
            .trim()
    }

    /** Quita etiquetas para obtener texto plano (cuando iOS las usa para 'textoPreferido') */
    fun stripTags(src: String?): String {
        if (src.isNullOrBlank()) return ""
        return HtmlCompat.fromHtml(src, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    /** Linkifica emails y teléfonos en HTML (igual intención que iOS) */
    fun linkifyEmailsAndPhones(src: String?): String {
        if (src.isNullOrBlank()) return ""
        var s = src
        // Emails
        val emailRegex = Regex("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
        s = s.replace(emailRegex) { m ->
            val e = m.value
            """<a href="mailto:$e">$e</a>"""
        }
        // Teléfonos sencillos (prefijo + dígitos, espacios o guiones)
        val phoneRegex = Regex("\\+?[0-9][0-9 \\-]{7,}[0-9]")
        s = s.replace(phoneRegex) { m ->
            val p = m.value.trim()
            val tel = p.replace(Regex("[^0-9+]"), "")
            """<a href="tel:$tel">$p</a>"""
        }
        return s
    }
}
