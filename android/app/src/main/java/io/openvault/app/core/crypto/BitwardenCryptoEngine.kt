package io.openvault.app.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MacVerificationException(message: String = "CipherString MAC verification failed") :
    SecurityException(message)

class CipherFormatException(message: String) : IllegalArgumentException(message)

data class KdfConfig(
    val kdfType: Int = KDF_ARGON2ID,
    val iterations: Int = DEFAULT_ARGON2_ITERATIONS,
    val memoryMiB: Int? = DEFAULT_ARGON2_MEMORY_MIB,
    val parallelism: Int? = DEFAULT_ARGON2_PARALLELISM,
) {
    companion object {
        const val KDF_PBKDF2 = 0
        const val KDF_ARGON2ID = 1
        const val DEFAULT_PBKDF2_ITERATIONS = 600_000
        const val DEFAULT_ARGON2_ITERATIONS = 3
        const val DEFAULT_ARGON2_MEMORY_MIB = 64
        const val DEFAULT_ARGON2_PARALLELISM = 4
    }
}

class SymmetricKey(
    val encKey: ByteArray,
    val macKey: ByteArray,
) {
    init {
        require(encKey.size == 32) { "encKey must be 32 bytes" }
        require(macKey.size == 32) { "macKey must be 32 bytes" }
    }

    fun toCombinedBytes(): ByteArray {
        val out = ByteArray(64)
        System.arraycopy(encKey, 0, out, 0, 32)
        System.arraycopy(macKey, 0, out, 32, 32)
        return out
    }

    fun copy(): SymmetricKey = SymmetricKey(encKey.copyOf(), macKey.copyOf())

    fun wipe() {
        encKey.fill(0)
        macKey.fill(0)
    }

    companion object {
        fun fromCombinedBytes(combined: ByteArray): SymmetricKey {
            require(combined.size == 64) { "Symmetric key must be 64 bytes, got ${combined.size}" }
            return SymmetricKey(
                encKey = combined.copyOfRange(0, 32),
                macKey = combined.copyOfRange(32, 64),
            )
        }
    }
}

data class RsaKeyPairEncrypted(
    val publicKeyB64: String,
    val encryptedPrivateKey: String,
)

/**
 * 100% Bitwarden-compatible Zero-Knowledge Cryptography Engine.
 * Verified against spec/test-vectors.json shared with @openvault/crypto-core (TypeScript).
 */
object BitwardenCryptoEngine {

    private val secureRandom = SecureRandom()

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { secureRandom.nextBytes(it) }

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * RFC 2898 PBKDF2-HMAC-SHA256 operating directly on raw password bytes (no charset conversion).
     * Critical for Bitwarden masterPasswordHash where password = raw 32-byte masterKey.
     */
    fun pbkdf2HmacSha256Raw(
        passwordBytes: ByteArray,
        saltBytes: ByteArray,
        iterations: Int,
        dkLen: Int = 32,
    ): ByteArray {
        require(iterations >= 1) { "iterations must be >= 1" }
        require(dkLen >= 1) { "dkLen must be >= 1" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        val hLen = 32
        val blocks = (dkLen + hLen - 1) / hLen
        val out = ByteArray(blocks * hLen)

        for (blockIndex in 1..blocks) {
            val saltBlock = ByteArray(saltBytes.size + 4)
            System.arraycopy(saltBytes, 0, saltBlock, 0, saltBytes.size)
            saltBlock[saltBytes.size] = ((blockIndex ushr 24) and 0xFF).toByte()
            saltBlock[saltBytes.size + 1] = ((blockIndex ushr 16) and 0xFF).toByte()
            saltBlock[saltBytes.size + 2] = ((blockIndex ushr 8) and 0xFF).toByte()
            saltBlock[saltBytes.size + 3] = (blockIndex and 0xFF).toByte()

            var u = mac.doFinal(saltBlock)
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) {
                    t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
            }
            System.arraycopy(t, 0, out, (blockIndex - 1) * hLen, hLen)
        }
        return if (out.size == dkLen) out else out.copyOf(dkLen)
    }

