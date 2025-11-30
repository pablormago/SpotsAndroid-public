package com.spotitfly.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Modelos de deeplinks de Spots (1:1 con iOS).
 *
 * spots://spot/<id>?n=Nombre&lat=...&lon=...
 * spots://chat/<chatId>
 * spots://invite/<code>
 */
sealed class SpotsDeepLink {
    data class Spot(
        val spotId: String,
        val name: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val fromChatId: String? = null
    ) : SpotsDeepLink()

    data class Chat(val chatId: String) : SpotsDeepLink()

    data class Invite(val code: String) : SpotsDeepLink()
}

/**
 * Parsea una Uri del tipo spots://... a nuestro modelo.
 */
fun parseSpotsDeepLink(uri: Uri?): SpotsDeepLink? {
    if (uri == null) return null

    val scheme = uri.scheme?.lowercase()
    if (scheme != "spots") return null

    val host = uri.host?.lowercase()
    val segments = uri.path
        ?.trim('/')
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()

    return when (host) {
        "spot" -> {
            val id = segments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            val name = uri.getQueryParameter("n")
            val lat = uri.getQueryParameter("lat")?.toDoubleOrNull()
            val lon = uri.getQueryParameter("lon")?.toDoubleOrNull()
            SpotsDeepLink.Spot(
                spotId = id,
                name = name,
                lat = lat,
                lon = lon
            )
        }

        "chat" -> {
            val id = segments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            SpotsDeepLink.Chat(id)
        }

        "invite" -> {
            val code = segments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            SpotsDeepLink.Invite(code.uppercase())
        }

        else -> null
    }
}

/**
 * Bus global de deeplinks (equivalente al NotificationCenter/openSpotsDeepLink de iOS).
 *
 * Usaremos este flujo dentro de AppRoot para navegar a Spot / Chat / Invite.
 */
object SpotsDeepLinkBus {
    private val _events = MutableSharedFlow<SpotsDeepLink>(
        replay = 1,                // el primer suscriptor recibe el último deeplink
        extraBufferCapacity = 1
    )

    val events: SharedFlow<SpotsDeepLink> = _events

    fun emit(deepLink: SpotsDeepLink) {
        _events.tryEmit(deepLink)
    }
}
