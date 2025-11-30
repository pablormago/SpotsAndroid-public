package com.spotitfly.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.spotitfly.app.data.chat.Chat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.Source
import java.util.Locale
import android.content.Context
import com.google.firebase.FirebaseApp
import com.spotitfly.app.data.local.AppDatabase
import com.spotitfly.app.data.local.dao.ChatDao
import com.spotitfly.app.data.local.dao.ChatParticipantDao
import com.spotitfly.app.data.local.entity.ChatEntity
import com.spotitfly.app.data.local.entity.ChatParticipantEntity
import com.google.firebase.firestore.FieldValue   // 👈 NUEVO
import com.google.firebase.firestore.SetOptions  // 👈 NUEVO
import kotlinx.coroutines.Dispatchers


class ChatsHomeViewModel : ViewModel() {

    private val ADMIN_EMAIL = "pablormago@gmail.com"
    private var isAdmin: Boolean = false

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val appContext: Context = FirebaseApp.getInstance().applicationContext
    private val chatDao: ChatDao by lazy { AppDatabase.get(appContext).chatsDao() }
    private val chatParticipantDao: ChatParticipantDao by lazy { AppDatabase.get(appContext).chatParticipantsDao() }

    private var listener: ListenerRegistration? = null

    private var currentUid: String? = null

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Cache de nombres como en iOS (ChatsViewModel.shared.usernames)
    private val namesCache = mutableMapOf<String, String>()
    private val inflightNameFetches = mutableSetOf<String>()


