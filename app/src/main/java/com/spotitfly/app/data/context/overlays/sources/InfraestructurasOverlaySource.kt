package com.spotitfly.app.data.context.overlays.sources

import com.google.android.gms.maps.model.LatLngBounds
import com.spotitfly.app.data.context.Http
import com.spotitfly.app.data.context.overlays.*
import org.json.JSONObject

internal object InfraestructurasOverlaySource {
    private const val ENDPOINT =
        "https://servais.enaire.es/insignia/rest/services/NSF_SRV/SRV_UAS_ZG_V1/FeatureServer/0/query"

    suspend fun fetch(bbox: LatLngBounds): List<AirspaceRenderable> {
        val params = mapOf(
            "where" to "1=1",
            "geometry" to envelope(bbox),
            "geometryType" to "esriGeometryEnvelope",
            "inSR" to "4326",
            "spatialRel" to "esriSpatialRelIntersects",
            "outFields" to "*",
            "returnGeometry" to "true",
            "outSR" to "4326",
            "f" to "geojson"
        )
        val root: JSONObject = Http.getJson(ENDPOINT, params)
        return GeoJsonOverlayParser.parseFeatures(root, AirspaceLayer.INFRAESTRUCTURAS)
    }

    private fun envelope(b: LatLngBounds): String =
        "${b.southwest.longitude},${b.southwest.latitude},${b.northeast.longitude},${b.northeast.latitude}"
}
