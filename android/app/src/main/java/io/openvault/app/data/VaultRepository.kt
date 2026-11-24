package io.openvault.app.data

import android.os.Build
import io.openvault.app.core.crypto.BitwardenCryptoEngine
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.DomainMatcher
import io.openvault.app.core.crypto.ImportVaultSummary
import io.openvault.app.core.crypto.KdfConfig
import io.openvault.app.core.crypto.MacVerificationException
import io.openvault.app.core.crypto.PinCrypto
import io.openvault.app.core.crypto.SecureNoteCategory
import io.openvault.app.core.crypto.SymmetricKey
import io.openvault.app.core.crypto.VaultCipherCodec
import io.openvault.app.core.crypto.VaultFido2Credential
import io.openvault.app.core.crypto.VaultField
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultImportExport
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.VaultLogin
import io.openvault.app.core.crypto.VaultUri
import io.openvault.app.core.crypto.WatchtowerAnalyzer
import io.openvault.app.core.crypto.WatchtowerReport
import io.openvault.app.core.database.EncryptedCipherEntity
import io.openvault.app.core.database.EncryptedFolderEntity
import io.openvault.app.core.database.VaultDao
import io.openvault.app.core.network.BitwardenApiClient
import io.openvault.app.core.network.CipherDto
import io.openvault.app.core.network.FolderDto
import io.openvault.app.core.network.ImportVaultRequest
import io.openvault.app.core.network.R2BackupItemDto
import io.openvault.app.core.network.R2BackupResultDto
import io.openvault.app.core.network.RegisterKeysDto
import io.openvault.app.core.network.RegisterRequest
import io.openvault.app.core.security.AppPreferences
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.core.security.VaultSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Cipher

data class PinUnlockOutcome(
    val success: Boolean,
    val errorMessage: String? = null,
    val remainingAttempts: Int = 0,
    val pinWiped: Boolean = false,
)

