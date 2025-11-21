package com.spotitfly.app.search

import com.spotitfly.app.data.model.Spot

sealed class SearchResult(
    open val title: String,
    open val subtitle: String?,
    open val lat: Double,
    open val lon: Double,
    open val distanceMeters: Double,
    val section: Int
) {
    data class SpotResult(
        val spot: Spot,
        override val title: String,
        override val subtitle: String?,
        override val lat: Double,
        override val lon: Double,
        override val distanceMeters: Double
    ) : SearchResult(title, subtitle, lat, lon, distanceMeters, 0)

    data class CoordinateResult(
        override val title: String,
        override val subtitle: String?,
        override val lat: Double,
        override val lon: Double,
        override val distanceMeters: Double
    ) : SearchResult(title, subtitle, lat, lon, distanceMeters, 1)

    data class AddressResult(
        override val title: String,
        override val subtitle: String?,
        override val lat: Double,
        override val lon: Double,
        override val distanceMeters: Double
    ) : SearchResult(title, subtitle, lat, lon, distanceMeters, 2)
}
