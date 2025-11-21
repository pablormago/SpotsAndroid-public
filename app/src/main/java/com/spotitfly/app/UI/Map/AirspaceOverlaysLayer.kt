package com.spotitfly.app.ui.map

import android.content.Context
import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.spotitfly.app.data.context.overlays.*

/**
 * - Polígonos: FILL (alpha) + contorno doble (HALO + STROKE).
 * - Líneas: contorno doble (HALO + STROKE).
 * - Render incremental por ID (no desaparecen si siguen en viewport).
 * - rescale(zoom) → no-op: mantenemos ancho CONSTANTE como en iOS (.stroke lineWidth fijo).
 */
class AirspaceOverlaysLayer(
    context: Context,
    private val map: GoogleMap
) {
    private data class Entry(val id: String, val objs: MutableList<Any> = mutableListOf())
    private data class PolyTag(val baseWidth: Float, val isHalo: Boolean)

    private val current = LinkedHashMap<String, Entry>() // id -> objetos mapa

    fun clearAll() {
        current.values.forEach { it.objs.forEach(::removeObj) }
        current.clear()
    }

    private fun removeObj(o: Any) {
        when (o) {
            is Polyline -> o.remove()
            is Polygon  -> o.remove()
        }
    }

    fun render(items: List<AirspaceRenderable>) {
        val incomingIds = items.map { it.id }.toSet()

        // 1) Eliminar lo que ya no está
        val toRemove = current.keys.filter { it !in incomingIds }
        toRemove.forEach { id ->
            current.remove(id)?.objs?.forEach(::removeObj)
        }

        // 2) Añadir los nuevos
        val incomingById = items.groupBy { it.id }
        for ((id, group) in incomingById) {
            if (id in current) continue // ya están pintados
            val entry = Entry(id)
            for (r in group) {
                when (r) {
                    is PolylineRenderable -> drawPolyline(r, entry)
                    is PolygonRenderable  -> drawPolygon(r, entry)
                }
            }
            current[id] = entry
        }
    }

    /** iOS usa lineWidth fijo en puntos; Google Maps usa px en pantalla: ya es constante. */
    fun rescale(zoom: Float) {
        // no-op: mantenemos los widths tal cual fueron creados (paridad iOS)
    }

    private fun haloColor(base: Int): Int = Color.argb(90, Color.red(base), Color.green(base), Color.blue(base))
    private fun strokePattern(dashed: Boolean): List<PatternItem>? =
        if (!dashed) null else listOf(Dash(20f), Gap(12f))

    private fun drawPolyline(r: PolylineRenderable, entry: Entry) {
        // HALO
        map.addPolyline(
            PolylineOptions()
                .addAll(r.points)
                .color(haloColor(r.strokeColor))
                .width(r.widthPx + 4f)
                .zIndex(r.zIndex - 0.1f)
                .pattern(strokePattern(r.dashed))
        ).also {
            it.tag = PolyTag(r.widthPx, true)
            entry.objs += it
        }

        // STROKE
        map.addPolyline(
            PolylineOptions()
                .addAll(r.points)
                .color(r.strokeColor)
                .width(r.widthPx)
                .zIndex(r.zIndex)
                .pattern(strokePattern(r.dashed))
        ).also {
            it.tag = PolyTag(r.widthPx, false)
            entry.objs += it
        }
    }

    private fun drawPolygon(r: PolygonRenderable, entry: Entry) {
        if (r.rings.isEmpty()) return

        val outer = r.rings.first()
        val holes = if (r.rings.size > 1) r.rings.drop(1) else emptyList()

        // FILL (alpha). El stroke del Polygon va transparente; contorno con polylines (soporta dashed)
        map.addPolygon(
            PolygonOptions()
                .addAll(outer)
                .apply { holes.forEach { addHole(it) } }
                .fillColor(r.fillColor)
                .strokeColor(Color.TRANSPARENT)
                .zIndex(r.zIndex - 0.2f)
        ).also { entry.objs += it }

        // Contornos: HALO + STROKE por cada ring
        r.rings.forEach { ring ->
            // HALO
            map.addPolyline(
                PolylineOptions()
                    .addAll(ring)
                    .color(haloColor(r.strokeColor))
                    .width(r.widthPx + 4f)
                    .zIndex(r.zIndex - 0.1f)
                    .pattern(strokePattern(r.dashed))
            ).also {
                it.tag = PolyTag(r.widthPx, true)
                entry.objs += it
            }

            // STROKE
            map.addPolyline(
                PolylineOptions()
                    .addAll(ring)
                    .color(r.strokeColor)
                    .width(r.widthPx)
                    .zIndex(r.zIndex)
                    .pattern(strokePattern(r.dashed))
            ).also {
                it.tag = PolyTag(r.widthPx, false)
                entry.objs += it
            }
        }
    }
}
