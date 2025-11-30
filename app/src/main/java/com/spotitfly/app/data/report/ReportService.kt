package com.spotitfly.app.data.report

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Equivalente a ReportService.swift en iOS.
 *
 * Esquema en Firestore:
 *   collection("reports") con docs que tienen:
 *   - type: "user" | "spot" | "comment"
 *   - reporterId: uid del usuario que reporta
 *   - reason: motivo (texto)
 *   - createdAt: FieldValue.serverTimestamp()
 *
 *   Según type:
 *   - "user":   targetId = uid del usuario reportado
 *   - "spot":   targetId = spotId, y opcionalmente spotId duplicado
 *   - "comment": spotId + commentId obligatorios (sin targetId)
 */
object ReportService {

    private const val TAG = "ReportService"

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    enum class ReportType(val raw: String) {
        COMMENT("comment"),
        USER("user"),
        SPOT("spot")
    }

    /**
     * Reporte genérico.
     * IMPORTANTE: Sólo se admiten "user" | "spot" | "comment" por las reglas.
     */
    suspend fun report(
        type: ReportType,
        targetId: String,
        spotId: String? = null,
        commentId: String? = null,
        reason: String
    ): Boolean {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "❌ ReportService: usuario no autenticado")
            return false
        }

        val data = mutableMapOf<String, Any>(
            "type" to type.raw,
            "reporterId" to uid,
            "reason" to reason,
            "createdAt" to FieldValue.serverTimestamp()
        )

        when (type) {
            ReportType.USER -> {
                // rules: targetId obligatorio (uid del reportado)
                data["targetId"] = targetId
            }

            ReportType.SPOT -> {
                // rules: targetId obligatorio (spotId)
                data["targetId"] = targetId
                spotId?.let { data["spotId"] = it }
            }

            ReportType.COMMENT -> {
                // rules: spotId y commentId obligatorios; targetId NO es necesario (lo evitamos)
                val sId = spotId
                val cId = commentId
                if (sId.isNullOrEmpty() || cId.isNullOrEmpty()) {
                    Log.e(TAG, "❌ ReportService: faltan spotId/commentId para reportar comentario")
                    return false
                }
                data["spotId"] = sId
                data["commentId"] = cId
            }
        }

        return try {
            db.collection("reports")
                .add(data)
                .await()
            Log.i(TAG, "✅ Reporte guardado (${type.raw})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar reporte: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Atajo para reportar comentario (cumple reglas: spotId + commentId).
     */
    suspend fun reportComment(
        commentId: String,
        spotId: String,
        reason: String
    ): Boolean {
        return report(
            type = ReportType.COMMENT,
            targetId = "",
            spotId = spotId,
            commentId = commentId,
            reason = reason
        )
    }

    /**
     * Atajo directo para reportar usuario (desde chat u otro contexto).
     */
    suspend fun reportUser(
        otherUserId: String,
        reason: String
    ): Boolean {
        return report(
            type = ReportType.USER,
            targetId = otherUserId,
            spotId = null,
            commentId = null,
            reason = reason
        )
    }

    /**
     * Compat: reportar "chat" → traduce a type:"user" resolviendo el otherUserId a partir del chatId.
     * Úsalo si en la llamada sólo tienes chatId. Si puedes, prefiere reportUser(otherUserId:).
     */
    suspend fun reportChat(
        chatId: String,
        reason: String
    ): Boolean {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "❌ ReportService: usuario no autenticado en reportChat")
            return false
        }

        return try {
            val doc = db.collection("chats")
                .document(chatId)
                .get()
                .await()

            val parts = doc.get("participants") as? List<*>
            if (parts == null || parts.size < 2) {
                Log.e(TAG, "❌ ReportService: chat sin participants")
                false
            } else {
                val other = parts
                    .mapNotNull { it as? String }
                    .firstOrNull { it != uid }

                if (other == null) {
                    Log.e(TAG, "❌ ReportService: no se pudo resolver otherUserId")
                    false
                } else {
                    reportUser(otherUserId = other, reason = reason)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ReportService: error obteniendo chat $chatId: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Atajo para reportar un Spot completo.
     */
    suspend fun reportSpot(
        spotId: String,
        reason: String
    ): Boolean {
        return report(
            type = ReportType.SPOT,
            targetId = spotId,
            spotId = spotId,
            commentId = null,
            reason = reason
        )
    }
}
