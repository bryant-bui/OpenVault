package io.openvault.app.core.crypto

import io.openvault.app.core.network.CipherDto
import io.openvault.app.core.network.FolderDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

data class ImportedFolderItem(
    val sourceId: String,
    val name: String,
)

data class ParsedVaultImport(
    val format: String,
    val folders: List<ImportedFolderItem>,
    val items: List<VaultItem>,
    val skippedCount: Int,
)

data class ImportVaultSummary(
    val format: String,
    val importedFolders: Int,
    val importedCiphers: Int,
    val skippedCount: Int,
)

object VaultImportExport {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportBitwardenPlaintextJson(
        items: List<VaultItem>,
        folders: List<VaultFolder>,
    ): String {
        val activeItems = items.filter { !it.isDeleted && !it.isInternalProfileItem }
        val root = buildJsonObject {
            put("encrypted", false)
            put("folders", buildJsonArray {
                for (f in folders) {
                    add(buildJsonObject {
                        put("id", f.id)
                        put("name", f.name)
                    })
                }
            })
            put("items", buildJsonArray {
                for (item in activeItems) {
                    add(buildJsonObject {
                        put("id", item.id)
                        if (item.folderId != null) put("folderId", item.folderId) else put("folderId", JsonNull)
                        put("type", item.type.value)
                        put("name", item.name)
                        if (item.notes.isNotBlank()) put("notes", item.notes) else put("notes", JsonNull)
                        put("favorite", item.favorite)
                        put("reprompt", item.reprompt)
                        item.login?.takeIf { item.type == CipherType.LOGIN }?.let { l ->
                            put("login", buildJsonObject {
                                put("uris", buildJsonArray {
                                    for (u in l.uris) {
                                        add(buildJsonObject {
                                            put("uri", u.uri)
                                            if (u.match != null) put("match", u.match) else put("match", JsonNull)
                                        })
                                    }
                                })
                                put("username", l.username.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("password", l.password.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("totp", l.totp?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
                                if (l.fido2Credentials.isNotEmpty()) {
                                    put("fido2Credentials", buildJsonArray {
                                        for (pk in l.fido2Credentials) {
                                            add(buildJsonObject {
                                                put("credentialId", pk.credentialId)
                                                put("keyType", pk.keyType)
                                                put("keyAlgorithm", pk.keyAlgorithm)
                                                put("keyCurve", pk.keyCurve)
                                                put("keyValue", pk.keyValue)
                                                put("rpId", pk.rpId)
                                                put("rpName", pk.rpName?.let { JsonPrimitive(it) } ?: JsonNull)
                                                put("userHandle", pk.userHandle?.let { JsonPrimitive(it) } ?: JsonNull)
                                                put("userName", pk.userName?.let { JsonPrimitive(it) } ?: JsonNull)
                                                put("userDisplayName", pk.userDisplayName?.let { JsonPrimitive(it) } ?: JsonNull)
                                                put("counter", pk.counter)
                                                put("discoverable", pk.discoverable)
                                                put("creationDate", pk.creationDate)
                                            })
                                        }
                                    })
                                }
                            })
                        }
                        if (item.type == CipherType.SECURE_NOTE) {
                            put("secureNote", buildJsonObject { put("type", item.secureNoteType) })
                        }
                        item.card?.takeIf { item.type == CipherType.CARD }?.let { c ->
                            put("card", buildJsonObject {
                                put("cardholderName", c.cardholderName.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("brand", c.brand.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("number", c.number.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("expMonth", c.expMonth.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("expYear", c.expYear.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("code", c.code.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                            })
                        }
                        item.identity?.takeIf { item.type == CipherType.IDENTITY }?.let { id ->
                            put("identity", buildJsonObject {
                                put("title", id.title.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("firstName", id.firstName.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("middleName", id.middleName.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("lastName", id.lastName.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("address1", id.address1.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("address2", id.address2.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("city", id.city.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("state", id.state.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("postalCode", id.postalCode.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("country", id.country.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("company", id.company.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("email", id.email.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("phone", id.phone.ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                            })
                        }
                        val visibleFields = item.fields.filter { it.name != VaultItem.ACCOUNT_AVATAR_FIELD_NAME }
                        if (visibleFields.isNotEmpty()) {
                            put("fields", buildJsonArray {
                                for (f in visibleFields) {
                                    add(buildJsonObject {
                                        put("name", f.name)
                                        put("value", f.value)
                                        put("type", f.type)
                                        if (f.linkedId != null) put("linkedId", f.linkedId) else put("linkedId", JsonNull)
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    fun exportEncryptedBackupJson(
        email: String,
        kdfConfig: KdfConfig,
        protectedKey: String,
        encryptedFolders: List<FolderDto>,
        encryptedCiphers: List<CipherDto>,
    ): String {
        val root = buildJsonObject {
            put("format", "openvault-encrypted-export")
            put("version", 1)
            put("exportedAt", Instant.now().toString())
            put("email", email)
            put("kdf", buildJsonObject {
                put("kdf", kdfConfig.kdfType)
                put("kdfIterations", kdfConfig.iterations)
                if (kdfConfig.memoryMiB != null) put("kdfMemory", kdfConfig.memoryMiB)
                if (kdfConfig.parallelism != null) put("kdfParallelism", kdfConfig.parallelism)
            })
            put("protectedKey", protectedKey)
            put("folders", Json.parseToJsonElement(prettyJson.encodeToString(encryptedFolders)))
            put("ciphers", Json.parseToJsonElement(prettyJson.encodeToString(encryptedCiphers)))
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    fun parseUniversalImport(bytes: ByteArray, fileName: String = ""): ParsedVaultImport {
        val isZip = (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) ||
            fileName.endsWith(".zip", ignoreCase = true)
        val rawText = if (isZip) extractTextFromZip(bytes) else bytes.toString(Charsets.UTF_8)
        return parseUniversalVaultText(rawText)
    }

    private fun extractTextFromZip(zipBytes: ByteArray): String {
        var jsonCandidate: Pair<String, String>? = null
        var csvCandidate: String? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val lower = entry.name.lowercase()
                    if (lower.endsWith("data.json") || lower.endsWith(".json")) {
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        if (jsonCandidate == null || lower.endsWith("data.json")) {
                            jsonCandidate = lower to content
                        }
                    } else if (lower.endsWith(".csv") && csvCandidate == null) {
                        csvCandidate = zis.readBytes().toString(Charsets.UTF_8)
                    }
                }
                entry = zis.nextEntry
            }
        }
        return jsonCandidate?.second
            ?: csvCandidate
            ?: throw IllegalArgumentException("Không tìm thấy tệp data.json hoặc CSV hợp lệ trong file ZIP.")
    }

    fun parseUniversalVaultText(rawText: String): ParsedVaultImport {
        val cleaned = rawText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Tệp import rỗng.")
        }
        if (cleaned.startsWith("{")) {
            val obj = runCatching { prettyJson.parseToJsonElement(cleaned).jsonObject }
                .getOrElse { throw IllegalArgumentException("Tệp JSON không hợp lệ.") }
            if (obj["vaults"] is JsonObject) {
                return parseProtonPassJson(obj)
            }
            if (obj["items"] is JsonArray) {
                return parseBitwardenJson(obj)
            }
            throw IllegalArgumentException("Định dạng JSON không được hỗ trợ (yêu cầu Bitwarden JSON hoặc Proton Pass JSON).")
        }
        return parseCsvImport(cleaned)
    }

    private fun parseBitwardenJson(root: JsonObject): ParsedVaultImport {
        if (root["encrypted"]?.jsonPrimitive?.booleanOrNull == true) {
            throw IllegalArgumentException("Không thể nhập tệp JSON đã mã hóa của tài khoản khác; hãy dùng JSON không mã hóa hoặc Proton Pass.")
        }
        val folders = root["folders"]?.jsonArray.orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val name = obj.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ImportedFolderItem(id, name)
        }
        var skipped = 0
        val items = mutableListOf<VaultItem>()
        for (el in root["items"]?.jsonArray.orEmpty()) {
            val obj = el as? JsonObject ?: continue
            if (!obj.str("deletedDate").isNullOrBlank()) {
                skipped++
                continue
            }
            val name = obj.str("name").orEmpty().ifBlank { "Untitled" }
            if (name == VaultItem.PROFILE_SYNC_CIPHER_NAME) {
                skipped++
                continue
            }
            val type = CipherType.fromValue(obj.int("type") ?: 1)
            val noteType = (obj["secureNote"] as? JsonObject)?.int("type") ?: 0
            val folderId = obj.str("folderId")?.takeIf { it.isNotBlank() }
            val favorite = obj.bool("favorite") ?: false
            val notes = obj.str("notes").orEmpty()
            val fields = (obj["fields"] as? JsonArray).orEmpty().mapNotNull { fEl ->
                val fObj = fEl as? JsonObject ?: return@mapNotNull null
                val fName = fObj.str("name").orEmpty()
                val fVal = fObj.str("value").orEmpty()
                if (fName == VaultItem.ACCOUNT_AVATAR_FIELD_NAME) return@mapNotNull null
                VaultField(
                    name = fName.ifBlank { "Field" },
                    value = fVal,
                    type = fObj.int("type") ?: 0,
                    linkedId = fObj.int("linkedId"),
                )
            }
            val login = (obj["login"] as? JsonObject)?.takeIf { type == CipherType.LOGIN }?.let { lObj ->
                VaultLogin(
                    uris = (lObj["uris"] as? JsonArray).orEmpty().mapNotNull { uEl ->
                        val uriStr = (uEl as? JsonObject)?.str("uri")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        VaultUri(uri = uriStr, match = (uEl as? JsonObject)?.int("match"))
                    },
                    username = lObj.str("username").orEmpty(),
                    password = lObj.str("password").orEmpty(),
                    totp = lObj.str("totp")?.takeIf { it.isNotBlank() },
                    fido2Credentials = (lObj["fido2Credentials"] as? JsonArray).orEmpty().mapNotNull { pkEl ->
                        val pk = pkEl as? JsonObject ?: return@mapNotNull null
                        val credId = pk.str("credentialId") ?: return@mapNotNull null
                        val keyVal = pk.str("keyValue") ?: return@mapNotNull null
                        val rpId = pk.str("rpId") ?: return@mapNotNull null
                        VaultFido2Credential(
                            credentialId = credId,
                            keyType = pk.str("keyType") ?: "public-key",
                            keyAlgorithm = pk.str("keyAlgorithm") ?: "ECDSA",
                            keyCurve = pk.str("keyCurve") ?: "P-256",
                            keyValue = keyVal,
                            rpId = rpId,
                            rpName = pk.str("rpName"),
                            userHandle = pk.str("userHandle"),
                            userName = pk.str("userName"),
                            userDisplayName = pk.str("userDisplayName"),
                            counter = pk.str("counter") ?: "0",
                            discoverable = pk.str("discoverable") ?: "true",
                            creationDate = pk.str("creationDate") ?: Instant.now().toString(),
                        )
                    },
                )
            }
            val card = (obj["card"] as? JsonObject)?.takeIf { type == CipherType.CARD }?.let { cObj ->
                VaultCard(
                    cardholderName = cObj.str("cardholderName").orEmpty(),
                    brand = cObj.str("brand").orEmpty(),
                    number = cObj.str("number").orEmpty(),
                    expMonth = cObj.str("expMonth").orEmpty(),
                    expYear = cObj.str("expYear").orEmpty(),
                    code = cObj.str("code").orEmpty(),
                )
            }
            val identity = (obj["identity"] as? JsonObject)?.takeIf { type == CipherType.IDENTITY }?.let { idObj ->
                VaultIdentity(
                    title = idObj.str("title").orEmpty(),
                    firstName = idObj.str("firstName").orEmpty(),
                    middleName = idObj.str("middleName").orEmpty(),
                    lastName = idObj.str("lastName").orEmpty(),
                    address1 = idObj.str("address1").orEmpty(),
                    address2 = idObj.str("address2").orEmpty(),
                    city = idObj.str("city").orEmpty(),
                    state = idObj.str("state").orEmpty(),
                    postalCode = idObj.str("postalCode").orEmpty(),
                    country = idObj.str("country").orEmpty(),
                    company = idObj.str("company").orEmpty(),
                    email = idObj.str("email").orEmpty(),
                    phone = idObj.str("phone").orEmpty(),
                )
            }
            items.add(
                VaultItem(
                    folderId = folderId,
                    type = type,
                    secureNoteType = noteType,
                    name = name,
                    notes = notes,
                    favorite = favorite,
                    reprompt = obj.int("reprompt") ?: 0,
                    login = login,
                    card = card,
                    identity = identity,
                    fields = fields,
                ),
            )
        }
        return ParsedVaultImport("bitwarden-json", folders, items, skipped)
    }

    private fun parseProtonPassJson(root: JsonObject): ParsedVaultImport {
        val vaultsObj = root["vaults"]?.jsonObject ?: JsonObject(emptyMap())
        val folders = mutableListOf<ImportedFolderItem>()
        val items = mutableListOf<VaultItem>()
        var skipped = 0

        for ((vaultKey, vaultVal) in vaultsObj) {
            val vObj = vaultVal as? JsonObject ?: continue
            val vaultName = vObj.str("name").orEmpty().ifBlank { "Personal" }
            folders.add(ImportedFolderItem(vaultKey, vaultName))

            for (rawEl in (vObj["items"] as? JsonArray).orEmpty()) {
                val pItem = rawEl as? JsonObject ?: continue
                if (pItem.int("state") == 2) {
                    skipped++
                    continue
                }
                val data = pItem["data"] as? JsonObject ?: continue
                val dataType = data.str("type")?.lowercase().orEmpty()
                val meta = data["metadata"] as? JsonObject
                val content = data["content"] as? JsonObject ?: JsonObject(emptyMap())
                val title = meta?.str("name").orEmpty().ifBlank { "Untitled" }
                val note = meta?.str("note").orEmpty()
                val favorite = (pItem.bool("pinned") == true) || (pItem.bool("favorite") == true)

                val extraFields = (data["extraFields"] as? JsonArray).orEmpty().mapNotNull { efEl ->
                    val ef = efEl as? JsonObject ?: return@mapNotNull null
                    val fName = ef.str("fieldName") ?: return@mapNotNull null
                    val efType = if (ef.str("type").equals("hidden", ignoreCase = true)) 1 else 0
                    val efData = ef["data"] as? JsonObject
                    val fVal = efData?.str("content") ?: efData?.str("totpUri") ?: return@mapNotNull null
                    VaultField(name = fName, value = fVal, type = efType)
                }

                when (dataType) {
                    "login", "alias" -> {
                        val email = content.str("itemEmail").orEmpty()
                        val user = content.str("itemUsername").orEmpty().ifBlank { email }
                        val fields = extraFields.toMutableList()
                        if (email.isNotBlank() && email != user) {
                            fields.add(VaultField("Email", email, type = 0))
                        }
                        val uris = (content["urls"] as? JsonArray).orEmpty()
                            .mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                            .map { VaultUri(uri = it) }
                        val passkeys = (content["passkeys"] as? JsonArray).orEmpty().mapNotNull { pkEl ->
                            val pk = pkEl as? JsonObject ?: return@mapNotNull null
                            val credId = pk.str("credentialId") ?: return@mapNotNull null
                            val keyVal = pk.str("content") ?: return@mapNotNull null
                            val rpId = pk.str("domain") ?: pk.str("rpId") ?: return@mapNotNull null
                            VaultFido2Credential(
                                credentialId = credId,
                                keyType = "public-key",
                                keyAlgorithm = "ECDSA",
                                keyCurve = "P-256",
                                keyValue = keyVal,
                                rpId = rpId,
                                rpName = pk.str("rpName") ?: title,
                                userHandle = pk.str("userHandle"),
                                userName = pk.str("userName") ?: user,
                                userDisplayName = pk.str("userDisplayName") ?: pk.str("userName") ?: user,
                                counter = "0",
                                discoverable = "true",
                                creationDate = Instant.now().toString(),
                            )
                        }
                        items.add(
                            VaultItem(
                                folderId = vaultKey,
                                type = CipherType.LOGIN,
                                name = title,
                                notes = note,
                                favorite = favorite,
                                login = VaultLogin(
                                    uris = uris,
                                    username = user,
                                    password = content.str("password").orEmpty(),
                                    totp = content.str("totpUri")?.takeIf { it.isNotBlank() },
                                    fido2Credentials = passkeys,
                                ),
                                fields = fields,
                            ),
                        )
                    }

                    "creditcard", "card" -> {
                        val exp = content.str("expirationDate").orEmpty()
                        val parts = exp.split('/', '-')
                        val expMonth = parts.getOrNull(0)?.trim()?.padStart(2, '0').orEmpty()
                        val rawYear = parts.getOrNull(1)?.trim().orEmpty()
                        val expYear = if (rawYear.length == 2) "20$rawYear" else rawYear
                        val fields = extraFields.toMutableList()
                        content.str("pin")?.takeIf { it.isNotBlank() }?.let {
                            fields.add(VaultField("PIN", it, type = 1))
                        }
                        items.add(
                            VaultItem(
                                folderId = vaultKey,
                                type = CipherType.CARD,
                                name = title,
                                notes = note,
                                favorite = favorite,
                                card = VaultCard(
                                    cardholderName = content.str("cardholderName").orEmpty(),
                                    brand = content.str("cardType").orEmpty(),
                                    number = content.str("number").orEmpty(),
                                    expMonth = expMonth,
                                    expYear = expYear,
                                    code = content.str("verificationNumber").orEmpty(),
                                ),
                                fields = fields,
                            ),
                        )
                    }

                    "identity" -> {
                        val parts = content.str("fullName").orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        items.add(
                            VaultItem(
                                folderId = vaultKey,
                                type = CipherType.IDENTITY,
                                name = title,
                                notes = note,
                                favorite = favorite,
                                identity = VaultIdentity(
                                    firstName = content.str("firstName").orEmpty().ifBlank { parts.firstOrNull().orEmpty() },
                                    middleName = content.str("middleName").orEmpty(),
                                    lastName = content.str("lastName").orEmpty().ifBlank {
                                        if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                                    },
                                    email = content.str("email").orEmpty(),
                                    phone = content.str("phoneNumber").orEmpty(),
                                    address1 = content.str("streetAddress").orEmpty(),
                                    city = content.str("city").orEmpty(),
                                    state = content.str("stateOrProvince").orEmpty(),
                                    postalCode = content.str("zipOrPostalCode").orEmpty(),
                                    country = content.str("countryOrRegion").orEmpty(),
                                    company = content.str("company").orEmpty(),
                                ),
                                fields = extraFields,
                            ),
                        )
                    }

                    else -> {
                        items.add(
                            VaultItem(
                                folderId = vaultKey,
                                type = CipherType.SECURE_NOTE,
                                secureNoteType = SecureNoteCategory.NOTE.typeCode,
                                name = title,
                                notes = note,
                                favorite = favorite,
                                fields = extraFields,
                            ),
                        )
                    }
                }
            }
        }
        return ParsedVaultImport("proton-pass-json", folders, items, skipped)
    }

    private fun parseCsvImport(csvText: String): ParsedVaultImport {
        val rows = parseCsvRows(csvText)
        if (rows.size < 2) {
            throw IllegalArgumentException("Tệp CSV không chứa dòng dữ liệu nào.")
        }
        val headers = rows[0].map { it.trim().lowercase() }
        fun col(row: List<String>, vararg names: String): String {
            for (n in names) {
                val idx = headers.indexOf(n)
                if (idx >= 0 && idx < row.size) {
                    val v = row[idx].trim()
                    if (v.isNotEmpty()) return v
                }
            }
            return ""
        }

        val folderMap = linkedMapOf<String, ImportedFolderItem>()
        val items = mutableListOf<VaultItem>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue
            val rowType = col(row, "type").lowercase()
            val name = col(row, "name", "title").ifBlank { "Untitled" }
            val urlRaw = col(row, "url", "urls", "login_uri", "website")
            val email = col(row, "email")
            val user = col(row, "username", "login_username").ifBlank { email }
            val password = col(row, "password", "login_password")
            val note = col(row, "note", "notes")
            val totp = col(row, "totp", "login_totp")
            val vaultName = col(row, "vault", "folder", "grouping")
            val folderId = if (vaultName.isNotBlank()) {
                folderMap.getOrPut(vaultName) { ImportedFolderItem("csv-f-${folderMap.size}", vaultName) }.sourceId
            } else {
                null
            }

            if (rowType == "note" || (password.isEmpty() && user.isEmpty() && urlRaw.isEmpty() && note.isNotEmpty())) {
                items.add(
                    VaultItem(
                        folderId = folderId,
                        type = CipherType.SECURE_NOTE,
                        secureNoteType = SecureNoteCategory.NOTE.typeCode,
                        name = name,
                        notes = note,
                    ),
                )
            } else {
                val uris = urlRaw.split(Regex("[\\s,]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { VaultUri(uri = it) }
                val fields = if (email.isNotBlank() && email != user) {
                    listOf(VaultField("Email", email, type = 0))
                } else {
                    emptyList()
                }
                items.add(
                    VaultItem(
                        folderId = folderId,
                        type = CipherType.LOGIN,
                        name = name,
                        notes = note,
                        login = VaultLogin(
                            uris = uris,
                            username = user,
                            password = password,
                            totp = totp.takeIf { it.isNotBlank() },
                        ),
                        fields = fields,
                    ),
                )
            }
        }
        return ParsedVaultImport("proton-pass-csv", folderMap.values.toList(), items, 0)
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.clear()
                    }
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }
        return rows
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
