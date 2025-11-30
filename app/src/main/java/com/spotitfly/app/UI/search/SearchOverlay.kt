package com.spotitfly.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotitfly.app.search.SearchResult
import com.spotitfly.app.search.SearchViewModel
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController


@Composable
fun SearchOverlay(
    onClose: () -> Unit,
    onSelect: (lat: Double, lon: Double, applyOffset: Boolean) -> Unit,
    centerLat: Double?,
    centerLon: Double?,
    zoom: Float?
) {
    val ctx = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val vm = remember(ctx) { SearchViewModel(ctx) }
    val viewport = remember(centerLat, centerLon, zoom) {
        vm.viewportFromZoom(centerLat, centerLon, zoom)
    }

// Intentamos usar la última ubicación del usuario como referencia de distancia
    var userRef by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(ctx) {
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            try {
                val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val providers = listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER
                )
                var best: android.location.Location? = null
                for (p in providers) {
                    val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                    if (loc != null && (best == null || loc.time > best!!.time)) best = loc
                }
                userRef = best?.let { it.latitude to it.longitude }
            } catch (se: SecurityException) {
                // Permiso denegado en runtime: seguimos con fallback (centro del mapa)
                userRef = null
            }
        } else {
            // Sin permisos: usamos el fallback (centro del mapa)
            userRef = null
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }



    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = {
                val refLat = userRef?.first ?: centerLat
                val refLon = userRef?.second ?: centerLon
                vm.setQuery(it, refLat, refLon, viewport)
            },
            placeholder = { Text("Buscar spots, lugares, coordenadas...") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(Modifier.height(10.dp))

        if (vm.results.isEmpty()) {
            Text("Escribe para buscar…", color = Color.Gray, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(vm.results) { item ->
                    SearchRow(item) {
                        val applyOffset = item is SearchResult.SpotResult
                        onSelect(item.lat, item.lon, applyOffset)
                        onClose()
                    }
                    Divider()
                }

            }
        }
    }
}

@Composable
private fun SearchRow(item: SearchResult, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeColor = when (item) {
            is SearchResult.SpotResult -> Color(0xFF1E88E5)
            is SearchResult.CoordinateResult -> Color(0xFF6D4C41)
            is SearchResult.AddressResult -> Color(0xFF00897B)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeColor.copy(alpha = 0.12f),
            tonalElevation = 0.dp,
            modifier = Modifier.size(44.dp, 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = when (item) {
                        is SearchResult.SpotResult -> "SPOT"
                        is SearchResult.CoordinateResult -> "GPS"
                        is SearchResult.AddressResult -> "DIR"
                    },
                    color = badgeColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        }
        Text(
            text = formatMeters(item.distanceMeters),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.6f)
        )
    }
}

private fun formatMeters(m: Double): String {
    if (m.isNaN() || m.isInfinite()) return ""
    return if (m >= 1000) String.format(Locale.getDefault(), "%.1f km", m / 1000.0)
    else String.format(Locale.getDefault(), "%.0f m", m)
}
