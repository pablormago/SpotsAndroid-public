package com.spotitfly.app.data.context.overlays

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.spotitfly.app.data.context.overlays.sources.InfraestructurasOverlaySource
import com.spotitfly.app.data.context.overlays.sources.MedioambienteOverlaySource
import com.spotitfly.app.data.context.overlays.sources.RestriccionesOverlaySource
import com.spotitfly.app.data.context.overlays.sources.UrbanoOverlaySource
import com.spotitfly.app.data.context.overlays.simplify.GeometrySimplifier
import com.spotitfly.app.data.context.overlays.prefs.OverlayToggles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Viewport-based por TILES.
 * - La CLAVE DE CACHÉ incluye los TOGGLES → cambiar toggles fuerza nueva carga del tile.
 * - Simplificación geométrica por zoom (Douglas–Peucker).
 * - Dedup por id y unión de todas las celdas visibles.
 */
class AirspaceOverlayStore(
    private val context: android.content.Context
) {
    private val cache = LinkedHashMap<String, List<AirspaceRenderable>>(128, 0.75f, true)
    private val stepDeg = 0.15  // malla aprox.
    private val logTag = "AirspaceOverlayStore"

    private fun putCache(k: String, v: List<AirspaceRenderable>) {
        synchronized(cache) {
            cache[k] = v
            if (cache.size > 128) {
                val it = cache.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }
    }

    suspend fun load(bounds: LatLngBounds, zoom: Float, toggles: OverlayToggles): List<AirspaceRenderable> =
        withContext(Dispatchers.IO) {
            //~5–7 → país / grandes regiones
            //
            //~10–11 → provincia / ciudad grande
            //
            //~13–15 → ciudad / barrio
            //
            //~16+ → calle muy detallada

            if (zoom < 8f) return@withContext emptyList()

            val tiles = tilesCovering(bounds)
            val acc = ArrayList<AirspaceRenderable>(4096)

            for (tb in tiles) {
                val keyBase = TileKey.from(tb, floor(zoom))
                val key = "$keyBase|u=${b(toggles.urbano)}|m=${b(toggles.medioambiente)}|r=${b(toggles.restricciones)}|i=${b(toggles.infraestructuras)}"

                val hit = synchronized(cache) { cache[key] }
                if (hit != null) {
                    acc.addAll(hit)
                    continue
                }

                val merged = buildList {
                    if (toggles.urbano) {
                        runCatching { UrbanoOverlaySource.fetch(tb) }
                            .onSuccess { addAll(it) }
                            .onFailure { e ->
                                Log.w(logTag, "Error cargando overlays URBANO para tile $tb: ${e.message}")
                            }
                    }
                    if (toggles.medioambiente) {
                        runCatching { MedioambienteOverlaySource.fetch(tb) }
                            .onSuccess { addAll(it) }
                            .onFailure { e ->
                                Log.w(logTag, "Error cargando overlays MEDIOAMBIENTE para tile $tb: ${e.message}")
                            }
                    }
                    if (toggles.restricciones) {
                        runCatching { RestriccionesOverlaySource.fetch(tb) }
                            .onSuccess { addAll(it) }
                            .onFailure { e ->
                                Log.w(logTag, "Error cargando overlays RESTRICCIONES para tile $tb: ${e.message}")
                            }
                    }
                    if (toggles.infraestructuras) {
                        runCatching { InfraestructurasOverlaySource.fetch(tb) }
                            .onSuccess { addAll(it) }
                            .onFailure { e ->
                                Log.w(logTag, "Error cargando overlays INFRA para tile $tb: ${e.message}")
                            }
                    }
                }

                putCache(key, merged)
                acc.addAll(merged)

            }

            // Dedup por id (un feature puede caer en dos tiles)
            val seen = HashSet<String>(acc.size)
            val dedup = ArrayList<AirspaceRenderable>(acc.size)
            for (r in acc) if (seen.add(r.id)) dedup += r

            // Simplificación geométrica dependiente del zoom
            GeometrySimplifier.simplify(dedup, zoom)
        }

    private fun b(v: Boolean) = if (v) 1 else 0

    private fun tilesCovering(b: LatLngBounds): List<LatLngBounds> {
        val minLat = min(b.southwest.latitude, b.northeast.latitude)
        val maxLat = max(b.southwest.latitude, b.northeast.latitude)
        val minLon = min(b.southwest.longitude, b.northeast.longitude)
        val maxLon = max(b.southwest.longitude, b.northeast.longitude)

        val i0 = floor(minLat / stepDeg).toInt()
        val i1 = floor(maxLat / stepDeg).toInt()
        val j0 = floor(minLon / stepDeg).toInt()
        val j1 = floor(maxLon / stepDeg).toInt()

        val out = ArrayList<LatLngBounds>((i1 - i0 + 1) * (j1 - j0 + 1))
        for (i in i0..i1) {
            for (j in j0..j1) {
                val sw = LatLng(i * stepDeg, j * stepDeg)
                val ne = LatLng((i + 1) * stepDeg, (j + 1) * stepDeg)
                out += LatLngBounds(sw, ne)
            }
        }
        return out
    }
}
