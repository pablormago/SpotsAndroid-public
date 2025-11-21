package com.spotitfly.app.data.context.sources

import android.util.Log
import com.spotitfly.app.data.context.Http
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Restricciones por punto (ZG + AIRAC) — paridad iOS.
 * ZG:   FeatureServer/2  (f=geojson → properties)
 * AIRAC: MapServer/41    (f=geojson → properties; fallback a f=json → attributes)
 *
 * Reglas:
 *  - Filtrar TMA **solo** en ZG.
 *  - Orden: ZG primero, luego AIRAC.
 *  - LED (TYPE_CODE "D"): construir mensaje (AVISO + Niveles + Notas + Disclaimers).
 *  - LER/LEP: usar REMARKS (HTML/Texto) linkificado.
 *  - Título: Name > Ident > "Restricción aérea".
 *  - Cuerpo: HTML saneado, sin estilos inline y **SIN NEGRITAS** en el cuerpo.
 */
internal object RestriccionesSource {
    private const val TAG = "RestriccionesSource"

    private const val ENDPOINT_ZG =
        "https://servais.enaire.es/insignia/rest/services/NSF_SRV/SRV_UAS_ZG_V1/FeatureServer/2/query"
    private const val ENDPOINT_AIRAC =
        "https://servais.enaire.es/insignia/rest/services/INSIGNIA_SRV/Aero_SRV_AIRAC_V1/MapServer/41/query"

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

