package com.spotitfly.app.search

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SearchEngine(private val context: Context) {

    private val repo = SpotsRepository()
    @Volatile private var spotsCache: List<Spot> = emptyList()

    init {
        // Calienta la caché de spots (no rompe si falla)
        runCatching {
            repo.listenAll { list ->
                spotsCache = list ?: emptyList()
                Log.d("Search/Local", "cache warmed: ${spotsCache.size}")
            }
        }
    }

    /**
     * Busca en: Spots locales -> Coordenadas -> Geocoder nativo (mejorado).
     * Orden: sección (Spots, Coords, Direcciones) y distancia al centro.
     */
    suspend fun search(
        query: String,
        centerLat: Double?,
        centerLon: Double?,
        viewport: LatLngBounds?,
        maxResults: Int = 30
    ): List<SearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val results = ArrayList<SearchResult>(maxResults)
        val cLat = centerLat ?: 0.0
        val cLon = centerLon ?: 0.0

        // ---------- 1) SPOTS LOCALES ----------
        try {
            val localSpots = withContext(Dispatchers.IO) {
                if (spotsCache.isNotEmpty()) spotsCache
                else runCatching { repo.getAllCache() }.getOrDefault(emptyList())
            }
            val tokens = normalize(q).split(Regex("\\s+")).filter { it.isNotBlank() }
            val filtered = localSpots.asSequence().filter { s ->
                val cat = s.category?.name.orEmpty()
                val hay = normalize("${s.name ?: ""} ${s.description ?: ""} $cat ${s.locality.orEmpty()}")
                tokens.all { hay.contains(it) }
            }.map { s ->
                val d = haversineMeters(cLat, cLon, s.latitude, s.longitude)
                val safeName = (s.name ?: "").ifBlank { s.category?.name?.replace('_', ' ') ?: "Spot" }
                val safeSubtitle = s.locality
                    ?: s.category?.name?.replace('_', ' ')
                        ?.lowercase(Locale.getDefault())
                        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                SearchResult.SpotResult(
                    spot = s,
                    title = safeName,
                    subtitle = safeSubtitle,
                    lat = s.latitude,
                    lon = s.longitude,
                    distanceMeters = d
                )
            }.sortedBy { it.distanceMeters }
                .take(maxResults / 2)
                .toList()

            Log.d("Search/Local", "q='$q' -> ${filtered.size} spots (cache=${localSpots.size})")
            results.addAll(filtered)
        } catch (e: Throwable) {
            Log.w("Search/Local", "failed", e)
        }

        // ---------- 2) COORDENADAS ----------
        parseCoords(q)?.let { (lat, lon) ->
            results.add(
                SearchResult.CoordinateResult(
                    title = "Coordenadas",
                    subtitle = String.format(Locale.getDefault(), "%.6f, %.6f", lat, lon),
                    lat = lat,
                    lon = lon,
                    distanceMeters = haversineMeters(cLat, cLon, lat, lon)
                )
            )
            Log.d("Search/Coord", "parsed -> $lat,$lon")
        }

        // ---------- 3) LUGARES: GEOCODER NATIVO (escalado + variantes + ranking) ----------
        val desiredLocale = spainBiasedLocale(centerLat, centerLon)
        val places = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) emptyList() else {
                val geo = Geocoder(context, desiredLocale)

                // Variantes de la consulta (mejora recall en algunos OEMs)
                val variants = buildQueryVariants(q)

                // Cajas escalonadas desde el viewport/centro
                val boxes = buildSearchBoxes(centerLat, centerLon, viewport)

                // Ejecuta por (variant × box) y acumula (tope ~40 crudos)
                val raw = ArrayList<android.location.Address>()
                for (variant in variants) {
                    for (box in boxes) {
                        val chunk = runCatching {
                            if (box != null) {
                                geo.getFromLocationName(
                                    variant, 20,
                                    box.southwest.latitude,
                                    box.southwest.longitude,
                                    box.northeast.latitude,
                                    box.northeast.longitude
                                )
                            } else {
                                geo.getFromLocationName(variant, 20)
                            }
                        }.getOrNull().orEmpty()
                        if (chunk.isNotEmpty()) raw += chunk
                        if (raw.size >= 40) break
                    }
                    if (raw.size >= 40) break
                }
                raw
            }
        }

        if (places.isNotEmpty()) {
            val tokens = normalize(q).split(Regex("\\s+")).filter { it.isNotBlank() }
            val ranked = places
                .asSequence()
                .mapNotNull { addr ->
                    val lat = addr.latitude
                    val lon = addr.longitude

                    val titleParts = listOfNotNull(addr.featureName, addr.thoroughfare, addr.subThoroughfare)
                    val title = titleParts.joinToString(" ").trim()

                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
                    val subtitleParts = listOfNotNull(addr.subThoroughfare, addr.thoroughfare, city)
                    val subtitle = subtitleParts.joinToString(", ").trim()

                    val score = geocoderMatchScore(addr, tokens)
                    val dist = haversineMeters(cLat, cLon, lat, lon)

                    Triple(
                        SearchResult.AddressResult(
                            title = if (title.isNotBlank()) title else (city ?: "Dirección"),
                            subtitle = subtitle.ifBlank { city },
                            lat = lat,
                            lon = lon,
                            distanceMeters = dist
                        ),
                        score,
                        dist
                    )
                }
                // dedup por coordenada (5 decimales ~ 1 m a 1.1 m)
                .distinctBy { t -> "${"%.5f".format(t.first.lat)},${"%.5f".format(t.first.lon)}" }
                .sortedWith(
                    compareByDescending<Triple<SearchResult.AddressResult, Int, Double>> { it.second } // score
                        .thenBy { it.third } // distancia
                )
                .map { it.first }
                .take(20)
                .toList()

            Log.d("Search/Geo", "q='$q' -> raw=${places.size}, kept=${ranked.size}, locale=$desiredLocale")
            results.addAll(ranked)
        } else {
            Log.d("Search/Geo", "q='$q' -> geo=0")
        }

        return results
            .sortedWith(compareBy<SearchResult> { it.section }.thenBy { it.distanceMeters })
            .take(maxResults)
    }

    // ---------- Helpers de viewport y distancia ----------

    fun viewportFromZoom(centerLat: Double?, centerLon: Double?, zoom: Float?): LatLngBounds? {
        if (centerLat == null || centerLon == null) return null
        val z = zoom ?: 14f
        val meters = when {
            z >= 16f -> 2000.0
            z >= 14f -> 6000.0
            z >= 12f -> 15000.0
            z >= 10f -> 40000.0
            else     -> 100000.0
        }
        return metersBox(centerLat, centerLon, meters)
    }

    private fun metersBox(lat: Double, lon: Double, meters: Double): LatLngBounds {
        val dLat = meters / 111_320.0
        val dLon = meters / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        val sw = LatLng(lat - dLat, lon - dLon)
        val ne = LatLng(lat + dLat, lon + dLon)
        return LatLngBounds(sw, ne)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // ---------- Normalización y tokens ----------

    private fun normalize(s: String): String {
        val tmp = Normalizer.normalize(s.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return tmp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun spainBiasedLocale(centerLat: Double?, centerLon: Double?): Locale {
        return if (centerLat != null && centerLon != null &&
            centerLat in 27.0..44.5 && centerLon in -19.0..5.0
        ) Locale("es", "ES") else Locale.getDefault()
    }

    // ---------- Parser de coordenadas ----------
    private fun parseCoords(q: String): Pair<Double, Double>? {
        val t = q.trim()
        // "lat lon" (espacio) – permite coma decimal
        val space = t.split(Regex("\\s+"))
        if (space.size == 2) {
            val lat = space[0].replace(',', '.').toDoubleOrNull()
            val lon = space[1].replace(',', '.').toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) return lat to lon
        }
        // "lat,lon" (coma separador)
        val comma = t.split(',')
        if (comma.size == 2) {
            val lat = comma[0].trim().replace(',', '.').toDoubleOrNull()
            val lon = comma[1].trim().replace(',', '.').toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) return lat to lon
        }
        return null
    }

    // ---------- Variantes y ranking del Geocoder ----------

    private fun buildQueryVariants(q: String): List<String> {
        val trimmed = denoiseStreetWords(q.trim())
        val expandedAbbrev = expandAbbreviations(trimmed)

        // Variantes por categorías POI (añade sinónimos y marcas comunes)
        val poiExpanded = expandPoiVariants(expandedAbbrev)

        val withAccents = (listOf(trimmed, expandedAbbrev) + poiExpanded).distinct()
        val noAccents = withAccents.map { stripAccents(it) }
        return (withAccents + noAccents).distinct().filter { it.isNotBlank() }
    }


    private fun expandAbbreviations(s: String): String {
        // comunes en ES: c/ cl avda av pº pza plza ctra crta urb
        return s
            .replace(Regex("\\bc/\\s*", RegexOption.IGNORE_CASE), "calle ")
            .replace(Regex("\\bcl\\.?\\s*", RegexOption.IGNORE_CASE), "calle ")
            .replace(Regex("\\bavda\\.?\\s*", RegexOption.IGNORE_CASE), "avenida ")
            .replace(Regex("\\bav\\.?\\s*", RegexOption.IGNORE_CASE), "avenida ")
            .replace(Regex("\\bpza\\.?\\s*", RegexOption.IGNORE_CASE), "plaza ")
            .replace(Regex("\\bplza\\.?\\s*", RegexOption.IGNORE_CASE), "plaza ")
            .replace(Regex("\\bpº\\s*", RegexOption.IGNORE_CASE), "paseo ")
            .replace(Regex("\\bpaseo\\s*", RegexOption.IGNORE_CASE), "paseo ")
            .replace(Regex("\\bctra\\.?\\s*", RegexOption.IGNORE_CASE), "carretera ")
            .replace(Regex("\\bcrta\\.?\\s*", RegexOption.IGNORE_CASE), "carretera ")
            .replace(Regex("\\burb\\.?\\s*", RegexOption.IGNORE_CASE), "urbanización ")
            .trim()
    }

    private fun denoiseStreetWords(s: String): String {
        return s.replace(Regex("[,;]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun stripAccents(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
    // --- POI: categorías y sinónimos (simplificados, sin acentos para matching robusto) ---
    private val poiGroups: Map<String, List<String>> = mapOf(
        "gasolinera" to listOf("gasolinera","repsol","cepsa","bp","shell","galp"),
        "farmacia" to listOf("farmacia","parafarmacia"),
        "restaurante" to listOf("restaurante","bar","cafe","cafeteria","pizzeria","burger","mcdonald","kfc","telepizza","domino"),
        "supermercado" to listOf("supermercado","mercadona","carrefour","dia","aldi","lidl","eroski"),
        "banco" to listOf("banco","cajero","atm","bbva","santander","caixa","sabadell","ing"),
        "hotel" to listOf("hotel","hostal","pension","parador"),
        "hospital" to listOf("hospital","ambulatorio","centro de salud","urgencias"),
        "parque" to listOf("parque","jardin","plaza"),
        "estacion" to listOf("estacion","metro","renfe","cercanias","tranvia","autobuses","bus")
    )

    private fun expandPoiVariants(s: String): List<String> {
        val qn = stripAccents(s).lowercase(Locale.getDefault())
        val hits = mutableSetOf<String>()

        // Si la query contiene una categoría o sinónimo, añadimos TODOS los sinónimos de esa categoría
        poiGroups.forEach { (cat, syns) ->
            val catN = stripAccents(cat)
            val synsN = syns.map { stripAccents(it) }
            val matchCat = qn.contains(catN)
            val matchSyn = synsN.any { qn.contains(it) }
            if (matchCat || matchSyn) hits += syns
        }

        // Si no hay match directo pero la query es corta, probamos match “empieza por” en categorías
        if (hits.isEmpty() && qn.length in 3..12) {
            poiGroups.forEach { (cat, syns) ->
                val catN = stripAccents(cat)
                if (catN.startsWith(qn)) hits += syns
            }
        }

        return hits.toList()
    }

    private fun poiBoost(addr: android.location.Address, tokens: List<String>): Int {
        // Boost si la featureName contiene sinónimos de la categoría buscada
        val fn = stripAccents(addr.featureName ?: "").lowercase(Locale.getDefault())
        val combined = (fn + " " + stripAccents(addr.thoroughfare ?: "") + " " +
                stripAccents(addr.locality ?: "")).lowercase(Locale.getDefault())

        // Tokens normalizados de la query
        val qTokens = tokens.map { stripAccents(it).lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }

        var boost = 0
        if (fn.isNotBlank()) boost += 1 // tener nombre de sitio ya es señal de POI

        // Si tokens incluyen una categoría/sinónimo y también aparece en el resultado → boost grande
        poiGroups.forEach { (_, syns) ->
            val synsN = syns.map { stripAccents(it).lowercase(Locale.getDefault()) }
            val tokenHits = qTokens.any { t -> synsN.any { s2 -> t.contains(s2) || s2.contains(t) } }
            val resultHits = synsN.any { s2 -> combined.contains(s2) }
            if (tokenHits && resultHits) boost += 6
        }
        return boost
    }

    private fun buildSearchBoxes(
        centerLat: Double?,
        centerLon: Double?,
        viewport: LatLngBounds?
    ): List<LatLngBounds?> {
        val boxes = mutableListOf<LatLngBounds?>()
        if (viewport != null) boxes += viewport
        if (centerLat != null && centerLon != null) {
            boxes += metersBox(centerLat, centerLon, 20_000.0)
            boxes += metersBox(centerLat, centerLon, 60_000.0)
            boxes += metersBox(centerLat, centerLon, 150_000.0)
        }
        boxes += null // sin límites
        return boxes
    }

    private val stopwords = setOf(
        "de","del","la","el","las","los","y","en","km","numero","n","no","s","sn",
        "calle","avenida","paseo","plaza","carretera","camino","travesia","rotonda",
        "carrer","ronda","via","bulevar","urbanizacion","barrio","poligono"
    )

    private fun geocoderMatchScore(addr: android.location.Address, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0

        fun norm(s: String?) = stripAccents(s ?: "").lowercase(Locale.getDefault())
        val fields = listOf(
            "feature" to norm(addr.featureName),
            "thoroughfare" to norm(addr.thoroughfare),
            "subThoroughfare" to norm(addr.subThoroughfare),
            "locality" to norm(addr.locality),
            "subAdmin" to norm(addr.subAdminArea),
            "admin" to norm(addr.adminArea),
            "country" to norm(addr.countryName)
        )

        var score = 0
        tokens.forEach { t0 ->
            val t = stripAccents(t0).lowercase(Locale.getDefault())
            if (t.isBlank()) return@forEach
            val isStop = t in stopwords || t.length <= 2
            fields.forEach { (key, value) ->
                if (value.isNotBlank() && value.contains(t)) {
                    val base = when (key) {
                        "feature" -> 4
                        "thoroughfare","subThoroughfare" -> 5
                        "locality" -> 6
                        "subAdmin" -> 3
                        "admin" -> 2
                        "country" -> 1
                        else -> 1
                    }
                    score += if (isStop) 1 else base
                }
            }
        }
        return score + poiBoost(addr, tokens)
    }
}
