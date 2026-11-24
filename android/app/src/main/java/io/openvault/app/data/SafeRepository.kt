package io.openvault.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.openvault.app.core.crypto.BitwardenCryptoEngine
import io.openvault.app.core.crypto.KdfConfig
import io.openvault.app.core.crypto.MacVerificationException
import io.openvault.app.core.crypto.OvsfBlobKind
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.core.crypto.SafeDocumentCategory
import io.openvault.app.core.crypto.SafeItemMetadata
import io.openvault.app.core.crypto.SymmetricKey
import io.openvault.app.core.database.EncryptedSafeConfigEntity
import io.openvault.app.core.database.EncryptedSafeItemEntity
import io.openvault.app.core.database.SafeDao
import io.openvault.app.core.network.ApiException
import io.openvault.app.core.network.SafeApiClient
import io.openvault.app.core.network.SafeConfigDto
import io.openvault.app.core.network.SafeItemCreateRequest
import io.openvault.app.core.network.SafeItemDto
import io.openvault.app.core.network.SafeItemUpdateRequest
import io.openvault.app.core.network.SafePasscodeUpdateRequest
import io.openvault.app.core.network.SafeSetupRequest
import io.openvault.app.core.security.AppPreferences
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.core.security.SafeLockState
import io.openvault.app.core.security.SafeLockoutLocalState
import io.openvault.app.core.security.SafeSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

