package com.spotitfly.app.ui.spots

import android.annotation.SuppressLint
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

data class SpotWithDistance(
    val spot: Spot,
    val distanceMeters: Double?
)

class SpotsListViewModel(
    private val repo: SpotsRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<SpotWithDistance>>(emptyList())
    val items: StateFlow<List<SpotWithDistance>> = _items

    fun start(context: android.content.Context) {
        viewModelScope.launch {
            val loc = getLastKnownLocation(context)
            repo.listenAll { spots ->
                val enriched = spots
                    .map { s ->
                        SpotWithDistance(
                            s,
                            loc?.let { distanceMeters(it.latitude, it.longitude, s.latitude, s.longitude) }
                        )
                    }
                    .sortedWith(
                        compareBy<SpotWithDistance>(
                            { it.distanceMeters == null },
                            { it.distanceMeters ?: Double.POSITIVE_INFINITY }
                        )
                    )
                _items.value = enriched
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(context: android.content.Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        //return client.lastLocation.awaitNullable()
        return try { client.lastLocation.awaitNullable() } catch (_: SecurityException) { null }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitNullable(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, null)
            else cont.resume(null, null)
        }
    }
