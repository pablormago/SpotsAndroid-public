package com.spotitfly.app.data.chat

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché JSON en disco + memoria para previews de enlaces de chat,
 * equivalente a LinkPreviewCache de iOS.
 *
 * NOTA: la integración en la UI llegará en D3.3.
 */
data class ChatLinkPreview(
    val url: String,
    val siteName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val mediaType: String? = null,
    val author: String? = null,
    val durationSeconds: Double? = null,
    val publishedAtMillis: Long? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

object ChatLinkPreviewCache {

    // 7 días (igual que iOS)
    private const val TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000L

    // Caché en memoria por clave normalizada
    private val memory = ConcurrentHashMap<String, ChatLinkPreview>()

    fun get(context: Context, url: String): ChatLinkPreview? {
        val key = cacheKey(url)

        // 1) Memoria
        memory[key]?.let { cached ->
            if (!isExpired(cached)) return cached
        }

        // 2) Disco
        val file = fileForKey(context, key)
        if (!file.exists()) return null

        return try {
            val raw = file.readText()
            val json = JSONObject(raw)

            val preview = ChatLinkPreview(
                url = json.optString("url"),
                siteName = json.optString("siteName").takeIf { it.isNotBlank() },
                title = json.optString("title").takeIf { it.isNotBlank() },
                description = json.optString("description").takeIf { it.isNotBlank() },
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                mediaType = json.optString("mediaType").takeIf { it.isNotBlank() },
                author = json.optString("author").takeIf { it.isNotBlank() },
                durationSeconds = if (json.has("durationSeconds")) json.optDouble("durationSeconds") else null,
                publishedAtMillis = if (json.has("publishedAtMillis")) json.optLong("publishedAtMillis") else null,
                fetchedAtMillis = json.optLong("fetchedAtMillis")
            )

            if (isExpired(preview)) {
                runCatching { file.delete() }
                null
            } else {
                memory[key] = preview
                preview
            }
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, preview: ChatLinkPreview) {
        val key = cacheKey(preview.url)
        val updated = preview.copy(fetchedAtMillis = System.currentTimeMillis())
        memory[key] = updated

        val file = fileForKey(context, key)

        runCatching {
            val json = JSONObject().apply {
                put("url", updated.url)
                put("siteName", updated.siteName)
                put("title", updated.title)
                put("description", updated.description)
                put("thumbnailUrl", updated.thumbnailUrl)
                put("mediaType", updated.mediaType)
                put("author", updated.author)
                updated.durationSeconds?.let { put("durationSeconds", it) }
                updated.publishedAtMillis?.let { put("publishedAtMillis", it) }
                put("fetchedAtMillis", updated.fetchedAtMillis)
            }
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            file.writeText(json.toString())
        }
    }

    fun clear(context: Context) {
        memory.clear()
        runCatching {
            val dir = File(context.cacheDir, "link_previews")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    // --- Helpers privados ---

    private fun isExpired(preview: ChatLinkPreview): Boolean {
        val age = System.currentTimeMillis() - preview.fetchedAtMillis
        return age > TTL_MILLIS
    }

    private fun fileForKey(context: Context, key: String): File {
        val dir = File(context.cacheDir, "link_previews")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.json")
    }

    private fun cacheKey(url: String): String {
        val normalized = normalizeUrl(url)
        return sha256(normalized)
    }

    private fun normalizeUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase() ?: return url
        val path = uri.path ?: ""
        val query = uri.query ?: ""
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(path)
            if (query.isNotBlank()) {
                append("?")
                append(query)
            }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }
}