data class DecryptedSafeItem(
    val id: String,
    val category: SafeDocumentCategory,
    val metadata: SafeItemMetadata,
    val hasThumbnail: Boolean,
    val blobSizeBytes: Long,
    val thumbSizeBytes: Long,
    val blobSha256: String?,
    val favorite: Boolean,
    val deletedAt: String?,
    val revisionDate: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class SafeStatusSnapshot(
    val initialized: Boolean = false,
    val lockState: SafeLockState = SafeLockState.NOT_INITIALIZED,
    val autoLockMinutes: Int = SafeCrypto.SAFE_DEFAULT_AUTO_LOCK_MINUTES,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long? = null,
    val recoveryRequired: Boolean = false,
    val usedBytes: Long = 0L,
    val itemCount: Int = 0,
    val maxFileBytes: Int = SafeCrypto.SAFE_MAX_FILE_BYTES,
    val biometricEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = true,
    val hasRecoveryConfig: Boolean = false,
)

data class SafePinUnlockOutcome(
    val success: Boolean,
    val errorMessage: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long? = null,
    val recoveryRequired: Boolean = false,
)

class SafeRepository(
    context: Context,
    private val api: SafeApiClient,
    private val safeDao: SafeDao,
    val session: SafeSessionManager,
    val preferences: AppPreferences,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val safeBlobDir = File(appContext.filesDir, "safe_blobs").apply { mkdirs() }
    private val safeTempDir = File(appContext.cacheDir, "safe_tmp").apply { mkdirs() }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        purgeTempFiles()
    }

    val status: StateFlow<SafeStatusSnapshot> = combine(
        safeDao.observeConfig(),
        session.lockState,
        preferences.settings,
    ) { cfg, lockState, settings ->
        val localLockout = preferences.getSafeLockoutState()
        val now = System.currentTimeMillis()
        val failed = if (cfg != null) maxOf(cfg.failedAttempts, localLockout.failedAttempts) else localLockout.failedAttempts
        val lockedUntil = listOfNotNull(cfg?.lockedUntilMs, localLockout.lockedUntilMs)
            .maxOrNull()
            ?.takeIf { it > now }
        val recoveryReq = (cfg?.recoveryRequired == true) || localLockout.recoveryRequired
        val isInitialized = cfg?.initialized == true
        val effectiveLockState = when {
            !isInitialized -> SafeLockState.NOT_INITIALIZED
            lockState == SafeLockState.UNLOCKED && !recoveryReq && lockedUntil == null -> SafeLockState.UNLOCKED
            else -> SafeLockState.LOCKED
        }
        SafeStatusSnapshot(
            initialized = isInitialized,
            lockState = effectiveLockState,
            autoLockMinutes = cfg?.autoLockMinutes ?: preferences.getSafeAutoLockMinutes(),
            failedAttempts = failed,
            lockedUntilMs = lockedUntil,
            recoveryRequired = recoveryReq,
            usedBytes = cfg?.usedBytes ?: 0L,
            itemCount = cfg?.itemCount ?: 0,
            maxFileBytes = cfg?.maxFileBytes ?: SafeCrypto.SAFE_MAX_FILE_BYTES,
            biometricEnabled = settings.safeBiometricEnabled && !recoveryReq && lockedUntil == null,
            flagSecureEnabled = settings.safeFlagSecureEnabled,
            hasRecoveryConfig = !cfg?.recoveryEncryptedSafeKey.isNullOrBlank(),
        )
    }.stateIn(scope, SharingStarted.Eagerly, SafeStatusSnapshot())

    val items: StateFlow<List<DecryptedSafeItem>> = combine(
        safeDao.observeItems(),
        session.lockState,
    ) { entities, lockState ->
        val safeKey = session.getActiveKey()
        if (lockState != SafeLockState.UNLOCKED || safeKey == null) {
            emptyList()
        } else {
            entities.mapNotNull { entity ->
                runCatching {
                    val meta = SafeCrypto.decryptSafeMetadata(entity.encryptedMetadata, safeKey)
                    DecryptedSafeItem(
                        id = entity.id,
                        category = SafeDocumentCategory.fromWire(meta.category),
                        metadata = meta,
                        hasThumbnail = entity.hasThumbnail,
                        blobSizeBytes = entity.blobSizeBytes,
                        thumbSizeBytes = entity.thumbSizeBytes,
                        blobSha256 = entity.blobSha256,
                        favorite = entity.favorite,
                        deletedAt = entity.deletedAt,
                        revisionDate = entity.revisionDate,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                    )
                }.getOrNull()
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<String>> = combine(
        preferences.safeCustomFolders,
        items,
    ) { custom, decryptedItems ->
        val fromItems = decryptedItems
            .asSequence()
            .filter { it.deletedAt == null }
            .mapNotNull { it.metadata.folderName?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .toList()
        (custom + fromItems)
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun syncSafe(): Boolean = withContext(Dispatchers.IO) {
        if (!preferences.account.value.isLoggedIn) return@withContext false
        if (_isSyncing.value) return@withContext false
        _isSyncing.value = true
        try {
            val serverCfg = api.getConfig()
            val cached = safeDao.getConfigOnce()
            val localLockout = preferences.getSafeLockoutState()
            val now = System.currentTimeMillis()

            if (!serverCfg.initialized) {
                clearAllSafeDataLocal()
                session.setConfigured(false, SafeCrypto.SAFE_DEFAULT_AUTO_LOCK_MINUTES)
                return@withContext true
            }

            val serverRecoveredOrReset = cached != null &&
                serverCfg.revisionDate != cached.revisionDate &&
                !serverCfg.recoveryRequired &&
                serverCfg.failedAttempts == 0 &&
                serverCfg.lockedUntilMs == null

            val mergedFailed = if (serverRecoveredOrReset) {
                0
            } else {
                maxOf(serverCfg.failedAttempts, localLockout.failedAttempts)
            }
            val mergedLockedUntil = if (serverRecoveredOrReset) {
                null
            } else {
                listOfNotNull(serverCfg.lockedUntilMs, localLockout.lockedUntilMs)
                    .maxOrNull()
                    ?.takeIf { it > now }
            }
            val mergedRecovery = if (serverRecoveredOrReset) {
                false
            } else {
                serverCfg.recoveryRequired || localLockout.recoveryRequired
            }

            if (mergedRecovery) {
                preferences.clearSafeBiometricBlob()
            }

            preferences.saveSafeLockoutState(
                SafeLockoutLocalState(
                    failedAttempts = mergedFailed,
                    lockedUntilMs = mergedLockedUntil,
                    recoveryRequired = mergedRecovery,
                ),
            )
            preferences.setSafeAutoLockMinutes(serverCfg.autoLockMinutes)
            safeDao.upsertConfig(
                serverCfg.toEntity(
                    mergedFailed = mergedFailed,
                    mergedLockedUntil = mergedLockedUntil,
                    mergedRecovery = mergedRecovery,
                ),
            )
            session.setConfigured(true, serverCfg.autoLockMinutes)

            val remoteItems = api.listItems()
            safeDao.replaceAllSafeItems(remoteItems.map { it.toEntity() })
            pruneOrphanCachedBlobs(remoteItems.map { it.id }.toSet())
            true
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun setupVaultPlus(
        pin: String,
        biometricEncryptCipher: Cipher? = null,
    ): String = withContext(Dispatchers.IO) {
        SafeCrypto.validateSafePin(pin)
        val safeKey = SafeCrypto.generateSafeKey()
        val recovery = SafeCrypto.generateRecoveryKey()
        try {
            val pinWrapped = SafeCrypto.protectSafeKeyWithPin(safeKey, pin)
            val recWrapped = SafeCrypto.protectSafeKeyWithRecoveryKey(safeKey, recovery.formatted)
            val createdDto = api.setup(
                SafeSetupRequest(
                    kdfType = pinWrapped.kdfConfig.kdfType,
                    kdfIterations = pinWrapped.kdfConfig.iterations,
                    kdfMemory = pinWrapped.kdfConfig.memoryMiB,
                    kdfParallelism = pinWrapped.kdfConfig.parallelism,
                    passcodeSalt = pinWrapped.saltB64,
                    passcodeEncryptedSafeKey = pinWrapped.encryptedSafeKey,
                    recoverySalt = recWrapped.saltB64,
                    recoveryKdfIterations = recWrapped.iterations,
                    recoveryEncryptedSafeKey = recWrapped.encryptedSafeKey,
                    autoLockMinutes = SafeCrypto.SAFE_DEFAULT_AUTO_LOCK_MINUTES,
                ),
            )

            if (biometricEncryptCipher != null) {
                val blob = BiometricKeyManager.encryptUserKeyWithAuthenticatedCipher(
                    biometricEncryptCipher,
                    safeKey,
                )
                preferences.saveSafeBiometricBlob(blob)
            }

            preferences.saveSafeLockoutState(SafeLockoutLocalState(0, null, false))
            preferences.setSafeAutoLockMinutes(createdDto.autoLockMinutes)
            safeDao.upsertConfig(createdDto.toEntity(0, null, false))
            session.unlock(safeKey, createdDto.autoLockMinutes)
            recovery.formatted
        } finally {
            safeKey.wipe()
            recovery.rawBytes.fill(0)
        }
    }

    suspend fun unlockWithPin(pin: String): SafePinUnlockOutcome = withContext(Dispatchers.IO) {
        SafeCrypto.validateSafePin(pin)
        var cfg = safeDao.getConfigOnce()
        val local = preferences.getSafeLockoutState()
        val now = System.currentTimeMillis()

        val initialRecovery = (cfg?.recoveryRequired == true) || local.recoveryRequired
        val initialLockedUntil = listOfNotNull(cfg?.lockedUntilMs, local.lockedUntilMs)
            .maxOrNull()
            ?.takeIf { it > now }

        if (initialRecovery) {
            preferences.clearSafeBiometricBlob()
            return@withContext SafePinUnlockOutcome(
                success = false,
                errorMessage = "Đã nhập sai quá 15 lần. Hãy dùng Recovery Key hoặc xóa dữ liệu Vault+.",
                failedAttempts = maxOf(cfg?.failedAttempts ?: 0, local.failedAttempts),
                recoveryRequired = true,
            )
        }
        if (initialLockedUntil != null) {
            return@withContext SafePinUnlockOutcome(
                success = false,
                errorMessage = "Nhập sai quá 5 lần. Vui lòng chờ hết thời gian khóa.",
                failedAttempts = maxOf(cfg?.failedAttempts ?: 0, local.failedAttempts),
                lockedUntilMs = initialLockedUntil,
            )
        }

        if (cfg == null || cfg.passcodeEncryptedSafeKey.isNullOrBlank() || cfg.passcodeSalt.isNullOrBlank()) {
            runCatching { syncSafe() }
            cfg = safeDao.getConfigOnce()
            val refreshedLocal = preferences.getSafeLockoutState()
            if ((cfg?.recoveryRequired == true) || refreshedLocal.recoveryRequired) {
                return@withContext SafePinUnlockOutcome(
                    success = false,
                    errorMessage = "Đã nhập sai quá 15 lần. Hãy dùng Recovery Key hoặc xóa dữ liệu Vault+.",
                    failedAttempts = maxOf(cfg?.failedAttempts ?: 0, refreshedLocal.failedAttempts),
                    recoveryRequired = true,
                )
            }
            val refreshedLockedUntil = listOfNotNull(cfg?.lockedUntilMs, refreshedLocal.lockedUntilMs)
                .maxOrNull()
                ?.takeIf { it > System.currentTimeMillis() }
            if (refreshedLockedUntil != null) {
                return@withContext SafePinUnlockOutcome(
                    success = false,
                    errorMessage = "Nhập sai quá 5 lần. Vui lòng chờ hết thời gian khóa.",
                    failedAttempts = maxOf(cfg?.failedAttempts ?: 0, refreshedLocal.failedAttempts),
                    lockedUntilMs = refreshedLockedUntil,
                )
            }
        }

        val activeCfg = cfg
            ?: return@withContext SafePinUnlockOutcome(false, "Chưa thể tải cấu hình Vault+. Kiểm tra kết nối mạng.")
        val encryptedSafeKey = activeCfg.passcodeEncryptedSafeKey
        val saltB64 = activeCfg.passcodeSalt
        if (encryptedSafeKey.isNullOrBlank() || saltB64.isNullOrBlank()) {
            return@withContext SafePinUnlockOutcome(false, "Vault+ hiện đang bị khóa tạm thời.")
        }

        val kdfConfig = KdfConfig(
            kdfType = activeCfg.kdfType,
            iterations = activeCfg.kdfIterations,
            memoryMiB = activeCfg.kdfMemory,
            parallelism = activeCfg.kdfParallelism,
        )

        val unwrappedKey: SymmetricKey
        try {
            unwrappedKey = SafeCrypto.unprotectSafeKeyWithPin(encryptedSafeKey, pin, saltB64, kdfConfig)
        } catch (_: MacVerificationException) {
            val baseFails = maxOf(activeCfg.failedAttempts, preferences.getSafeLockoutState().failedAttempts)
            var nextFailed = baseFails + 1
            var nextRecovery = nextFailed >= SafeCrypto.SAFE_MAX_TOTAL_FAILS
            var nextLockedUntil: Long? =
                if (!nextRecovery && nextFailed % SafeCrypto.SAFE_COOLDOWN_AFTER_FAILS == 0) {
                    System.currentTimeMillis() + SafeCrypto.SAFE_COOLDOWN_MS
                } else {
                    null
                }

            val remoteOutcome = runCatching { api.reportUnlockAttempt(false) }.getOrNull()
            if (remoteOutcome != null) {
                nextFailed = maxOf(nextFailed, remoteOutcome.failedAttempts)
                nextRecovery = nextRecovery || remoteOutcome.recoveryRequired
                nextLockedUntil = listOfNotNull(nextLockedUntil, remoteOutcome.lockedUntilMs).maxOrNull()
            } else {
                // Ignore if offline or transient error
            }

            if (nextRecovery) {
                nextLockedUntil = null
                preferences.clearSafeBiometricBlob()
            }

            preferences.saveSafeLockoutState(
                SafeLockoutLocalState(nextFailed, nextLockedUntil, nextRecovery),
            )
            safeDao.upsertConfig(
                activeCfg.copy(
                    failedAttempts = nextFailed,
                    lockedUntilMs = nextLockedUntil,
                    recoveryRequired = nextRecovery,
                ),
            )

            val msg = when {
                nextRecovery -> "Đã nhập sai 15 lần. Bắt buộc dùng Recovery Key hoặc xóa Vault+."
                nextLockedUntil != null -> "Nhập sai 5 lần. Đã tạm khóa 15 phút."
                else -> "Mã PIN Vault+ không đúng ($nextFailed/${SafeCrypto.SAFE_MAX_TOTAL_FAILS})."
            }
            return@withContext SafePinUnlockOutcome(
                success = false,
                errorMessage = msg,
                failedAttempts = nextFailed,
                lockedUntilMs = nextLockedUntil,
                recoveryRequired = nextRecovery,
            )
        }

        try {
            // Confirm with server that PIN unlock is permitted (guards against stale local cache)
            val remoteCheck = runCatching { api.reportUnlockAttempt(true) }
            val err = remoteCheck.exceptionOrNull()
            if (err is ApiException && (err.statusCode == 423 || err.statusCode == 429)) {
                runCatching { syncSafe() }
                val updated = status.value
                return@withContext SafePinUnlockOutcome(
                    success = false,
                    errorMessage = err.message,
                    failedAttempts = updated.failedAttempts,
                    lockedUntilMs = updated.lockedUntilMs,
                    recoveryRequired = updated.recoveryRequired,
                )
            }

            preferences.saveSafeLockoutState(SafeLockoutLocalState(0, null, false))
            safeDao.upsertConfig(
                activeCfg.copy(failedAttempts = 0, lockedUntilMs = null, recoveryRequired = false),
            )
            session.unlock(unwrappedKey, activeCfg.autoLockMinutes)
            runCatching {
                val remoteItems = api.listItems()
                safeDao.replaceAllSafeItems(remoteItems.map { it.toEntity() })
            }
            SafePinUnlockOutcome(success = true)
        } finally {
            unwrappedKey.wipe()
        }
    }

    suspend fun unlockWithBiometricCipher(authenticatedCipher: Cipher): Boolean =
        withContext(Dispatchers.IO) {
            val currentStatus = status.value
            if (currentStatus.recoveryRequired || (currentStatus.lockedUntilMs ?: 0L) > System.currentTimeMillis()) {
                if (currentStatus.recoveryRequired) preferences.clearSafeBiometricBlob()
                return@withContext false
            }
            val blob = preferences.getSafeBiometricBlob() ?: return@withContext false
            val safeKey = runCatching {
                BiometricKeyManager.decryptUserKeyWithAuthenticatedCipher(authenticatedCipher, blob)
            }.getOrNull() ?: return@withContext false

            try {
                val remoteCheck = runCatching { api.reportUnlockAttempt(true) }
                val err = remoteCheck.exceptionOrNull()
                if (err is ApiException && (err.statusCode == 423 || err.statusCode == 429)) {
                    runCatching { syncSafe() }
                    return@withContext false
                }
                preferences.saveSafeLockoutState(SafeLockoutLocalState(0, null, false))
                val cfg = safeDao.getConfigOnce()
                if (cfg != null) {
                    safeDao.upsertConfig(cfg.copy(failedAttempts = 0, lockedUntilMs = null, recoveryRequired = false))
                }
                session.unlock(safeKey, cfg?.autoLockMinutes ?: preferences.getSafeAutoLockMinutes())
                runCatching {
                    val remoteItems = api.listItems()
                    safeDao.replaceAllSafeItems(remoteItems.map { it.toEntity() })
                }
                true
            } finally {
                safeKey.wipe()
            }
        }

    fun enableBiometricWithCipher(authenticatedCipher: Cipher) {
        val safeKey = session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        val blob = BiometricKeyManager.encryptUserKeyWithAuthenticatedCipher(authenticatedCipher, safeKey)
        preferences.saveSafeBiometricBlob(blob)
    }

    fun disableBiometric() {
        preferences.clearSafeBiometricBlob()
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        preferences.setSafeFlagSecureEnabled(enabled)
    }

    suspend fun changePin(currentPin: String, newPin: String) = withContext(Dispatchers.IO) {
        SafeCrypto.validateSafePin(currentPin)
        SafeCrypto.validateSafePin(newPin)

        // Verify current PIN locally via unlock path so lockout policy cannot be bypassed
        val check = unlockWithPin(currentPin)
        if (!check.success) {
            error(check.errorMessage ?: "Mã PIN hiện tại không chính xác.")
        }

        val safeKey = session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        val wrapped = SafeCrypto.protectSafeKeyWithPin(safeKey, newPin)
        val updated = api.updatePasscode(
            SafePasscodeUpdateRequest(
                mode = "change",
                kdfType = wrapped.kdfConfig.kdfType,
                kdfIterations = wrapped.kdfConfig.iterations,
                kdfMemory = wrapped.kdfConfig.memoryMiB,
                kdfParallelism = wrapped.kdfConfig.parallelism,
                passcodeSalt = wrapped.saltB64,
                passcodeEncryptedSafeKey = wrapped.encryptedSafeKey,
            ),
        )
        safeDao.upsertConfig(updated.toEntity(0, null, false))
        preferences.saveSafeLockoutState(SafeLockoutLocalState(0, null, false))
    }

    suspend fun recoverWithRecoveryKey(
        recoveryKeyInput: String,
        newPin: String,
        rotateRecoveryKey: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        SafeCrypto.validateSafePin(newPin)
        var cfg = safeDao.getConfigOnce()
        if (cfg == null || cfg.recoveryEncryptedSafeKey.isNullOrBlank() || cfg.recoverySalt.isNullOrBlank()) {
            syncSafe()
            cfg = safeDao.getConfigOnce()
        }
        val activeCfg = cfg ?: error("Không tìm thấy cấu hình Vault+ trên máy chủ.")
        val recEnc = activeCfg.recoveryEncryptedSafeKey ?: error("Thiếu khóa khôi phục.")
        val recSalt = activeCfg.recoverySalt ?: error("Thiếu salt khôi phục.")

        val safeKey = try {
            SafeCrypto.unprotectSafeKeyWithRecoveryKey(
                encryptedSafeKey = recEnc,
                recoveryKeyFormatted = recoveryKeyInput,
                saltB64 = recSalt,
                iterations = activeCfg.recoveryKdfIterations,
            )
        } catch (_: MacVerificationException) {
            error("Mã Recovery Key không chính xác.")
        }

        val newRecovery = if (rotateRecoveryKey) SafeCrypto.generateRecoveryKey() else null
        try {
            val pinWrapped = SafeCrypto.protectSafeKeyWithPin(safeKey, newPin)
            val recWrapped = newRecovery?.let {
                SafeCrypto.protectSafeKeyWithRecoveryKey(safeKey, it.formatted)
            }

            val updated = api.updatePasscode(
                SafePasscodeUpdateRequest(
                    mode = "recovery",
                    kdfType = pinWrapped.kdfConfig.kdfType,
                    kdfIterations = pinWrapped.kdfConfig.iterations,
                    kdfMemory = pinWrapped.kdfConfig.memoryMiB,
                    kdfParallelism = pinWrapped.kdfConfig.parallelism,
                    passcodeSalt = pinWrapped.saltB64,
                    passcodeEncryptedSafeKey = pinWrapped.encryptedSafeKey,
                    recoverySalt = recWrapped?.saltB64,
                    recoveryKdfIterations = recWrapped?.iterations,
                    recoveryEncryptedSafeKey = recWrapped?.encryptedSafeKey,
                ),
            )

            preferences.clearSafeBiometricBlob()
            preferences.saveSafeLockoutState(SafeLockoutLocalState(0, null, false))
            safeDao.upsertConfig(updated.toEntity(0, null, false))
            session.unlock(safeKey, updated.autoLockMinutes)
            runCatching {
                val remoteItems = api.listItems()
                safeDao.replaceAllSafeItems(remoteItems.map { it.toEntity() })
            }
            newRecovery?.formatted
        } finally {
            safeKey.wipe()
            newRecovery?.rawBytes?.fill(0)
        }
    }

    suspend fun updateAutoLockMinutes(minutes: Int) = withContext(Dispatchers.IO) {
        val clamped = minutes.coerceIn(1, 15)
        api.patchAutoLock(clamped)
        preferences.setSafeAutoLockMinutes(clamped)
        session.setAutoLockMinutes(clamped)
        safeDao.getConfigOnce()?.let { cfg ->
            safeDao.upsertConfig(cfg.copy(autoLockMinutes = clamped))
        }
    }

    suspend fun uploadDocument(
        title: String,
        fileName: String,
        mimeType: String,
        category: SafeDocumentCategory,
        folderName: String? = null,
        notes: String?,
        expiresAt: String?,
        rawBytes: ByteArray,
    ): DecryptedSafeItem = withContext(Dispatchers.IO) {
        val safeKey = session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        require(rawBytes.isNotEmpty()) { "Tệp rỗng không hợp lệ." }
        require(rawBytes.size <= SafeCrypto.SAFE_MAX_FILE_BYTES) {
            "Tệp vượt quá giới hạn tối đa 5 MB."
        }
        session.touchActivity()

        val cleanFolder = folderName?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanFolder != null) {
            preferences.addSafeFolder(cleanFolder)
        }

        val fileKey = SafeCrypto.generateFileKey()
        val thumbJpeg = runCatching { generateCompressedThumbJpeg(rawBytes, mimeType) }.getOrNull()
        val thumbKey = if (thumbJpeg != null) SafeCrypto.generateFileKey() else null
        val encoder = Base64.getEncoder()

        val metadata = SafeItemMetadata(
            v = 1,
            title = title.trim().ifBlank { fileName },
            fileName = fileName.trim().ifBlank { "document" },
            mimeType = mimeType,
            category = category.wireValue,
            folderName = cleanFolder,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            fileKeyB64 = encoder.encodeToString(fileKey),
            thumbKeyB64 = thumbKey?.let { encoder.encodeToString(it) },
            originalSize = rawBytes.size,
            originalSha256 = SafeCrypto.sha256Hex(rawBytes),
            expiresAt = expiresAt?.trim()?.takeIf { it.isNotEmpty() },
        )

        val encMeta = SafeCrypto.encryptSafeMetadata(metadata, safeKey)
        val created = api.createItem(
            SafeItemCreateRequest(
                category = category.wireValue,
                encryptedMetadata = encMeta,
                favorite = false,
            ),
        )

        val ovsfBlob = SafeCrypto.encryptSafeFileBinary(rawBytes, fileKey, created.id, OvsfBlobKind.BLOB)
        var readyDto = api.uploadBlob(created.id, ovsfBlob)
        blobFile(created.id).writeBytes(ovsfBlob)

        if (thumbJpeg != null && thumbKey != null) {
            val ovsfThumb = SafeCrypto.encryptSafeFileBinary(
                thumbJpeg,
                thumbKey,
                created.id,
                OvsfBlobKind.THUMB,
            )
            runCatching {
                api.uploadThumbnail(created.id, ovsfThumb)
                thumbFile(created.id).writeBytes(ovsfThumb)
                readyDto = readyDto.copy(
                    hasThumbnail = true,
                    thumbSizeBytes = ovsfThumb.size.toLong(),
                )
            }
        }

        fileKey.fill(0)
        thumbKey?.fill(0)

        safeDao.upsertItem(readyDto.toEntity())
        runCatching { syncConfigUsageOnly() }
        DecryptedSafeItem(
            id = readyDto.id,
            category = category,
            metadata = metadata,
            hasThumbnail = readyDto.hasThumbnail,
            blobSizeBytes = readyDto.blobSizeBytes,
            thumbSizeBytes = readyDto.thumbSizeBytes,
            blobSha256 = readyDto.blobSha256,
            favorite = readyDto.favorite,
            deletedAt = readyDto.deletedAt,
            revisionDate = readyDto.revisionDate,
            createdAt = readyDto.createdAt,
            updatedAt = readyDto.updatedAt,
        )
    }

    suspend fun loadThumbnailBytes(item: DecryptedSafeItem): ByteArray? = withContext(Dispatchers.IO) {
        if (!item.hasThumbnail) return@withContext null
        val thumbKeyB64 = item.metadata.thumbKeyB64 ?: return@withContext null
        val thumbKey = Base64.getDecoder().decode(thumbKeyB64)
        try {
            val localFile = thumbFile(item.id)
            val encrypted = if (localFile.exists()) {
                localFile.readBytes()
            } else {
                val downloaded = api.downloadThumbnail(item.id)
                localFile.writeBytes(downloaded)
                downloaded
            }
            SafeCrypto.decryptSafeFileBinary(encrypted, thumbKey, item.id, OvsfBlobKind.THUMB)
        } catch (_: Exception) {
            null
        } finally {
            thumbKey.fill(0)
        }
    }

    suspend fun loadDocumentBytes(item: DecryptedSafeItem): ByteArray = withContext(Dispatchers.IO) {
        session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        session.touchActivity()
        val fileKey = Base64.getDecoder().decode(item.metadata.fileKeyB64)
        try {
            val localFile = blobFile(item.id)
            val encrypted = if (localFile.exists()) {
                localFile.readBytes()
            } else {
                val downloaded = api.downloadBlob(item.id)
                localFile.writeBytes(downloaded)
                downloaded
            }
            SafeCrypto.decryptSafeFileBinary(encrypted, fileKey, item.id, OvsfBlobKind.BLOB)
        } finally {
            fileKey.fill(0)
        }
    }

    fun createTempPdfFile(decryptedBytes: ByteArray): File {
        val tempFile = File(safeTempDir, "pdf_${UUID.randomUUID()}.tmp")
        FileOutputStream(tempFile).use { it.write(decryptedBytes) }
        return tempFile
    }

    fun shredTempFile(file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            val len = file.length().coerceAtMost(6 * 1024 * 1024).toInt()
            if (len > 0) {
                FileOutputStream(file).use { out ->
                    out.write(ByteArray(len))
                    out.fd.sync()
                }
            }
        }
        runCatching { file.delete() }
    }

    suspend fun updateItemMetadata(
        item: DecryptedSafeItem,
        newTitle: String,
        newCategory: SafeDocumentCategory,
        newNotes: String?,
        newExpiresAt: String?,
        newFolderName: String? = item.metadata.folderName,
        favorite: Boolean = item.favorite,
    ) = withContext(Dispatchers.IO) {
        val safeKey = session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        session.touchActivity()
        val cleanFolder = newFolderName?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanFolder != null) {
            preferences.addSafeFolder(cleanFolder)
        }
        val nextMeta = item.metadata.copy(
            title = newTitle.trim().ifBlank { item.metadata.fileName },
            category = newCategory.wireValue,
            folderName = cleanFolder,
            notes = newNotes?.trim()?.takeIf { it.isNotEmpty() },
            expiresAt = newExpiresAt?.trim()?.takeIf { it.isNotEmpty() },
        )
        val encMeta = SafeCrypto.encryptSafeMetadata(nextMeta, safeKey)
        val updated = api.updateItem(
            item.id,
            SafeItemUpdateRequest(
                category = newCategory.wireValue,
                encryptedMetadata = encMeta,
                favorite = favorite,
            ),
        )
        safeDao.upsertItem(updated.toEntity())
    }

    suspend fun moveItemToFolder(item: DecryptedSafeItem, folderName: String?) =
        updateItemMetadata(
            item = item,
            newTitle = item.metadata.title,
            newCategory = item.category,
            newNotes = item.metadata.notes,
            newExpiresAt = item.metadata.expiresAt,
            newFolderName = folderName,
            favorite = item.favorite,
        )

    fun createFolder(name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Tên thư mục không được để trống." }
        preferences.addSafeFolder(clean)
    }

    suspend fun renameFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val cleanNew = newName.trim()
        require(cleanNew.isNotEmpty()) { "Tên thư mục không được để trống." }
        session.touchActivity()
        preferences.renameSafeFolder(oldName, cleanNew)
        val affected = items.value.filter { it.metadata.folderName == oldName }
        for (item in affected) {
            moveItemToFolder(item, cleanNew)
        }
    }

    suspend fun deleteFolder(folderName: String) = withContext(Dispatchers.IO) {
        session.touchActivity()
        preferences.removeSafeFolder(folderName)
        val affected = items.value.filter { it.metadata.folderName == folderName }
        for (item in affected) {
            moveItemToFolder(item, null)
        }
    }

    fun isCachedOffline(itemId: String): Boolean = blobFile(itemId).exists()

    suspend fun downloadForOffline(item: DecryptedSafeItem) = withContext(Dispatchers.IO) {
        session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        session.touchActivity()
        val localFile = blobFile(item.id)
        if (!localFile.exists()) {
            val downloaded = api.downloadBlob(item.id)
            localFile.writeBytes(downloaded)
        }
    }

    fun clearOfflineCache(itemId: String) {
        runCatching { blobFile(itemId).delete() }
    }

    suspend fun toggleFavorite(item: DecryptedSafeItem) = withContext(Dispatchers.IO) {
        session.getActiveKey() ?: error("Vault+ đang bị khóa.")
        session.touchActivity()
        val updated = api.updateItem(item.id, SafeItemUpdateRequest(favorite = !item.favorite))
        safeDao.upsertItem(updated.toEntity())
    }

    suspend fun softDeleteItem(id: String) = withContext(Dispatchers.IO) {
        session.touchActivity()
        api.softDeleteItem(id)
        syncSafe()
    }

    suspend fun restoreItem(id: String) = withContext(Dispatchers.IO) {
        session.touchActivity()
        val restored = api.restoreItem(id)
        safeDao.upsertItem(restored.toEntity())
    }

    suspend fun hardDeleteItem(id: String) = withContext(Dispatchers.IO) {
        session.touchActivity()
        api.hardDeleteItem(id)
        safeDao.deleteItemById(id)
        runCatching { blobFile(id).delete() }
        runCatching { thumbFile(id).delete() }
        runCatching { syncConfigUsageOnly() }
    }

    suspend fun resetVaultPlus(masterPassword: String) = withContext(Dispatchers.IO) {
        val account = preferences.account.value
        require(account.isLoggedIn) { "Chưa đăng nhập." }
        val masterKey = BitwardenCryptoEngine.deriveMasterKey(
            masterPassword = masterPassword,
            email = account.email,
            config = account.kdfConfig,
        )
        val authHash = try {
            BitwardenCryptoEngine.computeMasterPasswordHash(masterKey, masterPassword, 1)
        } finally {
            masterKey.fill(0)
        }
        api.resetVaultPlus(authHash)
        clearAllSafeDataLocal()
        session.setConfigured(false, SafeCrypto.SAFE_DEFAULT_AUTO_LOCK_MINUTES)
    }

    suspend fun clearOnLogout() = withContext(Dispatchers.IO) {
        clearAllSafeDataLocal()
        session.markLoggedOut()
    }

    private suspend fun clearAllSafeDataLocal() {
        safeDao.clearAllSafe()
        preferences.clearSafeAllLocalState()
        safeBlobDir.listFiles()?.forEach { runCatching { it.delete() } }
        purgeTempFiles()
    }

    fun purgeTempFiles() {
        safeTempDir.listFiles()?.forEach { shredTempFile(it) }
    }

    private suspend fun syncConfigUsageOnly() {
        val serverCfg = api.getConfig()
        val existing = safeDao.getConfigOnce() ?: return
        safeDao.upsertConfig(
            existing.copy(
                usedBytes = serverCfg.usedBytes,
                itemCount = serverCfg.itemCount,
                revisionDate = serverCfg.revisionDate,
            ),
        )
    }

    private fun blobFile(id: String): File = File(safeBlobDir, "$id.ovsf")
    private fun thumbFile(id: String): File = File(safeBlobDir, "$id.thumb.ovsf")

    private fun pruneOrphanCachedBlobs(validIds: Set<String>) {
        safeBlobDir.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".thumb.ovsf").removeSuffix(".ovsf")
            if (id !in validIds) {
                runCatching { file.delete() }
            }
        }
    }

    private fun generateCompressedThumbJpeg(rawBytes: ByteArray, mimeType: String): ByteArray? {
        val bitmap: Bitmap? = when {
            mimeType.startsWith("image/") -> decodeSampledImageThumb(rawBytes, 320)
            mimeType == "application/pdf" -> renderPdfFirstPageThumb(rawBytes, 320)
            else -> null
        }
        if (bitmap == null) return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 76, out)
            val jpeg = out.toByteArray()
            if (jpeg.size <= SafeCrypto.SAFE_MAX_THUMB_BYTES) jpeg else null
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampledImageThumb(rawBytes: ByteArray, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while ((opts.outWidth / sample) > maxDim * 2 || (opts.outHeight / sample) > maxDim * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null
        val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale >= 0.99f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun renderPdfFirstPageThumb(rawBytes: ByteArray, targetWidth: Int): Bitmap? {
        var tempFile: File? = null
        return try {
            tempFile = createTempPdfFile(rawBytes)
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val ratio = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val w = targetWidth.coerceAtLeast(1)
                        val h = (page.height * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            shredTempFile(tempFile)
        }
    }

    private fun SafeConfigDto.toEntity(
        mergedFailed: Int,
        mergedLockedUntil: Long?,
        mergedRecovery: Boolean,
    ): EncryptedSafeConfigEntity = EncryptedSafeConfigEntity(
        id = 1,
        initialized = initialized,
        kdfType = kdfType,
        kdfIterations = kdfIterations,
        kdfMemory = kdfMemory,
        kdfParallelism = kdfParallelism,
        passcodeSalt = passcodeSalt,
        passcodeEncryptedSafeKey = passcodeEncryptedSafeKey,
        recoverySalt = recoverySalt,
        recoveryKdfIterations = recoveryKdfIterations,
        recoveryEncryptedSafeKey = recoveryEncryptedSafeKey,
        autoLockMinutes = autoLockMinutes,
        failedAttempts = mergedFailed,
        lockedUntilMs = mergedLockedUntil,
        recoveryRequired = mergedRecovery,
        usedBytes = usedBytes,
        itemCount = itemCount,
        maxFileBytes = maxFileBytes,
        revisionDate = revisionDate,
    )

    private fun SafeItemDto.toEntity(): EncryptedSafeItemEntity = EncryptedSafeItemEntity(
        id = id,
        category = category,
        encryptedMetadata = encryptedMetadata,
        hasThumbnail = hasThumbnail,
        blobSizeBytes = blobSizeBytes,
        thumbSizeBytes = thumbSizeBytes,
        blobSha256 = blobSha256,
        favorite = favorite,
        deletedAt = deletedAt,
        revisionDate = revisionDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
