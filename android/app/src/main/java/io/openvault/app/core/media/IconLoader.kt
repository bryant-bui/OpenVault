package io.openvault.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class GitHubAuthorInfo(
    val name: String,
    val login: String,
    val bio: String?,
    val avatarUrl: String,
    val profileUrl: String,
    val repoUrl: String,
    val repoFullName: String,
    val primaryLanguage: String?,
    val stars: Int?,
    val licenseName: String,
)

@Serializable
private data class GitHubUserDto(
    val login: String = "",
    val name: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
private data class GitHubLicenseDto(
    @SerialName("spdx_id") val spdxId: String? = null,
    val name: String? = null,
)

@Serializable
private data class GitHubRepoDto(
    @SerialName("full_name") val fullName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    val language: String? = null,
    val license: GitHubLicenseDto? = null,
)

/**
 * Lightweight, privacy-respecting image & website favicon manager:
 * - Automatic website favicons cached on local cacheDir + memory LRU (0 bytes in Cloudflare D1!)
 * - Custom item icons compressed to 96x96 WebP (~3-8 KB) and stored E2EE inside Bitwarden `_ov_icon` field
 * - Account avatar compressed to 160x160 WebP (~6-12 KB)
 */
object IconLoader {

    private val memoryCache = LruCache<String, ImageBitmap>(180)
    private val negativeHosts = mutableSetOf<String>()
    private val githubJson = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedGitHubAuthor: GitHubAuthorInfo? = null

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
     * compresses to WebP (with JPEG fallback), and returns a pure Base64 string safe for E2EE D1 storage.
     */
    suspend fun compressPickedImageToBase64(
        context: Context,
        uri: Uri,
        targetPx: Int = 192,
        quality: Int = 82,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rawDecoded = decodeUriForAvatar(context, uri, targetPx) ?: return@runCatching null
            val decoded = if (rawDecoded.config != Bitmap.Config.ARGB_8888) {
                rawDecoded.copy(Bitmap.Config.ARGB_8888, false).also { rawDecoded.recycle() }
            } else {
                rawDecoded
            }

            // Center-crop square
            val side = min(decoded.width, decoded.height)
            if (side <= 0) {
                decoded.recycle()
                return@runCatching null
            }
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

            val encoded = encodeBoundedBase64(scaled, quality) ?: run {
                val smaller = if (scaled.width > 128) {
                    Bitmap.createScaledBitmap(scaled, 128, 128, true)
                } else {
                    null
                }
                val retry = smaller?.let { bmp ->
                    encodeBoundedBase64(bmp, 72).also {
                        if (bmp !== scaled) bmp.recycle()
                    }
                }
                retry
            }
            scaled.recycle()
            encoded
        }.getOrNull()
    }

    private fun decodeUriForAvatar(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    val minSide = min(info.size.width, info.size.height)
                    if (minSide > targetPx * 3) {
                        val sample = (minSide / (targetPx * 2)).coerceAtLeast(2)
                        decoder.setTargetSampleSize(sample)
                    }
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        // Single-open fallback for streams that reject double openInputStream calls
        val rawBytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (rawBytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sample = 1
        while ((srcW / sample) > targetPx * 3 && (srcH / sample) > targetPx * 3) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
    }

    private fun encodeBoundedBase64(bitmap: Bitmap, initialQuality: Int): String? {
        val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val qualities = listOf(initialQuality, min(initialQuality, 70), 56, 40)
        for (format in listOf(webpFormat, Bitmap.CompressFormat.JPEG)) {
            for (q in qualities) {
                val baos = ByteArrayOutputStream()
                val ok = bitmap.compress(format, q, baos)
                if (!ok) continue
                val compressed = baos.toByteArray()
                if (compressed.isEmpty()) continue
                val b64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
                if (b64.length <= 36_000) return b64
            }
        }
        return null
    }

    suspend fun loadRemoteAvatar(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val cacheKey = "gh_avatar:$url"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val dir = File(context.cacheDir, "ov_avatars").apply { mkdirs() }
        val file = File(dir, "${sha256Short(url)}.img")
        if (file.exists() && file.length() > 64L) {
            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (bmp != null) {
                return@withContext bmp.asImageBitmap().also { memoryCache.put(cacheKey, it) }
            }
        }

        val bytes = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenVault-Android")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val data = resp.body?.bytes() ?: return@runCatching null
                if (data.size < 64 || data.size > 2_000_000) return@runCatching null
                data
            }
        }.getOrNull() ?: return@withContext null

        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return@withContext null
        runCatching { file.writeBytes(bytes) }
        bmp.asImageBitmap().also { memoryCache.put(cacheKey, it) }
    }

    suspend fun fetchGitHubAuthorInfo(): GitHubAuthorInfo? = withContext(Dispatchers.IO) {
        cachedGitHubAuthor?.let { return@withContext it }
        runCatching {
            val userReq = Request.Builder()
                .url("https://api.github.com/users/bryant-bui")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenVault-Android")
                .get()
                .build()
            val userBody = httpClient.newCall(userReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            } ?: return@runCatching null
            val userDto = githubJson.decodeFromString<GitHubUserDto>(userBody)

            val repoDto = runCatching {
                val repoReq = Request.Builder()
                    .url("https://api.github.com/repos/bryant-bui/OpenVault")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OpenVault-Android")
                    .get()
                    .build()
                httpClient.newCall(repoReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()?.let { githubJson.decodeFromString<GitHubRepoDto>(it) }
                }
            }.getOrNull()

            GitHubAuthorInfo(
                name = userDto.name?.takeIf { it.isNotBlank() } ?: "Bryant Bui",
                login = userDto.login.ifBlank { "bryant-bui" },
                bio = userDto.bio?.takeIf { it.isNotBlank() },
                avatarUrl = userDto.avatarUrl.ifBlank { "https://avatars.githubusercontent.com/u/249981187?v=4" },
                profileUrl = userDto.htmlUrl.ifBlank { "https://github.com/bryant-bui" },
                repoUrl = repoDto?.htmlUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/bryant-bui/OpenVault",
                repoFullName = repoDto?.fullName?.takeIf { it.isNotBlank() } ?: "bryant-bui/OpenVault",
                primaryLanguage = repoDto?.language,
                stars = repoDto?.stargazersCount,
                licenseName = repoDto?.license?.spdxId ?: repoDto?.license?.name ?: "MIT",
            ).also { cachedGitHubAuthor = it }
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
