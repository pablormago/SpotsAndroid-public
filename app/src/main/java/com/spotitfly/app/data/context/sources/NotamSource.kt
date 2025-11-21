package com.spotitfly.app.data.context.sources

import android.util.Log
import com.spotitfly.app.data.context.Http
import com.spotitfly.app.ui.context.NotamUi
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * NOTAM (MapServer/1, f=json → attributes) con logs y parser robusto.
 */
internal object NotamSource {
    private const val TAG = "NotamSource"

    private const val ENDPOINT =
        "https://servais.enaire.es/insignias/rest/services/NOTAM/NOTAM_UAS_APP_V2/MapServer/1/query"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun buildUrl(base: String, params: Map<String, String>): String =
        "$base?" + params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

    private fun keys(obj: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val it = obj.keys()
        while (it.hasNext()) out += it.next()
        return out
    }

    fun fetch(lat: Double, lng: Double): List<NotamUi> {
        val out = mutableListOf<NotamUi>()
        runCatching {
            val params = mapOf(
                "f" to "json",
                "where" to "(itemB <= CURRENT_TIMESTAMP AND (itemC IS NULL OR itemC >= CURRENT_TIMESTAMP))",
                "outFields" to "*",
                "geometry" to "$lng,$lat",
                "geometryType" to "esriGeometryPoint",
                "inSR" to "4326",
                "spatialRel" to "esriSpatialRelIntersects",
                "returnGeometry" to "true"
            )
            Log.d(TAG, "NOTAM → ${buildUrl(ENDPOINT, params)}")
            val t0 = System.currentTimeMillis()
            val js = Http.getJson(ENDPOINT, params)
            val dt = System.currentTimeMillis() - t0
            Log.d(TAG, "NOTAM ← ${dt}ms; rootKeys=${keys(js)}")

            val feats = js.optJSONArray("features") ?: JSONArray()
            Log.d(TAG, "NOTAM features=${feats.length()} (json→attributes)")
            for (i in 0 until feats.length()) {
                val a = feats.getJSONObject(i).optJSONObject("attributes") ?: JSONObject()
                if (i == 0) Log.d(TAG, "NOTAM first.attributes.keys=${keys(a)}")

                val id    = a.optString("id").ifBlank { a.opt("OBJECTID")?.toString().orEmpty() }
                val itemB = a.optString("itemB").ifBlank { null }
                val itemC = a.optString("itemC").ifBlank { null }
                val itemD = a.optString("itemD").ifBlank { null }
                val itemE = a.optString("itemE").ifBlank { null }

                out += NotamUi(
                    notamId = id,
                    itemB = itemB,
                    itemC = itemC,
                    itemD = itemD,
                    descriptionHtml = itemE
                )
            }
        }.onFailure { Log.w(TAG, "NOTAM error: ${it.message}", it) }
        return out
    }
}
