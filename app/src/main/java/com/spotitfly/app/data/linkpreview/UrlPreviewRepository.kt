package com.spotitfly.app.data.linkpreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale
import java.util.regex.Pattern
import android.util.Log
private const val URL_PREVIEW_TAG = "UrlPreviewRepo"

/**
 * Repositorio de previews de URL.
 *
 * - Primero intenta leer de caché (UrlPreviewCache).
 * - Si no hay caché, hace petición HTTP para obtener título, host e imagen.
 * - Guarda el resultado en disco (metadatos + miniatura opcional).
 *
 * Es el equivalente a URLPreviewPrefetcher + LinkPreviewCache en iOS.
 */
object UrlPreviewRepository {

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                "Version/17.0 Mobile/15E148 Safari/604.1"

    /**
     * Carga una preview para mostrar en UI.
     * - Devuelve inmediatamente el contenido de caché si existe.
     * - Si no existe, hace fetch de red y guarda en caché.
     */
    suspend fun loadForUi(context: Context, url: String): UrlPreviewEntry? {
        return withContext(Dispatchers.IO) {
            // 1) Intentar leer de caché
            val cached = UrlPreviewCache.get(context, url)
            if (cached != null) return@withContext cached

            // 2) No hay caché → fetch desde la red y guardar
            fetchAndCache(context, url)
        }
    }

