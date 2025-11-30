package com.spotitfly.app.ui.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Pill compacta de tiempo: muestra sólo la temperatura máxima diaria (paridad iOS).
 * Cachea por coordenadas (tile ~0.1º) para evitar peticiones repetidas.
 * No requiere nuevas dependencias (usa HttpURLConnection + org.json).
 */
@Composable
fun WeatherCompactPill(lat: Double, lng: Double) {
    var temp by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(lat, lng) {
        temp = WeatherService.getMaxTemp(lat, lng)
    }

    val txt = temp?.let { "$it°" } ?: "—"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(txt, style = MaterialTheme.typography.labelMedium)
    }
}

private object WeatherService {
    private val cache = ConcurrentHashMap<String, Int?>()

    suspend fun getMaxTemp(lat: Double, lng: Double): Int? = withContext(Dispatchers.IO) {
        val key = "${(lat * 10).roundToInt() / 10.0},${(lng * 10).roundToInt() / 10.0}"
        cache[key]?.let { return@withContext it }

        try {
            // Open-Meteo (sin API key): max temp diaria
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&daily=temperature_2m_max&timezone=auto")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            conn.inputStream.use { input ->
                val body = input.readBytes().decodeToString()
                val json = JSONObject(body)
                val daily = json.optJSONObject("daily")
                val arr = daily?.optJSONArray("temperature_2m_max")
                val first = arr?.optDouble(0)
                val value = first?.let { it.roundToInt() }
                cache[key] = value
                return@withContext value
            }
        } catch (_: Exception) {
            cache[key] = null
            return@withContext null
        }
    }
}