class VaultRepository(
    private val api: BitwardenApiClient,
    private val dao: VaultDao,
    val session: VaultSessionManager,
    val preferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val folders: StateFlow<List<VaultFolder>> = combine(
        dao.observeFolders(),
        session.lockState,
    ) { entities, state ->
        val key = session.getActiveKey()
        if (state != VaultLockState.UNLOCKED || key == null) {
            emptyList()
        } else {
            entities.map { entity ->
                VaultCipherCodec.decryptFolder(
                    FolderDto(entity.id, entity.encryptedName, entity.revisionDate),
                    key,
                )
            }.sortedBy { it.name.lowercase() }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<VaultItem>> = combine(
        dao.observeCiphers(),
        session.lockState,
    ) { entities, state ->
        val key = session.getActiveKey()
        if (state != VaultLockState.UNLOCKED || key == null) {
            emptyList()
        } else {
            val allDecrypted = entities.mapNotNull { entity ->
                runCatching {
                    val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                    VaultCipherCodec.decryptCipher(dto, key)
                }.getOrNull()
            }
            val profileItem = allDecrypted.firstOrNull { !it.isDeleted && it.isInternalProfileItem }
            if (profileItem != null) {
                val syncedAvatar = profileItem.syncedAccountAvatarBase64
                if (preferences.settings.value.accountAvatarBase64 != syncedAvatar) {
                    preferences.setAccountAvatarBase64(syncedAvatar)
                }
            }
            allDecrypted
                .filter { !it.isInternalProfileItem }
                .sortedWith(
                    compareByDescending<VaultItem> { it.favorite }
                        .thenBy { it.name.lowercase() },
                )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val watchtower: StateFlow<WatchtowerReport> = items
        .map { WatchtowerAnalyzer.analyze(it) }
        .stateIn(scope, SharingStarted.Eagerly, WatchtowerReport())

    suspend fun login(
        serverUrl: String,
        email: String,
        masterPassword: String,
    ) = withContext(Dispatchers.Default) {
        val cleanEmail = BitwardenCryptoEngine.normalizeEmail(email)
        val pre = api.prelogin(serverUrl, cleanEmail)
        val kdfConfig = KdfConfig(
            kdfType = pre.kdf,
            iterations = pre.kdfIterations,
            memoryMiB = pre.kdfMemory,
            parallelism = pre.kdfParallelism,
        )
        val masterKey = BitwardenCryptoEngine.deriveMasterKey(masterPassword, cleanEmail, kdfConfig)
        val authHash = BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, masterPassword, 1)
        val stretched = BitwardenCryptoEngine.stretchMasterKey(masterKey)
        try {
            val token = api.loginWithPassword(
                serverUrl = serverUrl,
                email = cleanEmail,
                masterPasswordHash = authHash,
                deviceId = preferences.getOrCreateDeviceId(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            )
            val userKey = BitwardenCryptoEngine.unprotectSymmetricKey(token.key, stretched)
            try {
                preferences.saveAccountSession(
                    serverUrl = serverUrl,
                    email = cleanEmail,
                    kdfConfig = KdfConfig(
                        kdfType = token.kdf,
                        iterations = token.kdfIterations,
                        memoryMiB = token.kdfMemory,
                        parallelism = token.kdfParallelism,
                    ),
                    protectedSymmetricKey = token.key,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                )
                session.unlock(userKey)
            } finally {
                userKey.wipe()
            }
        } finally {
            masterKey.fill(0)
            stretched.wipe()
        }
        runCatching { syncNow(force = true) }
    }

    suspend fun register(
        serverUrl: String,
        email: String,
        name: String?,
        masterPassword: String,
        hint: String?,
    ) = withContext(Dispatchers.Default) {
        val cleanEmail = BitwardenCryptoEngine.normalizeEmail(email)
        val kdfConfig = KdfConfig(
            kdfType = KdfConfig.KDF_ARGON2ID,
            iterations = KdfConfig.DEFAULT_ARGON2_ITERATIONS,
            memoryMiB = KdfConfig.DEFAULT_ARGON2_MEMORY_MIB,
            parallelism = KdfConfig.DEFAULT_ARGON2_PARALLELISM,
        )
        val masterKey = BitwardenCryptoEngine.deriveMasterKey(masterPassword, cleanEmail, kdfConfig)
        val authHash = BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, masterPassword, 1)
        val stretched = BitwardenCryptoEngine.stretchMasterKey(masterKey)
        val userKey = BitwardenCryptoEngine.generateUserSymmetricKey()
        try {
            val protectedKey = BitwardenCryptoEngine.protectSymmetricKey(userKey, stretched)
            val rsaKeys = BitwardenCryptoEngine.generateRsa2048KeyPair(userKey)
            val keysDto = RegisterKeysDto(
                publicKey = rsaKeys.publicKeyB64,
                encryptedPrivateKey = rsaKeys.encryptedPrivateKey,
            )
            api.register(
                serverUrl = serverUrl,
                payload = RegisterRequest(
                    email = cleanEmail,
                    name = name?.takeIf { it.isNotBlank() },
                    masterPasswordHash = authHash,
                    masterPasswordHint = hint?.takeIf { it.isNotBlank() },
                    key = protectedKey,
                    userSymmetricKey = protectedKey,
                    kdf = kdfConfig.kdfType,
                    kdfIterations = kdfConfig.iterations,
                    kdfMemory = kdfConfig.memoryMiB,
                    kdfParallelism = kdfConfig.parallelism,
                    keys = keysDto,
                    userAsymmetricKeys = keysDto,
                ),
            )
        } finally {
            masterKey.fill(0)
            stretched.wipe()
            userKey.wipe()
        }
        // Automatically sign in after successful registration
        login(serverUrl, cleanEmail, masterPassword)
    }

    suspend fun unlockWithMasterPassword(masterPassword: String): Boolean =
        withContext(Dispatchers.Default) {
            val meta = preferences.account.value
            if (!meta.isLoggedIn) return@withContext false

            val masterKey = BitwardenCryptoEngine.deriveMasterKey(
                masterPassword = masterPassword,
                email = meta.email,
                config = meta.kdfConfig,
            )
            val stretched = BitwardenCryptoEngine.stretchMasterKey(masterKey)
            try {
                val userKey = BitwardenCryptoEngine.unprotectSymmetricKey(
                    meta.protectedSymmetricKey,
                    stretched,
                )
                try {
                    session.unlock(userKey)
                    preferences.setPinFailedAttempts(0)
                } finally {
                    userKey.wipe()
                }
                true
            } catch (_: MacVerificationException) {
                false
            } finally {
                masterKey.fill(0)
                stretched.wipe()
            }
        }

    suspend fun unlockWithPin(pin: String): PinUnlockOutcome = withContext(Dispatchers.Default) {
        val record = preferences.getPinRecord()
            ?: return@withContext PinUnlockOutcome(false, "Mã PIN chưa được thiết lập.")
        val settings = preferences.settings.value

        try {
            val userKey = PinCrypto.unprotectUserKeyWithPin(record, pin)
            try {
                session.unlock(userKey)
                preferences.setPinFailedAttempts(0)
            } finally {
                userKey.wipe()
            }
            PinUnlockOutcome(success = true)
        } catch (_: MacVerificationException) {
            val nextFailed = settings.pinFailedAttempts + 1
            val remaining = (settings.pinMaxAttempts - nextFailed).coerceAtLeast(0)
            if (remaining <= 0) {
                preferences.clearPinRecord()
                PinUnlockOutcome(
                    success = false,
                    errorMessage = "Nhập sai mã PIN quá giới hạn. Mã PIN đã bị xóa — hãy dùng Mật khẩu chính.",
                    remainingAttempts = 0,
                    pinWiped = true,
                )
            } else {
                preferences.setPinFailedAttempts(nextFailed)
                PinUnlockOutcome(
                    success = false,
                    errorMessage = "Mã PIN không đúng (còn $remaining lần thử).",
                    remainingAttempts = remaining,
                )
            }
        }
    }

    suspend fun enablePin(pin: String) = withContext(Dispatchers.Default) {
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
        val record = PinCrypto.protectUserKeyWithPin(key, pin)
        preferences.savePinRecord(record)
    }

    fun disablePin() {
        preferences.clearPinRecord()
    }

    fun enableBiometricWithCipher(authenticatedCipher: Cipher) {
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
        val blob = BiometricKeyManager.encryptUserKeyWithAuthenticatedCipher(authenticatedCipher, key)
        preferences.saveBiometricBlob(blob)
    }

    fun unlockWithBiometricCipher(authenticatedCipher: Cipher): Boolean {
        val blob = preferences.getBiometricBlob() ?: return false
        return try {
            val userKey = BiometricKeyManager.decryptUserKeyWithAuthenticatedCipher(
                authenticatedCipher,
                blob,
            )
            try {
                session.unlock(userKey)
                preferences.setPinFailedAttempts(0)
            } finally {
                userKey.wipe()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disableBiometric() {
        preferences.clearBiometricBlob()
    }

    suspend fun syncNow(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!preferences.account.value.isLoggedIn) return@withContext false
        if (_isSyncing.value) return@withContext false
        _isSyncing.value = true
        try {
            val remoteRevision = runCatching { api.getRevisionDate() }.getOrNull()
            val localRevision = preferences.account.value.lastRevisionMs
            val hasLocalData = dao.getAllCiphersOnce().isNotEmpty()
            if (!force && remoteRevision != null && remoteRevision == localRevision && hasLocalData) {
                preferences.updateSyncTimestamps(System.currentTimeMillis(), remoteRevision)
                return@withContext true
            }

            val syncResp = api.sync()
            val folderEntities = syncResp.folders.map {
                EncryptedFolderEntity(
                    id = it.id,
                    encryptedName = it.name,
                    revisionDate = it.revisionDate,
                )
            }
            val cipherEntities = syncResp.ciphers.map { it.toEntity() }
            dao.replaceAllSyncData(folderEntities, cipherEntities)
            preferences.updateSyncTimestamps(
                lastSyncMs = System.currentTimeMillis(),
                lastRevisionMs = remoteRevision ?: System.currentTimeMillis(),
            )
            true
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun saveItem(item: VaultItem): VaultItem = withContext(Dispatchers.IO) {
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
        val req = VaultCipherCodec.encryptCipher(item, key)
        val savedDto = if (item.id.isBlank()) {
            api.createCipher(req)
        } else {
            api.updateCipher(item.id, req)
        }
        dao.upsertCipher(savedDto.toEntity())
        VaultCipherCodec.decryptCipher(savedDto, key)
    }

    suspend fun toggleFavorite(item: VaultItem) {
        saveItem(item.copy(favorite = !item.favorite))
    }

    suspend fun softDeleteItem(id: String) = withContext(Dispatchers.IO) {
        api.softDeleteCipher(id)
        syncNow(force = true)
    }

    suspend fun restoreItem(id: String) = withContext(Dispatchers.IO) {
        val restored = api.restoreCipher(id)
        dao.upsertCipher(restored.toEntity())
    }

    suspend fun hardDeleteItem(id: String) = withContext(Dispatchers.IO) {
        api.hardDeleteCipher(id)
        dao.deleteCipherById(id)
    }

    suspend fun createFolder(name: String): VaultFolder = withContext(Dispatchers.IO) {
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
        val encName = VaultCipherCodec.encryptFolderName(name.trim(), key)
        val dto = api.createFolder(encName)
        dao.upsertFolder(EncryptedFolderEntity(dto.id, dto.name, dto.revisionDate))
        VaultCipherCodec.decryptFolder(dto, key)
    }

    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        api.deleteFolder(id)
        dao.deleteFolderById(id)
        syncNow(force = true)
    }

    suspend fun triggerR2Backup(): R2BackupResultDto = api.triggerR2Backup()

    suspend fun listR2Backups(): List<R2BackupItemDto> = api.listR2Backups()

    suspend fun saveAccountAvatar(avatarBase64: String?) = withContext(Dispatchers.IO) {
        val cleaned = avatarBase64?.trim()?.takeIf { it.isNotEmpty() }
        preferences.setAccountAvatarBase64(cleaned)
        val key = session.getActiveKey() ?: return@withContext

        val existingProfile = dao.getAllCiphersOnce().firstNotNullOfOrNull { entity ->
            runCatching {
                val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                VaultCipherCodec.decryptCipher(dto, key)
            }.getOrNull()?.takeIf { !it.isDeleted && it.isInternalProfileItem }
        }

        val otherFields = existingProfile?.fields?.filter {
            it.name != VaultItem.ACCOUNT_AVATAR_FIELD_NAME
        }.orEmpty()
        val profileItem = (existingProfile ?: VaultItem(
            type = CipherType.SECURE_NOTE,
            secureNoteType = SecureNoteCategory.NOTE.typeCode,
            name = VaultItem.PROFILE_SYNC_CIPHER_NAME,
        )).copy(
            name = VaultItem.PROFILE_SYNC_CIPHER_NAME,
            fields = otherFields + VaultField(
                name = VaultItem.ACCOUNT_AVATAR_FIELD_NAME,
                value = cleaned.orEmpty(),
                type = 1,
            ),
        )
        runCatching { saveItem(profileItem) }
    }

    suspend fun exportVaultJson(encrypted: Boolean): String = withContext(Dispatchers.IO) {
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
        val meta = preferences.account.value
        val encFolders = dao.getAllFoldersOnce().map { f ->
            FolderDto(id = f.id, name = f.encryptedName, revisionDate = f.revisionDate)
        }
        val encCiphers = dao.getAllCiphersOnce().mapNotNull { entity ->
            runCatching { json.decodeFromString<CipherDto>(entity.encryptedJson) }.getOrNull()
        }
        if (encrypted) {
            VaultImportExport.exportEncryptedBackupJson(
                email = meta.email,
                kdfConfig = meta.kdfConfig,
                protectedKey = meta.protectedSymmetricKey,
                encryptedFolders = encFolders,
                encryptedCiphers = encCiphers,
            )
        } else {
            val decFolders = encFolders.map { VaultCipherCodec.decryptFolder(it, key) }
            val decItems = encCiphers.map { VaultCipherCodec.decryptCipher(it, key) }
            VaultImportExport.exportBitwardenPlaintextJson(decItems, decFolders)
        }
    }

    suspend fun importVaultBytes(bytes: ByteArray, fileName: String): ImportVaultSummary =
        withContext(Dispatchers.IO) {
            val key = session.getActiveKey() ?: error("Kho đang bị khóa.")
            val parsed = VaultImportExport.parseUniversalImport(bytes, fileName)
            if (parsed.items.isEmpty()) {
                return@withContext ImportVaultSummary(
                    format = parsed.format,
                    importedFolders = 0,
                    importedCiphers = 0,
                    skippedCount = parsed.skippedCount,
                )
            }

            // Reuse existing folder with identical name (case-insensitive) or create it E2EE
            val existingFolders = dao.getAllFoldersOnce().map {
                VaultCipherCodec.decryptFolder(FolderDto(it.id, it.encryptedName, it.revisionDate), key)
            }
            val nameToId = existingFolders.associateBy({ it.name.trim().lowercase() }, { it.id }).toMutableMap()
            val sourceToServerFolder = mutableMapOf<String, String>()
            var createdFolders = 0
            for (folder in parsed.folders) {
                val norm = folder.name.trim().lowercase()
                val resolvedId = nameToId[norm] ?: run {
                    val created = api.createFolder(VaultCipherCodec.encryptFolderName(folder.name, key))
                    createdFolders++
                    nameToId[norm] = created.id
                    created.id
                }
                sourceToServerFolder[folder.sourceId] = resolvedId
            }

            val encryptedRequests = parsed.items.map { item ->
                val mappedFolderId = item.folderId?.let { sourceToServerFolder[it] }
                VaultCipherCodec.encryptCipher(item.copy(folderId = mappedFolderId), key)
            }

            var importedCount = 0
            for (chunk in encryptedRequests.chunked(40)) {
                val resp = api.importVault(ImportVaultRequest(ciphers = chunk))
                importedCount += resp.importedCiphers
            }

            syncNow(force = true)
            ImportVaultSummary(
                format = parsed.format,
                importedFolders = createdFolders,
                importedCiphers = importedCount,
                skippedCount = parsed.skippedCount,
            )
        }

    suspend fun logout(allDevices: Boolean = false) = withContext(Dispatchers.IO) {
        api.logout(preferences.getRefreshToken(), allDevices)
        dao.clearAll()
        preferences.clearSessionForLogout()
        session.markLoggedOut()
    }

    /**
     * Used synchronously/suspending by VaultAutofillService to find best matching credentials.
     */
    suspend fun findAutofillMatches(webDomain: String?, packageName: String?): List<VaultItem> =
        withContext(Dispatchers.Default) {
            val key = session.getActiveKey() ?: return@withContext emptyList()
            val all = dao.getAllCiphersOnce().mapNotNull { entity ->
                runCatching {
                    val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                    VaultCipherCodec.decryptCipher(dto, key)
                }.getOrNull()
            }.filter { !it.isDeleted && !it.isInternalProfileItem && it.login != null }

            all.mapNotNull { item ->
                val best = item.login?.uris.orEmpty().maxOfOrNull { u ->
                    DomainMatcher.scoreMatch(u.uri, webDomain, packageName)
                } ?: 0
                if (best > 0) (item to (best + if (item.favorite) 5 else 0)) else null
            }
                .sortedByDescending { it.second }
                .map { it.first }
        }

    /**
     * Returns all decrypted Passkeys matching the requested RP ID (and optional allowCredentials filter).
     */
    suspend fun findPasskeysForRp(
        rpId: String,
        allowCredentialIds: List<String> = emptyList(),
    ): List<Pair<VaultItem, VaultFido2Credential>> = withContext(Dispatchers.Default) {
        val key = session.getActiveKey() ?: return@withContext emptyList()
        val cleanRp = rpId.trim().lowercase().trimEnd('.')
        val allowSet = allowCredentialIds.takeIf { it.isNotEmpty() }?.toSet()

        val matches = mutableListOf<Pair<VaultItem, VaultFido2Credential>>()
        for (entity in dao.getAllCiphersOnce()) {
            val item = runCatching {
                val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                VaultCipherCodec.decryptCipher(dto, key)
            }.getOrNull() ?: continue
            if (item.isDeleted || item.isInternalProfileItem || item.type != CipherType.LOGIN) continue
            val creds = item.login?.fido2Credentials.orEmpty()
            for (cred in creds) {
                if (cred.rpId.trim().lowercase().trimEnd('.') != cleanRp) continue
                if (allowSet != null && cred.credentialId !in allowSet) continue
                matches += (item to cred)
            }
        }
        matches.sortedWith(
            compareByDescending<Pair<VaultItem, VaultFido2Credential>> { it.first.favorite }
                .thenByDescending { it.second.creationDate },
        )
    }

    suspend fun findPasskeyByIds(
        cipherId: String,
        credentialId: String,
    ): Pair<VaultItem, VaultFido2Credential>? = withContext(Dispatchers.Default) {
        val key = session.getActiveKey() ?: return@withContext null
        for (entity in dao.getAllCiphersOnce()) {
            if (entity.id != cipherId) continue
            val item = runCatching {
                val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                VaultCipherCodec.decryptCipher(dto, key)
            }.getOrNull() ?: return@withContext null
            if (item.isDeleted || item.isInternalProfileItem) return@withContext null
            val cred = item.login?.fido2Credentials?.firstOrNull { it.credentialId == credentialId }
                ?: return@withContext null
            return@withContext (item to cred)
        }
        null
    }

    suspend fun findItemById(cipherId: String): VaultItem? = withContext(Dispatchers.Default) {
        val key = session.getActiveKey() ?: return@withContext null
        val entity = dao.getAllCiphersOnce().firstOrNull { it.id == cipherId } ?: return@withContext null
        runCatching {
            val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
            VaultCipherCodec.decryptCipher(dto, key)
        }.getOrNull()?.takeIf { !it.isDeleted && !it.isInternalProfileItem }
    }

    /**
     * Attaches a newly created Passkey to an existing matching Login item (same domain/RP + username),
     * or creates a new Login item, then encrypts and syncs to Cloudflare D1.
     */
    suspend fun savePasskeyCredential(
        rpId: String,
        rpName: String,
        origin: String,
        packageName: String?,
        userName: String,
        credential: VaultFido2Credential,
    ): VaultItem = withContext(Dispatchers.IO) {
        val cleanRp = rpId.trim().lowercase().trimEnd('.')
        val normalizedUser = userName.trim().lowercase()
        val key = session.getActiveKey() ?: error("Kho đang bị khóa.")

        val allItems = dao.getAllCiphersOnce().mapNotNull { entity ->
            runCatching {
                val dto = json.decodeFromString<CipherDto>(entity.encryptedJson)
                VaultCipherCodec.decryptCipher(dto, key)
            }.getOrNull()
        }.filter { !it.isDeleted && !it.isInternalProfileItem && it.type == CipherType.LOGIN && it.login != null }

        val webDomain = if (origin.startsWith("http", ignoreCase = true)) origin else "https://$cleanRp"
        val siteMatches = allItems.filter { item ->
            val login = item.login ?: return@filter false
            login.uris.any { DomainMatcher.scoreMatch(it.uri, webDomain, packageName) > 0 } ||
                login.fido2Credentials.any { it.rpId.lowercase() == cleanRp }
        }

        fun identifiersOf(item: VaultItem): List<String> = buildList {
            item.login?.username?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::add)
            for (f in item.fields) {
                if (f.name.trim().lowercase() in setOf("email", "e-mail", "username", "user") &&
                    f.value.isNotBlank()
                ) {
                    add(f.value.trim().lowercase())
                }
            }
            item.login?.fido2Credentials?.forEach {
                it.userName?.trim()?.lowercase()?.takeIf { u -> u.isNotEmpty() }?.let(::add)
            }
        }

        val inputLocal = normalizedUser.substringBefore('@')
        val displayLower = credential.userDisplayName?.trim()?.lowercase().orEmpty()
        val existing = siteMatches.firstOrNull { item ->
            normalizedUser.isNotEmpty() && normalizedUser in identifiersOf(item)
        } ?: siteMatches.firstOrNull { item ->
            (inputLocal.isNotEmpty() || displayLower.isNotEmpty()) &&
                identifiersOf(item).any { id ->
                    val idLocal = id.substringBefore('@')
                    (inputLocal.isNotEmpty() && idLocal == inputLocal) ||
                        (displayLower.isNotEmpty() && (id == displayLower || idLocal == displayLower))
                }
        } ?: if (siteMatches.size == 1) siteMatches.first() else null

        val targetItem = if (existing != null && existing.login != null) {
            val filtered = existing.login.fido2Credentials.filterNot {
                it.rpId.lowercase() == cleanRp &&
                    !it.userHandle.isNullOrBlank() &&
                    it.userHandle == credential.userHandle
            }
            existing.copy(
                login = existing.login.copy(
                    username = existing.login.username.ifBlank { userName },
                    fido2Credentials = filtered + credential,
                ),
            )
        } else {
            val uris = buildList {
                add(VaultUri(uri = if (origin.startsWith("http", ignoreCase = true)) origin else "https://$cleanRp"))
                if (!packageName.isNullOrBlank() && origin.startsWith("android:apk-key-hash:", ignoreCase = true)) {
                    add(VaultUri(uri = "androidapp://$packageName"))
                }
            }
            VaultItem(
                type = CipherType.LOGIN,
                name = rpName.ifBlank { cleanRp },
                login = VaultLogin(
                    uris = uris,
                    username = userName,
                    password = "",
                    fido2Credentials = listOf(credential),
                ),
            )
        }

        saveItem(targetItem)
    }

    private fun CipherDto.toEntity(): EncryptedCipherEntity = EncryptedCipherEntity(
        id = id,
        folderId = folderId,
        type = type,
        encryptedJson = json.encodeToString(this),
        favorite = favorite,
        deletedDate = deletedDate,
        revisionDate = revisionDate,
        creationDate = creationDate,
    )
}
