package com.spotitfly.app.ui.spots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotitfly.app.data.model.Spot
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.*
import com.spotitfly.app.ui.weather.WeatherCompactPill
import com.spotitfly.app.data.weather.WeatherRepository
import com.spotitfly.app.data.weather.WeatherDaily
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.ui.favorites.FavoritesViewModel
import androidx.compose.material.icons.filled.Favorite
import com.spotitfly.app.PreferredNavApp
import com.spotitfly.app.getPreferredNavApp
import android.content.ActivityNotFoundException
import android.content.Context




private val IOS_PURPLE = Color(0xFFAF52DE) // equivalente a systemPurple (iOS)

@Composable
fun SpotRow(
    spot: Spot,
    distanceMeters: Double?,
    onClick: (Spot) -> Unit,
    onViewMap: (Spot) -> Unit = {},
    onShare: (Spot) -> Unit = {}
) {

    // Rating local-first (1:1 iOS): cache → ratings → legacy
    val ctx = LocalContext.current
    var avgUi by remember(spot.id) { mutableStateOf(resolveAverageForRow(ctx, spot)) }

    // Favoritos (VM global, mismo para toda la app)
    val favoritesVM: FavoritesViewModel = viewModel()
    val favoriteIds by favoritesVM.favoriteIds.collectAsState()
    val isFavorite = favoriteIds.contains(spot.id)

    // Actualiza en caliente si cambia la cache del spot (p.ej. al votar en detalle)
    DisposableEffect(spot.id) {
        val prefs = ctx.getSharedPreferences("ratings_cache", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "mean.${spot.id}" || key == "count.${spot.id}") {
                avgUi = resolveAverageForRow(ctx, spot)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }


    val basePillHeight: Dp = 32.dp
    val pillHeight: Dp = (basePillHeight.value * (2f / 3f)).dp  // ≈ 21.3dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick(spot) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // -------- Cabecera: miniatura + título/desc + fila rating / mapa / corazón --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Miniatura 96dp, radio 12
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    val ctx = LocalContext.current
                    val local = spotLocalThumbFile(ctx, spot.id)
                    val hasLocal = local.exists()

                    if (hasLocal || !spot.imageUrl.isNullOrBlank()) {
                        val model = ImageRequest.Builder(ctx)
                            .data(if (hasLocal) local else (spot.imageUrl ?: ""))
                            .crossfade(true)
                            .build()

                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { success ->
                                if (!hasLocal) {
                                    try { saveDrawableToFile(success.result.drawable, local) } catch (_: Exception) {}
                                }
                            }
                        )
                    } else {
                        Text(
                            text = (spot.category?.name ?: "IMG").take(3),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    // TÍTULO
                    val body = MaterialTheme.typography.bodyMedium
                    Text(
                        text = spot.name.ifBlank { "Nombre del spot" },
                        style = body.copy(
                            fontSize = (body.fontSize.value + 2).sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Clip
                    )

                    // Descripción
                    if (spot.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            spot.description,
                            style = body,
                            color = Color(0xFF6E6E73),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Estrellas + media | chip "Ver Mapa" | corazón
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // IZQ: estrellas + media (local-first: cache → ratings → legacy)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(average = avgUi, size = 14)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                String.format(java.util.Locale.US, "%.1f", avgUi),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black
                            )
                        }


                        // CENTRO: chip "Ver Mapa"
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE6F0FF)) {
                            Text(
                                "Ver Mapa",
                                color = Color(0xFF1E88E5),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .clickable { onViewMap(spot) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }


                        // DCHA: corazón
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    favoritesVM.toggleFavoriteAsync(spot.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFavorite) {
                                // Relleno rojo + contorno rojo
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Favorito",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(22.dp)
                                )
                                Icon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = null, // para que TalkBack no lo lea dos veces
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                // Solo contorno gris
                                Icon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }


                    }
                }
            }

            // -------- Meteo + compartir + comentarios --------
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // IZQ: pill del tiempo
                WeatherExtendedPill(height = pillHeight, lat = spot.latitude, lon = spot.longitude)


                // CENTRO: compartir
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1A1E88E5),
                    modifier = Modifier
                        .size(pillHeight)
                        .clickable { onShare(spot) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.IosShare,
                            contentDescription = "Compartir",
                            tint = Color(0xFF1E88E5)
                        )
                    }
                }



                // DCHA: comentarios
                val chatIconSize = (pillHeight.value * 0.60f).dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(chatIconSize)
                    )
                    Spacer(Modifier.width(6.dp))
                    val safeComments = (spot.commentCount ?: 0).coerceAtLeast(0)
                    Text(
                        safeComments.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }

            // -------- Tag + distancia --------
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TagPill(text = prettyCategory(spot.category?.name), height = pillHeight)
                Spacer(Modifier.weight(1f))
                DistancePill(distanceMeters = distanceMeters, height = pillHeight)
            }

            // -------- Localidad + botón Ir --------
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFFF2F2F7)) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(14.dp))
                    }
                }
                if (!spot.locality.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        spot.locality!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Spacer(Modifier.weight(1f))
                val context = LocalContext.current
                GoButton(onClick = {
                    openDirectionsInGoogleMaps(
                        context = context,
                        lat = spot.latitude,
                        lon = spot.longitude
                    )
                })
            }
        }
    }
}


private fun shouldUseWaze(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    val raw = prefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
    return raw == "waze"
}


/* ------------------ Subcomponentes ------------------ */