    private fun optAnyCI(obj: JSONObject, name: String): Any? {
        if (obj.has(name)) return obj.opt(name)
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k.equals(name, ignoreCase = true)) return obj.opt(k)
        }
        return null
    }

    private fun containsHtml(s: String): Boolean =
        Regex("<\\s*\\w+[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(s)

    /** Prefiere NIVEL_*; si no hay, cae a VAL + REF. */
    private fun buildAltitude(
        lowerVal: Any?, lowerRefTxtOrCode: String?, lowerNivelStr: String?,
        upperVal: Any?, upperRefTxtOrCode: String?, upperNivelStr: String?
    ): String? {
        val loS = (lowerNivelStr ?: "").trim()
        val upS = (upperNivelStr ?: "").trim()
        if (loS.isNotEmpty() || upS.isNotEmpty()) {
            val loTxt = if (loS.isNotEmpty()) loS else "SFC"
            return if (upS.isNotEmpty()) "$loTxt – $upS" else loTxt
        }
        fun fmt(v: String?, ref: String?): String? {
            val vv = v?.trim().orEmpty()
            if (vv.isEmpty() || vv.equals("null", true)) return null
            val unit = when ((ref ?: "").trim().uppercase()) {
                "ALT" -> "ft ALT"
                "AMSL" -> "m AMSL"
                "SFC" -> "SFC"
                "STD", "FL" -> "FL"
                else -> (ref ?: "").trim().uppercase()
            }
            return if (unit == "SFC") "SFC" else "$vv${if (unit.isNotBlank()) " $unit" else ""}"
        }
        val lo = fmt(lowerVal?.toString(), lowerRefTxtOrCode)
        val up = fmt(upperVal?.toString(), upperRefTxtOrCode)
        if (lo == null && up == null) return null
        return listOfNotNull(lo, up).joinToString(" – ")
    }

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
     * Limpieza/normalización como iOS y **SIN NEGRITAS** en el cuerpo (b/strong/elem).
     * - quita target/style/color inline
     * - <p> → <br>; colapsa <br> múltiples
     * - elimina <b>,</b>,<strong>,</strong> y <elem>
     */
    private fun sanitizeHtml(raw: String): String {
        var x = raw
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

    /**
     * Detección robusta de TMA (solo la usaremos en ZG).
     */
    private fun isTma(
        name: String?,
        type: String?,
        ident: String? = null,
        message: String? = null
    ): Boolean {
        val blob = listOf(name, type, ident, message)
            .joinToString(" ")
            .lowercase()

        val patterns = listOf(
            Regex("""\btma\b""", RegexOption.IGNORE_CASE),
            Regex("""terminal\s+control\s+area""", RegexOption.IGNORE_CASE),
            Regex("""zona\s+de\s+control\s+terminal""", RegexOption.IGNORE_CASE),
            Regex("""control\s+terminal""", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(blob) }
    }

    private fun buildLedMessage(ident: String?, altitude: String?, remarksRaw: String?): String {
        val sb = StringBuilder()

        // AVISO (sin <b>)
        sb.append("<p>AVISO: Se encuentra en una zona en la que pueden desplegarse actividades peligrosas para el vuelo de aeronaves")
        if (!ident.isNullOrBlank()) sb.append(": ").append(ident.trim())
        sb.append(".</p>")

        if (!altitude.isNullOrBlank()) {
            val parts = altitude.split("–").map { it.trim() }
            val lower = parts.getOrNull(0)?.ifBlank { "SFC" } ?: "SFC"
            val upper = parts.getOrNull(1)
            sb.append("<p>Nivel inferior: ").append(lower).append("</p>")
            if (!upper.isNullOrBlank()) {
                sb.append("<p>Nivel superior: ").append(upper).append("</p>")
            }
        }

        sb.append("<p>Notas:</p>")

        val remarks = (remarksRaw ?: "").trim()
        val notas = when {
            remarks.isBlank() -> ""
            containsHtml(remarks) -> sanitizeHtml(remarks)
            else -> sanitizeHtml(linkify(remarks))
        }

        val disclaimers =
            "<p style=\"margin-top:12px;\"><em>Tenga en cuenta que debe verificar la normativa vigente y la información aeronáutica oficial del día en que vaya a volar, para saber si esta zona le aplica.</em></p>" +
                    "<p style=\"margin-top:8px;\">Información publicada en el AIP (sección ENR 5.1) y la circular " +
                    "<a href=\"https://aip.enaire.es/aip/Circulares-es.html\" rel=\"noopener\">AIC NTL 01/25</a>.</p>"

        return sb.toString() + notas + disclaimers
    }

    // ---------- API ----------
    fun fetch(lat: Double, lng: Double): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()

        // 1) Zonas Geográficas (FS/2, geojson→properties) — filtrar TMA aquí
        runCatching {
            val params = mapOf(
                "where" to "1=1",
                "geometry" to "$lng,$lat",
                "geometryType" to "esriGeometryPoint",
                "inSR" to "4326",
                "spatialRel" to "esriSpatialRelIntersects",
                "outFields" to "Identifier,Name,Type,Lower,LowerReference,Upper,UpperReference,Message,Service,Phone,Email",
                "returnGeometry" to "true",
                "outSR" to "4326",
                "f" to "geojson"
            )
            Log.d(TAG, "ZG → ${buildUrl(ENDPOINT_ZG, params)}")
            val t0 = System.currentTimeMillis()
            val root = Http.getJson(ENDPOINT_ZG, params)
            val dt = System.currentTimeMillis() - t0
            Log.d(TAG, "ZG ← ${dt}ms; rootKeys=${keys(root)}")

            val feats = root.optJSONArray("features") ?: JSONArray()
            Log.d(TAG, "ZG features=${feats.length()} (geojson→properties)")
            for (i in 0 until feats.length()) {
                val p = feats.getJSONObject(i).optJSONObject("properties") ?: JSONObject()
                if (i == 0) Log.d(TAG, "ZG first.properties.keys=${keys(p)}")

                val name = optStringCI(p, "Name")
                val ident = optStringCI(p, "Identifier")
                val type = optStringCI(p, "Type")
                val msg = optStringCI(p, "Message")

                // 👇 Solo aquí filtramos TMA
                if (isTma(name, type, ident, msg)) continue

                val title = when {
                    name.isNotBlank() -> name
                    ident.isNotBlank() -> ident
                    else -> "Restricción aérea"
                }
                val html = when {
                    msg.isBlank() -> null
                    containsHtml(msg) -> sanitizeHtml(msg)
                    else -> sanitizeHtml(linkify(msg))
                }
                out += title to html
            }
        }.onFailure { Log.w(TAG, "ZG error: ${it.message}", it) }

        // 2) AIRAC: LED/LEP/LER (MapServer/41) — **NO** filtrar TMA aquí
        runCatching {
            val paramsGeo = mapOf(
                "where" to "TYPE_CODE IN ('D','R','P')",
                "geometry" to "$lng,$lat",
                "geometryType" to "esriGeometryPoint",
                "inSR" to "4326",
                "spatialRel" to "esriSpatialRelIntersects",
                "outFields" to "IDENT_TXT,NAME_TXT,TYPE_CODE,LOWER_VAL,DISTVERTLOWER_CODE,DISTVERTLOWER_TXT,NIVEL_INF,UPPER_VAL,DISTVERTUPPER_CODE,DISTVERTUPPER_TXT,NIVEL_SUP,REMARKS_TXT",
                "returnGeometry" to "true",
                "outSR" to "4326",
                "f" to "geojson"
            )
            Log.d(TAG, "AIRAC(geojson) → ${buildUrl(ENDPOINT_AIRAC, paramsGeo)}")
            val t0 = System.currentTimeMillis()
            val js = Http.getJson(ENDPOINT_AIRAC, paramsGeo)
            val dt = System.currentTimeMillis() - t0
            if (js.has("error")) {
                Log.w(TAG, "AIRAC geojson error=${js.optJSONObject("error")}")
                val paramsJson = paramsGeo.toMutableMap().apply { this["f"] = "json" }
                Log.d(TAG, "AIRAC(json) retry → ${buildUrl(ENDPOINT_AIRAC, paramsJson)}")
                val js2 = Http.getJson(ENDPOINT_AIRAC, paramsJson)
                Log.d(TAG, "AIRAC(json) ← rootKeys=${keys(js2)}")
                parseAiracJson(js2, out)
            } else {
                Log.d(TAG, "AIRAC(geojson) ← ${dt}ms; rootKeys=${keys(js)}")
                parseAiracGeo(js, out)
            }
        }.onFailure { Log.w(TAG, "AIRAC error: ${it.message}", it) }

        return out
    }

    private fun parseAiracGeo(root: JSONObject, out: MutableList<Pair<String, String?>>) {
        val feats = root.optJSONArray("features") ?: JSONArray()
        Log.d(TAG, "AIRAC features=${feats.length()} (geojson→properties)")
        for (i in 0 until feats.length()) {
            val p = feats.getJSONObject(i).optJSONObject("properties") ?: JSONObject()
            if (i == 0) Log.d(TAG, "AIRAC first.properties.keys=${keys(p)}")

            val name = optStringCI(p, "NAME_TXT")
            val ident = optStringCI(p, "IDENT_TXT")

            val title = when {
                name.isNotBlank() -> name
                ident.isNotBlank() -> ident
                else -> "Restricción aérea"
            }

            val typeCode = optStringCI(p, "TYPE_CODE")
            val remarks = optStringCI(p, "REMARKS_TXT")
            val html = if (typeCode.equals("D", ignoreCase = true)) {
                val altitude = buildAltitude(
                    optAnyCI(p, "LOWER_VAL"), optStringCI(p, "DISTVERTLOWER_TXT").ifBlank { optStringCI(p, "DISTVERTLOWER_CODE") }, optStringCI(p, "NIVEL_INF"),
                    optAnyCI(p, "UPPER_VAL"), optStringCI(p, "DISTVERTUPPER_TXT").ifBlank { optStringCI(p, "DISTVERTUPPER_CODE") }, optStringCI(p, "NIVEL_SUP")
                )
                buildLedMessage(ident, altitude, remarks)
            } else {
                when {
                    remarks.isBlank() -> null
                    containsHtml(remarks) -> sanitizeHtml(remarks)
                    else -> sanitizeHtml(linkify(remarks))
                }
            }

            out += title to html
        }
    }

    private fun parseAiracJson(root: JSONObject, out: MutableList<Pair<String, String?>>) {
        val feats = root.optJSONArray("features") ?: JSONArray()
        Log.d(TAG, "AIRAC features=${feats.length()} (json→attributes)")
        for (i in 0 until feats.length()) {
            val a = feats.getJSONObject(i).optJSONObject("attributes") ?: JSONObject()
            if (i == 0) Log.d(TAG, "AIRAC first.attributes.keys=${keys(a)}")

            val name = optStringCI(a, "NAME_TXT")
            val ident = optStringCI(a, "IDENT_TXT")

            val title = when {
                name.isNotBlank() -> name
                ident.isNotBlank() -> ident
                else -> "Restricción aérea"
            }

            val typeCode = optStringCI(a, "TYPE_CODE")
            val remarks = optStringCI(a, "REMARKS_TXT")
            val html = if (typeCode.equals("D", ignoreCase = true)) {
                val altitude = buildAltitude(
                    optAnyCI(a, "LOWER_VAL"), optStringCI(a, "DISTVERTLOWER_TXT").ifBlank { optStringCI(a, "DISTVERTLOWER_CODE") }, optStringCI(a, "NIVEL_INF"),
                    optAnyCI(a, "UPPER_VAL"), optStringCI(a, "DISTVERTUPPER_TXT").ifBlank { optStringCI(a, "DISTVERTUPPER_CODE") }, optStringCI(a, "NIVEL_SUP")
                )
                buildLedMessage(ident, altitude, remarks)
            } else {
                when {
                    remarks.isBlank() -> null
                    containsHtml(remarks) -> sanitizeHtml(remarks)
                    else -> sanitizeHtml(linkify(remarks))
                }
            }

            out += title to html
        }
    }
}
