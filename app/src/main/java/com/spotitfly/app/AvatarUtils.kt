package com.spotitfly.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File

fun avatarLocalFile(context: Context, uid: String): File {
    val dir = File(context.filesDir, "avatars")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${uid}_avatar.jpg")
}

fun chatFileThumbnailLocalFile(context: Context, messageId: String): File {
    val dir = File(context.filesDir, "chat_thumbnails")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${messageId}_thumb.jpg")
}

/**
 * Ruta para guardar la imagen principal de un LinkPreview enriquecido.
 *
 * El parámetro [key] debería ser estable (por ejemplo, un hash SHA-256 de la URL).
 */
fun linkPreviewImageLocalFile(context: Context, key: String): File {
    val dir = File(context.filesDir, "link_previews")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${key}_image.jpg")
}

fun saveDrawableToFile(drawable: Drawable, file: File) {
    val bmp: Bitmap? = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> null
    }
    if (bmp != null) {
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }
}
