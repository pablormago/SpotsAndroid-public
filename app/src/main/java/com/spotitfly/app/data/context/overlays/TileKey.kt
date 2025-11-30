package com.spotitfly.app.data.context.overlays

import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Clave de tile estable a partir de bounds + bucket de zoom.
 * Cuantiza a una malla ~0.15º para evitar thrash.
 */
object TileKey {
    fun from(bounds: LatLngBounds, zoom: Float): String {
        val step = 0.15
        fun q(v: Double) = floor(v / step)
        val minLat = min(bounds.southwest.latitude, bounds.northeast.latitude)
        val maxLat = max(bounds.southwest.latitude, bounds.northeast.latitude)
        val minLon = min(bounds.southwest.longitude, bounds.northeast.longitude)
        val maxLon = max(bounds.southwest.longitude, bounds.northeast.longitude)
        val zb = floor(zoom / 1.0f).toInt() // bucket por nivel entero
        return "q:${q(minLat)}:${q(minLon)}:${q(maxLat)}:${q(maxLon)}:z$zb"
    }
}
