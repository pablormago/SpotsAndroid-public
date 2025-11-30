package com.spotitfly.app.ui.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spotitfly.app.data.context.ContextRepository
import com.spotitfly.app.data.context.PointContextResult
import com.spotitfly.app.ui.map.MapCameraStore

/**
 * PointContextSheet: ahora usa SIEMPRE el centro de la cruz (MapCameraStore) si está disponible.
 * Fallback: usa los parámetros (lat/lng) recibidos para no romper llamadas antiguas.
 *
 * Firma intacta (lat, lng, onClose) para compatibilidad.
 */
@Composable
fun PointContextSheet(
    lat: Double,
    lng: Double,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ContextRepository(context) }

    // Centro real: prioriza la cruz; si no existe, usa los parámetros recibidos
    val centerLat = MapCameraStore.lat ?: lat
    val centerLng = MapCameraStore.lng ?: lng

    var data by remember { mutableStateOf<PointContextResult?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(centerLat, centerLng) {
        loading = true
        data = repo.fetchPointContext(centerLat, centerLng)
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Información del punto", style = MaterialTheme.typography.titleLarge)

        val locality = data?.locality
        locality?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(Modifier.height(4.dp))

        if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val d = data
            ContextSectionsScaffold(
                restricciones    = d?.restricciones     ?: emptyList(),
                infraestructuras = d?.infraestructuras   ?: emptyList(),
                medioambiente    = d?.medioambiente      ?: emptyList(),
                notams           = d?.notams             ?: emptyList(),
                urbanas          = d?.urbanas            ?: emptyList()
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}
