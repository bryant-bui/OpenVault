package io.openvault.app.core.crypto

import kotlinx.serialization.Serializable
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class PinProtectedRecord(
    val saltB64: String,
    val iterations: Int,
    val protectedKey: String,
    val pinLength: Int = 6,
)

/**
 * Zero-Knowledge PIN key wrapping — identical algorithm to packages/crypto-core/src/pin.ts.
 * The raw PIN is NEVER stored; it derives a PBKDF2-SHA256 key (600,000 iter + random 16-byte salt)
 * stretched via raw HKDF-Expand to encrypt the 64-byte UserSymmetricKey as a Type 2 CipherString.
 */
object PinCrypto {

    const val DEFAULT_PIN_ITERATIONS = 600_000
    const val DEFAULT_PIN_MAX_ATTEMPTS = 5

    private fun derivePinStretchedKey(
        pin: String,
        saltBytes: ByteArray,
        iterations: Int,
    ): SymmetricKey {
        require(pin.length in 4..32) { "Mã PIN phải dài từ 4 đến 32 ký tự." }
        val chars = pin.toCharArray()
        val derived = try {
            val spec = PBEKeySpec(chars, saltBytes, iterations, 256)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
        }
        return try {
            BitwardenCryptoEngine.stretchMasterKey(derived)
        } finally {
            derived.fill(0)
        }
    }

    fun protectUserKeyWithPin(
        userKey: SymmetricKey,
        pin: String,
        iterations: Int = DEFAULT_PIN_ITERATIONS,
    ): PinProtectedRecord {
        val salt = BitwardenCryptoEngine.randomBytes(16)
        val stretched = derivePinStretchedKey(pin, salt, iterations)
        return try {
            val protectedKey = BitwardenCryptoEngine.protectSymmetricKey(userKey, stretched)
            PinProtectedRecord(
                saltB64 = Base64.getEncoder().encodeToString(salt),
                iterations = iterations,
                protectedKey = protectedKey,
                pinLength = pin.length,
            )
        } finally {
            stretched.wipe()
        }
    }

    fun unprotectUserKeyWithPin(record: PinProtectedRecord, pin: String): SymmetricKey {
        val salt = Base64.getDecoder().decode(record.saltB64)
        val stretched = derivePinStretchedKey(pin, salt, record.iterations)
        return try {
            BitwardenCryptoEngine.unprotectSymmetricKey(record.protectedKey, stretched)
        } finally {
            stretched.wipe()
        }
    }
}
