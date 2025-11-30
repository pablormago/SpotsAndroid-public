package com.spotitfly.app.data.context

/**
 * Ficha "Zona Urbana" (texto informativo 1:1 con iOS).
 * No depende de servidor: se muestra siempre que el punto esté dentro de una zona urbana
 * (la decisión de mostrar u ocultar se toma en el repositorio a partir de la capa urbana).
 */

private fun stripBoldTags(raw: String): String =
    raw.replace(Regex("""<\s*(strong|b)\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<\s*/\s*(strong|b)\s*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<\s*/?\s*elem\s*>""", RegexOption.IGNORE_CASE), "")

internal object UrbanoSource {

    fun fichaZonaUrbana(): Pair<String, String?> {
        val title = "Zona Urbana"
        val html = """
            Antes de volar compruebe si la zona de vuelo se encuentra en entorno urbano.<br><br>
            <b>Definición de entorno urbano:</b><br>
            a) Núcleos de población con áreas consolidadas por la edificación;<br>
            b) Áreas residenciales, comerciales o industriales con accesos rodados, vías públicas pavimentadas, 
               evacuación de aguas y alumbrado público;<br>
            c) Áreas recreativas de acceso público con instalaciones permanentes o eventuales (parques, jardines y playas que reúnan ambos requisitos).<br><br>
            Si el vuelo se produce en alguna de estas zonas, los operadores de UAS sujetos a la obligación de registrarse 
            deberán comunicar su vuelo previamente al <a href="https://www.interior.gob.es/">Ministerio del Interior</a> 
            con una antelación mínima de 5 días naturales respecto a la fecha prevista para el inicio de la operación.
        """.trimIndent()
        return title to html?.let { stripBoldTags(it) }
    }
}
