package com.spotitfly.app.data.context.overlays

import com.google.android.gms.maps.model.LatLng
import android.graphics.Color

enum class AirspaceLayer { URBANO, MEDIOAMBIENTE, RESTRICCIONES, INFRAESTRUCTURAS }

/** Estilos 1:1 con iOS (SF System palette). Ajusta aquí si quieres otros HEX/alphas. */
object AirspaceStyle {

    data class Style(
        val strokeColor: Int,   // ARGB
        val fillColor: Int,     // ARGB (con alpha aplicado)
        val zIndex: Float,
        val dashed: Boolean,
        val baseWidthPx: Float
    )

    private fun argb(hex: String): Int {
        val clean = hex.removePrefix("#")
        return if (clean.length == 6) {
            val rgb = clean.toLong(16).toInt()
            0xFF000000.toInt() or rgb
        } else {
            clean.toLong(16).toInt()
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f,1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }



    /** Colores iOS (systemBlue, systemGreen, systemIndigo, systemOrange). */
    private val IOS_ORANGE = argb("#FF9500")  // systemOrange
    private val IOS_GREEN  = argb("#34C759")  // systemGreen
    private val IOS_BLUE   = argb("#007AFF")  // systemBlue
    private val IOS_PURPLE = argb("#AF52DE")  // systemPurple

    fun of(layer: AirspaceLayer): Style = when (layer) {
        // Orden iOS: URBANO (abajo) → MEDIOAMBIENTE → RESTRICCIONES → INFRAESTRUCTURAS
        AirspaceLayer.URBANO -> Style(
            strokeColor = IOS_PURPLE,
            fillColor   = withAlpha(IOS_PURPLE, 0.16f),
            zIndex = 1f,
            dashed = true,
            baseWidthPx = 3.0f
        )
        AirspaceLayer.MEDIOAMBIENTE -> Style(
            strokeColor = IOS_GREEN,
            fillColor   = withAlpha(IOS_GREEN, 0.16f),
            zIndex = 2f,
            dashed = true,
            baseWidthPx = 3.0f
        )
        // ⬇️ Intercambiado: RESTRICCIONES ahora NARANJA
        AirspaceLayer.RESTRICCIONES -> Style(
            strokeColor = IOS_ORANGE,
            fillColor   = withAlpha(IOS_ORANGE, 0.16f),
            zIndex = 3f,
            dashed = false,
            baseWidthPx = 3.0f
        )
        // ⬇️ Intercambiado: INFRAESTRUCTURAS ahora AZUL
        AirspaceLayer.INFRAESTRUCTURAS -> Style(
            strokeColor = IOS_BLUE,
            fillColor   = withAlpha(IOS_BLUE, 0.16f),
            zIndex = 4f,
            dashed = false,
            baseWidthPx = 3.0f
        )
    }
}

/** Renderables con ID estable para diff incremental. */
sealed class AirspaceRenderable {
    abstract val id: String
    abstract val layer: AirspaceLayer
    abstract val strokeColor: Int
    abstract val fillColor: Int
    abstract val zIndex: Float
    abstract val widthPx: Float
}

data class PolylineRenderable(
    override val id: String,
    val points: List<LatLng>,
    override val layer: AirspaceLayer,
    override val strokeColor: Int,
    override val fillColor: Int, // no aplica, pero lo mantenemos por firma homogénea
    override val zIndex: Float,
    override val widthPx: Float,
    val dashed: Boolean
) : AirspaceRenderable()

data class PolygonRenderable(
    override val id: String,
    /** ring[0] = outer, ring[1..n] = holes */
    val rings: List<List<LatLng>>,
    override val layer: AirspaceLayer,
    override val strokeColor: Int,
    override val fillColor: Int,
    override val zIndex: Float,
    override val widthPx: Float,
    val dashed: Boolean
) : AirspaceRenderable()
