package io.openvault.app.core.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

@Serializable
private data class PersonalSafeVectorsRoot(
    val personalSafe: PersonalSafeVectors,
)

@Serializable
private data class PersonalSafeVectors(
    val safeKeyHex: String,
    val pinWrap: PinWrapVector,
    val recoveryKey: RecoveryKeyVector,
    val ovsfBinary: OvsfBinaryVector,
)

@Serializable
private data class PinWrapVector(
    val pin: String,
    val saltB64: String,
    val ivHex: String,
    val kdfType: Int,
    val kdfIterations: Int,
    val kdfMemoryMiB: Int,
    val kdfParallelism: Int,
    val expectedEncryptedSafeKey: String,
)

@Serializable
private data class RecoveryKeyVector(
    val rawHex: String,
    val expectedFormatted: String,
    val saltB64: String,
    val ivHex: String,
    val iterations: Int,
    val expectedEncryptedSafeKey: String,
)

@Serializable
private data class OvsfBinaryVector(
    val itemId: String,
    val fileKeyHex: String,
    val ivHex: String,
    val plaintextUtf8: String,
    val expectedBlobB64: String,
    val expectedThumbB64: String,
)

class SafeCryptoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadSafeVectors(): PersonalSafeVectors {
        val candidates = listOf(
            File("../../spec/test-vectors.json"),
            File("../spec/test-vectors.json"),
            File("spec/test-vectors.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate spec/test-vectors.json from ${File(".").absolutePath}")
        return json.decodeFromString<PersonalSafeVectorsRoot>(file.readText(Charsets.UTF_8)).personalSafe
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    @Test
    fun pinWrapAndUnwrapMatchesSpecVectors() {
        val v = loadSafeVectors()
        val safeKey = SymmetricKey.fromCombinedBytes(v.safeKeyHex.hexToBytes())
        val config = KdfConfig(
            kdfType = v.pinWrap.kdfType,
            iterations = v.pinWrap.kdfIterations,
            memoryMiB = v.pinWrap.kdfMemoryMiB,
            parallelism = v.pinWrap.kdfParallelism,
        )

        val wrapped = SafeCrypto.protectSafeKeyWithPin(
            safeKey = safeKey,
            pin = v.pinWrap.pin,
            kdfConfig = config,
            explicitSalt = Base64.getDecoder().decode(v.pinWrap.saltB64),
            explicitIv = v.pinWrap.ivHex.hexToBytes(),
        )
        assertEquals(v.pinWrap.expectedEncryptedSafeKey, wrapped.encryptedSafeKey)

        val unwrapped = SafeCrypto.unprotectSafeKeyWithPin(
            encryptedSafeKey = v.pinWrap.expectedEncryptedSafeKey,
            pin = v.pinWrap.pin,
            saltB64 = v.pinWrap.saltB64,
            kdfConfig = config,
        )
        assertEquals(v.safeKeyHex, unwrapped.toCombinedBytes().toHex())

        try {
            SafeCrypto.unprotectSafeKeyWithPin(
                encryptedSafeKey = v.pinWrap.expectedEncryptedSafeKey,
                pin = "000000",
                saltB64 = v.pinWrap.saltB64,
                kdfConfig = config,
            )
            fail("Expected MacVerificationException on wrong PIN")
        } catch (_: MacVerificationException) {
            // Expected
        }
    }

    @Test
    fun recoveryKeyFormatNormalizationAndWrappingMatchesSpecVectors() {
        val v = loadSafeVectors()
        val safeKey = SymmetricKey.fromCombinedBytes(v.safeKeyHex.hexToBytes())
        val raw = v.recoveryKey.rawHex.hexToBytes()

        val formatted = SafeCrypto.formatRecoveryKey(raw)
        assertEquals(v.recoveryKey.expectedFormatted, formatted)

        val messyInput = formatted
            .lowercase()
            .replace("0410", "o4io")
            .replace("6105", "6Lo5")
            .replace("-", " ")
        assertEquals(v.recoveryKey.rawHex, SafeCrypto.parseRecoveryKey(messyInput).toHex())

        val wrapped = SafeCrypto.protectSafeKeyWithRecoveryKey(
            safeKey = safeKey,
            recoveryKeyFormatted = formatted,
            iterations = v.recoveryKey.iterations,
            explicitSalt = Base64.getDecoder().decode(v.recoveryKey.saltB64),
            explicitIv = v.recoveryKey.ivHex.hexToBytes(),
        )
        assertEquals(v.recoveryKey.expectedEncryptedSafeKey, wrapped.encryptedSafeKey)

        val unwrapped = SafeCrypto.unprotectSafeKeyWithRecoveryKey(
            encryptedSafeKey = v.recoveryKey.expectedEncryptedSafeKey,
            recoveryKeyFormatted = messyInput,
            saltB64 = v.recoveryKey.saltB64,
            iterations = v.recoveryKey.iterations,
        )
        assertEquals(v.safeKeyHex, unwrapped.toCombinedBytes().toHex())
    }

    @Test
    fun ovsfBinaryEnvelopeMatchesSpecVectorsAndRejectsTampering() {
        val v = loadSafeVectors().ovsfBinary
        val fileKey = v.fileKeyHex.hexToBytes()
        val iv = v.ivHex.hexToBytes()
        val plain = v.plaintextUtf8.toByteArray(StandardCharsets.UTF_8)

        val blob = SafeCrypto.encryptSafeFileBinary(plain, fileKey, v.itemId, OvsfBlobKind.BLOB, iv)
        assertEquals(v.expectedBlobB64, Base64.getEncoder().encodeToString(blob))

        val thumb = SafeCrypto.encryptSafeFileBinary(plain, fileKey, v.itemId, OvsfBlobKind.THUMB, iv)
        assertEquals(v.expectedThumbB64, Base64.getEncoder().encodeToString(thumb))

        assertArrayEquals(plain, SafeCrypto.decryptSafeFileBinary(blob, fileKey, v.itemId, OvsfBlobKind.BLOB))
        assertArrayEquals(plain, SafeCrypto.decryptSafeFileBinary(thumb, fileKey, v.itemId, OvsfBlobKind.THUMB))

        try {
            SafeCrypto.decryptSafeFileBinary(blob, fileKey, "00000000-0000-0000-0000-000000000000", OvsfBlobKind.BLOB)
            fail("Expected AAD mismatch failure")
        } catch (_: MacVerificationException) {
            // Expected
        }

        val tampered = blob.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()
        try {
            SafeCrypto.decryptSafeFileBinary(tampered, fileKey, v.itemId, OvsfBlobKind.BLOB)
            fail("Expected GCM tag failure")
        } catch (_: MacVerificationException) {
            // Expected
        }
    }

    @Test
    fun metadataAndFiveMibRoundTripWorkCorrectly() {
        val safeKey = SafeCrypto.generateSafeKey()
        val fileKey = SafeCrypto.generateFileKey()
        val itemId = "a1b2c3d4-e5f6-4a1b-9c2d-0123456789ab"
        val bigPayload = ByteArray(SafeCrypto.SAFE_MAX_FILE_BYTES) { (it and 0xFF).toByte() }
        val sha = SafeCrypto.sha256Hex(bigPayload)

        val enc = SafeCrypto.encryptSafeFileBinary(bigPayload, fileKey, itemId, OvsfBlobKind.BLOB)
        assertEquals(SafeCrypto.SAFE_MAX_FILE_BYTES + SafeCrypto.OVSF_OVERHEAD_BYTES, enc.size)
        val dec = SafeCrypto.decryptSafeFileBinary(enc, fileKey, itemId, OvsfBlobKind.BLOB)
        assertEquals(sha, SafeCrypto.sha256Hex(dec))

        val encMeta = SafeCrypto.encryptSafeMetadata(
            SafeItemMetadata(
                title = "Hộ chiếu Việt Nam",
                fileName = "passport.pdf",
                mimeType = "application/pdf",
                category = SafeDocumentCategory.PASSPORT.wireValue,
                notes = "Trang thông tin chính",
                fileKeyB64 = Base64.getEncoder().encodeToString(fileKey),
                originalSize = bigPayload.size,
                originalSha256 = sha,
                expiresAt = "2034-01-15",
            ),
            safeKey,
        )
        val decMeta = SafeCrypto.decryptSafeMetadata(encMeta, safeKey)
        assertEquals("Hộ chiếu Việt Nam", decMeta.title)
        assertEquals(SafeDocumentCategory.PASSPORT.wireValue, decMeta.category)
        assertEquals(sha, decMeta.originalSha256)
    }
}
