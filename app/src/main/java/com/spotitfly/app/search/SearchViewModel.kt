package com.spotitfly.app.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(context: Context) {

    private val engine = SearchEngine(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.Main)

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    private var job: Job? = null

    fun setQuery(newQuery: String, centerLat: Double?, centerLon: Double?, viewport: LatLngBounds?) {
        query = newQuery
        job?.cancel()
        job = scope.launch {
            delay(150)
            results = if (newQuery.isBlank()) emptyList()
            else engine.search(newQuery, centerLat, centerLon, viewport)
        }
    }

    fun viewportFromZoom(centerLat: Double?, centerLon: Double?, zoom: Float?) =
        engine.viewportFromZoom(centerLat, centerLon, zoom)
}
