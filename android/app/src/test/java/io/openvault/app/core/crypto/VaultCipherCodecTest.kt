package io.openvault.app.core.crypto

import io.openvault.app.core.network.CipherDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCipherCodecTest {

    @Test
    fun encryptDecryptLoginWithPasskeysAndTotpRoundTrip() {
        val key = BitwardenCryptoEngine.generateUserSymmetricKey()
        val original = VaultItem(
            id = "cipher-1",
            folderId = "folder-1",
            type = CipherType.LOGIN,
            name = "GitHub Enterprise",
            notes = "Ghi chú tiếng Việt có dấu",
            favorite = true,
            login = VaultLogin(
                uris = listOf(VaultUri("https://github.com"), VaultUri("androidapp://com.github.android")),
                username = "alex@example.com",
                password = "Sup3r#Str0ng!Password",
                totp = "JBSWY3DPEHPK3PXP",
                fido2Credentials = listOf(
                    VaultFido2Credential(
                        credentialId = "cred-abc-123",
                        keyType = "public-key",
                        keyAlgorithm = "ECDSA",
                        keyCurve = "P-256",
                        keyValue = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg",
                        rpId = "github.com",
                        rpName = "GitHub",
                        userHandle = "dXNlci0x",
                        userName = "alex",
                        userDisplayName = "Alex Morgan",
                        counter = "0",
                        discoverable = "true",
                        creationDate = "2026-11-23T00:00:00Z",
                    ),
                ),
            ),
            fields = listOf(VaultField("Recovery Code", "ABCD-1234", type = 1)),
        )

        val encryptedReq = VaultCipherCodec.encryptCipher(original, key)
        // Ensure zero plaintext leaked into encrypted request
        assertTrue(encryptedReq.name.startsWith("2."))
        assertTrue(encryptedReq.login!!.username!!.startsWith("2."))
        assertTrue(encryptedReq.login!!.password!!.startsWith("2."))
        assertTrue(encryptedReq.login!!.totp!!.startsWith("2."))
        assertTrue(encryptedReq.login!!.fido2Credentials!![0].keyValue.startsWith("2."))

        val dto = CipherDto(
            id = original.id,
            folderId = encryptedReq.folderId,
            type = encryptedReq.type,
            name = encryptedReq.name,
            notes = encryptedReq.notes,
            favorite = encryptedReq.favorite,
            reprompt = encryptedReq.reprompt,
            login = encryptedReq.login,
            fields = encryptedReq.fields,
        )

        val decrypted = VaultCipherCodec.decryptCipher(dto, key)
        assertEquals(original.name, decrypted.name)
        assertEquals(original.notes, decrypted.notes)
        assertEquals(original.login?.username, decrypted.login?.username)
        assertEquals(original.login?.password, decrypted.login?.password)
        assertEquals(original.login?.totp, decrypted.login?.totp)
        assertEquals(2, decrypted.login?.uris?.size)
        assertEquals("github.com", decrypted.login?.fido2Credentials?.first()?.rpId)
        assertEquals("Recovery Code", decrypted.fields.first().name)
        assertEquals("ABCD-1234", decrypted.fields.first().value)
    }

    @Test
    fun watchtowerDetectsWeakReusedAndMissing2fa() {
        val items = listOf(
            VaultItem(
                id = "1",
                type = CipherType.LOGIN,
                name = "Weak & Reused A",
                login = VaultLogin(username = "a", password = "password123"),
            ),
            VaultItem(
                id = "2",
                type = CipherType.LOGIN,
                name = "Weak & Reused B",
                login = VaultLogin(username = "b", password = "password123"),
            ),
            VaultItem(
                id = "3",
                type = CipherType.LOGIN,
                name = "Strong With 2FA",
                login = VaultLogin(
                    username = "c",
                    password = PasswordGenerator.generatePassword(PasswordOptions(length = 24)),
                    totp = "JBSWY3DPEHPK3PXP",
                ),
            ),
        )

        val report = WatchtowerAnalyzer.analyze(items)
        assertEquals(3, report.totalLogins)
        assertEquals(2, report.weakItems.size)
        assertEquals(2, report.reusedItems.size)
        assertEquals(2, report.missingTotpItems.size)
        assertTrue(report.score in 0..70)
    }

    @Test
    fun splitSecureNotesRoundTripAndProfileAvatarHiding() {
        val key = BitwardenCryptoEngine.generateUserSymmetricKey()
        val categories = listOf(
            SecureNoteCategory.NOTE,
            SecureNoteCategory.SOFTWARE,
            SecureNoteCategory.PASSPORT,
            SecureNoteCategory.ID_CARD,
        )
        for (cat in categories) {
            val original = VaultItem(
                id = "note-${cat.typeCode}",
                type = CipherType.SECURE_NOTE,
                secureNoteType = cat.typeCode,
                name = "Doc ${cat.labelEn}",
                notes = "Confidential details",
                fields = listOf(VaultField("License", "XXXX-YYYY", type = 1)),
            )
            val req = VaultCipherCodec.encryptCipher(original, key)
            assertEquals(cat.typeCode, req.secureNote?.type)

            val decrypted = VaultCipherCodec.decryptCipher(
                CipherDto(
                    id = original.id,
                    type = req.type,
                    secureNote = req.secureNote,
                    name = req.name,
                    notes = req.notes,
                    fields = req.fields,
                ),
                key,
            )
            assertEquals(cat, decrypted.secureNoteCategory)
            assertEquals(cat.typeCode, decrypted.secureNoteType)
            assertEquals(1, decrypted.visibleFields.size)
        }

        val profileItem = VaultItem(
            id = "internal-profile",
            type = CipherType.SECURE_NOTE,
            name = VaultItem.PROFILE_SYNC_CIPHER_NAME,
            fields = listOf(
                VaultField(VaultItem.ACCOUNT_AVATAR_FIELD_NAME, "data:image/webp;base64,AAA", type = 1),
                VaultField("Public Note", "hello", type = 0),
            ),
        )
        assertTrue(profileItem.isInternalProfileItem)
        assertEquals("data:image/webp;base64,AAA", profileItem.syncedAccountAvatarBase64)
        assertEquals(1, profileItem.visibleFields.size)
        assertEquals("Public Note", profileItem.visibleFields.first().name)
    }
}
