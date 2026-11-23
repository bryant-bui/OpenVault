package io.openvault.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Lightweight, privacy-respecting image & website favicon manager:
 * - Automatic website favicons cached on local cacheDir + memory LRU (0 bytes in Cloudflare D1!)
 * - Custom item icons compressed to 96x96 WebP (~3-8 KB) and stored E2EE inside Bitwarden `_ov_icon` field
 * - Account avatar compressed to 160x160 WebP (~6-12 KB)
 */
object IconLoader {

    private val memoryCache = LruCache<String, ImageBitmap>(180)
    private val negativeHosts = mutableSetOf<String>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun decodeBase64Bitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        val key = "b64:${base64.hashCode()}:${base64.length}"
        memoryCache.get(key)?.let { return it }
        return runCatching {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bmp.asImageBitmap().also { memoryCache.put(key, it) }
        }.getOrNull()
    }

    /**
     * Reads a picked gallery URI, center-crops to a crisp square, resizes to [targetPx],
     * compresses to WebP (~3-10 KB), and returns a pure Base64 string safe for E2EE D1 storage.
     */
    suspend fun compressPickedImageToBase64(
        context: Context,
        uri: Uri,
        targetPx: Int = 96,
        quality: Int = 80,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            // Pass 1: measure bounds without loading full bitmap into RAM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return@runCatching null

            var sample = 1
            while ((srcW / sample) > targetPx * 3 && (srcH / sample) > targetPx * 3) {
                sample *= 2
            }

            // Pass 2: decode downsampled bitmap
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@runCatching null

            // Center-crop square
            val side = min(decoded.width, decoded.height)
            val x = (decoded.width - side) / 2
            val y = (decoded.height - side) / 2
            val square = Bitmap.createBitmap(decoded, x, y, side, side)
            if (square !== decoded) decoded.recycle()

            val scaled = if (side != targetPx) {
                Bitmap.createScaledBitmap(square, targetPx, targetPx, true).also {
                    if (it !== square) square.recycle()
                }
            } else {
                square
            }

            val baos = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            scaled.compress(format, quality, baos)
            scaled.recycle()

            val compressed = baos.toByteArray()
            if (compressed.isEmpty() || compressed.size > 32_000) return@runCatching null
            Base64.encodeToString(compressed, Base64.NO_WRAP)
        }.getOrNull()
    }

    fun extractHostForFavicon(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("androidapp://", ignoreCase = true)) return null
        val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = runCatching { Uri.parse(normalized).host?.lowercase()?.trim() }.getOrNull() ?: return null
        if (host.isBlank() || !host.contains('.') || host.startsWith("127.") || host.startsWith("192.168.") || host == "localhost") {
            return null
        }
        return host
    }

    suspend fun loadFavicon(context: Context, host: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "fav:$host"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        if (host in negativeHosts) return@withContext null

        val dir = File(context.cacheDir, "ov_favicons").apply { mkdirs() }
        val file = File(dir, "${sha256Short(host)}.img")

        // 1. Try local disk cache first
        if (file.exists() && file.length() > 64L) {
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (bmp != null && bmp.width >= 16 && bmp.height >= 16) {
                return@withContext bmp.asImageBitmap().also { memoryCache.put(cacheKey, it) }
            }
        }

        // 2. Fetch crisp 128px favicon from Google S2 favicon service
        val url = "https://www.google.com/s2/favicons?domain=${Uri.encode(host)}&sz=128"
        val bytes = runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val data = resp.body?.bytes() ?: return@runCatching null
                if (data.size < 120 || data.size > 128_000) return@runCatching null
                data
            }
        }.getOrNull()

        if (bytes == null) {
            negativeHosts.add(host)
            return@withContext null
        }

        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        // Reject tiny 16x16 generic globe fallback icons so our clean monogram shows instead
        if (bmp == null || bmp.width <= 16 || bmp.height <= 16) {
            negativeHosts.add(host)
            return@withContext null
        }

        runCatching { file.writeBytes(bytes) }
        bmp.asImageBitmap().also { memoryCache.put(cacheKey, it) }
    }

    private fun sha256Short(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun rememberWebsiteFavicon(
    websiteUrl: String?,
    enabled: Boolean,
): ImageBitmap? {
    val context = LocalContext.current
    val host = remember(websiteUrl, enabled) {
        if (!enabled) null else IconLoader.extractHostForFavicon(websiteUrl)
    }
    var bitmap by remember(host) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(host) {
        bitmap = if (host != null) {
            IconLoader.loadFavicon(context, host)
        } else {
            null
        }
    }
    return bitmap
}
