package io.openvault.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreloginRequest(
    val email: String,
)

@Serializable
data class PreloginResponse(
    val kdf: Int = 1,
    val kdfIterations: Int = 3,
    val kdfMemory: Int? = 64,
    val kdfParallelism: Int? = 4,
)

@Serializable
data class RegisterKeysDto(
    val publicKey: String,
    val encryptedPrivateKey: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val name: String? = null,
    val masterPasswordHash: String,
    val masterPasswordHint: String? = null,
    val key: String,
    val userSymmetricKey: String,
    val kdf: Int,
    val kdfIterations: Int,
    val kdfMemory: Int? = null,
    val kdfParallelism: Int? = null,
    val keys: RegisterKeysDto? = null,
    val userAsymmetricKeys: RegisterKeysDto? = null,
)

@Serializable
data class RegisterResponse(
    val id: String = "",
    val email: String = "",
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("Key") val key: String,
    @SerialName("PrivateKey") val privateKey: String? = null,
    @SerialName("Kdf") val kdf: Int = 1,
    @SerialName("KdfIterations") val kdfIterations: Int = 3,
    @SerialName("KdfMemory") val kdfMemory: Int? = 64,
    @SerialName("KdfParallelism") val kdfParallelism: Int? = 4,
    @SerialName("ForcePasswordReset") val forcePasswordReset: Boolean = false,
)

@Serializable
data class ProfileDto(
    val id: String = "",
    val name: String? = null,
    val email: String = "",
    val premium: Boolean = true,
    val masterPasswordHint: String? = null,
    val key: String = "",
    val privateKey: String? = null,
    val securityStamp: String? = null,
)

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val revisionDate: String? = null,
)

@Serializable
data class FolderRequest(
    val name: String,
)

@Serializable
data class CipherUriDto(
    val uri: String? = null,
    val match: Int? = null,
)

@Serializable
data class Fido2CredentialDto(
    val credentialId: String,
    val keyType: String,
    val keyAlgorithm: String,
    val keyCurve: String,
    val keyValue: String,
    val rpId: String,
    val rpName: String? = null,
    val userHandle: String? = null,
    val userName: String? = null,
    val userDisplayName: String? = null,
    val counter: String,
    val discoverable: String,
    val creationDate: String,
)

@Serializable
data class CipherLoginDto(
    val uris: List<CipherUriDto>? = null,
    val username: String? = null,
    val password: String? = null,
    val passwordRevisionDate: String? = null,
    val totp: String? = null,
    val fido2Credentials: List<Fido2CredentialDto>? = null,
)

@Serializable
data class CipherSecureNoteDto(
    val type: Int = 0,
)

@Serializable
data class CipherCardDto(
    val cardholderName: String? = null,
    val brand: String? = null,
    val number: String? = null,
    val expMonth: String? = null,
    val expYear: String? = null,
    val code: String? = null,
)

@Serializable
data class CipherIdentityDto(
    val title: String? = null,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val address3: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val company: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val ssn: String? = null,
    val username: String? = null,
    val passportNumber: String? = null,
    val licenseNumber: String? = null,
)

@Serializable
data class CipherFieldDto(
    val name: String? = null,
    val value: String? = null,
    val type: Int = 0,
    val linkedId: Int? = null,
)

@Serializable
data class PasswordHistoryDto(
    val password: String,
    val lastUsedDate: String,
)

@Serializable
data class CipherDto(
    val id: String = "",
    val organizationId: String? = null,
    val folderId: String? = null,
    val type: Int = 1,
    val name: String,
    val notes: String? = null,
    val favorite: Boolean = false,
    val reprompt: Int = 0,
    val login: CipherLoginDto? = null,
    val secureNote: CipherSecureNoteDto? = null,
    val card: CipherCardDto? = null,
    val identity: CipherIdentityDto? = null,
    val fields: List<CipherFieldDto>? = null,
    val passwordHistory: List<PasswordHistoryDto>? = null,
    val creationDate: String? = null,
    val revisionDate: String? = null,
    val deletedDate: String? = null,
)

@Serializable
data class CipherRequest(
    val type: Int,
    val folderId: String? = null,
    val organizationId: String? = null,
    val name: String,
    val notes: String? = null,
    val favorite: Boolean = false,
    val reprompt: Int = 0,
    val login: CipherLoginDto? = null,
    val secureNote: CipherSecureNoteDto? = null,
    val card: CipherCardDto? = null,
    val identity: CipherIdentityDto? = null,
    val fields: List<CipherFieldDto>? = null,
    val passwordHistory: List<PasswordHistoryDto>? = null,
)

@Serializable
data class SyncResponse(
    val profile: ProfileDto,
    val folders: List<FolderDto> = emptyList(),
    val ciphers: List<CipherDto> = emptyList(),
)

@Serializable
data class R2BackupResultDto(
    val key: String = "",
    val uploadedAt: String = "",
    val usersCount: Int = 0,
    val foldersCount: Int = 0,
    val ciphersCount: Int = 0,
    val sizeBytes: Long = 0,
)

@Serializable
data class R2BackupItemDto(
    val key: String = "",
    val size: Long = 0,
    val uploaded: String = "",
)

@Serializable
data class R2BackupListResponse(
    val backups: List<R2BackupItemDto> = emptyList(),
)

@Serializable
data class LogoutRequest(
    val refreshToken: String? = null,
    val allDevices: Boolean = false,
)

@Serializable
data class ImportFolderRelationshipDto(
    val key: Int,
    val value: Int,
)

@Serializable
data class ImportVaultRequest(
    val folders: List<FolderRequest> = emptyList(),
    val ciphers: List<CipherRequest> = emptyList(),
    val folderRelationships: List<ImportFolderRelationshipDto> = emptyList(),
)

@Serializable
data class ImportVaultResponse(
    val ok: Boolean = true,
    val importedFolders: Int = 0,
    val importedCiphers: Int = 0,
    val revisionDate: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("ErrorModel") val errorModel: ErrorModelDto? = null,
)

@Serializable
data class ErrorModelDto(
    @SerialName("Message") val message: String? = null,
)
