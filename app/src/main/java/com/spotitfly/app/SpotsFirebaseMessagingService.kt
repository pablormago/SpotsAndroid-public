package com.spotitfly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Locale
import android.graphics.BitmapFactory


class SpotsFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SpotsFCM", "Nuevo FCM token: $token")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.d("SpotsFCM", "No hay usuario autenticado, no se guarda token")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users")
            .document(uid)
            .collection("devices")
            .document(token)

        val language = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Exception) {
            "es-ES"
        }

        val data = hashMapOf(
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp(),
            "language" to language
        )

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("SpotsFCM", "✅ Guardado FCM token en devices: $token")
            }
            .addOnFailureListener { e ->
                Log.e("SpotsFCM", "⚠️ Error guardando FCM token", e)
            }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        Log.d(
            "SpotsFCM",
            "📩 Push recibida. notification=${message.notification} data=$data"
        )

        // 1) Badge-only de iOS: sin notification y sin data -> NO mostramos nada en Android
        if (message.notification == null && data.isEmpty()) {
            Log.d("SpotsFCM", "🔕 Push badge-only (sin notification ni data), ignorada en Android")
            return
        }

        // 2) Título y cuerpo:
        //    - Primero lo que manda el backend en notification{title, body}
        //    - Si alguna vez viene solo data["title"]/["body"], también lo usamos
        val title = message.notification?.title
            ?: data["title"]
            ?: "Spots"

        val body = message.notification?.body
            ?: data["body"]
            ?: "Tienes una notificación nueva."

        // 3) Deep link robusto:
        //    Priorizamos IDs explícitos (spotId / chatId / code) para evitar
        //    interpretar como invitación algo que en realidad es un spot.
        val rawLink = data["link"]
        val type = data["type"]?.lowercase()

        val chatId = data["chatId"] ?: data["id"]
        val spotId = data["spotId"] ?: data["id"]
        val inviteCode = data["code"] ?: data["inviteCode"]

        val deepLink = when {
            // 🟢 Notificaciones relacionadas con SPOTS (comentarios, etc.)
            // Si viene un spotId, lo tratamos como spot aunque el link sea raro.
            !spotId.isNullOrBlank() &&
                    (type == "spot" || type == "spot_comment" || type == "comment" || type.isNullOrBlank()) -> {
                val openComments =
                    data["comments"] == "1" ||
                            data["openComments"] == "1" ||
                            type == "spot_comment" ||
                            type == "comment"

                if (openComments) {
                    "spots://spot/$spotId?comments=1"
                } else {
                    "spots://spot/$spotId"
                }
            }

            // 🔵 Notificaciones de CHAT
            !chatId.isNullOrBlank() && type == "chat" -> {
                "spots://chat/$chatId"
            }

            // 🟣 Invitaciones (join con enlace)
            !inviteCode.isNullOrBlank() &&
                    (type == "invite" ||
                            type == "chat_invite" ||
                            type == "invite_chat" ||
                            type.isNullOrBlank()) -> {
                "spots://invite/${inviteCode.uppercase()}"
            }

            // ⚪️ Fallback: si solo viene un link genérico, lo usamos tal cual
            !rawLink.isNullOrBlank() -> {
                rawLink
            }

            else -> null
        }
        Log.d(
            "SpotsFCM",
            "▶️ showNotification deepLink=$deepLink rawLink=$rawLink type=${data["type"]} " +
                    "chatId=${data["chatId"] ?: data["id"]} " +
                    "spotId=${data["spotId"] ?: data["id"]} " +
                    "code=${data["code"] ?: data["inviteCode"]}"
        )
        showNotification(title, body, deepLink)
    }




    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?
    ) {
        val channelId = "spots_default"

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones Spots",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensajes y avisos de Spots"
            }
            manager.createNotificationChannel(channel)
        }

        // ✅ Intent único para todos los casos
        val intent = Intent(this, MainActivity::class.java).apply {
            // Estos flags hacen que:
            // - Si la app está cerrada: se cree la tarea normal.
            // - Si está en background/foreground: traiga la misma activity al frente.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            if (!deepLink.isNullOrBlank()) {
                // Guardamos el deep link tanto en data como en un extra
                // para que MainActivity pueda leerlo sí o sí.
                action = Intent.ACTION_VIEW
                data = Uri.parse(deepLink)
                putExtra("spots_deep_link", deepLink)
            }
        }

        Log.d(
            "SpotsFCM",
            "🔗 Notification intent action=${intent.action} data=${intent.data} deepLink=$deepLink"
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            (deepLink ?: "").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Icono grande a color dentro de la notificación
        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_notification_spotitfly_logo
        )

        val notification = NotificationCompat.Builder(this, channelId)
            // Icono pequeño monocromo (para barra de estado / glifo)
            .setSmallIcon(R.drawable.ic_notification_spotitfly)
            // Icono grande a color dentro del cuerpo de la noti
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

}

