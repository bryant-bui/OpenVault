package io.openvault.app.core.network

import io.openvault.app.core.security.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    override val message: String,
) : IOException(message)

class BitwardenApiClient(
    private val preferences: AppPreferences,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(override: String? = null): String =
        (override ?: preferences.getServerUrl()).trim().removeSuffix("/")

    suspend fun prelogin(serverUrl: String, email: String): PreloginResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(PreloginRequest(email = email.trim().lowercase()))
                .toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("${baseUrl(serverUrl)}/identity/accounts/prelogin")
                .post(body)
                .build()
            executeJson(req)
        }

    suspend fun register(serverUrl: String, payload: RegisterRequest): RegisterResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val req = Request.Builder()
                .url("${baseUrl(serverUrl)}/identity/accounts/register/finish")
                .post(body)
                .build()
            executeJson(req)
        }

    suspend fun loginWithPassword(
        serverUrl: String,
        email: String,
        masterPasswordHash: String,
        deviceId: String,
        deviceName: String,
    ): TokenResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", email.trim().lowercase())
            .add("password", masterPasswordHash)
            .add("scope", "api offline_access")
            .add("client_id", "mobile")
            .add("deviceType", "0") // 0 = Android in Bitwarden protocol
            .add("deviceIdentifier", deviceId)
            .add("deviceName", deviceName)
            .build()

        val req = Request.Builder()
            .url("${baseUrl(serverUrl)}/identity/connect/token")
            .post(form)
            .build()
        executeJson(req)
    }

    suspend fun refreshAccessToken(): TokenResponse = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val refresh = preferences.getRefreshToken()
                ?: throw ApiException(401, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.")
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", "mobile")
                .build()
            val req = Request.Builder()
                .url("${baseUrl()}/identity/connect/token")
                .post(form)
                .build()
            val res: TokenResponse = executeJson(req)
            preferences.updateTokens(res.accessToken, res.refreshToken)
            res
        }
    }

    suspend fun getRevisionDate(): Long = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/accounts/revision-date")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val text = executeRaw(req).trim()
        text.toLongOrNull() ?: System.currentTimeMillis()
    }

    suspend fun sync(): SyncResponse = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/sync")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(req)
    }

    suspend fun createCipher(payload: CipherRequest): CipherDto = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/ciphers")
            .header("Authorization", "Bearer $token")
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun updateCipher(id: String, payload: CipherRequest): CipherDto = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/ciphers/$id")
            .header("Authorization", "Bearer $token")
            .put(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun softDeleteCipher(id: String) {
        authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/ciphers/$id/delete")
                .header("Authorization", "Bearer $token")
                .put("".toRequestBody(null))
                .build()
            executeRaw(req)
        }
    }

    suspend fun restoreCipher(id: String): CipherDto = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/ciphers/$id/restore")
            .header("Authorization", "Bearer $token")
            .put("".toRequestBody(null))
            .build()
        executeJson(req)
    }

    suspend fun hardDeleteCipher(id: String) {
        authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/ciphers/$id")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            executeRaw(req)
        }
    }

    suspend fun createFolder(encryptedName: String): FolderDto = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/folders")
            .header("Authorization", "Bearer $token")
            .post(json.encodeToString(FolderRequest(encryptedName)).toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun deleteFolder(id: String) {
        authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/folders/$id")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            executeRaw(req)
        }
    }

    suspend fun importVault(payload: ImportVaultRequest): ImportVaultResponse = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/ciphers/import")
            .header("Authorization", "Bearer $token")
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun triggerR2Backup(): R2BackupResultDto = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/backup")
            .header("Authorization", "Bearer $token")
            .post("".toRequestBody(null))
            .build()
        executeJson(req)
    }

    suspend fun listR2Backups(): List<R2BackupItemDto> = authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/backups")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson<R2BackupListResponse>(req).backups
    }

    suspend fun logout(refreshToken: String?, allDevices: Boolean = false) {
        runCatching {
            authedRequest { token ->
                val req = Request.Builder()
                    .url("${baseUrl()}/api/accounts/logout")
                    .header("Authorization", "Bearer $token")
                    .post(
                        json.encodeToString(LogoutRequest(refreshToken, allDevices))
                            .toRequestBody(jsonMediaType),
                    )
                    .build()
                executeRaw(req)
            }
        }
    }

    private suspend fun <T> authedRequest(block: suspend (String) -> T): T =
        withContext(Dispatchers.IO) {
            val token = preferences.getAccessToken()
                ?: throw ApiException(401, "Chưa đăng nhập.")
            try {
                block(token)
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    val refreshed = refreshAccessToken()
                    block(refreshed.accessToken)
                } else {
                    throw e
                }
            }
        }

    private inline fun <reified T> executeJson(request: Request): T {
        val raw = executeRaw(request)
        return json.decodeFromString(raw)
    }

    private fun executeRaw(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                return text
            }
            val friendlyMessage = parseErrorMessage(response.code, text)
            throw ApiException(response.code, friendlyMessage)
        }
    }

    private fun parseErrorMessage(code: Int, rawBody: String): String {
        val parsed = runCatching { json.decodeFromString<ApiErrorResponse>(rawBody) }.getOrNull()
        val detail = parsed?.errorModel?.message
            ?: parsed?.message
            ?: parsed?.errorDescription
            ?: parsed?.error
        return when {
            detail == "invalid_username_or_password" -> "Email hoặc mật khẩu chính không đúng."
            !detail.isNullOrBlank() -> detail
            code == 401 -> "Phiên làm việc đã hết hạn."
            code == 403 -> "Yêu cầu bị từ chối (tài khoản cá nhân đã đạt giới hạn hoặc bị khóa)."
            code == 429 -> "Thử sai quá nhiều lần. Vui lòng chờ một lát."
            else -> "Lỗi máy chủ ($code)."
        }
    }
}
