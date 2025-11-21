package com.spotitfly.app.data.linkpreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Entrada de caché para una URL (equivalente a URLPreviewCacheEntry en iOS).
 *
 * - originalUrl: URL tal como la escribió el usuario.
 * - finalUrl: URL final tras redirecciones (si la conocemos).
 * - title: título de la página.
 * - host: dominio (host) de la página.
 * - imageLocalPath: ruta absoluta al fichero de imagen en disco (si existe).
 * - imageAspectRatio: ancho/alto de la imagen (para mantener el alto estable).
 */
data class UrlPreviewEntry(
    val originalUrl: String,
    val finalUrl: String,
    val title: String? = null,
    val host: String? = null,
    val imageLocalPath: String? = null,
    val imageAspectRatio: Float? = null
)

/**
 * Caché de previews de URL en disco.
 *
 * Carpeta: context.cacheDir / "url_previews"
 * Para cada URL se guardan:
 *  - <hash>.json → metadatos (title, host, finalUrl, imagePath, aspectRatio)
 *  - <hash>.jpg  → miniatura (opcional)
 */
object UrlPreviewCache {

    private const val CACHE_DIR_NAME = "url_previews"
    private const val META_EXTENSION = ".json"
    private const val IMAGE_EXTENSION = ".jpg"

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Hash estable para la URL (no tiene por qué coincidir con iOS, solo ser consistente aquí).
     */
    private fun keyForUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(url.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    /**
     * Devuelve la entrada de caché para una URL, o null si no existe o está corrupta.
     */
    fun get(context: Context, url: String): UrlPreviewEntry? {
        val dir = cacheDir(context)
        val key = keyForUrl(url)
        val metaFile = File(dir, key + META_EXTENSION)

        if (!metaFile.exists()) return null

        return try {
            val jsonText = metaFile.readText()
            val json = JSONObject(jsonText)

            val originalUrl = json.optString("originalUrl", url)
            val finalUrl = json.optString("finalUrl", originalUrl)
            val title = json.optString("title").takeIf { it.isNotBlank() }
            val host = json.optString("host").takeIf { it.isNotBlank() }
            val imagePath = json.optString("imageLocalPath").takeIf { it.isNotBlank() }
            val ratio = if (json.has("imageAspectRatio")) {
                json.optDouble("imageAspectRatio", Double.NaN).let { v ->
                    if (v.isNaN()) null else v.toFloat()
                }
            } else {
                null
            }

            UrlPreviewEntry(
                originalUrl = originalUrl,
                finalUrl = finalUrl,
                title = title,
                host = host,
                imageLocalPath = imagePath,
                imageAspectRatio = ratio
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda una entrada de caché. Si se pasa bitmap, se guarda la miniatura en disco.
     *
     * @param entry metadatos de la preview.
     * @param bitmap imagen asociada (opcional).
     *
     * Devuelve la entrada actualizada con imageLocalPath rellenado si se pudo guardar el bitmap.
     */
    fun put(
        context: Context,
        entry: UrlPreviewEntry,
        bitmap: Bitmap?
    ): UrlPreviewEntry {
        val dir = cacheDir(context)
        val key = keyForUrl(entry.originalUrl)

        var imagePath: String? = entry.imageLocalPath

        // Guardar imagen si la tenemos
        if (bitmap != null) {
            val imageFile = File(dir, key + IMAGE_EXTENSION)
            try {
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                imagePath = imageFile.absolutePath
            } catch (_: IOException) {
                // Si falla la imagen, seguimos adelante con solo metadatos
                imagePath = null
            }
        }

        // Guardar metadatos (siempre intentamos guardar aunque no haya imagen)
        val metaFile = File(dir, key + META_EXTENSION)
        try {
            val json = JSONObject().apply {
                put("originalUrl", entry.originalUrl)
                put("finalUrl", entry.finalUrl)
                put("title", entry.title ?: JSONObject.NULL)
                put("host", entry.host ?: JSONObject.NULL)
                put("imageLocalPath", imagePath ?: JSONObject.NULL)
                put(
                    "imageAspectRatio",
                    entry.imageAspectRatio?.toDouble() ?: JSONObject.NULL
                )
            }
            metaFile.writeText(json.toString())
        } catch (_: Exception) {
            // Si falla, no pasa nada grave: simplemente no habrá caché para esa URL
        }

        return entry.copy(imageLocalPath = imagePath)
    }

    /**
     * Carga el bitmap desde una entrada de caché, o null si no se puede.
     */
    fun loadBitmap(entry: UrlPreviewEntry): Bitmap? {
        val path = entry.imageLocalPath ?: return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }
}
