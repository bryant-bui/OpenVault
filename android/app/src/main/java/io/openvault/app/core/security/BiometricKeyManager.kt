package io.openvault.app.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.crypto.SymmetricKey
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class BiometricWrappedBlob(
    val ivB64: String,
    val ciphertextB64: String,
)

/**
 * Hardware-backed Android Keystore (StrongBox preferred, TEE fallback) key manager:
 * 1. Biometric-gated AES-256-GCM key for wrapping the 64-byte UserSymmetricKey.
 *    Requires BIOMETRIC_STRONG verification on EVERY use (CryptoObject).
 * 2. Non-auth AES-256-GCM key for encrypting OAuth tokens at rest in SharedPreferences.
 */
object BiometricKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "openvault_biometric_user_key_v1"
    private const val TOKEN_KEY_ALIAS = "openvault_token_wrap_key_v1"
    private const val GCM_TAG_BITS = 128

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateBiometricKey(recreate: Boolean = false): SecretKey {
        val ks = loadKeyStore()
        if (recreate && ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            ks.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
        if (!recreate && ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            return (ks.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey)
        }

        val buildSpec: (Boolean) -> KeyGenParameterSpec = { strongBox ->
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }

            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            builder.build()
        }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            kg.init(buildSpec(true))
            kg.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            kg.init(buildSpec(false))
            kg.generateKey()
        } catch (_: Exception) {
            kg.init(buildSpec(false))
            kg.generateKey()
        }
    }

    fun createBiometricEncryptCipher(): Cipher {
        val key = getOrCreateBiometricKey(recreate = true)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun createBiometricDecryptCipher(blob: BiometricWrappedBlob): Cipher {
        val key = getOrCreateBiometricKey(recreate = false)
        val iv = Base64.getDecoder().decode(blob.ivB64)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }

    fun encryptUserKeyWithAuthenticatedCipher(
        cipher: Cipher,
        userKey: SymmetricKey,
    ): BiometricWrappedBlob {
        val raw = userKey.toCombinedBytes()
        return try {
            val ct = cipher.doFinal(raw)
            val encoder = Base64.getEncoder()
            BiometricWrappedBlob(
                ivB64 = encoder.encodeToString(cipher.iv),
                ciphertextB64 = encoder.encodeToString(ct),
            )
        } finally {
            raw.fill(0)
        }
    }

    fun decryptUserKeyWithAuthenticatedCipher(
        cipher: Cipher,
        blob: BiometricWrappedBlob,
    ): SymmetricKey {
        val ct = Base64.getDecoder().decode(blob.ciphertextB64)
        val raw = cipher.doFinal(ct)
        return try {
            SymmetricKey.fromCombinedBytes(raw)
        } finally {
            raw.fill(0)
        }
    }

    fun deleteBiometricKey() {
        runCatching {
            val ks = loadKeyStore()
            if (ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                ks.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String = "Dùng mã PIN / Mật khẩu",
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher != null) {
                        onSuccess(authedCipher)
                    } else {
                        onError("Không nhận được khóa sinh trắc học từ Android Keystore.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // System prompt displays retry message automatically
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    // --- Non-auth Hardware Keystore encryption for OAuth access/refresh tokens at rest ---

    private fun getOrCreateTokenKey(): SecretKey {
        val ks = loadKeyStore()
        if (ks.containsAlias(TOKEN_KEY_ALIAS)) {
            return ks.getKey(TOKEN_KEY_ALIAS, null) as SecretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            TOKEN_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(spec)
        return kg.generateKey()
    }

    fun sealStringForDisk(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateTokenKey())
        val ct = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(cipher.iv)}.${enc.encodeToString(ct)}"
    }

    fun unsealStringFromDisk(sealed: String?): String? {
        if (sealed.isNullOrBlank()) return null
        val dot = sealed.indexOf('.')
        if (dot <= 0) return null
        return runCatching {
            val dec = Base64.getDecoder()
            val iv = dec.decode(sealed.substring(0, dot))
            val ct = dec.decode(sealed.substring(dot + 1))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateTokenKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
