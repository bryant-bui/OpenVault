package io.openvault.app.core.network

import kotlinx.serialization.Serializable

@Serializable
data class SafeConfigDto(
    val initialized: Boolean = false,
    val version: Int = 1,
    val kdfType: Int = 1,
    val kdfIterations: Int = 3,
    val kdfMemory: Int? = 64,
    val kdfParallelism: Int? = 4,
    val passcodeSalt: String? = null,
    val passcodeEncryptedSafeKey: String? = null,
    val recoverySalt: String? = null,
    val recoveryKdfIterations: Int = 100_000,
    val recoveryEncryptedSafeKey: String? = null,
    val autoLockMinutes: Int = 3,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long? = null,
    val recoveryRequired: Boolean = false,
    val usedBytes: Long = 0,
    val itemCount: Int = 0,
    val maxFileBytes: Int = 5 * 1024 * 1024,
    val maxThumbBytes: Int = 128 * 1024,
    val revisionDate: String? = null,
)

@Serializable
data class SafeSetupRequest(
    val kdfType: Int,
    val kdfIterations: Int,
    val kdfMemory: Int? = null,
    val kdfParallelism: Int? = null,
    val passcodeSalt: String,
    val passcodeEncryptedSafeKey: String,
    val recoverySalt: String,
    val recoveryKdfIterations: Int,
    val recoveryEncryptedSafeKey: String,
    val autoLockMinutes: Int = 3,
)

@Serializable
data class SafePasscodeUpdateRequest(
    val mode: String = "change",
    val kdfType: Int,
    val kdfIterations: Int,
    val kdfMemory: Int? = null,
    val kdfParallelism: Int? = null,
    val passcodeSalt: String,
    val passcodeEncryptedSafeKey: String,
    val recoverySalt: String? = null,
    val recoveryKdfIterations: Int? = null,
    val recoveryEncryptedSafeKey: String? = null,
)

@Serializable
data class SafeUnlockAttemptRequest(
    val success: Boolean,
)

@Serializable
data class SafeUnlockAttemptResponse(
    val ok: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long? = null,
    val recoveryRequired: Boolean = false,
)

@Serializable
data class SafeSettingsPatchRequest(
    val autoLockMinutes: Int,
)

@Serializable
data class SafeItemCreateRequest(
    val category: String,
    val encryptedMetadata: String,
    val favorite: Boolean = false,
)

@Serializable
data class SafeItemCreateResponse(
    val id: String,
    val revisionDate: String,
)

@Serializable
data class SafeItemUpdateRequest(
    val category: String? = null,
    val encryptedMetadata: String? = null,
    val favorite: Boolean? = null,
)

@Serializable
data class SafeItemDto(
    val id: String,
    val status: String = "ready",
    val category: String = "other",
    val encryptedMetadata: String,
    val hasThumbnail: Boolean = false,
    val blobSizeBytes: Long = 0,
    val thumbSizeBytes: Long = 0,
    val blobSha256: String? = null,
    val favorite: Boolean = false,
    val deletedAt: String? = null,
    val revisionDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SafeItemsResponse(
    val items: List<SafeItemDto> = emptyList(),
)

@Serializable
data class SafeResetRequest(
    val masterPasswordHash: String,
)
