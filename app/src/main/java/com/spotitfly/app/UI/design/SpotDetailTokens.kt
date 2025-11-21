package com.spotitfly.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens de diseño para SpotDetail (pixel-perfect con iOS, solo UI).
 * Ajusta aquí valores finos sin tocar la pantalla.
 */
object SpotDetailTokens {
    // Base
    val surface = Color(0xFFFFFFFF)
    val textPrimary = Color(0xFF000000)
    val textBody = Color(0xFF111111)
    val textMuted = Color(0xFF6B6B6B)
    val divider = Color(0x1F000000)

    // Hero
    val heroHeight = 220.dp
    val heroCorner = 12.dp
    val heroShadow = 4.dp
    val heroPlaceholder = Color(0xFFEAEAEA)

    // Botones flotantes sobre el hero
    val floatBg = Color(0xFFFFFFFF)
    val floatFg = Color(0xFF7A7A7A)
    val floatSize = 34.dp
    val floatShadow = 4.dp

    // Favorito (chip oscuro)
    val favScrimBg = Color(0x66000000)
    val favOn = Color(0xFFFF0000)
    val favOff = Color(0xFFFFFFFF)

    // Tipografía y espaciados
    val padPage = 16.dp
    val gapXS = 4.dp
    val gapS = 6.dp
    val gapM = 8.dp
    val gapL = 10.dp
    val gapXL = 12.dp
    val gapXXL = 16.dp

    // Chips de “propietario”
    val ownerBlue = Color(0xFF1976D2)
    val ownerBlueBg = Color(0x1F1976D2)
    val ownerRed = Color(0xFFD32F2F)
    val ownerRedBg = Color(0x1FD32F2F)
    val ownerCorner = 8.dp

    // Categoría
    val catBlue = Color(0xFF1976D2)
    val catBlueBg = Color(0x1F1976D2)
    val catCorner = 8.dp

    // Rating
    val starOn = Color(0xFFFFC107)
    val starOff = Color(0xFFBDBDBD)
    val starSize = 20.dp

    // Botón VOTAR
    val voteBg = Color(0x262196F3) // azul 15% aprox
    val voteCorner = 8.dp

    // Comentarios + Acceso
    val accessGreen = Color(0xFF4CAF50)
    val accessCorner = 8.dp

    // Best date
    val bestDateTint = Color(0xFF666666)
    val calendarIconSize = 16.dp   // 2/3 de 24dp
    val commentIconSize = 16.dp    // igual que calendario

    // Restricciones
    val restrRed = Color(0xFFD32F2F)
    val restrCorner = 10.dp
    val restrReloadBg = Color(0x262196F3)

    // Localidad
    val pinRed = Color(0xFFD32F2F)

    // Mini-mapa
    val miniMapCorner = 12.dp
    val miniMapShadow = 4.dp
    val miniMapBg = Color(0xFFEEEEEE)

    // Botón Ir — color iOS (systemPurple)
    val goPurple = Color(0xFFAF52DE)
    val goCorner = 10.dp

    // Reportar
    val danger = Color(0xFFD32F2F)
    val dangerBg = Color(0x1FD32F2F)
    val reportCorner = 10.dp

    // **NUEVO**: tamaño de fuente para descripción (+2pt vs body)
    val descFontSizeSp = 16.sp
}
