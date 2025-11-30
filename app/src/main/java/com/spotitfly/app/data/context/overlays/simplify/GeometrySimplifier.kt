package com.spotitfly.app.data.context.overlays.simplify

import com.google.android.gms.maps.model.LatLng
import com.spotitfly.app.data.context.overlays.*

object GeometrySimplifier {

    /** Tolerancia en grados aprox: a menor zoom → mayor tolerancia. */
    /*private fun toleranceDegrees(zoom: Float): Double {
        // Mapa mundo ~ 256*2^z px. Aproximamos deg/px en ecuador ≈ 360 / (256 * 2^z)
        val degPerPx = 360.0 / (256.0 * Math.pow(2.0, zoom.toDouble().coerceIn(0.0, 21.0)))
        // Factor base MUY bajo (0.5–1.5 px) para mantener los polígonos suaves como en iOS
        val px = when {
            zoom < 10f -> 1.5
            zoom < 12f -> 1.25
            zoom < 14f -> 1.0
            zoom < 16f -> 0.75
            else       -> 0.5
        }
        return degPerPx * px
    }*/

    private fun toleranceDegrees(zoom: Float): Double {
        val degPerPx = 360.0 / (256.0 * Math.pow(2.0, zoom.toDouble().coerceIn(0.0, 21.0)))
        val px = when {
            zoom < 8f  -> 1.5   // muy lejos (medio país) → mucha simplificación
            zoom < 10f -> 1.0   // rango donde ves una Comunidad / provincia
            zoom < 12f -> 0.75
            zoom < 14f -> 0.6
            zoom < 16f -> 0.5
            else       -> 0.4
        }
        return degPerPx * px
    }

    fun simplify(list: List<AirspaceRenderable>, zoom: Float): List<AirspaceRenderable> {
        val tol = toleranceDegrees(zoom)
        return list.map { r ->
            when (r) {
                is PolylineRenderable -> r.copy(points = dp(r.points, tol))
                is PolygonRenderable  -> r.copy(rings = r.rings.map { dp(it, tol) }.filter { it.size >= 3 })
            }
        }
    }

    /** Douglas–Peucker en lat/lon (grados). */
    private fun dp(points: List<LatLng>, eps: Double): List<LatLng> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.lastIndex] = true
        dpRec(points, 0, points.lastIndex, eps, keep)
        val out = ArrayList<LatLng>(points.size)
        for (i in points.indices) if (keep[i]) out += points[i]
        return out
    }

    private fun dpRec(pts: List<LatLng>, i: Int, j: Int, eps: Double, keep: BooleanArray) {
        if (j <= i + 1) return
        var idx = -1
        var dmax = 0.0
        val a = pts[i]; val b = pts[j]
        for (k in i + 1 until j) {
            val d = perpDistDeg(pts[k], a, b)
            if (d > dmax) { dmax = d; idx = k }
        }
        if (dmax > eps && idx != -1) {
            keep[idx] = true
            dpRec(pts, i, idx, eps, keep)
            dpRec(pts, idx, j, eps, keep)
        }
    }

    // Distancia perpendicular aproximada en grados (lat/lon; suficiente para simplificación visual)
    private fun perpDistDeg(p: LatLng, a: LatLng, b: LatLng): Double {
        val x0 = p.longitude; val y0 = p.latitude
        val x1 = a.longitude; val y1 = a.latitude
        val x2 = b.longitude; val y2 = b.latitude
        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return hypot(x0 - x1, y0 - y1)
        val t = ((x0 - x1) * dx + (y0 - y1) * dy) / (dx*dx + dy*dy)
        val xx = x1 + t * dx; val yy = y1 + t * dy
        return hypot(x0 - xx, y0 - yy)
    }

    private fun hypot(a: Double, b: Double) = kotlin.math.sqrt(a*a + b*b)
}
