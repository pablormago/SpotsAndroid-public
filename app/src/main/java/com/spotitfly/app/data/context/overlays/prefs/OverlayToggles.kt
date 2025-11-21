package com.spotitfly.app.data.context.overlays.prefs

data class OverlayToggles(
    val urbano: Boolean = false,
    val medioambiente: Boolean = true,
    val restricciones: Boolean = true,   // iOS: ON por defecto
    val infraestructuras: Boolean = true
)
