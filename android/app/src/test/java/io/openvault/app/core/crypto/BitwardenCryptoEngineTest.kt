package io.openvault.app.core.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

@Serializable
private data class TestVectorsFile(
    val version: Int,
    val pbkdf2: Pbkdf2Vector,
    val argon2id: Argon2idVector,
    val cipherStringType2: CipherStringVector,
    val totpRfc6238: List<TotpVectorGroup>,
)

@Serializable
private data class Pbkdf2Vector(
    val email: String,
    val masterPassword: String,
    val iterations: Int,
    val expectedMasterKeyHex: String,
    val expectedMasterPasswordHashB64: String,
    val expectedLocalMasterPasswordHashB64: String,
    val expectedStretchedEncKeyHex: String,
    val expectedStretchedMacKeyHex: String,
)

@Serializable
private data class Argon2idVector(
    val email: String,
    val masterPassword: String,
    val iterations: Int,
    val memoryMiB: Int,
    val parallelism: Int,
    val expectedSaltHex: String,
    val expectedMasterKeyHex: String,
    val expectedMasterPasswordHashB64: String,
)

@Serializable
private data class CipherStringVector(
    val encKeyHex: String,
    val macKeyHex: String,
    val ivHex: String,
    val plaintext: String,
    val expectedCipherString: String,
)

@Serializable
private data class TotpVectorGroup(
    val secretBase32: String,
    val digits: Int,
    val period: Int,
    val algorithm: String,
    val cases: List<TotpCase>,
)

@Serializable
private data class TotpCase(
    val unixSeconds: Long,
    val expectedCode: String,
)

class BitwardenCryptoEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadVectors(): TestVectorsFile {
        val candidates = listOf(
            File("../../spec/test-vectors.json"),
            File("../spec/test-vectors.json"),
            File("spec/test-vectors.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate spec/test-vectors.json from ${File(".").absolutePath}")
        return json.decodeFromString(file.readText(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun pbkdf2MasterKeyHashAndRawHkdfExpandMatchSpecVectors() {
        val v = loadVectors().pbkdf2
        val masterKey = BitwardenCryptoEngine.deriveMasterKeyPbkdf2(
            masterPassword = v.masterPassword,
            email = v.email,
            iterations = v.iterations,
        )
        assertEquals(v.expectedMasterKeyHex, masterKey.toHex())

        val authHash = BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, v.masterPassword, 1)
        assertEquals(v.expectedMasterPasswordHashB64, authHash)

        val localHash = BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, v.masterPassword, 2)
        assertEquals(v.expectedLocalMasterPasswordHashB64, localHash)

        val stretched = BitwardenCryptoEngine.stretchMasterKey(masterKey)
        assertEquals(v.expectedStretchedEncKeyHex, stretched.encKey.toHex())
        assertEquals(v.expectedStretchedMacKeyHex, stretched.macKey.toHex())
    }

    @Test
    fun argon2idMatchesHashWasmSpecVectors() {
        val v = loadVectors().argon2id
        val salt = BitwardenCryptoEngine.sha256(
            BitwardenCryptoEngine.normalizeEmail(v.email).toByteArray(Charsets.UTF_8),
        )
        assertEquals(v.expectedSaltHex, salt.toHex())

        val masterKey = BitwardenCryptoEngine.deriveMasterKeyArgon2id(
            masterPassword = v.masterPassword,
            email = v.email,
            iterations = v.iterations,
            memoryMiB = v.memoryMiB,
            parallelism = v.parallelism,
        )
        assertEquals(v.expectedMasterKeyHex, masterKey.toHex())

        val authHash = BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, v.masterPassword, 1)
        assertEquals(v.expectedMasterPasswordHashB64, authHash)
    }

