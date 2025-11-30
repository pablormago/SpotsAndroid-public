package com.spotitfly.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Objetivos posibles de un deep link spots://...
sealed class SpotsDeepLinkTarget {
    data class Spot(
        val spotId: String,
        val openComments: Boolean = false
    ) : SpotsDeepLinkTarget()

    data class Chat(
        val chatId: String
    ) : SpotsDeepLinkTarget()

    data class Invite(
        val code: String
    ) : SpotsDeepLinkTarget()
}

/**
 * Estado global mínimo para comunicar los intents de Android
 * con la capa Compose (AppRoot, ChatsHomeScreen, etc.).
 *
 * Leer SpotsDeepLinkState.target dentro de un @Composable
 * dispara recomposición cuando cambie desde MainActivity.
 */
object SpotsDeepLinkState {
    var target by mutableStateOf<SpotsDeepLinkTarget?>(null)
}