private fun openDirectionsInGoogleMaps(
    context: Context,
    lat: Double,
    lon: Double
) {
    val pm = context.packageManager

    // Leemos directamente el string de prefs
    val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    val raw = prefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
    val useWaze = raw == "waze"

    // 1️⃣ Si el usuario ha elegido Waze, lo intentamos PRIMERO, forzando el paquete
    if (useWaze) {
        try {
            // Comprobar que Waze está instalado
            pm.getPackageInfo("com.waze", 0)

            val wazeUri = Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                setPackage("com.waze")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(wazeIntent)
            return
        } catch (_: Exception) {
            // Waze no instalado o algo ha fallado → seguimos al fallback
        }
    }

    // 2️⃣ Fallback: Google Maps (app)
    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val hasGMaps = try {
        pm.getPackageInfo("com.google.android.apps.maps", PackageManager.GET_ACTIVITIES)
        true
    } catch (_: Exception) {
        false
    }

    if (hasGMaps) {
        context.startActivity(mapIntent)
    } else {
        // 3️⃣ Último recurso: navegador web con Google Maps
        val webUri = Uri.parse("https://maps.google.com/?daddr=$lat,$lon")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}




@Composable
private fun RatingStars(average: Double, size: Int = 14) {
    val filled = average.coerceIn(0.0, 5.0).toInt()
    Row {
        repeat(5) { idx ->
            val tint = if (idx < filled) Color(0xFFFFC107) else Color(0xFFE0E0E0)
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size.dp)
            )
            if (idx < 4) Spacer(Modifier.width(2.dp))
        }
    }
}


@Composable
private fun WeatherExtendedPill(height: Dp, lat: Double, lon: Double) {
    // Carga perezosa con cache interna; anti-flicker: no pinta hasta tener datos (1:1 iOS)
    val repo = remember { WeatherRepository() }
    var day by remember(lat, lon) { mutableStateOf<WeatherDaily?>(null) }

    LaunchedEffect(lat, lon) {
        runCatching { repo.getDaily(lat, lon) }
            .onSuccess { list -> day = list.firstOrNull() }
        // onFailure: silencioso → no renderiza pill
    }

    day?.let { d ->
        WeatherCompactPill(day = d, height = height)
    }
}



@Composable
private fun TagPill(text: String, height: Dp) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE8F1FF),
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = (height.value * 0.6f).dp
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Color.Black)
        }
    }
}


@Composable
private fun DistancePill(distanceMeters: Double?, height: Dp) {
    val text = when {
        distanceMeters == null -> "—"
        distanceMeters >= 950 -> {
            val km = distanceMeters / 1000.0
            "${(km * 10).roundToInt() / 10.0} km"
        }
        else -> "${distanceMeters.roundToInt()} m"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF2F2F7),
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = (height.value * 0.6f).dp
            Icon(
                Icons.Rounded.DirectionsCar,
                contentDescription = null,
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = Color.Black)
        }
    }
}


@Composable
private fun GoButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IOS_PURPLE,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Ir",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


private fun prettyCategory(maybeRaw: String?): String {
    val raw = maybeRaw ?: return "Freestyle campo abierto"
    return raw
        .lowercase(Locale.getDefault())
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
}


private fun prettyDistance(meters: Double): String {
    return if (meters >= 950) {
        val km = meters / 1000.0
        "${(km * 10).roundToInt() / 10.0} km"
    } else {
        "${meters.roundToInt()} m"
    }
}

private fun spotLocalThumbFile(ctx: android.content.Context, spotId: String?): File {
    val safeId = (spotId ?: "unknown").replace(Regex("[^A-Za-z0-9_-]"), "_")
    val dir = File(ctx.filesDir, "spotImages")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${safeId}-thumb.jpg")
}


private fun saveDrawableToFile(drawable: Drawable, file: File) {
    val bmp = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> {
            val w = maxOf(1, drawable.intrinsicWidth)
            val h = maxOf(1, drawable.intrinsicHeight)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)
            b
        }
    }
    file.parentFile?.mkdirs()
    file.outputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    }
}

// -------- Rating local-first helpers (cache → ratings → legacy) --------

private fun cachePrefs(ctx: android.content.Context) =
    ctx.getSharedPreferences("ratings_cache", android.content.Context.MODE_PRIVATE)

private fun loadCachedMean(ctx: android.content.Context, spotId: String?): Double? {
    val id = spotId ?: return null
    val s = cachePrefs(ctx).getString("mean.$id", null) ?: return null
    return s.toDoubleOrNull()
}

private fun computeRatingsMapMean(spot: com.spotitfly.app.data.model.Spot): Double? {
    // Esperamos un Map<String, *>, pero casteamos de forma segura
    val map = spot.ratings as? Map<*, *> ?: return null
    var sum = 0.0
    var count = 0
    for (v in map.values) {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
        if (d != null) {
            sum += d
            count++
        }
    }
    return if (count > 0) sum / count else null
}


private fun resolveAverageForRow(
    ctx: android.content.Context,
    spot: com.spotitfly.app.data.model.Spot
): Double {
    // 1) Cache local (escrito desde el detalle al votar)
    loadCachedMean(ctx, spot.id)?.let { return it }

    // 2) ratingMean si existe (nuevo sistema)
    val meanField = (spot.ratingMean as? Number)?.toDouble()
    if (meanField != null) return meanField

    // 3) averageRating si existe (campo consolidado en doc)
    val avgField = (spot.averageRating as? Number)?.toDouble()
    if (avgField != null) return avgField

    // 4) Media de 'ratings' (mapa de votos por usuario)
    computeRatingsMapMean(spot)?.let { return it }

    // 5) Legacy 'rating'
    val legacy = (spot.rating as? Number)?.toDouble()
    return legacy ?: 0.0
}

private fun startActivitySafe(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        // Ignoramos errores si no hay actividad que pueda manejar el intent
    }
}



