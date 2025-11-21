package com.spotitfly.app.ui.map

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.spotitfly.app.R
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.text.TextUtils


class SpotsMapLayer(
    private val context: Context,
    private val map: GoogleMap
) {
    private val repo = SpotsRepository()
    private val markers: MutableMap<String, Marker> = ConcurrentHashMap()
    private var lastQueryTime: Long = 0L
    private var lastBounds: LatLngBounds? = null

    private var hasAllSpots: Boolean = false
    private var lastScale: Float? = null

    // --- LOD: cambio de marcador según nivel de zoom ---
    // A partir de este “alto” en metros, usamos marcador simple (sin texto ni estrellas)
    private val SIMPLE_MARKER_METERS_THRESHOLD = 250_000.0

    // Icono simple compartido para zoom lejanos (drone_pin “pelado”)
    private var simpleMarkerIcon: BitmapDescriptor? = null

    // Ejecutores para generar iconos en background y aplicarlos en el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconExecutor = Executors.newFixedThreadPool(4)
    private val inflightIconJobs: MutableMap<String, Boolean> = ConcurrentHashMap()

    // Versión del layout de icono (se añade a la clave de caché)
    private var iconVersion: Int = 1


    /** --- Config 1:1 con iOS --- */
    private val baseWidth = 240        // corresponde a baseSize.width en iOS
    private val baseHeight = 270       // corresponde a baseSize.height en iOS
    private val baseOffsetY = -100     // centerOffset Y en iOS (ajustamos con anchor abajo)
    private val iconSize = 126         // tamaño del "dronePin" en iOS
    private val pinColor = Color.rgb(64, 225, 210)  // ~ (0.25, 0.88, 0.82)
    private val infoBlue = Color.rgb(33, 150, 243)  // azul Material similar a iOS
    private val titleFontSize = 20f
    private val starsFontSize = 16f
    private val ratingFontSize = 18f
    // Desplazamiento vertical constante ~5 mm (≈ 31.5 dp) — independiente del zoom
    private val DROP_DP = 0f


    // Escala extra solo para estrellas (1.5x)
    private val STARS_SCALE = 1.5f

    // Gaps (dp relativos al 'scale') para igualar iOS
    private val TITLE_TOP = 4f            // margen superior del título
    private val GAP_TITLE_TO_STARS = 3f   // título → estrellas
    private val GAP_STARS_TO_PIN = -12f     // estrellas → pin
    private val GAP_PIN_TO_INFO = -18f      // pin → (i)
    private val RATING_GAP = 4f           // estrellas → número (4.7)


    // Cache de bitmaps por (id, scale, title/rating hash) para no recrear en exceso
    private val iconCache = LruCache<String, RenderedPin>(64)

    private data class RenderedPin(
        val bitmap: Bitmap,
        val anchorY: Float   // 0..1 relativo a la altura del bitmap
    )


    /** Lógica iOS: escala según metros “verticales” de la región */
    // Multiplicador base para abrir 3× más grande en el zoom inicial
    private val baseScaleMultiplier = 3.0f

    /** Lógica iOS: escala según metros “verticales” de la región, con multiplicador 3× */
    private fun pinScaleForBounds(bounds: LatLngBounds): Float {
        val meters = kotlin.math.abs(bounds.northeast.latitude - bounds.southwest.latitude) * 111_000.0
        val base = when {
            meters >= 400_000 -> 0.20f
            meters >= 250_000 -> 0.35f
            meters >= 100_000 -> 0.50f
            meters >=  50_000 -> 0.70f
            meters >=  10_000 -> 0.85f
            meters >=   1_500 -> 1.00f
            else              -> 1.15f
        }
        // Aplicamos 3× y acotamos por seguridad para evitar bitmaps descomunales
        return (base * baseScaleMultiplier).coerceIn(0.5f, 3.2f)
    }

    // --- Helpers LOD ---

    private fun metersForBounds(bounds: LatLngBounds): Double {
        return kotlin.math.abs(bounds.northeast.latitude - bounds.southwest.latitude) * 111_000.0
    }

    /** Devuelve true si estamos lo bastante lejos como para usar marcador simple */
    private fun shouldUseSimpleMarker(bounds: LatLngBounds): Boolean {
        val meters = metersForBounds(bounds)
        return meters >= SIMPLE_MARKER_METERS_THRESHOLD
    }

    /** Icono simple compartido (drone_pin) para zoom lejanos */
    private fun getSimpleMarkerIcon(): BitmapDescriptor {
        simpleMarkerIcon?.let { return it }
        val descriptor = BitmapDescriptorFactory.fromResource(R.drawable.drone_pin)
        simpleMarkerIcon = descriptor
        return descriptor
    }


    /** Refresco por viewport (consulta y diff) */
    fun refresh(bounds: LatLngBounds) {
        // Si ya cargamos TODO (paridad iOS), solo reescalamos
        if (hasAllSpots) {
            rescale(bounds)
            lastBounds = bounds
            return
        }
        // Si aún no cargamos (primera vez), cae al modo viewport (fallback)
        val now = System.currentTimeMillis()
        if (now - lastQueryTime < 350 && bounds == lastBounds) return
        lastQueryTime = now
        lastBounds = bounds
        repo.fetchInViewport(
            bounds = bounds,
            limit = 400,
            onResult = { spots -> applySpots(spots, bounds) },
            onError = { /* opcional: log */ }
        )
    }


    /** Llamar en onCameraIdle para reescalar sin relanzar consulta */
    /** Llamar en onCameraIdle para reescalar sin relanzar consulta */
    /** Llamar en onCameraIdle para reescalar sin relanzar consulta */
    fun rescale(bounds: LatLngBounds) {
        val scale = pinScaleForBounds(bounds)

        // Si la escala efectiva no ha cambiado lo suficiente, no hacemos nada
        val prev = lastScale
        if (prev != null && kotlin.math.abs(prev - scale) < 0.07f) {
            return
        }
        lastScale = scale

        // Reescalamos TODOS los markers para que no haya ninguno desincronizado
        for ((_, m) in markers) {
            val spot = m.tag as? Spot ?: continue
            updateMarkerIconAsync(spot, m, scale)
        }
    }






    /** Cargar TODOS los spots una sola vez (paridad iOS) */
    fun loadAll(onDone: (() -> Unit)? = null) {
        // Si ya tenemos marcadores y la colección cargada, no hacemos nada
        if (hasAllSpots && markers.isNotEmpty()) { onDone?.invoke(); return }

        // 1) Pintar inmediatamente desde cache si existe (cero espera al volver del detalle)
        if (repo.hasAllCache()) {
            applySpots(repo.getAllCache(), null)
            hasAllSpots = true
            onDone?.invoke()
        }

        // 2) Mantenerse sincronizado con un único listener (SpotsRepository lo memoiza)
        repo.listenAll { spots ->
            applySpots(spots, null) // bounds null → usa visibleRegion (o escala 1f si aún no hay)
            hasAllSpots = true
            onDone?.invoke()
        }
    }

    fun renderCachedOnce() {
        if (repo.hasAllCache()) {
            applySpots(repo.getAllCache(), null)
            hasAllSpots = true
        }
    }

    private fun applySpots(spots: List<Spot>, bounds: LatLngBounds?) {
        val wanted = HashSet<String>(spots.size)
        val currentBounds = bounds ?: map.projection?.visibleRegion?.latLngBounds
        val scale = currentBounds?.let { pinScaleForBounds(it) } ?: 1.0f
        lastScale = scale

        // add/update
        for (spot in spots) {
            wanted.add(spot.id)
            val existing = markers[spot.id]
            if (existing == null) {
                val pin = makePinBitmap(spot, scale)
                val mk = map.addMarker(
                    MarkerOptions()
                        .position(spot.coord)
                        .title(spot.name)
                        .icon(BitmapDescriptorFactory.fromBitmap(pin.bitmap))
                        .anchor(0.5f, pin.anchorY)
                        .zIndex(1f)
                )

                if (mk != null) {
                    mk.tag = spot
                    markers[spot.id] = mk
                }
            } else {
                if (existing.position != spot.coord) {
                    existing.position = spot.coord
                }
                existing.title = spot.name

                // Antes: makePinBitmap + setIcon/setAnchor en el hilo principal
                // Ahora: lo mandamos a background
                updateMarkerIconAsync(spot, existing, scale)
            }
        }

        // remove
        val it = markers.entries.iterator()
        while (it.hasNext()) {
            val (id, mk) = it.next()
            if (!wanted.contains(id)) {
                mk.remove()
                it.remove()
            }
        }
    }



    /** --- Render bitmap 1:1 con iOS (título → rating → pin turquesa → botón info) --- */
    private fun makePinBitmap(spot: Spot, scale: Float): RenderedPin {
        val title = capTitle(spot.name)
        val ratingMean = spot.ratingMean
        val ratingCount = spot.ratings?.size ?: spot.rating.takeIf { it > 0 } ?: 0

        val density = context.resources.displayMetrics.density
        val dropPx = DROP_DP * density

        val key = "${spot.id}:${title}:${ratingMean}:${ratingCount}:$scale:anchorV2:drop$DROP_DP:v$iconVersion"
        iconCache.get(key)?.let { return it }

        val w = (baseWidth * scale).roundToInt().coerceAtLeast(1)
        // Altura base SIN márgenes “fantasma” al final: la ajustaremos para que el (i) quede pegado al borde
        var h = (baseHeight * scale + dropPx).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ===== Layout vertical =====
        var currentTop = dropPx + TITLE_TOP * scale

        // 1) Título en cápsula (si hay) — inicio visible, elipsis al final si no cabe
        if (title.isNotEmpty()) {
            val padH = 12f * scale
            val padV = 5f * scale

            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = titleFontSize * scale
            paint.isAntiAlias = true
            paint.isSubpixelText = true

            // Limitar el ancho de la cápsula al bitmap para que no se “corte” en bordes
            val sideMargin = 12f * scale
            val maxCapW = (w - 2f * sideMargin).coerceAtLeast(48f * scale)
            val maxTextW = (maxCapW - 2f * padH).coerceAtLeast(0f)

            // Elide al FINAL → el principio del título siempre se ve
            val displayText = if (paint.measureText(title) > maxTextW) {
                android.text.TextUtils.ellipsize(
                    title,
                    android.text.TextPaint(paint),
                    maxTextW,
                    android.text.TextUtils.TruncateAt.END
                ).toString()
            } else {
                title
            }

            val textW = paint.measureText(displayText)
            val capH = (paint.fontMetrics.bottom - paint.fontMetrics.top) + 2f * padV
            val capW = (textW + 2f * padH).coerceAtMost(maxCapW)

            val cx = w / 2f
            val rect = RectF(
                cx - capW / 2f,
                currentTop,
                cx + capW / 2f,
                currentTop + capH
            )
            val r = capH / 2f

            // Fondo cápsula
            paint.color = Color.WHITE
            c.drawRoundRect(rect, r, r, paint)

            // Texto alineado a la IZQUIERDA dentro de la cápsula
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            val textY = rect.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            c.drawText(displayText, rect.left + padH, textY, paint)

            currentTop = rect.bottom + GAP_TITLE_TO_STARS * scale
        }


        // 2) Estrellas + media (grupo centrado)
        val starGap = 4f * scale
        val starW = starGlyphWidth(scale)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = starsFontSize * STARS_SCALE * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val starFM = starPaint.fontMetrics
        val starsBaseline = currentTop - starFM.top
        val totalStarsWidth = (5 * starW) + (4 * starGap)

        val ratingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = ratingFontSize * scale
            color = Color.BLACK
        }
        val meanText = if (ratingMean != null) String.format("%.1f", ratingMean) else "—"
        val ratingTextW = ratingPaint.measureText(meanText)
        val groupW = totalStarsWidth + (RATING_GAP * scale) + ratingTextW
        val groupLeft = (w - groupW) / 2f

        val afterStarsX = drawStarsRow(c, groupLeft, starsBaseline, ratingMean ?: 0.0, scale, starW, starGap)
        val ratingX = afterStarsX + (RATING_GAP * scale)
        c.drawText(meanText, ratingX, starsBaseline, ratingPaint)

        val starsBottom = starsBaseline + starFM.descent
        currentTop = starsBottom + GAP_STARS_TO_PIN * scale

        // 3) Drone pin turquesa
        val id = context.resources.getIdentifier("drone_pin", "drawable", context.packageName)
        var pinBottom = currentTop
        if (id != 0) {
            val raw = BitmapFactory.decodeResource(context.resources, id)
            val pinW = (iconSize * scale).roundToInt().coerceAtLeast(1)
            val pinH = (iconSize * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, pinW, pinH, true)
            val pTint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(pinColor, PorterDuff.Mode.SRC_IN)
            }
            val left = (w - pinW) / 2f
            val top = currentTop
            c.drawBitmap(scaled, left, top, pTint)
            pinBottom = top + pinH
        }

        // 4) Botón (i) – lo colocamos y tomamos su borde inferior como punto de ANCLAJE
        val rInfo = 18f * scale
        val cyInfo = pinBottom + GAP_PIN_TO_INFO * scale + rInfo
        val cxInfo = w / 2f

        paint.color = Color.WHITE
        c.drawCircle(cxInfo, cyInfo, rInfo, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(32, 0, 0, 0)
        paint.strokeWidth = 2.5f * scale
        c.drawCircle(cxInfo, cyInfo, rInfo, paint)
        paint.style = Paint.Style.FILL
        paint.color = infoBlue
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 16f * scale
        val iText = "i"
        val iY = cyInfo - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
        c.drawText(iText, cxInfo - paint.measureText(iText) / 2f, iY, paint)

        // AnchorY relativo sin recortes: borde inferior del (i)
        val bottomY = cyInfo + rInfo
        val anchorY = (bottomY / h.toFloat()).coerceIn(0f, 1f)

        val rendered = RenderedPin(bmp, anchorY)
        iconCache.put(key, rendered)
        return rendered

    }

    // Genera/actualiza el icono del marcador en un hilo de fondo
    private fun updateMarkerIconAsync(spot: Spot, marker: Marker, scale: Float) {
        val jobKey = "${spot.id}:$scale"
        if (inflightIconJobs.putIfAbsent(jobKey, true) != null) {
            // Ya hay un trabajo en curso para este (spot, escala)
            return
        }

        iconExecutor.execute {
            try {
                val pin = makePinBitmap(spot, scale)
                mainHandler.post {
                    // Comprobamos que el marker siga representando a este spot
                    val tagSpot = marker.tag as? Spot
                    if (tagSpot?.id == spot.id) {
                        marker.setIcon(BitmapDescriptorFactory.fromBitmap(pin.bitmap))
                        marker.setAnchor(0.5f, pin.anchorY)
                    }
                }
            } finally {
                inflightIconJobs.remove(jobKey)
            }
        }
    }


    private fun capTitle(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return ""
        // igual que iOS: 42 chars máx + “…”
        return if (trimmed.length > 42) trimmed.substring(0, 42) + "…" else trimmed
    }

    // Dibuja SIEMPRE 5 estrellas como "slots" y devuelve la x al final del quinto slot
    private fun drawStarsRow(
        c: Canvas,
        xLeft: Float,
        baseline: Float,
        value: Double,
        scale: Float,
        starW: Float,
        starGap: Float
    ): Float {
        val clamped = max(0.0, min(5.0, value))
        val full = clamped.toInt()
        val half = (clamped - full) >= 0.5
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = starsFontSize * STARS_SCALE * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        var x = xLeft
        repeat(5) { idx ->
            when {
                idx < full -> {
                    c.drawText("★", x, baseline, paint)
                }
                idx == full && half -> {
                    // media estrella: pintamos estrella llena recortada a la mitad
                    val save = c.save()
                    val clipRect = RectF(x, baseline - paint.textSize, x + (starW / 2f), baseline)
                    c.clipRect(clipRect)
                    c.drawText("★", x, baseline, paint)
                    c.restoreToCount(save)
                    // contorno suave para el resto (opcional)
                    paint.alpha = 128
                    c.drawText("☆", x, baseline, paint)
                    paint.alpha = 255
                }
                else -> {
                    paint.alpha = 128
                    c.drawText("☆", x, baseline, paint)
                    paint.alpha = 255
                }
            }
            x += (starW + starGap)
        }
        return x
    }

    private fun starGlyphWidth(scale: Float): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = starsFontSize * STARS_SCALE * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        // usar el mayor de los dos para evitar solapes
        return max(p.measureText("★"), p.measureText("☆"))
    }


    /** Cache LRU simple para bitmaps */
    private class LruCache<K, V>(private val capacity: Int) {
        private val map = LinkedHashMap<K, V>(capacity, 0.75f, true)
        fun get(k: K): V? = synchronized(map) { map[k] }
        fun put(k: K, v: V) = synchronized(map) {
            map[k] = v
            if (map.size > capacity) {
                val it = map.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    fun bustIconLayout() {
        iconVersion += 1
        lastScale = null

        val b = lastBounds ?: map.projection?.visibleRegion?.latLngBounds
        if (b != null) {
            val scale = pinScaleForBounds(b)
            lastScale = scale
            for ((_, m) in markers) {
                val s = m.tag as? Spot ?: continue
                updateMarkerIconAsync(s, m, scale)
            }
        }
    }




}
