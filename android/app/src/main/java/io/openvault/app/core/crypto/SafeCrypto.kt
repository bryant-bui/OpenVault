package io.openvault.app.core.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class SafeDocumentCategory(val wireValue: String) {
    ID_CARD("id_card"),
    PASSPORT("passport"),
    DRIVER_LICENSE("driver_license"),
    MEDICAL("medical"),
    FINANCIAL("financial"),
    PHOTO("photo"),
    OTHER("other");

    companion object {
        fun fromWire(value: String?): SafeDocumentCategory =
            entries.firstOrNull { it.wireValue == value } ?: OTHER
    }
}

@Serializable
data class SafeItemMetadata(
    val v: Int = 1,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val folderName: String? = null,
    val notes: String? = null,
    val fileKeyB64: String,
    val thumbKeyB64: String? = null,
    val originalSize: Int,
    val originalSha256: String,
    val expiresAt: String? = null,
)

data class ProtectedSafePinKey(
    val saltB64: String,
    val kdfConfig: KdfConfig,
    val encryptedSafeKey: String,
)

data class ProtectedSafeRecoveryKey(
    val saltB64: String,
    val iterations: Int,
    val encryptedSafeKey: String,
)

data class GeneratedRecoveryKey(
    val formatted: String,
    val rawBytes: ByteArray,
)

class InvalidOvsfEnvelopeException(message: String) : IllegalArgumentException(message)

enum class OvsfBlobKind {
    BLOB,
    THUMB,
}

object SafeCrypto {

    const val SAFE_MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MiB
    const val SAFE_MAX_THUMB_BYTES = 128 * 1024 // 128 KiB
    const val OVSF_VERSION: Byte = 0x01
    const val OVSF_SUITE_AES256_GCM: Byte = 0x01
    const val OVSF_HEADER_BYTES = 18
    const val OVSF_IV_BYTES = 12
    const val OVSF_TAG_BITS = 128
    const val OVSF_TAG_BYTES = 16
    const val OVSF_OVERHEAD_BYTES = OVSF_HEADER_BYTES + OVSF_TAG_BYTES // 34 bytes

    const val SAFE_PIN_LENGTH = 6
    const val SAFE_COOLDOWN_AFTER_FAILS = 5
    const val SAFE_COOLDOWN_MS = 15 * 60_000L
    const val SAFE_MAX_TOTAL_FAILS = 15
    const val SAFE_DEFAULT_AUTO_LOCK_MINUTES = 3

    const val DEFAULT_RECOVERY_KDF_ITERATIONS = 100_000
    const val SAFE_RECOVERY_KEY_BYTES = 20

    val DEFAULT_SAFE_KDF_CONFIG = KdfConfig(
        kdfType = KdfConfig.KDF_ARGON2ID,
        iterations = 3,
        memoryMiB = 64,
        parallelism = 4,
    )

    private val OVSF_MAGIC = byteArrayOf(0x4F, 0x56, 0x53, 0x46) // "OVSF"
    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val safeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun validateSafePin(pin: String) {
        require(pin.length == SAFE_PIN_LENGTH && pin.all { it in '0'..'9' }) {
            "Mã PIN Vault+ phải gồm đúng 6 chữ số."
        }
    }

    fun generateSafeKey(): SymmetricKey = BitwardenCryptoEngine.generateUserSymmetricKey()