    /**
     * Prefetch para calentar la caché sin bloquear la UI.
     * - Igual que loadForUi, pero el resultado se ignora.
     */
    suspend fun prefetch(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            val cached = UrlPreviewCache.get(context, url)
            if (cached != null) return@withContext
            fetchAndCache(context, url)
        }
    }

    // --- Internals ---

    private suspend fun fetchAndCache(context: Context, url: String): UrlPreviewEntry? {
        return try {
            val result = fetchPreviewFromNetwork(url)
            if (result != null) {
                val (entry, bitmap) = result
                UrlPreviewCache.put(context, entry, bitmap)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Hace el trabajo "gordo":
     * - Sigue redirects hasta la URL final.
     * - Intenta leer título, host e imagen (OpenGraph / <title>).
     * - Si es YouTube, usa miniatura estándar si no hay og:image.
     */
    private fun fetchPreviewFromNetwork(originalUrl: String): Pair<UrlPreviewEntry, Bitmap?>? {
        val normalizedUrl = normalizeUrl(originalUrl) ?: return null

        Log.d(URL_PREVIEW_TAG, "Fetching preview: original=$originalUrl normalized=$normalizedUrl")

        // 1) Abrir conexión y seguir redirects
        val connection = openConnectionFollowRedirects(normalizedUrl) ?: return null
        val finalUrl = connection.url.toString()

        val contentType = connection.contentType ?: ""
        val isHtml = contentType.lowercase(Locale.ROOT).contains("text/html")

        var html: String? = null
        if (isHtml) {
            html = readLimitedText(connection.inputStream)
        } else {
            // No es HTML → podemos intentar igualmente tratarlo como recurso directo,
            // pero para simplificar, si no es HTML usamos solo host y título vacío.
        }

        val uri = Uri.parse(finalUrl)
        val host = uri.host

        Log.d(
            URL_PREVIEW_TAG,
            "Final URL=$finalUrl host=$host contentType=$contentType isHtml=$isHtml"
        )

        // 2) Extraer título y meta OG si hay HTML
        var title: String? = null
        var ogImageUrl: String? = null
        var ogSiteName: String? = null

        if (!html.isNullOrEmpty()) {
            // Title normal
            title = findTagContent(html, "<title>", "</title>") ?: title

            // Intentamos varias variantes de imagen: og:image, og:image:secure_url, twitter:image, name=og:image
            val rawOgImage = findMetaContent(html, "property", "og:image")
                ?: findMetaContent(html, "property", "og:image:secure_url")
                ?: findMetaContent(html, "name", "og:image")
                ?: findMetaContent(html, "name", "twitter:image")

            if (!rawOgImage.isNullOrBlank()) {
                ogImageUrl = htmlDecode(rawOgImage)
            }

            // og:site_name
            ogSiteName = findMetaContent(html, "property", "og:site_name") ?: ogSiteName

            // Fallback de título desde og:title
            if (title.isNullOrBlank()) {
                val rawOgTitle = findMetaContent(html, "property", "og:title")
                if (!rawOgTitle.isNullOrBlank()) {
                    title = htmlDecode(rawOgTitle)
                }
            }

            Log.d(
                URL_PREVIEW_TAG,
                "Parsed meta: title=${title?.take(80)}, ogImageUrl=$ogImageUrl, ogSiteName=$ogSiteName"
            )
        }





        // 3) Host final para mostrar (prioridad: og:site_name > host)
        val displayHost = when {
            !ogSiteName.isNullOrBlank() -> ogSiteName
            !host.isNullOrBlank() -> host
            else -> Uri.parse(normalizedUrl).host
        }

        // 4) Resolver imagen (incluyendo YouTube + fallback Amazon)
        var imageBitmap: Bitmap? = null
        var imageAspectRatio: Float? = null

        // Fallback específico para Amazon: buscar la primera imagen de producto en el HTML
        val amazonImageFromHtml =
            if (!html.isNullOrEmpty() && !host.isNullOrBlank() && host.contains("amazon.", ignoreCase = true)) {
                extractAmazonImageFromHtml(html)
            } else {
                null
            }

        val youtubeThumbUrl = youtubeThumbnailUrl(finalUrl)

        val rawImageUrl = when {
            !ogImageUrl.isNullOrBlank()          -> ogImageUrl
            youtubeThumbUrl != null              -> youtubeThumbUrl
            !amazonImageFromHtml.isNullOrBlank() -> amazonImageFromHtml
            else                                 -> null
        }

        // Normalizamos y resolvemos la URL de imagen (Amazon, noticias, etc.)
        val imageCandidateUrl = rawImageUrl
            ?.let { normalizeImageUrl(it, finalUrl) }
            ?.let { resolveImageUrl(finalUrl, it) }

        Log.d(
            URL_PREVIEW_TAG,
            "Image candidate: host=$host ogImageUrl=$ogImageUrl youtubeThumbUrl=$youtubeThumbUrl raw=$rawImageUrl normalized=$imageCandidateUrl"
        )


        if (!imageCandidateUrl.isNullOrBlank()) {
            try {
                val imageResult = downloadImage(imageCandidateUrl)

                if (imageResult != null) {
                    val (bmp, ratio) = imageResult
                    imageBitmap = bmp
                    imageAspectRatio = ratio
                }
            } catch (_: Exception) {
                // Si falla la imagen, continuamos sólo con texto
                imageBitmap = null
                imageAspectRatio = null
            }
        }


        val entry = UrlPreviewEntry(
            originalUrl = originalUrl,
            finalUrl = finalUrl,
            title = title?.takeIf { it.isNotBlank() },
            host = displayHost?.takeIf { it.isNotBlank() },
            imageLocalPath = null,  // se rellenará en UrlPreviewCache.put si guardamos bitmap
            imageAspectRatio = imageAspectRatio
        )

        return entry to imageBitmap
    }

    // --- Helpers de red y parsing ---

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            // No intentamos añadir https aquí; solo soportamos http/https como en iOS
            null
        }
    }

    private fun openConnectionFollowRedirects(urlStr: String): HttpURLConnection? {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as? HttpURLConnection) ?: return null
            // 👇 NUEVO: cabeceras tipo navegador
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; SpotItFly) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.useCaches = true
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            conn.inputStream // fuerza la conexión

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) return null
                currentUrl = if (location.startsWith("http")) {
                    location
                } else {
                    // Resolver redirects relativos
                    URL(url, location).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
        return null
    }


    private fun readLimitedText(input: InputStream, maxBytes: Int = 256 * 1024): String {
        return try {
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            var total = 0
            while (true) {
                val read = input.read(tmp)
                if (read == -1) break
                if (total + read > maxBytes) {
                    buffer.write(tmp, 0, maxBytes - total)
                    break
                } else {
                    buffer.write(tmp, 0, read)
                    total += read
                }
            }
            buffer.toString(Charset.forName("UTF-8").name())
        } catch (_: Exception) {
            ""
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun findTagContent(html: String, openTag: String, closeTag: String): String? {
        val lower = html.lowercase(Locale.ROOT)
        val open = openTag.lowercase(Locale.ROOT)
        val close = closeTag.lowercase(Locale.ROOT)

        val start = lower.indexOf(open)
        if (start == -1) return null
        val end = lower.indexOf(close, start + open.length)
        if (end == -1) return null

        val content = html.substring(start + open.length, end)
        return content.trim().replace(Regex("\\s+"), " ")
    }

    private fun htmlDecode(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    // Normaliza URLs de imagen que vienen con entidades HTML o sin esquema / relativas
    private fun normalizeImageUrl(imageUrl: String, baseUrlString: String?): String {
        var url = htmlDecode(imageUrl).trim()

        val base = try {
            baseUrlString?.let { java.net.URL(it) }
        } catch (_: Exception) {
            null
        }

        // Caso típico: //m.media-amazon.com/...
        if (url.startsWith("//")) {
            val scheme = base?.protocol ?: "https"
            url = "$scheme:$url"
        }
        // Caso relativo: /images/...
        else if (url.startsWith("/") && base != null) {
            url = "${base.protocol}://${base.host}$url"
        }

        return url
    }



    private fun findMetaContent(html: String, attrName: String, attrValue: String): String? {
        // Versión más tolerante:
        // - Recorre todos los <meta ...>
        // - Acepta property="og:..." o name="og:..."
        // - Acepta cualquier orden de atributos (content puede ir antes o después)
        // - Acepta comillas simples o dobles
        val lowerAttrValue = attrValue.lowercase(Locale.ROOT)

        // Buscar todos los tags <meta ...>
        val metaPattern = Pattern.compile(
            "<meta[^>]+>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val metaMatcher = metaPattern.matcher(html)

        while (metaMatcher.find()) {
            val tag = metaMatcher.group()
            val lowerTag = tag.lowercase(Locale.ROOT)

            // ¿Este meta tiene property/name con el valor que buscamos?
            val hasAttr = lowerTag.contains("""property="$lowerAttrValue"""") ||
                    lowerTag.contains("""property='$lowerAttrValue'""") ||
                    lowerTag.contains("""name="$lowerAttrValue"""") ||
                    lowerTag.contains("""name='$lowerAttrValue'""")

            if (!hasAttr) continue

            // Extraer content="..."
            val contentPattern = Pattern.compile(
                "content\\s*=\\s*([\"'])(.*?)\\1",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val contentMatcher = contentPattern.matcher(tag)

            if (contentMatcher.find()) {
                return contentMatcher.group(2)
            }
        }

        return null
    }




    private fun youtubeThumbnailUrl(url: String): String? {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null

        val videoId = when {
            "youtube.com" in host -> {
                uri.getQueryParameter("v")
            }
            "youtu.be" in host -> {
                uri.lastPathSegment
            }
            else -> null
        } ?: return null

        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    private fun resolveImageUrl(pageUrl: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // //cdn.instagram.com/… → usa el mismo esquema que la página (normalmente https)
        if (trimmed.startsWith("//")) {
            val scheme = Uri.parse(pageUrl).scheme ?: "https"
            return "$scheme:$trimmed"
        }

        // Ya es absoluta
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Ruta relativa → resolver respecto a la URL de la página
        return try {
            val base = URL(pageUrl)
            URL(base, trimmed).toString()
        } catch (_: Exception) {
            null
        }
    }

    // Fallback específico para Amazon: buscamos la primera URL de imagen de producto
    // Fallback específico para Amazon: intentamos encontrar la imagen real de producto
    private fun extractAmazonImageFromHtml(html: String): String? {
        // 1) Intentar primero el campo "hiRes" del JSON embebido
        run {
            val hiResPattern = Pattern.compile(
                "\"hiRes\"\\s*:\\s*\"(https://m\\.media-amazon\\.com/images/[^\"\\\\]+\\.(?:jpg|jpeg|png|webp))\"",
                Pattern.CASE_INSENSITIVE
            )
            val hiResMatcher = hiResPattern.matcher(html)
            if (hiResMatcher.find()) {
                val url = hiResMatcher.group(1)
                val decoded = htmlDecode(url)
                Log.d(URL_PREVIEW_TAG, "Amazon hiRes image: $decoded")
                return decoded
            }
        }

        // 2) Siguiente opción: atributo data-old-hires en la imagen principal
        run {
            val oldHiresPattern = Pattern.compile(
                "data-old-hires\\s*=\\s*\"(https://m\\.media-amazon\\.com/images/[^\"\\\\]+\\.(?:jpg|jpeg|png|webp))\"",
                Pattern.CASE_INSENSITIVE
            )
            val oldHiresMatcher = oldHiresPattern.matcher(html)
            if (oldHiresMatcher.find()) {
                val url = oldHiresMatcher.group(1)
                val decoded = htmlDecode(url)
                Log.d(URL_PREVIEW_TAG, "Amazon data-old-hires image: $decoded")
                return decoded
            }
        }

        // 3) Fallback genérico: primera imagen de producto "I..." evitando sprites/nav
        run {
            val genericPattern = Pattern.compile(
                "https://m\\.media-amazon\\.com/images/I[^\"'\\s>]+\\.(?:jpg|jpeg|png|webp)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = genericPattern.matcher(html)
            while (matcher.find()) {
                val candidate = matcher.group()
                // Filtramos sprites, navbars, etc.
                if (candidate.contains("sprite", ignoreCase = true)) continue
                if (candidate.contains("sprites", ignoreCase = true)) continue
                if (candidate.contains("nav", ignoreCase = true)) continue
                if (candidate.contains("gno", ignoreCase = true)) continue

                val decoded = htmlDecode(candidate)
                Log.d(URL_PREVIEW_TAG, "Amazon generic product image: $decoded")
                return decoded
            }
        }

        // Si no encontramos nada, devolvemos null
        return null
    }



    private fun downloadImage(urlStr: String): Pair<Bitmap, Float>? {
        val url = URL(urlStr)
        val conn = (url.openConnection() as? HttpURLConnection) ?: return null
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.doInput = true
        // 👇 NUEVO: cabeceras tipo navegador también para imágenes
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; SpotItFly) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"
        )
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        conn.connect()

        conn.inputStream.use { input ->
            val bitmap = BitmapFactory.decodeStream(input) ?: return null
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val ratio = width.toFloat() / height.toFloat()
            return bitmap to ratio
        }
    }
}