    fun deriveMasterKeyPbkdf2(
        masterPassword: String,
        email: String,
        iterations: Int = KdfConfig.DEFAULT_PBKDF2_ITERATIONS,
    ): ByteArray {
        val salt = normalizeEmail(email).toByteArray(StandardCharsets.UTF_8)
        val chars = masterPassword.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, iterations, 256)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
        }
    }

    fun deriveMasterKeyArgon2id(
        masterPassword: String,
        email: String,
        iterations: Int = KdfConfig.DEFAULT_ARGON2_ITERATIONS,
        memoryMiB: Int = KdfConfig.DEFAULT_ARGON2_MEMORY_MIB,
        parallelism: Int = KdfConfig.DEFAULT_ARGON2_PARALLELISM,
    ): ByteArray {
        val salt = sha256(normalizeEmail(email).toByteArray(StandardCharsets.UTF_8))
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryMiB * 1024)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)
        val passwordBytes = masterPassword.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArray(32)
        return try {
            generator.generateBytes(passwordBytes, output)
            output
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun deriveMasterKey(
        masterPassword: String,
        email: String,
        config: KdfConfig,
    ): ByteArray = when (config.kdfType) {
        KdfConfig.KDF_PBKDF2 -> deriveMasterKeyPbkdf2(masterPassword, email, config.iterations)
        KdfConfig.KDF_ARGON2ID -> deriveMasterKeyArgon2id(
            masterPassword = masterPassword,
            email = email,
            iterations = config.iterations,
            memoryMiB = config.memoryMiB ?: KdfConfig.DEFAULT_ARGON2_MEMORY_MIB,
            parallelism = config.parallelism ?: KdfConfig.DEFAULT_ARGON2_PARALLELISM,
        )
        else -> throw IllegalArgumentException("Unsupported KDF type: ${config.kdfType}")
    }

    /**
     * Bitwarden authentication hash:
     * PBKDF2-HMAC-SHA256(password = masterKey [32 raw bytes], salt = UTF8(masterPassword), iter = 1|2, dkLen = 32)
     */
    fun computeMasterPasswordHash(
        masterKey: ByteArray,
        masterPassword: String,
        iterations: Int = 1,
    ): String {
        require(masterKey.size == 32) { "masterKey must be 32 bytes" }
        val salt = masterPassword.toByteArray(StandardCharsets.UTF_8)
        return try {
            val derived = pbkdf2HmacSha256Raw(masterKey, salt, iterations, 32)
            Base64.getEncoder().encodeToString(derived)
        } finally {
            salt.fill(0)
        }
    }

    /**
     * Bitwarden raw HKDF-Expand (NO HKDF-Extract!):
     * stretchedEncKey = HMAC-SHA256(masterKey, UTF8("enc") || 0x01)
     * stretchedMacKey = HMAC-SHA256(masterKey, UTF8("mac") || 0x01)
     */
    fun stretchMasterKey(masterKey: ByteArray): SymmetricKey {
        require(masterKey.size == 32) { "masterKey must be 32 bytes" }
        val encInfo = byteArrayOf('e'.code.toByte(), 'n'.code.toByte(), 'c'.code.toByte(), 0x01)
        val macInfo = byteArrayOf('m'.code.toByte(), 'a'.code.toByte(), 'c'.code.toByte(), 0x01)
        return SymmetricKey(
            encKey = hmacSha256(masterKey, encInfo),
            macKey = hmacSha256(masterKey, macInfo),
        )
    }

    fun generateUserSymmetricKey(): SymmetricKey =
        SymmetricKey(encKey = randomBytes(32), macKey = randomBytes(32))

    fun encryptBytes(
        plaintext: ByteArray,
        key: SymmetricKey,
        ivOverride: ByteArray? = null,
    ): String {
        val iv = ivOverride?.also { require(it.size == 16) { "IV must be 16 bytes" } }
            ?: randomBytes(16)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.encKey, "AES"), IvParameterSpec(iv))
        val ct = cipher.doFinal(plaintext)

        val macInput = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, macInput, 0, iv.size)
        System.arraycopy(ct, 0, macInput, iv.size, ct.size)
        val mac = hmacSha256(key.macKey, macInput)

        val encoder = Base64.getEncoder()
        return "2.${encoder.encodeToString(iv)}|${encoder.encodeToString(ct)}|${encoder.encodeToString(mac)}"
    }

    fun encryptString(
        plaintext: String,
        key: SymmetricKey,
        ivOverride: ByteArray? = null,
    ): String = encryptBytes(plaintext.toByteArray(StandardCharsets.UTF_8), key, ivOverride)

    /**
     * Encrypt-then-MAC strict decryption:
     * Validates format and verifies HMAC-SHA256 in constant time (MessageDigest.isEqual)
     * BEFORE running AES-256-CBC decrypt.
     */
    fun decryptBytes(cipherString: String, key: SymmetricKey): ByteArray {
        if (!cipherString.startsWith("2.")) {
            throw CipherFormatException("Unsupported CipherString type (expected Type 2)")
        }
        val body = cipherString.substring(2)
        val parts = body.split('|')
        if (parts.size != 3 || parts.any { it.isEmpty() }) {
            throw CipherFormatException("Malformed Type 2 CipherString")
        }

        val decoder = Base64.getDecoder()
        val iv: ByteArray
        val ct: ByteArray
        val mac: ByteArray
        try {
            iv = decoder.decode(parts[0])
            ct = decoder.decode(parts[1])
            mac = decoder.decode(parts[2])
        } catch (e: IllegalArgumentException) {
            throw CipherFormatException("Invalid Base64 in CipherString: ${e.message}")
        }

        if (iv.size != 16 || mac.size != 32 || ct.size < 16 || ct.size % 16 != 0) {
            throw CipherFormatException("Invalid CipherString component lengths")
        }

        val macInput = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, macInput, 0, iv.size)
        System.arraycopy(ct, 0, macInput, iv.size, ct.size)
        val expectedMac = hmacSha256(key.macKey, macInput)

        if (!MessageDigest.isEqual(expectedMac, mac)) {
            throw MacVerificationException()
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.encKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ct)
    }

    fun decryptString(cipherString: String, key: SymmetricKey): String =
        String(decryptBytes(cipherString, key), StandardCharsets.UTF_8)

    fun protectSymmetricKey(userKey: SymmetricKey, stretchedMasterKey: SymmetricKey): String {
        val raw = userKey.toCombinedBytes()
        return try {
            encryptBytes(raw, stretchedMasterKey)
        } finally {
            raw.fill(0)
        }
    }

    fun unprotectSymmetricKey(protectedKey: String, stretchedMasterKey: SymmetricKey): SymmetricKey {
        val raw = decryptBytes(protectedKey, stretchedMasterKey)
        return try {
            SymmetricKey.fromCombinedBytes(raw)
        } finally {
            raw.fill(0)
        }
    }

    fun generateRsa2048KeyPair(userKey: SymmetricKey): RsaKeyPairEncrypted {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val pair = kpg.generateKeyPair()
        val publicB64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        val privateBytes = pair.private.encoded
        val encryptedPrivate = try {
            encryptBytes(privateBytes, userKey)
        } finally {
            privateBytes.fill(0)
        }
        return RsaKeyPairEncrypted(publicKeyB64 = publicB64, encryptedPrivateKey = encryptedPrivate)
    }
}
