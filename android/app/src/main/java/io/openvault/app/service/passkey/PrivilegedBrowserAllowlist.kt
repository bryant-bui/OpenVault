package io.openvault.app.service.passkey

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.CallingAppInfo
import io.openvault.app.R
import io.openvault.app.core.crypto.PasskeyCryptoEngine

/**
 * Resolves the trusted WebAuthn origin for an Android 14+ Credential Manager caller:
 * - For privileged browsers (Chrome, Brave, Edge, Firefox, Samsung Internet, Opera, etc.)
 *   verified against Google's official `res/raw/privileged_browsers.json`, returns the
 *   browser-asserted `https://...` web origin.
 * - For native Android apps, computes `android:apk-key-hash:<base64url(SHA-256(cert))>`.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object PrivilegedBrowserAllowlist {

    @Volatile
    private var cachedAllowlistJson: String? = null

    fun getAllowlistJson(context: Context): String {
        cachedAllowlistJson?.let { return it }
        return synchronized(this) {
            cachedAllowlistJson ?: context.resources
                .openRawResource(R.raw.privileged_browsers)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .also { cachedAllowlistJson = it }
        }
    }

    fun resolveOrigin(context: Context, callingAppInfo: CallingAppInfo): String? {
        // 1. Privileged browser path (returns web origin like "https://github.com")
        val privilegedOrigin = runCatching {
            callingAppInfo.getOrigin(getAllowlistJson(context))
        }.getOrNull()
        if (!privilegedOrigin.isNullOrBlank()) {
            return privilegedOrigin.trimEnd('/')
        }

        // 2. Native Android app path ("android:apk-key-hash:<b64url>")
        val signingInfo = callingAppInfo.signingInfo
        val certBytes = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            signingInfo.signingCertificateHistory?.firstOrNull()?.toByteArray()
                ?: signingInfo.apkContentsSigners?.firstOrNull()?.toByteArray()
        }
        return certBytes?.let { PasskeyCryptoEngine.computeAndroidApkKeyHashOrigin(it) }
    }
}