    fun generateFileKey(): ByteArray = BitwardenCryptoEngine.randomBytes(32)

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun encodeCrockfordBase32(bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "Buffer must not be empty" }
        var bits = 0
        var value = 0
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(CROCKFORD_ALPHABET[(value ushr bits) and 0x1F])
            }
        }
        if (bits > 0) {
            out.append(CROCKFORD_ALPHABET[(value shl (5 - bits)) and 0x1F])
        }
        return out.toString()
    }

    fun decodeCrockfordBase32(input: String): ByteArray {
        val cleaned = input.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[\\s-]+"), "")
            .replace('I', '1')
            .replace('L', '1')
            .replace('O', '0')
        require(cleaned.isNotEmpty()) { "Recovery key cannot be empty" }

        var bits = 0
        var value = 0
        val out = ArrayList<Byte>((cleaned.length * 5) / 8)
        for (ch in cleaned) {
            val idx = CROCKFORD_ALPHABET.indexOf(ch)
            require(idx >= 0) { "Invalid recovery key character: $ch" }
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((value ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    fun formatRecoveryKey(rawBytes: ByteArray): String {
        require(rawBytes.size == SAFE_RECOVERY_KEY_BYTES) {
            "Recovery key must be $SAFE_RECOVERY_KEY_BYTES bytes"
        }
        val encoded = encodeCrockfordBase32(rawBytes)
        val groups = encoded.chunked(4)
        return "OVRK-${groups.joinToString("-")}"
    }

    fun generateRecoveryKey(): GeneratedRecoveryKey {
        val raw = BitwardenCryptoEngine.randomBytes(SAFE_RECOVERY_KEY_BYTES)
        return GeneratedRecoveryKey(
            formatted = formatRecoveryKey(raw),
            rawBytes = raw,
        )
    }

    fun parseRecoveryKey(input: String): ByteArray {
        var cleaned = input.trim().uppercase(Locale.ROOT).replace(Regex("[\\s-]+"), "")
        if (cleaned.startsWith("OVRK")) {
            cleaned = cleaned.substring(4)
        }
        require(cleaned.length == 32) {
            "Recovery key phải gồm đúng 32 ký tự (OVRK-XXXX-...)."
        }
        val decoded = decodeCrockfordBase32(cleaned)
        require(decoded.size == SAFE_RECOVERY_KEY_BYTES) {
            "Độ dài Recovery key không hợp lệ."
        }
        return decoded
    }

    private fun derivePinWrappingKey(
        pin: String,
        saltBytes: ByteArray,
        config: KdfConfig,
    ): SymmetricKey {
        validateSafePin(pin)
        require(saltBytes.size == 16) { "Passcode salt must be 16 bytes" }

        val derived32 = when (config.kdfType) {
            KdfConfig.KDF_ARGON2ID -> {
                val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(config.iterations)
                    .withMemoryAsKB((config.memoryMiB ?: 64) * 1024)
                    .withParallelism(config.parallelism ?: 4)
                    .withSalt(saltBytes)
                    .build()
                val gen = Argon2BytesGenerator()
                gen.init(params)
                val pinBytes = pin.toByteArray(StandardCharsets.UTF_8)
                val out = ByteArray(32)
                try {
                    gen.generateBytes(pinBytes, out)
                    out
                } finally {
                    pinBytes.fill(0)
                }
            }

            KdfConfig.KDF_PBKDF2 -> {
                val chars = pin.toCharArray()
                try {
                    val spec = PBEKeySpec(chars, saltBytes, config.iterations, 256)
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).encoded
                } finally {
                    chars.fill('\u0000')
                }
            }

            else -> throw IllegalArgumentException("Unsupported KDF type: ${config.kdfType}")
        }

        return try {
            BitwardenCryptoEngine.stretchMasterKey(derived32)
        } finally {
            derived32.fill(0)
        }
    }

    fun protectSafeKeyWithPin(
        safeKey: SymmetricKey,
        pin: String,
        kdfConfig: KdfConfig = DEFAULT_SAFE_KDF_CONFIG,
        explicitSalt: ByteArray? = null,
        explicitIv: ByteArray? = null,
    ): ProtectedSafePinKey {
        val salt = explicitSalt ?: BitwardenCryptoEngine.randomBytes(16)
        val wrappingKey = derivePinWrappingKey(pin, salt, kdfConfig)
        val rawSafe = safeKey.toCombinedBytes()
        return try {
            val encrypted = BitwardenCryptoEngine.encryptBytes(rawSafe, wrappingKey, explicitIv)
            ProtectedSafePinKey(
                saltB64 = Base64.getEncoder().encodeToString(salt),
                kdfConfig = kdfConfig,
                encryptedSafeKey = encrypted,
            )
        } finally {
            rawSafe.fill(0)
            wrappingKey.wipe()
        }
    }

    fun unprotectSafeKeyWithPin(
        encryptedSafeKey: String,
        pin: String,
        saltB64: String,
        kdfConfig: KdfConfig,
    ): SymmetricKey {
        val salt = Base64.getDecoder().decode(saltB64)
        val wrappingKey = derivePinWrappingKey(pin, salt, kdfConfig)
        return try {
            BitwardenCryptoEngine.unprotectSymmetricKey(encryptedSafeKey, wrappingKey)
        } finally {
            wrappingKey.wipe()
        }
    }

    private fun deriveRecoveryWrappingKey(
        recoveryKeyFormattedOrRaw: Any,
        saltBytes: ByteArray,
        iterations: Int,
    ): SymmetricKey {
        require(saltBytes.size == 16) { "Recovery salt must be 16 bytes" }
        require(iterations >= 1_000) { "Invalid recovery KDF iterations" }
        val rawRecovery = when (recoveryKeyFormattedOrRaw) {
            is String -> parseRecoveryKey(recoveryKeyFormattedOrRaw)
            is ByteArray -> recoveryKeyFormattedOrRaw.copyOf()
            else -> throw IllegalArgumentException("Recovery key must be String or ByteArray")
        }
        require(rawRecovery.size == SAFE_RECOVERY_KEY_BYTES) {
            "Recovery key must be $SAFE_RECOVERY_KEY_BYTES bytes"
        }

        val derived32 = try {
            BitwardenCryptoEngine.pbkdf2HmacSha256Raw(rawRecovery, saltBytes, iterations, 32)
        } finally {
            rawRecovery.fill(0)
        }

        return try {
            BitwardenCryptoEngine.stretchMasterKey(derived32)
        } finally {
            derived32.fill(0)
        }
    }

    fun protectSafeKeyWithRecoveryKey(
        safeKey: SymmetricKey,
        recoveryKeyFormatted: String,
        iterations: Int = DEFAULT_RECOVERY_KDF_ITERATIONS,
        explicitSalt: ByteArray? = null,
        explicitIv: ByteArray? = null,
    ): ProtectedSafeRecoveryKey {
        val salt = explicitSalt ?: BitwardenCryptoEngine.randomBytes(16)
        val wrappingKey = deriveRecoveryWrappingKey(recoveryKeyFormatted, salt, iterations)
        val rawSafe = safeKey.toCombinedBytes()
        return try {
            val encrypted = BitwardenCryptoEngine.encryptBytes(rawSafe, wrappingKey, explicitIv)
            ProtectedSafeRecoveryKey(
                saltB64 = Base64.getEncoder().encodeToString(salt),
                iterations = iterations,
                encryptedSafeKey = encrypted,
            )
        } finally {
            rawSafe.fill(0)
            wrappingKey.wipe()
        }
    }

    fun unprotectSafeKeyWithRecoveryKey(
        encryptedSafeKey: String,
        recoveryKeyFormatted: String,
        saltB64: String,
        iterations: Int,
    ): SymmetricKey {
        val salt = Base64.getDecoder().decode(saltB64)
        val wrappingKey = deriveRecoveryWrappingKey(recoveryKeyFormatted, salt, iterations)
        return try {
            BitwardenCryptoEngine.unprotectSymmetricKey(encryptedSafeKey, wrappingKey)
        } finally {
            wrappingKey.wipe()
        }
    }

    fun encryptSafeMetadata(metadata: SafeItemMetadata, safeKey: SymmetricKey): String {
        val jsonText = safeJson.encodeToString(metadata)
        return BitwardenCryptoEngine.encryptString(jsonText, safeKey)
    }

    fun decryptSafeMetadata(cipherString: String, safeKey: SymmetricKey): SafeItemMetadata {
        val jsonText = BitwardenCryptoEngine.decryptString(cipherString, safeKey)
        return safeJson.decodeFromString<SafeItemMetadata>(jsonText)
    }

    fun buildOvsfAad(itemId: String, kind: OvsfBlobKind = OvsfBlobKind.BLOB): ByteArray {
        val cleanId = itemId.trim()
        require(cleanId.isNotEmpty()) { "itemId is required for OVSF AAD binding" }
        val aadString = if (kind == OvsfBlobKind.THUMB) "OVSF:v1:thumb:$cleanId" else "OVSF:v1:$cleanId"
        return aadString.toByteArray(StandardCharsets.UTF_8)
    }

    fun encryptSafeFileBinary(
        plaintext: ByteArray,
        fileKey: ByteArray,
        itemId: String,
        kind: OvsfBlobKind = OvsfBlobKind.BLOB,
        explicitIv: ByteArray? = null,
    ): ByteArray {
        require(fileKey.size == 32) { "FileKey must be 32 bytes" }
        val maxPlain = if (kind == OvsfBlobKind.THUMB) SAFE_MAX_THUMB_BYTES else SAFE_MAX_FILE_BYTES
        require(plaintext.size <= maxPlain) { "Tệp vượt quá dung lượng tối đa ($maxPlain bytes)." }

        val iv = explicitIv?.also { require(it.size == OVSF_IV_BYTES) { "IV must be 12 bytes" } }
            ?: BitwardenCryptoEngine.randomBytes(OVSF_IV_BYTES)
        val aad = buildOvsfAad(itemId, kind)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(fileKey, "AES"),
            GCMParameterSpec(OVSF_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        val ctAndTag = cipher.doFinal(plaintext)

        val out = ByteArray(OVSF_HEADER_BYTES + ctAndTag.size)
        System.arraycopy(OVSF_MAGIC, 0, out, 0, 4)
        out[4] = OVSF_VERSION
        out[5] = OVSF_SUITE_AES256_GCM
        System.arraycopy(iv, 0, out, 6, OVSF_IV_BYTES)
        System.arraycopy(ctAndTag, 0, out, OVSF_HEADER_BYTES, ctAndTag.size)
        return out
    }

    fun decryptSafeFileBinary(
        envelope: ByteArray,
        fileKey: ByteArray,
        itemId: String,
        kind: OvsfBlobKind = OvsfBlobKind.BLOB,
    ): ByteArray {
        require(fileKey.size == 32) { "FileKey must be 32 bytes" }
        if (envelope.size < OVSF_OVERHEAD_BYTES) {
            throw InvalidOvsfEnvelopeException("OVSF envelope too short: ${envelope.size}")
        }
        for (i in OVSF_MAGIC.indices) {
            if (envelope[i] != OVSF_MAGIC[i]) {
                throw InvalidOvsfEnvelopeException("Invalid OVSF magic header")
            }
        }
        if (envelope[4] != OVSF_VERSION) {
            throw InvalidOvsfEnvelopeException("Unsupported OVSF version: ${envelope[4]}")
        }
        if (envelope[5] != OVSF_SUITE_AES256_GCM) {
            throw InvalidOvsfEnvelopeException("Unsupported OVSF suite: ${envelope[5]}")
        }

        val iv = envelope.copyOfRange(6, OVSF_HEADER_BYTES)
        val ctAndTag = envelope.copyOfRange(OVSF_HEADER_BYTES, envelope.size)
        val aad = buildOvsfAad(itemId, kind)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(fileKey, "AES"),
            GCMParameterSpec(OVSF_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ctAndTag)
        } catch (_: GeneralSecurityException) {
            throw MacVerificationException("OVSF GCM authentication tag verification failed")
        }
    }
}
