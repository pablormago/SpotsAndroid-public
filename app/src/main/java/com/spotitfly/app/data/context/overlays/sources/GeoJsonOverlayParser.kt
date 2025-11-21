package com.spotitfly.app.data.context.overlays.sources

import com.google.android.gms.maps.model.LatLng
import com.spotitfly.app.data.context.overlays.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object GeoJsonOverlayParser {

    private fun takeId(f: JSONObject, props: JSONObject?): String {
        // 1) geojson.id
        f.optString("id", null)?.takeIf { it.isNotBlank() }?.let { return it }
        // 2) OBJECTID / cualquier *OBJECTID* en properties
        if (props != null) {
            val it = props.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (k.contains("OBJECTID", ignoreCase = true)) {
                    val v = props.optString(k)
                    if (v.isNotBlank()) return v
                }
            }
        }
        // 3) hash de geometría como fallback
        return "geom:" + md5(f.optJSONObject("geometry")?.toString().orEmpty())
    }

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val d = md.digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun parseFeatures(
        root: JSONObject,
        layer: AirspaceLayer
    ): List<AirspaceRenderable> {
        val style = AirspaceStyle.of(layer)
        val features = root.optJSONArray("features") ?: JSONArray()
        val out = mutableListOf<AirspaceRenderable>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val g = f.optJSONObject("geometry") ?: continue
            val p = f.optJSONObject("properties")

            when (g.optString("type")) {
                "Polygon" -> {
                    val rings = parsePolygon(g.optJSONArray("coordinates"))
                    if (rings.isNotEmpty()) {
                        out += PolygonRenderable(
                            id = takeId(f, p),
                            rings = rings,
                            layer = layer,
                            strokeColor = style.strokeColor,
                            fillColor = style.fillColor,
                            zIndex = style.zIndex,
                            widthPx = style.baseWidthPx,
                            dashed = style.dashed
                        )
                    }
                }
                "MultiPolygon" -> {
                    val arr = g.optJSONArray("coordinates") ?: JSONArray()
                    val baseId = takeId(f, p)
                    for (k in 0 until arr.length()) {
                        val rings = parsePolygon(arr.optJSONArray(k))
                        if (rings.isNotEmpty()) {
                            out += PolygonRenderable(
                                id = "$baseId#$k",
                                rings = rings,
                                layer = layer,
                                strokeColor = style.strokeColor,
                                fillColor = style.fillColor,
                                zIndex = style.zIndex,
                                widthPx = style.baseWidthPx,
                                dashed = style.dashed
                            )
                        }
                    }
                }
                "LineString" -> {
                    val pts = parseLine(g.optJSONArray("coordinates"))
                    if (pts.size >= 2) {
                        out += PolylineRenderable(
                            id = takeId(f, p),
                            points = pts,
                            layer = layer,
                            strokeColor = style.strokeColor,
                            fillColor = style.fillColor,
                            zIndex = style.zIndex,
                            widthPx = style.baseWidthPx,
                            dashed = style.dashed
                        )
                    }
                }
                "MultiLineString" -> {
                    val arr = g.optJSONArray("coordinates") ?: JSONArray()
                    val baseId = takeId(f, p)
                    for (k in 0 until arr.length()) {
                        val pts = parseLine(arr.optJSONArray(k))
                        if (pts.size >= 2) {
                            out += PolylineRenderable(
                                id = "$baseId#$k",
                                points = pts,
                                layer = layer,
                                strokeColor = style.strokeColor,
                                fillColor = style.fillColor,
                                zIndex = style.zIndex,
                                widthPx = style.baseWidthPx,
                                dashed = style.dashed
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun parsePolygon(coords: JSONArray?): List<List<LatLng>> {
        if (coords == null) return emptyList()
        val rings = mutableListOf<List<LatLng>>()
        for (i in 0 until coords.length()) {
            val ring = parseLine(coords.optJSONArray(i))
            if (ring.size >= 3) rings += ring
        }
        return rings
    }

    private fun parseLine(coords: JSONArray?): List<LatLng> {
        if (coords == null) return emptyList()
        val pts = ArrayList<LatLng>(coords.length())
        for (i in 0 until coords.length()) {
            val xy = coords.optJSONArray(i) ?: continue
            if (xy.length() < 2) continue
            val lon = xy.optDouble(0)
            val lat = xy.optDouble(1)
            pts += LatLng(lat, lon)
        }
        return pts
    }
}
