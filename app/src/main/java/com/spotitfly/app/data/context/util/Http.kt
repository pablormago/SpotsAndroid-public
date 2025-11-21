package com.spotitfly.app.data.context

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object Http {
    private const val TAG = "ContextHttp"

    private fun encode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    /**
     * GET JSON con parámetros de consulta y cabeceras opcionales.
     * - `params` se codifican en la querystring.
     * - `headers` se añaden via `setRequestProperty`.
     */
    fun getJson(
        baseUrl: String,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val url = URL("$baseUrl?${encode(params)}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            requestMethod = "GET"
            // Cabeceras opcionales (User-Agent para Nominatim, etc.)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            .orEmpty()

        if (code !in 200..299) {
            Log.w(TAG, "HTTP $code @ $baseUrl")
        }

        return try {
            JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error @ $baseUrl: ${e.message}")
            JSONObject().put("_raw", true).put("_body", body)
        }
    }
}