    /**
     * Arranca el listener en Firestore para la colección de chats del usuario.
     * Equivalente al comportamiento de ChatsViewModel en iOS.
     */
    fun start() {
        val user = auth.currentUser
        if (user == null) {
            // No hay usuario autenticado: paramos cualquier listener y vaciamos la UI
            stop()
            return
        }

        val uid = user.uid

        // Si el usuario ha cambiado desde la última vez, reiniciamos todo
        if (currentUid != uid) {
            stop() // limpia listener + estado + lista
            currentUid = uid
        }

        // Si ya hay un listener activo para ESTE uid, no hacemos nada
        if (listener != null) {
            return
        }

        // Cargar la caché local en background para no bloquear el hilo principal
        viewModelScope.launch(Dispatchers.IO) {
            loadChatsFromLocal(uid)
        }

        // Resolver flag admin por email (igual que en iOS)
        val email = user.email?.lowercase(Locale.ROOT) ?: ""
        isAdmin = email == ADMIN_EMAIL.lowercase(Locale.ROOT)

        _isLoading.value = _chats.value.isEmpty()


        val ref = db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        listener = ref.addSnapshotListener { snap, error ->
            if (error != null) {
                _isLoading.value = false
                return@addSnapshotListener
            }

            // Si el usuario actual ya no es el mismo para el que se creó este listener,
            // ignoramos completamente este snapshot (logout / cambio de usuario)
            val currentUid = auth.currentUser?.uid
            if (currentUid == null || currentUid != uid) {
                _isLoading.value = false
                return@addSnapshotListener
            }

            val docs = snap?.documents ?: emptyList()
            val out = mutableListOf<Chat>()

            for (doc in docs) {
                val id = doc.id

                val participantsAny = doc.get("participants") as? List<*>
                val participants = participantsAny
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                val isSupport = doc.getBoolean("isSupport") ?: false

                // B4 – En bandeja normal del admin, no queremos triadas {admin, soporte, otro}
                if (isAdmin && isSupport) {
                    val humans = participants.filter { it != SUPPORT_BOT_ID }
                    val keep = humans.size == 1 && humans.firstOrNull() == uid
                    if (!keep) {
                        continue
                    }
                }

                val lastMessage = doc.getString("lastMessage")
                val lastSenderId = doc.getString("lastSenderId")

                val updatedTs: Timestamp? =
                    doc.getTimestamp("updatedAt") ?: doc.getTimestamp("createdAt")
                val updatedAtMillis = updatedTs?.toDate()?.time

                val lastReadField = doc.get("lastRead")
                val lastReadMillis: Map<String, Long>? = when (lastReadField) {
                    is Map<*, *> -> {
                        lastReadField
                            .mapNotNull { (k, v) ->
                                val key = k as? String ?: return@mapNotNull null
                                val ts = v as? Timestamp ?: return@mapNotNull null
                                key to ts.toDate().time
                            }
                            .toMap()
                    }
                    is Timestamp -> {
                        // Compatibilidad: chats donde lastRead es un timestamp plano
                        val me = uid
                        if (me != null) mapOf(me to lastReadField.toDate().time) else null
                    }
                    else -> null
                }

                val hiddenForAny = doc.get("hiddenFor") as? List<*>
                val hiddenFor = hiddenForAny
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                val isHiddenAny = doc.get("isHidden") as? Map<*, *>
                val isHidden = isHiddenAny
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? Boolean ?: return@mapNotNull null
                        key to value
                    }
                    ?.toMap()

                val isGroup = !isSupport && participants.size > 2

                // Nombre del chat (1:1 / grupo / soporte) siguiendo la lógica de iOS
                val displayName: String? = when {
                    isSupport -> {
                        // soporte: usa título si existe, si no "Soporte"
                        doc.getString("title")
                            ?: doc.getString("name")
                            ?: doc.getString("displayName")
                            ?: "Soporte"
                    }
                    isGroup -> {
                        // grupo: usa name/displayName del doc o un fallback
                        doc.getString("title")
                            ?: doc.getString("name")
                            ?: doc.getString("displayName")
                            ?: "Grupo"
                    }
                    else -> {
                        // 1:1 → nombre del otro usuario
                        val me = uid
                        val otherId = participants.firstOrNull { it != me && it != SUPPORT_BOT_ID }

                        if (otherId != null) {
                            // dispara carga en background (cache) como primeUserCache en iOS
                            ensureUserNameCached(otherId)
                            cachedNameOrStub(otherId)
                        } else {
                            "Chat"
                        }
                    }
                }

                val chat = Chat(
                    id = id,
                    participantIds = participants,
                    isSupport = isSupport,
                    isGroup = isGroup,
                    displayName = displayName,
                    lastMessageText = lastMessage,
                    lastMessageSenderId = lastSenderId,
                    updatedAtMillis = updatedAtMillis,
                    lastReadMillis = lastReadMillis,
                    hiddenFor = hiddenFor,
                    isHidden = isHidden
                )

                out.add(chat)
            }

            _chats.value = out
            cacheChatsForUser(uid, out)
            _isLoading.value = false
        }
    }

    private fun cacheChatsForUser(uid: String, chats: List<Chat>) {
        if (chats.isEmpty()) return

        // Seguridad extra: si el usuario actual ya no es "uid",
        // no cacheamos nada en Room (evita revivir chats tras logout)
        val currentUid = auth.currentUser?.uid
        if (currentUid == null || currentUid != uid) {
            return
        }

        viewModelScope.launch {
            val entities = chats.map { ChatEntity.fromDomain(it) }
            chatDao.upsertAll(entities)

            chats.forEach { chat ->
                val participants = chat.participantIds.distinct()
                val participantEntities = participants.map { participantId ->
                    ChatParticipantEntity(
                        chatId = chat.id,
                        userId = participantId
                    )
                }
                chatParticipantDao.deleteForChat(chat.id)
                if (participantEntities.isNotEmpty()) {
                    chatParticipantDao.insertAll(participantEntities)
                }
            }
        }
    }

    private fun loadChatsFromLocal(uid: String) {
        val entities = chatDao.getAllOnce()
        if (entities.isEmpty()) return

        // Solo queremos chats donde "uid" sea participante
        val chats = entities.mapNotNull { entity ->
            val participants = chatParticipantDao
                .getForChat(entity.id)
                .map { it.userId }

            if (!participants.contains(uid)) {
                // Chat que pertenece a otro usuario → no se muestra
                return@mapNotNull null
            }

            ChatEntity.toDomain(
                entity = entity,
                participantIds = participants
            )
        }

        if (chats.isEmpty()) return

        // 1) Mostrar primero lo que tengamos en local (instantáneo)
        _chats.value = chats

        // 2) Precalentar la cache de nombres para 1:1
        //    para que cachedNameOrStub() devuelva el nombre real y no "usuario-XXXX"
        val me = auth.currentUser?.uid
        chats.forEach { chat ->
            if (!chat.isSupport && !chat.isGroup) {
                val otherId = chat.participantIds.firstOrNull { it != me && it != SUPPORT_BOT_ID }
                val name = chat.displayName?.trim()
                if (!otherId.isNullOrEmpty() && !name.isNullOrEmpty()) {
                    namesCache[otherId] = name
                }
            }
        }
    }

    // -------------------------
    // Helpers de nombres (1:1 iOS)
    // -------------------------

    private fun ensureUserNameCached(uid: String) {
        if (namesCache.containsKey(uid) || inflightNameFetches.contains(uid)) return
        inflightNameFetches.add(uid)

        val users = db.collection("users")

        // 1) Intentar cache local
        users.document(uid).get(Source.CACHE)
            .addOnSuccessListener { snap ->
                val data = snap.data
                if (data != null) {
                    val name = preferUsername(data, uid)
                    namesCache[uid] = name
                    updateChatsDisplayName(uid, name)
                    inflightNameFetches.remove(uid)
                } else {
                    // 2) Si no hay cache, ir a red
                    users.document(uid).get()
                        .addOnSuccessListener { netSnap ->
                            val netData = netSnap.data
                            if (netData != null) {
                                val name = preferUsername(netData, uid)
                                namesCache[uid] = name
                                updateChatsDisplayName(uid, name)
                            }
                            inflightNameFetches.remove(uid)
                        }
                        .addOnFailureListener {
                            inflightNameFetches.remove(uid)
                        }
                }
            }
            .addOnFailureListener {
                // Fallback directo a red
                users.document(uid).get()
                    .addOnSuccessListener { netSnap ->
                        val netData = netSnap.data
                        if (netData != null) {
                            val name = preferUsername(netData, uid)
                            namesCache[uid] = name
                            updateChatsDisplayName(uid, name)
                        }
                        inflightNameFetches.remove(uid)
                    }
                    .addOnFailureListener {
                        inflightNameFetches.remove(uid)
                    }
            }
    }

    private fun preferUsername(data: Map<String, Any>, uid: String): String {
        fun field(key: String): String =
            (data[key] as? String)?.trim().orEmpty()

        val username = field("username")
        if (username.isNotEmpty()) return username

        val usernameLower = field("usernameLower")
        if (usernameLower.isNotEmpty()) return usernameLower

        val displayName = field("displayName")
        if (displayName.isNotEmpty()) return displayName

        val name = field("name")
        if (name.isNotEmpty()) return name

        val fullName = field("fullName")
        if (fullName.isNotEmpty()) return fullName

        val email = field("email")
        if (email.isNotEmpty()) {
            val local = email.substringBefore("@").trim()
            if (local.isNotEmpty()) return local
        }

        return stubName(uid)
    }

    private fun stubName(uid: String): String =
        "usuario-" + uid.take(6)

    private fun cachedNameOrStub(uid: String): String {
        val cached = namesCache[uid]?.trim()
        return if (!cached.isNullOrEmpty()) cached else stubName(uid)
    }

    private fun updateChatsDisplayName(uid: String, displayName: String) {
        val me = auth.currentUser?.uid

        val updated = _chats.value.map { chat ->
            if (chat.isSupport || chat.isGroup) {
                chat
            } else {
                val otherId = chat.participantIds.firstOrNull { it != me && it != SUPPORT_BOT_ID }
                if (otherId == uid) {
                    chat.copy(displayName = displayName)
                } else {
                    chat
                }
            }
        }

        _chats.value = updated

        // Sincronizar también la caché local para que el próximo arranque
        // ya use el nombre bueno y no "usuario-XXXX".
        viewModelScope.launch {
            val toPersist = updated.filter { chat ->
                !chat.isSupport && !chat.isGroup && chat.participantIds
                    .firstOrNull { it != me && it != SUPPORT_BOT_ID } == uid
            }
            if (toPersist.isNotEmpty()) {
                val entities = toPersist.map { ChatEntity.fromDomain(it) }
                chatDao.upsertAll(entities)
            }
        }
    }

    // -------------------------
    // Swipes desde la Home (D2.4)
    // -------------------------

    fun markChatAsReadFromHome(chatId: String) {
        val uid = auth.currentUser?.uid ?: return

        val nowTs = Timestamp.now()
        val nowMillis = nowTs.toDate().time

        // Update optimista local (igual que en iOS: applyLocalRead)
        val updated = _chats.value.map { chat ->
            if (chat.id != chatId) chat
            else {
                val current = chat.lastReadMillis ?: emptyMap()
                val newMap = current + (uid to nowMillis)
                chat.copy(lastReadMillis = newMap)
            }
        }
        _chats.value = updated

        // Persistir en Firestore (chats.lastRead.{uid} + users/{uid}/chatsReads)
        val chatDoc = db.collection("chats").document(chatId)
        val userDoc = db.collection("users").document(uid)
            .collection("chatsReads")
            .document(chatId)

        chatDoc.update("lastRead.$uid", nowTs)
        userDoc.set(mapOf("updatedAt" to nowTs), SetOptions.merge())
    }

    fun setHidden(chatId: String, hide: Boolean) {
        val uid = auth.currentUser?.uid ?: return

        // Update optimista local
        val updated = _chats.value.map { chat ->
            if (chat.id != chatId) chat
            else {
                val current = chat.isHidden ?: emptyMap()
                val newMap = if (hide) {
                    current + (uid to true)
                } else {
                    current - uid
                }
                chat.copy(isHidden = if (newMap.isEmpty()) null else newMap)
            }
        }
        _chats.value = updated

        // Firestore: isHidden.{uid} + hiddenFor (como en iOS ChatService.setHidden)
        val updates = mutableMapOf<String, Any>()
        if (hide) {
            updates["isHidden.$uid"] = true
            updates["hiddenFor"] = FieldValue.arrayUnion(uid)
        } else {
            updates["isHidden.$uid"] = FieldValue.delete()
            updates["hiddenFor"] = FieldValue.arrayRemove(uid)
        }

        db.collection("chats")
            .document(chatId)
            .update(updates)
    }

    fun stop() {
        listener?.remove()
        listener = null
        _isLoading.value = false
        _chats.value = emptyList()
        namesCache.clear()
        currentUid = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    companion object {
        // Mismo UID que en iOS
        private const val SUPPORT_BOT_ID = "26CSxWS7R7eZlrvXUV1qJFyL7Oc2"
    }
}
