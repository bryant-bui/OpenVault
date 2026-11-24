package io.openvault.app.core.crypto

import io.openvault.app.core.network.CipherDto
import io.openvault.app.core.network.FolderDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VaultImportExportTest {

    @Test
    fun bitwardenPlaintextExportAndImportRoundTripFiltersInternalAndPreservesNoteCategory() {
        val folder = VaultFolder(id = "f-1", name = "Travel")
        val login = VaultItem(
            id = "i-1",
            folderId = "f-1",
            type = CipherType.LOGIN,
            name = "GitHub",
            favorite = true,
            login = VaultLogin(
                uris = listOf(VaultUri("https://github.com")),
                username = "alex@example.com",
                password = "Strong#Password99",
                totp = "JBSWY3DPEHPK3PXP",
            ),
            fields = listOf(
                VaultField(VaultItem.CUSTOM_ICON_FIELD_NAME, "iconB64", type = 1),
                VaultField("Recovery Codes", "0011 2233 4455 6677", type = 1),
            ),
        )
        val passportNote = VaultItem(
            id = "i-2",
            folderId = "f-1",
            type = CipherType.SECURE_NOTE,
            secureNoteType = SecureNoteCategory.PASSPORT.typeCode,
            name = "My Passport",
            notes = "Issue country notes",
        )
        val internalProfile = VaultItem(
            id = "i-internal",
            type = CipherType.SECURE_NOTE,
            name = VaultItem.PROFILE_SYNC_CIPHER_NAME,
            fields = listOf(VaultField(VaultItem.ACCOUNT_AVATAR_FIELD_NAME, "abc", type = 1)),
        )
        val deletedItem = VaultItem(
            id = "i-deleted",
            type = CipherType.LOGIN,
            name = "Old Account",
            deletedDate = "2026-01-01T00:00:00Z",
        )

        val jsonText = VaultImportExport.exportBitwardenPlaintextJson(
            items = listOf(login, passportNote, internalProfile, deletedItem),
            folders = listOf(folder),
        )
        assertFalse(jsonText.contains(VaultItem.PROFILE_SYNC_CIPHER_NAME))
        assertFalse(jsonText.contains(VaultItem.ACCOUNT_AVATAR_FIELD_NAME))
        assertFalse(jsonText.contains("Old Account"))

        val parsed = VaultImportExport.parseUniversalVaultText(jsonText)
        assertEquals("bitwarden-json", parsed.format)
        assertEquals(1, parsed.folders.size)
        assertEquals("Travel", parsed.folders.first().name)
        assertEquals(2, parsed.items.size)

        val importedLogin = parsed.items.first { it.name == "GitHub" }
        assertEquals("alex@example.com", importedLogin.login?.username)
        assertEquals("Strong#Password99", importedLogin.login?.password)
        assertEquals("JBSWY3DPEHPK3PXP", importedLogin.login?.totp)
        assertEquals(1, importedLogin.visibleFields.size)
        assertEquals("Recovery Codes", importedLogin.visibleFields.first().name)
        assertEquals("iconB64", importedLogin.customIconBase64)

        val importedNote = parsed.items.first { it.name == "My Passport" }
        assertEquals(SecureNoteCategory.PASSPORT, importedNote.secureNoteCategory)
    }

    @Test
    fun encryptedBackupExportIncludesMetadataAndEncryptedPayloads() {
        val jsonText = VaultImportExport.exportEncryptedBackupJson(
            email = "alex@example.com",
            kdfConfig = KdfConfig(kdfType = 1, iterations = 3, memoryMiB = 64, parallelism = 4),
            protectedKey = "2.iv|ct|mac",
            encryptedFolders = listOf(FolderDto(id = "f1", name = "2.fold|ct|mac")),
            encryptedCiphers = listOf(CipherDto(id = "c1", type = 1, name = "2.name|ct|mac")),
        )
        val obj = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("openvault-encrypted-export", obj["format"]?.jsonPrimitive?.content)
        assertEquals("alex@example.com", obj["email"]?.jsonPrimitive?.content)
        assertEquals("2.iv|ct|mac", obj["protectedKey"]?.jsonPrimitive?.content)
        assertEquals(1, obj["folders"]?.jsonArray?.size)
        assertEquals(1, obj["ciphers"]?.jsonArray?.size)
    }

    @Test
    fun protonPassJsonAndZipImportHandlesVaultsLoginsPasskeysAndSkipsTrash() {
        val protonJson = """
            {
              "vaults": {
                "v1": {
                  "name": "Work Vault",
                  "items": [
                    {
                      "state": 1,
                      "pinned": true,
                      "data": {
                        "type": "login",
                        "metadata": { "name": "Cloudflare", "note": "Primary admin" },
                        "content": {
                          "itemEmail": "ops@example.com",
                          "itemUsername": "ops-admin",
                          "password": "Pass!Word#2026",
                          "urls": ["https://dash.cloudflare.com"],
                          "totpUri": "otpauth://totp/CF?secret=JBSWY3DPEHPK3PXP",
                          "passkeys": [
                            {
                              "credentialId": "pk-id-1",
                              "content": "MIGHAgEA",
                              "domain": "dash.cloudflare.com",
                              "userName": "ops@example.com"
                            }
                          ]
                        },
                        "extraFields": [
                          { "fieldName": "PIN", "type": "hidden", "data": { "content": "9988" } }
                        ]
                      }
                    },
                    {
                      "state": 2,
                      "data": {
                        "type": "note",
                        "metadata": { "name": "Trashed Item" },
                        "content": {}
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("Proton Pass/data.json"))
            zos.write(protonJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val parsed = VaultImportExport.parseUniversalImport(zipBaos.toByteArray(), "proton_export.zip")
        assertEquals("proton-pass-json", parsed.format)
        assertEquals(1, parsed.folders.size)
        assertEquals("Work Vault", parsed.folders.first().name)
        assertEquals(1, parsed.items.size)
        assertEquals(1, parsed.skippedCount)

        val item = parsed.items.first()
        assertEquals("Cloudflare", item.name)
        assertTrue(item.favorite)
        assertEquals("ops-admin", item.login?.username)
        assertEquals("https://dash.cloudflare.com", item.login?.uris?.firstOrNull()?.uri)
        assertEquals("pk-id-1", item.login?.fido2Credentials?.firstOrNull()?.credentialId)
        assertEquals(2, item.fields.size) // PIN + extra Email field since username differs
    }

    @Test
    fun protonPassCsvImportParsesLoginsAndNotesWithFolders() {
        val csv = """
            name,url,username,password,totp,note,vault,type
            "GitLab","https://gitlab.com","dev@example.com","S3cr3t!Pass","","Repo token","Engineering","login"
            "Passport Backup","","","",,"Serial X99","Docs","note"
        """.trimIndent()

        val parsed = VaultImportExport.parseUniversalImport(csv.toByteArray(Charsets.UTF_8), "proton.csv")
        assertEquals("proton-pass-csv", parsed.format)
        assertEquals(2, parsed.folders.size)
        assertEquals(2, parsed.items.size)

        val login = parsed.items[0]
        assertEquals(CipherType.LOGIN, login.type)
        assertEquals("dev@example.com", login.login?.username)
        assertEquals("https://gitlab.com", login.login?.uris?.firstOrNull()?.uri)

        val note = parsed.items[1]
        assertEquals(CipherType.SECURE_NOTE, note.type)
        assertEquals("Serial X99", note.notes)
    }
}