    @Test
    fun cipherStringType2EncryptDecryptAndTamperDetection() {
        val v = loadVectors().cipherStringType2
        val key = SymmetricKey(v.encKeyHex.hexToBytes(), v.macKeyHex.hexToBytes())
        val iv = v.ivHex.hexToBytes()

        val encrypted = BitwardenCryptoEngine.encryptString(v.plaintext, key, ivOverride = iv)
        assertEquals(v.expectedCipherString, encrypted)

        val decrypted = BitwardenCryptoEngine.decryptString(encrypted, key)
        assertEquals(v.plaintext, decrypted)

        // Flip a character in the ciphertext and verify MAC check fails BEFORE AES decryption
        val parts = encrypted.substring(2).split('|')
        val ctChars = parts[1].toCharArray()
        ctChars[0] = if (ctChars[0] == 'A') 'B' else 'A'
        val tampered = "2.${parts[0]}|${String(ctChars)}|${parts[2]}"

        try {
            BitwardenCryptoEngine.decryptString(tampered, key)
            fail("Expected MacVerificationException on tampered CipherString")
        } catch (_: MacVerificationException) {
            // Expected
        }
    }

    @Test
    fun totpMatchesAll18Rfc6238AppendixBVectors() {
        val groups = loadVectors().totpRfc6238
        var count = 0
        for (group in groups) {
            val algo = TotpAlgorithm.valueOf(group.algorithm)
            val config = TotpConfig(
                secretBase32 = group.secretBase32,
                algorithm = algo,
                digits = group.digits,
                period = group.period,
            )
            for (tc in group.cases) {
                val res = TotpEngine.generateCode(config, unixSeconds = tc.unixSeconds)
                assertEquals(
                    "Mismatch for ${group.algorithm} at t=${tc.unixSeconds}",
                    tc.expectedCode,
                    res.code,
                )
                count++
            }
        }
        assertEquals(18, count)
    }

    @Test
    fun pinProtectionRoundTripAndWrongPinRejection() {
        val userKey = BitwardenCryptoEngine.generateUserSymmetricKey()
        val record = PinCrypto.protectUserKeyWithPin(userKey, "258469", iterations = 10_000)
        assertEquals(6, record.pinLength)

        val unwrapped = PinCrypto.unprotectUserKeyWithPin(record, "258469")
        assertArrayEquals(userKey.encKey, unwrapped.encKey)
        assertArrayEquals(userKey.macKey, unwrapped.macKey)

        try {
            PinCrypto.unprotectUserKeyWithPin(record, "000000")
            fail("Expected MacVerificationException for wrong PIN")
        } catch (_: MacVerificationException) {
            // Expected
        }
    }

    @Test
    fun totpUriParserAndDomainMatcherWorkCorrectly() {
        val parsed = TotpEngine.parseTotpInput(
            "otpauth://totp/GitHub:alex%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30&algorithm=SHA1",
        )
        assertEquals("GitHub", parsed.issuer)
        assertEquals("alex@example.com", parsed.label)
        assertEquals("JBSWY3DPEHPK3PXP", parsed.secretBase32)
        assertEquals(6, parsed.digits)

        assertEquals(100, DomainMatcher.scoreMatch("https://github.com/login", "github.com", null))
        assertEquals(75, DomainMatcher.scoreMatch("https://gist.github.com", "github.com", null))
        assertEquals(75, DomainMatcher.scoreMatch("https://shop.vnexpress.com.vn", "mail.vnexpress.com.vn", null))
        assertEquals(100, DomainMatcher.scoreMatch("androidapp://com.github.android", null, "com.github.android"))
        assertEquals(70, DomainMatcher.scoreMatch("https://github.com", null, "com.github.android"))
        assertEquals(0, DomainMatcher.scoreMatch("https://evil.com", "github.com", null))

        val pwd = PasswordGenerator.generatePassword(PasswordOptions(length = 24, avoidAmbiguous = true))
        assertEquals(24, pwd.length)
        assertTrue(PasswordGenerator.estimateStrength(pwd).level.score >= 3)
    }
}
