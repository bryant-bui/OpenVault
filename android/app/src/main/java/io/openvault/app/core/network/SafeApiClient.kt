package io.openvault.app.core.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

class SafeApiClient(
    private val core: BitwardenApiClient,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun baseUrl(): String = core.baseUrl()

    suspend fun getConfig(): SafeConfigDto = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/config")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(req)
    }

    suspend fun setup(payload: SafeSetupRequest): SafeConfigDto = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/setup")
            .header("Authorization", "Bearer $token")
            .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun reportUnlockAttempt(success: Boolean): SafeUnlockAttemptResponse =
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/unlock-attempt")
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(SafeUnlockAttemptRequest(success)).toRequestBody(jsonMediaType))
                .build()
            executeJson(req)
        }

    suspend fun updatePasscode(payload: SafePasscodeUpdateRequest): SafeConfigDto =
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/passcode")
                .header("Authorization", "Bearer $token")
                .put(json.encodeToString(payload).toRequestBody(jsonMediaType))
                .build()
            executeJson(req)
        }

    suspend fun patchAutoLock(autoLockMinutes: Int) {
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/settings")
                .header("Authorization", "Bearer $token")
                .patch(
                    json.encodeToString(SafeSettingsPatchRequest(autoLockMinutes))
                        .toRequestBody(jsonMediaType),
                )
                .build()
            executeRaw(req)
        }
    }

    suspend fun listItems(): List<SafeItemDto> = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/items")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson<SafeItemsResponse>(req).items
    }

    suspend fun createItem(payload: SafeItemCreateRequest): SafeItemCreateResponse =
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/items")
                .header("Authorization", "Bearer $token")
                .post(json.encodeToString(payload).toRequestBody(jsonMediaType))
                .build()
            executeJson(req)
        }

    suspend fun uploadBlob(
        itemId: String,
        ovsfBytes: ByteArray,
        onProgress: ((Float) -> Unit)? = null,
    ): SafeItemDto = core.authedRequest { token ->
        val body = if (onProgress != null) {
            ProgressRequestBody(ovsfBytes, octetMediaType, onProgress)
        } else {
            ovsfBytes.toRequestBody(octetMediaType)
        }
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/items/$itemId/blob")
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()
        executeJson(req)
    }

    private class ProgressRequestBody(
        private val data: ByteArray,
        private val mediaType: MediaType,
        private val onProgress: (Float) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = data.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            val total = data.size.coerceAtLeast(1)
            var offset = 0
            var lastReported = -1f
            val chunkSize = 16 * 1024
            onProgress(0f)
            while (offset < data.size) {
                val len = minOf(chunkSize, data.size - offset)
                sink.write(data, offset, len)
                offset += len
                val frac = (offset.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                if (frac - lastReported >= 0.02f || offset >= data.size) {
                    lastReported = frac
                    onProgress(frac)
                }
            }
        }
    }

    suspend fun uploadThumbnail(itemId: String, ovsfThumbBytes: ByteArray) {
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/items/$itemId/thumbnail")
                .header("Authorization", "Bearer $token")
                .put(ovsfThumbBytes.toRequestBody(octetMediaType))
                .build()
            executeRaw(req)
        }
    }

    suspend fun downloadBlob(itemId: String): ByteArray = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/items/$itemId/blob")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeBytes(req)
    }

    suspend fun downloadThumbnail(itemId: String): ByteArray = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/items/$itemId/thumbnail")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeBytes(req)
    }

    suspend fun updateItem(id: String, payload: SafeItemUpdateRequest): SafeItemDto =
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/items/$id")
                .header("Authorization", "Bearer $token")
                .put(json.encodeToString(payload).toRequestBody(jsonMediaType))
                .build()
            executeJson(req)
        }

    suspend fun softDeleteItem(id: String) {
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/items/$id/delete")
                .header("Authorization", "Bearer $token")
                .put("".toRequestBody(null))
                .build()
            executeRaw(req)
        }
    }

    suspend fun restoreItem(id: String): SafeItemDto = core.authedRequest { token ->
        val req = Request.Builder()
            .url("${baseUrl()}/api/openvault/safe/items/$id/restore")
            .header("Authorization", "Bearer $token")
            .put("".toRequestBody(null))
            .build()
        executeJson(req)
    }

    suspend fun hardDeleteItem(id: String) {
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/items/$id")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            executeRaw(req)
        }
    }

    suspend fun resetVaultPlus(masterPasswordHash: String) {
        core.authedRequest { token ->
            val req = Request.Builder()
                .url("${baseUrl()}/api/openvault/safe/reset")
                .header("Authorization", "Bearer $token")
                .delete(json.encodeToString(SafeResetRequest(masterPasswordHash)).toRequestBody(jsonMediaType))
                .build()
            executeRaw(req)
        }
    }

    private inline fun <reified T> executeJson(request: Request): T {
        val raw = executeRaw(request)
        return json.decodeFromString(raw)
    }

    private fun executeRaw(request: Request): String {
        core.binaryClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) return text
            throw ApiException(response.code, core.parseErrorMessage(response.code, text))
        }
    }

    private fun executeBytes(request: Request): ByteArray {
        core.binaryClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.bytes() ?: ByteArray(0)
            }
            val text = response.body?.string().orEmpty()
            throw ApiException(response.code, core.parseErrorMessage(response.code, text))
        }
    }
}
