package com.spotitfly.app.data.context

/**
 * Linkifica URLs, emails y teléfonos de forma robusta:
 * - Acepta "www." añadiendo http:// si falta.
 * - Evita arrastrar puntuación de cierre (. , ; : ) ])
 * - Tolera espacios/guiones/paréntesis en teléfonos (+34 ...).
 * - No re-ancla si ya viene <a ...>.
 */
internal object HtmlUtils {

    fun linkifyRich(input: String?): String? {
        if (input.isNullOrBlank()) return input
        val s0 = input
        if (s0.contains("<a", ignoreCase = true)) return s0 // ya viene con anclas

        // Des-ofuscaciones típicas
        var s = s0
            .replace(" [at] ", "@", ignoreCase = true)
            .replace(" (at) ", "@", ignoreCase = true)
            .replace(" [dot] ", ".", ignoreCase = true)
            .replace(" (dot) ", ".", ignoreCase = true)

        // URLs con o sin esquema (www.)
        s = Regex("""(?i)\b((?:https?://|www\.)[^\s<>()]+)""")
            .replace(s) { m ->
                var url = m.groupValues[1]
                while (url.isNotEmpty() && ".,;:)]]".contains(url.last())) {
                    url = url.dropLast(1)
                }
                val href = if (url.startsWith("http", ignoreCase = true)) url else "http://$url"
                """<a href="$href" target="_blank" rel="noopener">$url</a>"""
            }

        // Emails
        s = Regex("""(?i)\b([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})\b""")
            .replace(s) { m ->
                val e = m.groupValues[1]
                """<a href="mailto:$e">$e</a>"""
            }

        // Teléfonos (mín. 7 dígitos; permite +, espacios, guiones, paréntesis)
        s = Regex("""(?<!\d)(\+?\d[\d\s\-\(\)]{6,}\d)""")
            .replace(s) { m ->
                var raw = m.groupValues[1].trim()
                while (raw.isNotEmpty() && ".,;:)".contains(raw.last())) {
                    raw = raw.dropLast(1)
                }
                val digits = raw.replace(Regex("""[^\d+]"""), "")
                """<a href="tel:$digits">$raw</a>"""
            }

        return s
    }
}
