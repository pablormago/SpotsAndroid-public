package com.spotitfly.app.data.context.sources

import android.util.Log
import com.spotitfly.app.data.context.Http
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Infraestructuras por punto (ZG/FeatureServer/0) — paridad iOS:
 *  - Título: identifier (fallback "(Sin nombre)")
 *  - Cuerpo: message (HTML) o, si falta, description (HTML)
 *  - Limpieza HTML como iOS + SIN NEGRITAS en el cuerpo
 *  - Linkify (urls, emails, teléfonos)
 */
internal object InfraestructurasSource {
    private const val TAG = "InfraestructurasSource"

    private const val ENDPOINT_ZG_INFRA =
        "https://servais.enaire.es/insignia/rest/services/NSF_SRV/SRV_UAS_ZG_V1/FeatureServer/0/query"

    // ---------- utils ----------
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun buildUrl(base: String, params: Map<String, String>): String =
        "$base?" + params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

    private fun keys(obj: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val it = obj.keys()
        while (it.hasNext()) out += it.next()
        return out
    }

    private fun optStringCI(obj: JSONObject, name: String): String {
        if (obj.has(name)) return obj.optString(name)
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k.equals(name, ignoreCase = true)) return obj.optString(k)
        }
        return ""
    }

    private fun containsHtml(s: String): Boolean =
        Regex("<\\s*\\w+[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(s)

    private fun decodeBasicEntities(raw: String): String =
        raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun linkify(raw: String): String {
        var x = raw
        // URLs
        x = x.replace(Regex("""https?://[^\s<]+""", RegexOption.IGNORE_CASE)) { m ->
            val url = m.value
            """<a href="$url">$url</a>"""
        }
        // emails
        x = x.replace(Regex("""[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)) { m ->
            val e = m.value
            """<a href="mailto:$e">$e</a>"""
        }
        // teléfonos sencillos
        x = x.replace(Regex("""\+?[0-9][0-9 \-]{7,}[0-9]""")) { m ->
            val p = m.value.trim()
            val tel = p.replace(Regex("[^0-9+]"), "")
            """<a href="tel:$tel">$p</a>"""
        }
        return x
    }

    /**
     * Limpieza/normalización como iOS y SIN NEGRITAS en cuerpo (b/strong/elem).
     * - quita target/style/color inline
     * - <p> → <br>; colapsa <br> múltiples
     * - elimina <b>,</b>,<strong>,</strong> y <elem>
     */
    private fun sanitizeHtml(raw: String): String {
        var x = decodeBasicEntities(raw)

        // quitar atributos molestos (target, style, color)
        x = x.replace(Regex("""\s*(target|style|color)=['"][^'"]*['"]""", RegexOption.IGNORE_CASE), "")

        // normalizar <p> → <br>, colapsar <br> múltiples y trims
        x = x.replace(Regex("""<\s*/?\s*p[^>]*>""", RegexOption.IGNORE_CASE), "<br>")
        x = x.replace(Regex("""(<br\s*/?>\s*){3,}""", RegexOption.IGNORE_CASE), "<br><br>")
        x = x.replace(Regex("""\s*<br\s*/?>\s*""", RegexOption.IGNORE_CASE), "<br>")

        // eliminar negritas en el cuerpo + pseudo-tag elem
        x = x.replace(Regex("""<\s*(strong|b)\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        x = x.replace(Regex("""<\s*/\s*(strong|b)\s*>""", RegexOption.IGNORE_CASE), "")
        x = x.replace(Regex("""<\s*/?\s*elem\s*>""", RegexOption.IGNORE_CASE), "")

        return x.trim()
    }

    // ---------- API ----------
    fun fetch(lat: Double, lng: Double): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()

        runCatching {
            val params = mapOf(
                "where" to "1=1",
                "geometry" to "$lng,$lat",
                "geometryType" to "esriGeometryPoint",
                "inSR" to "4326",
                "spatialRel" to "esriSpatialRelIntersects",
                "outFields" to "Identifier,Name,Message,Description,SiteURL,Phone,Email",
                "returnGeometry" to "true",
                "outSR" to "4326",
                "f" to "geojson"
            )
            Log.d(TAG, "INFRA(geojson) → ${buildUrl(ENDPOINT_ZG_INFRA, params)}")
            val t0 = System.currentTimeMillis()
            val root = Http.getJson(ENDPOINT_ZG_INFRA, params)
            val dt = System.currentTimeMillis() - t0
            Log.d(TAG, "INFRA(geojson) ← ${dt}ms; rootKeys=${keys(root)}")

            val feats = root.optJSONArray("features") ?: JSONArray()
            Log.d(TAG, "INFRA features=${feats.length()} (geojson→properties)")
            for (i in 0 until feats.length()) {
                val p = feats.getJSONObject(i).optJSONObject("properties") ?: JSONObject()
                if (i == 0) Log.d(TAG, "INFRA first.properties.keys=${keys(p)}")

                val identifier = optStringCI(p, "Identifier")
                val title = if (identifier.isNotBlank()) identifier else "(Sin nombre)"

                val message = optStringCI(p, "Message")
                val description = optStringCI(p, "Description")
                val raw = when {
                    message.isNotBlank() -> message
                    description.isNotBlank() -> description
                    else -> ""
                }
                val html = when {
                    raw.isBlank() -> null
                    containsHtml(raw) -> sanitizeHtml(raw)
                    else -> sanitizeHtml(linkify(raw))
                }

                out += title to html
            }
        }.onFailure { Log.w(TAG, "INFRA error: ${it.message}", it) }

        return out
    }
}
