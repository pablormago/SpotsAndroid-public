package com.spotitfly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [NOTIF-LAG-01] Procesamos el deep link inicial (si lo hay)
        handleDeepLinkIntent(intent)

        // Habilitamos Edge-to-Edge para que funcionen los insets (teclado, etc.)
        enableEdgeToEdge()

        // [NOTIF-LAG-02] Montamos la UI UNA sola vez
        setRootContent()
    }

    // Cuando la Activity ya está creada y recibimos un nuevo intent (deep link / notificación)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // [NOTIF-LAG-03] Solo procesamos el nuevo deep link.
        // NO volvemos a llamar a setRootContent() para evitar recrear
        // toda la jerarquía de Compose (mapa, overlays, etc.)
        handleDeepLinkIntent(intent)
    }

    private fun setRootContent() {
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        Log.d(
            "SpotsDeepLink",
            "MainActivity.handleDeepLinkIntent intent=$intent action=${intent?.action} data=${intent?.data}"
        )

        if (intent == null) return

        // 1) Caso normal: tenemos un URI (spots://...) o un extra explícito
        val extraDeepLink = intent.getStringExtra("spots_deep_link")

        val uriFromIntent: Uri? = when {
            intent.data != null -> intent.data
            !extraDeepLink.isNullOrBlank() -> Uri.parse(extraDeepLink)
            else -> null
        }

        val uri: Uri = uriFromIntent ?: run {
            // 2) Fallback: notificaciones automáticas de FCM en background.
            //    No traen data, pero traen los "data" originales como extras.
            val extras = intent.extras
            if (extras == null) {
                Log.d(
                    "SpotsDeepLink",
                    "MainActivity.handleDeepLinkIntent: no data, no spots_deep_link extra y sin extras, ignorando"
                )
                return
            }

            Log.d("SpotsDeepLink", "MainActivity.handleDeepLinkIntent: intentando reconstruir deeplink desde extras=$extras")

            val type = extras.getString("type")?.lowercase()
            val chatId = extras.getString("chatId") ?: extras.getString("id")
            val spotId = extras.getString("spotId") ?: extras.getString("id")
            val inviteCode = extras.getString("code") ?: extras.getString("inviteCode")
            val rawLink = extras.getString("link")

            val openComments =
                extras.getString("comments") == "1" ||
                        extras.getString("openComments") == "1" ||
                        type == "spot_comment" ||
                        type == "comment"

            val deepLinkString = when {
                // 🟢 SPOT / COMENTARIOS
                !spotId.isNullOrBlank() &&
                        (type == "spot" || type == "spot_comment" || type == "comment" || type.isNullOrBlank()) -> {
                    if (openComments) {
                        "spots://spot/$spotId?comments=1"
                    } else {
                        "spots://spot/$spotId"
                    }
                }

                // 🔵 CHAT
                !chatId.isNullOrBlank() && type == "chat" -> {
                    "spots://chat/$chatId"
                }

                // 🟣 INVITACIÓN
                !inviteCode.isNullOrBlank() &&
                        (type == "invite" ||
                                type == "chat_invite" ||
                                type == "invite_chat" ||
                                type.isNullOrBlank()) -> {
                    "spots://invite/${inviteCode.uppercase()}"
                }

                // ⚪️ Fallback link genérico
                !rawLink.isNullOrBlank() -> rawLink

                else -> null
            }

            if (deepLinkString.isNullOrBlank()) {
                Log.d(
                    "SpotsDeepLink",
                    "MainActivity.handleDeepLinkIntent: extras presentes pero no se pudo reconstruir deeplink, ignorando"
                )
                return
            }

            Uri.parse(deepLinkString)
        }

        // Usamos el parser común de deeplinks
        val deepLink = parseSpotsDeepLink(uri)

        if (deepLink == null) {
            Log.d("SpotsDeepLink", "MainActivity.handleDeepLinkIntent: parse devolvió null, ignorando")
            return
        }

        Log.d("SpotsDeepLink", "MainActivity.handleDeepLinkIntent: deepLink=$deepLink, emitiendo en bus")
        SpotsDeepLinkBus.emit(deepLink)
    }

}
