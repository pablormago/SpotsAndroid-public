package com.spotitfly.app.data.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GeocodingService {
    private const val UA = "Spotitfly/1.0 (Android) geocoding"

    suspend fun reverseLocality(lat: Double, lng: Double, lang: String = "es"): String? =
        withContext(Dispatchers.IO) {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}&zoom=12&accept-language=${URLEncoder.encode(lang, "UTF-8")}"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                connectTimeout = 6000
                readTimeout = 6000
            }
            conn.inputStream.bufferedReader().use { br ->
                val js = JSONObject(br.readText())
                val addr = js.optJSONObject("address") ?: return@use null
                // Preferencias como en iOS
                val keys = listOf("city", "town", "village", "municipality", "hamlet", "suburb")
                for (k in keys) {
                    val v = addr.optString(k)
                    if (v.isNotBlank()) return@withContext v
                }
                // Fallback a distrito/provincia/comunidad
                val fallback = listOf("city_district", "county", "state", "region", "province")
                for (k in fallback) {
                    val v = addr.optString(k)
                    if (v.isNotBlank()) return@withContext v
                }
                null
            }
        }
}
