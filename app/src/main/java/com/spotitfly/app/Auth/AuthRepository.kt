package com.spotitfly.app.auth

import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID



class AuthRepository(
    val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    init {
        auth.setLanguageCode("es")
    }

    suspend fun signIn(email: String, pass: String) {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        auth.signInWithEmailAndPassword(email.trim(), pass).await()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun register(username: String, email: String, pass: String, acceptedRules: Boolean) {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        val cleanEmail = email.trim()
        val cleanName = username.trim()
        val lowerName = cleanName.lowercase(Locale.getDefault())

        // 1) Crea el usuario
        val result = auth.createUserWithEmailAndPassword(cleanEmail, pass).await()
        val user = result.user ?: error("No se pudo crear el usuario")

        // 2) Perfil visible en FirebaseAuth
        user.updateProfile(userProfileChangeRequest {
            displayName = cleanName
        }).await()

        // 3) Doc en Firestore (users/{uid}) — createdAt con serverTimestamp
        val doc = mapOf(
            "displayName" to cleanName,
            "displayNameLowercase" to lowerName,
            "username" to cleanName,
            "usernameLower" to lowerName,
            "email" to cleanEmail,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "acceptedRulesAt" to if (acceptedRules) Date() else null,
            "photoUrl" to null,
            "profileImageUrl" to null,
            "avatarBustToken" to null
        )
        db.collection("users").document(user.uid).set(doc).await()



        // 4) Verificación de email
        user.sendEmailVerification().await()
    }

    suspend fun resendVerification() {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        val user = auth.currentUser ?: return
        user.sendEmailVerification().await()
    }

    suspend fun isEmailVerified(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        return user.isEmailVerified
    }

    suspend fun sendReset(email: String) {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        val storage: FirebaseStorage = FirebaseStorage.getInstance()

        auth.signOut()
    }

    fun currentUid(): String? = auth.currentUser?.uid

    // === Username availability (debounced desde el VM) ===
    suspend fun isUsernameAvailable(username: String): Boolean {
        val candidate = username.trim().lowercase(Locale.getDefault())
        if (candidate.isEmpty()) return false

        val snap = db.collection("users")
            .whereEqualTo("usernameLower", candidate)
            .limit(1)
            .get().await()

        return snap.isEmpty
    }



    suspend fun uploadProfilePhotoBytes(uid: String, data: ByteArray): String {
        val ref = storage.reference.child("profileImages/$uid/avatar.jpg")
        ref.putBytes(data).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadProfilePhoto(uid: String, uri: Uri): String {
        val ref = storage.reference.child("profileImages/$uid/avatar.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun updateUserPhotoUrl(uid: String, url: String) {
        db.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "profileImageUrl" to url,
                    "avatarBustToken" to UUID.randomUUID().toString(),
                    "photoUrl" to url // compat
                )
            )
            .await()

        auth.currentUser
            ?.updateProfile(userProfileChangeRequest { photoUri = Uri.parse(url) })
            ?.await()
    }
    suspend fun removeProfilePhoto(uid: String) {
        val ref = storage.reference.child("profileImages/$uid/avatar.jpg")
        try { ref.delete().await() } catch (_: Exception) {}
        db.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "profileImageUrl" to FieldValue.delete(),
                    "avatarBustToken" to UUID.randomUUID().toString()
                )
            )
            .await()
    }
    suspend fun updateUsername(newUsername: String) {
        val u = auth.currentUser ?: throw IllegalStateException("No user")
        val clean = newUsername.trim()
        val lower = clean.lowercase(Locale.getDefault())
        db.collection("users")
            .document(u.uid)
            .update(
                mapOf(
                    "username" to clean,
                    "usernameLower" to lower
                )
            )
            .await()
        u.updateProfile(userProfileChangeRequest { displayName = clean }).await()
    }
    suspend fun registerDeviceForPushIfPossible() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        try {
            // Pide el token FCM actual (igual que hace iOS con Messaging.messaging().token)
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isBlank()) return

            val language = Locale.getDefault().toLanguageTag()

            val data = mapOf(
                "platform" to "android",
                "language" to language,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // Misma estructura que iOS: users/{uid}/devices/{token}
            db.collection("users")
                .document(uid)
                .collection("devices")
                .document(token)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error registrando device FCM", e)
        }
    }
    /**
     * Se llama justo después de un login correcto + email verificado.
     *
     * - Si granted = true:
     *   - Crea/actualiza users/{uid}/meta/notifications con enabled/messages/comments = true.
     *   - Registra este dispositivo en users/{uid}/devices/{token}.
     * - Si granted = false:
     *   - Crea/actualiza meta/notifications con todo a false y NO registra device.
     */
    suspend fun initNotificationsAfterLogin(granted: Boolean) {
        val user = auth.currentUser ?: return
        val uid = user.uid

        val lang = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Exception) {
            "es-ES"
        }

        // 1️⃣ Meta de notificaciones
        val prefsData = mapOf(
            "enabled" to granted,
            "messages" to granted,
            "comments" to granted,
            "lang" to lang,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(uid)
            .collection("meta")
            .document("notifications")
            .set(prefsData, SetOptions.merge())
            .await()

        if (!granted) {
            // El usuario no quiere notificaciones → no registramos device
            return
        }

        // 2️⃣ Registrar este dispositivo en users/{uid}/devices/{token}
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isBlank()) return

            val deviceData = mapOf(
                "platform" to "android",
                "language" to lang,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(uid)
                .collection("devices")
                .document(token)
                .set(deviceData, SetOptions.merge())
                .await()
        } catch (_: Exception) {
            // No rompemos el login si falla el registro del device
        }
    }
}






