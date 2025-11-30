package com.spotitfly.app.data.chat

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await


data class GroupInviteMeta(
    val code: String,
    val chatId: String,
    val name: String,
    val photoURL: String?
)


object GroupApi {

    private val functions = Firebase.functions("europe-west1")
    private val db = FirebaseFirestore.getInstance()

    /**
     * Crea un chat de grupo llamando a la Cloud Function `createGroup`.
     * Devuelve el chatId creado.
     */
    suspend fun createGroup(
        name: String,
        memberIds: List<String>
    ): String {
        val data = hashMapOf(
            "name" to name,
            "memberIds" to memberIds
        )

        val result = functions
            .getHttpsCallable("createGroup")
            .call(data)
            .await()

        val map = result.data as? Map<*, *>
            ?: error("Respuesta inválida de createGroup")

        val chatId = map["chatId"] as? String
            ?: error("Falta chatId en createGroup")

        return chatId
    }

    /**
     * Actualiza la foto del grupo en Firestore (campo `photoURL` del chat).
     * Corrige el caso `?bust=` cuando la URL ya trae query (`alt=media&token=...`).
     */
    suspend fun setGroupPhoto(
        chatId: String,
        photoUrl: String
    ) {
        val fixed = if (photoUrl.contains("alt=media") && photoUrl.contains("?bust=")) {
            photoUrl.replace("?bust=", "&bust=")
        } else {
            photoUrl
        }

        db.collection("chats")
            .document(chatId)
            .set(
                mapOf(
                    "photoURL" to fixed,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    // 🔹 Invitaciones de grupo (1:1 con las Functions que usa iOS)
    suspend fun createInviteLink(chatId: String): String {
        val result = functions
            .getHttpsCallable("createInviteLink")
            .call(mapOf("chatId" to chatId))
            .await()

        val data = result.data as? Map<*, *>
            ?: throw IllegalStateException("Respuesta inválida de createInviteLink")

        val url = data["url"] as? String
            ?: throw IllegalStateException("Respuesta sin campo url en createInviteLink")

        return url
    }

    suspend fun revokeInviteLink(chatId: String) {
        functions
            .getHttpsCallable("revokeInviteLink")
            .call(mapOf("chatId" to chatId))
            .await()
    }

    // ⬇️ NUEVO: añadir miembros respetando maxMembers (mismo flujo que iOS)
    suspend fun addMembersRespectingLimit(
        chatId: String,
        userIds: List<String>
    ) {
        if (userIds.isEmpty()) return

        val chatRef = db.collection("chats").document(chatId)
        val snapshot = chatRef.get().await()

        val maxMembers = (snapshot.getLong("maxMembers") ?: 64L)
            .toInt()
            .coerceAtLeast(1)

        val existing = (snapshot.get("participantIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

        val uniqueToAdd = userIds.toSet().minus(existing.toSet())
        if (uniqueToAdd.isEmpty()) return

        val remainingSlots = maxMembers - existing.size
        if (remainingSlots <= 0) return

        val finalList = uniqueToAdd.take(remainingSlots)

        if (finalList.isEmpty()) return

        val payload = hashMapOf(
            "chatId" to chatId,
            "userIds" to finalList
        )

        functions
            .getHttpsCallable("addMembersWithLimit")
            .call(payload)
            .await()
    }

    // ----------------------------
    // Invitaciones: leer meta + unirse
    // ----------------------------

    private fun extractInviteCode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val lower = trimmed.lowercase()
        val prefix = "spots://invite/"

        val codePart = if (lower.startsWith(prefix)) {
            // spots://invite/ABC123...
            trimmed.substring(prefix.length)
        } else {
            trimmed
        }

        // Nos quedamos con el primer “token”, sin espacios ni / ? #
        return codePart
            .takeWhile { !it.isWhitespace() && it != '/' && it != '?' && it != '#' }
            .uppercase()
    }

    suspend fun getInviteMeta(input: String): GroupInviteMeta {
        val code = extractInviteCode(input)
        if (code.isEmpty()) {
            throw IllegalArgumentException("Código de invitación vacío")
        }

        val result = functions
            .getHttpsCallable("getInviteMeta")
            .call(mapOf("code" to code))
            .await()

        val data = result.data as? Map<*, *>
            ?: throw IllegalStateException("Respuesta inválida de getInviteMeta")

        val ok = data["ok"] as? Boolean ?: false
        if (!ok) {
            throw IllegalStateException("Esta invitación no es válida o ya no está activa")
        }

        val chatId = data["chatId"] as? String
            ?: throw IllegalStateException("Respuesta getInviteMeta sin chatId")
        val name = data["name"] as? String ?: "Grupo"
        val photoURL = data["photoURL"] as? String

        return GroupInviteMeta(
            code = code,
            chatId = chatId,
            name = name,
            photoURL = photoURL
        )
    }

    /**
     * Llama a la Cloud Function `joinByInvite` con el código o URL.
     * Devuelve el chatId del grupo al que te has unido.
     */
    suspend fun joinByInvite(input: String): String {
        val code = extractInviteCode(input)
        if (code.isEmpty()) {
            throw IllegalArgumentException("Código de invitación vacío")
        }

        val result = functions
            .getHttpsCallable("joinByInvite")
            .call(mapOf("code" to code))
            .await()

        val data = result.data as? Map<*, *>
            ?: throw IllegalStateException("Respuesta inválida de joinByInvite")

        val ok = data["ok"] as? Boolean ?: false
        if (!ok) {
            throw IllegalStateException("No se pudo usar esta invitación")
        }

        val chatId = data["chatId"] as? String
            ?: throw IllegalStateException("Respuesta joinByInvite sin chatId")

        return chatId
    }

    // Quitar un miembro del grupo
    suspend fun removeMember(chatId: String, memberId: String) {
        val data = hashMapOf(
            "chatId" to chatId,
            "uid" to memberId
        )

        functions
            .getHttpsCallable("removeMember")
            .call(data)
            .await()
    }

    // Salir del grupo (para el usuario actual)
    suspend fun leaveGroup(chatId: String) {
        val data = hashMapOf(
            "chatId" to chatId
        )

        functions
            .getHttpsCallable("leaveGroup")
            .call(data)
            .await()
    }

    // Promover a admin
    suspend fun grantAdmin(chatId: String, userId: String) {
        val data = hashMapOf(
            "chatId" to chatId,
            "userId" to userId
        )

        functions
            .getHttpsCallable("grantAdmin")
            .call(data)
            .await()
    }

    // Quitar admin
    suspend fun revokeAdmin(chatId: String, userId: String) {
        val data = hashMapOf(
            "chatId" to chatId,
            "userId" to userId
        )

        functions
            .getHttpsCallable("revokeAdmin")
            .call(data)
            .await()
    }

}


