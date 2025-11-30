package com.spotitfly.app.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.spotitfly.app.data.chat.Chat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

data class NewChatUser(
    val uid: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?
)

@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onChatCreated: (Chat) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var searchText by rememberSaveable { mutableStateOf("") }
    var allUsers by remember { mutableStateOf<List<NewChatUser>>(emptyList()) }
    var filteredUsers by remember { mutableStateOf<List<NewChatUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val currentUid = auth.currentUser?.uid
            val snap = db.collection("users")
                .orderBy("usernameLower")
                .limit(50)
                .get()
                .await()

            val list = snap.documents.mapNotNull { doc ->
                val uid = doc.getString("uid") ?: doc.id
                if (uid == currentUid) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null

                val username = (data["username"] as? String)?.takeIf { it.isNotBlank() }
                    ?: (data["displayName"] as? String)?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val displayName = data["displayName"] as? String
                val avatarUrl = (data["profileImageUrl"] as? String)
                    ?: (data["photoURL"] as? String)
                    ?: (data["avatarURL"] as? String)

                NewChatUser(
                    uid = uid,
                    username = username,
                    displayName = displayName,
                    avatarUrl = avatarUrl
                )
            }
            allUsers = list
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(searchText, allUsers) {
        val query = norm(searchText)
        filteredUsers = if (query.isBlank()) {
            allUsers
        } else {
            allUsers.filter { user ->
                norm(user.username).contains(query)
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Buscar usuarios") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Error cargando usuarios",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            filteredUsers.isEmpty() -> {
                Text(
                    text = "No se han encontrado usuarios.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredUsers, key = { it.uid }) { user ->
                        UserRow(
                            user = user,
                            onClick = {
                                scope.launch {
                                    val chat = createOrOpenChatWithUser(db, auth, user, this)
                                    if (chat != null) {
                                        onChatCreated(chat)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: NewChatUser,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}


private fun norm(value: String): String {
    if (value.isBlank()) return ""
    val tmp = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    val sb = StringBuilder()
    for (c in tmp) {
        if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
            sb.append(c)
        }
    }
    return sb.toString()
}

suspend fun createOrOpenChatWithUser(
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    user: NewChatUser,
    scope: CoroutineScope
): Chat? {
    val myUid = auth.currentUser?.uid ?: return null
    val participants = listOf(myUid, user.uid).sorted()
    val chatId = participants.joinToString("_")

    val ref = db.collection("chats").document(chatId)
    val snap = ref.get().await()

    return if (snap.exists()) {
        val data = snap.data ?: emptyMap<String, Any?>()

        val lastMessage = data["lastMessage"] as? String
        val lastSenderId = data["lastSenderId"] as? String
        val updatedAtTs = data["updatedAt"] as? Timestamp
        val updatedAtMillis = updatedAtTs?.toDate()?.time

        val lastReadAny = data["lastRead"] as? Map<*, *>
        val lastReadMillis = lastReadAny
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val ts = v as? Timestamp ?: return@mapNotNull null
                key to ts.toDate().time
            }
            ?.toMap()

        val hiddenForAny = data["hiddenFor"] as? List<*>
        val hiddenFor = hiddenForAny
            ?.mapNotNull { it as? String }
            ?: emptyList()

        val isHiddenAny = data["isHidden"] as? Map<*, *>
        val isHidden = isHiddenAny
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }
            ?.toMap()

        val isSupport = (data["isSupport"] as? Boolean) ?: false
        val isGroup = !isSupport && participants.size > 2

        val displayName = (data["title"] as? String)
            ?: (data["displayName"] as? String)
            ?: user.username

        Chat(
            id = chatId,
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
    } else {
        val now = FieldValue.serverTimestamp()

        val lastRead = hashMapOf<String, Any>(
            myUid to now,
            user.uid to now
        )

        ref.set(
            hashMapOf(
                "participants" to participants,
                "lastMessage" to "",
                "updatedAt" to now,
                "lastSenderId" to null,
                "lastRead" to lastRead
            )
        ).await()

        Chat(
            id = chatId,
            participantIds = participants,
            isSupport = false,
            isGroup = false,
            displayName = user.username,
            lastMessageText = "",
            lastMessageSenderId = null,
            updatedAtMillis = null,
            lastReadMillis = null,
            hiddenFor = emptyList(),
            isHidden = null
        )
    }
}
