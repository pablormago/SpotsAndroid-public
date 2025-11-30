package com.spotitfly.app.data.context

import android.content.Context
import com.spotitfly.app.data.geocoding.GeocodingService
import com.spotitfly.app.ui.context.NotamUi
import com.spotitfly.app.ui.map.MapCameraStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.round

// Sources
import com.spotitfly.app.data.context.sources.RestriccionesSource
import com.spotitfly.app.data.context.sources.InfraestructurasSource
import com.spotitfly.app.data.context.sources.MedioambienteSource
import com.spotitfly.app.data.context.sources.NotamSource

// Urbanas como capa propia (aviso urbano), siempre antes del aviso legal en la UI
import com.spotitfly.app.data.context.UrbanoSource

/**
 * Agregador por punto (prioriza el centro de cámara si está disponible, salvo modo "spot").
 *
 * Orden canónico de salida (igual que iOS y como debe pintarse en el sheet):
 *  1) Restricciones
 *  2) Infraestructuras
 *  3) Medioambiente
 *  4) NOTAMs
 *  5) Urbanas
 *  (El "Aviso legal" lo añade la UI al final del sheet)
 */
class ContextRepository(private val context: Context) {

    private val cache = LinkedHashMap<String, PointContextResult>(64, 0.75f, true)

    /** Clave de caché: lat,lng redondeados + flag de localidad (L1/L0) */
    private fun keyFor(lat: Double, lng: Double, includeLocality: Boolean = true): String {
        val la = round(lat * 1000.0) / 1000.0
        val ln = round(lng * 1000.0) / 1000.0
        val locFlag = if (includeLocality) "L1" else "L0"
        return "$la,$ln|$locFlag"
    }

    private fun putCache(k: String, v: PointContextResult) {
        synchronized(cache) {
            cache[k] = v
            if (cache.size > 64) {
                val it = cache.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    /** Mantiene el comportamiento previo: usa centro de cámara si existe. */
    suspend fun fetchAtCameraCenter(): PointContextResult = withContext(Dispatchers.IO) {
        val lat = MapCameraStore.lat ?: 40.4168
        val lng = MapCameraStore.lng ?: -3.7038
        fetchPointContext(lat, lng)
    }

    /**
     * API pública "clásica": incluye localidad y puede priorizar el centro de la cámara.
     * Úsala para aperturas desde mapa.
     */
    suspend fun fetchPointContext(lat: Double, lng: Double): PointContextResult =
        fetchPointContextInternal(
            lat = lat,
            lng = lng,
            includeLocality = true,              // se muestra localidad
            preferGivenCoordinates = false       // si hay centro de cámara, se usa
        )

    /**
     * API pública para SpotDetail: fuerza coords del spot y NO incluye localidad.
     * Úsala cuando abras el PointContextSheet desde el detalle del spot.
     */
    suspend fun fetchForSpot(lat: Double, lng: Double): PointContextResult =
        fetchPointContextInternal(
            lat = lat,
            lng = lng,
            includeLocality = false,             // NO mostrar localidad
            preferGivenCoordinates = true        // forzar coords del spot
        )

    /**
     * Implementación centralizada con control de localidad y preferencia de coords.
     */
    private suspend fun fetchPointContextInternal(
        lat: Double,
        lng: Double,
        includeLocality: Boolean,
        preferGivenCoordinates: Boolean
    ): PointContextResult = withContext(Dispatchers.IO) {
        // 1) Elegir coordenadas
        val (la, ln) = if (!preferGivenCoordinates && MapCameraStore.lat != null && MapCameraStore.lng != null) {
            Pair(MapCameraStore.lat!!, MapCameraStore.lng!!)
        } else Pair(lat, lng)

        // 2) Cache
        val k = keyFor(la, ln, includeLocality)
        synchronized(cache) { cache[k] }?.let { return@withContext it }

        // 3) Fuentes
        val restricciones: List<Pair<String, String?>> =
            runCatching { RestriccionesSource.fetch(la, ln) }.getOrElse { emptyList() }

        val infraestructuras: List<Pair<String, String?>> =
            runCatching { InfraestructurasSource.fetch(la, ln) }.getOrElse { emptyList() }

        val medioambiente: List<Pair<String, String?>> =
            runCatching { MedioambienteSource.fetch(la, ln) }.getOrElse { emptyList() }

        val notams: List<NotamUi> =
            runCatching { NotamSource.fetch(la, ln) }.getOrElse { emptyList() }

        // Capa "Urbanas": ficha/aviso propio si hay contenido
        val urbanas: List<Pair<String, String?>> =
            runCatching {
                val pair = UrbanoSource.fichaZonaUrbana()
                if (pair.second.isNullOrBlank()) emptyList() else listOf(pair)
            }.getOrElse { emptyList() }

        // 4) Localidad (condicionada)
        val loc = if (includeLocality) {
            runCatching { GeocodingService.reverseLocality(la, ln) }.getOrNull()
        } else null

        val res = PointContextResult(
            restricciones = restricciones,
            infraestructuras = infraestructuras,
            medioambiente = medioambiente,
            notams = notams,
            urbanas = urbanas,
            locality = loc
        )
        putCache(k, res)
        res
    }
}
