package com.spotitfly.app.ui.map

object MapCameraStore {
    @Volatile var lat: Double? = null
    @Volatile var lng: Double? = null
    @Volatile var zoom: Float? = null
    @Volatile var initialCentered: Boolean = false

    fun hasSaved(): Boolean = lat != null && lng != null && zoom != null
    fun save(lat: Double, lng: Double, zoom: Float) {
        this.lat = lat
        this.lng = lng
        this.zoom = zoom
    }
}
