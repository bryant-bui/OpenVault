package io.openvault.app.service.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import io.openvault.app.OpenVaultApplication
import io.openvault.app.R
import io.openvault.app.core.crypto.PasskeyCryptoEngine
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android 14+ System Credential Provider Service for OpenVault Passkeys (WebAuthn / FIDO2)
 * and Credential Manager passwords.
 *
 * - Enforces WebAuthn RP ID <-> Origin validation using Google's official privileged browser allowlist.
 * - Never exposes decrypted secrets while locked (returns an AuthenticationAction to unlock first).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VaultCredentialProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        val container = (application as OpenVaultApplication).container
        val account = container.preferences.account.value
        if (!account.isLoggedIn) {
            callback.onError(CreateCredentialNoCreateOptionException("Chưa đăng nhập tài khoản OpenVault."))
            return
        }

        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnsupportedException("Loại thông tin xác thực không được hỗ trợ."))
            return
        }

        val parsed = PasskeyCryptoEngine.parseCreateRequestJson(request.requestJson)
        if (parsed == null || !parsed.supportsEs256) {
            callback.onError(CreateCredentialUnsupportedException("Trang web không hỗ trợ thuật toán ES256 (P-256)."))
            return
        }

        val callingAppInfo = request.callingAppInfo
        if (callingAppInfo != null) {
            val origin = PrivilegedBrowserAllowlist.resolveOrigin(this, callingAppInfo)
            if (origin == null || !PasskeyCryptoEngine.isValidRpIdForOrigin(parsed.rpId, origin)) {
                callback.onError(CreateCredentialUnsupportedException("Nguồn gọi không khớp với RP ID '${parsed.rpId}'."))
                return
            }
        }

        val pendingIntent = createPendingIntent(
            context = this,
            intent = PasskeyAuthActivity.createPasskeyCreateIntent(this),
        )
        val icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
        val accountLabel = account.email.ifBlank { "OpenVault" }
        val description = "Lưu mã khóa cho ${parsed.rpName} vào OpenVault"

        val entry = CreateEntry.Builder(accountLabel, pendingIntent)
            .setDescription(description)
            .setIcon(icon)
            .build()

        callback.onResult(
            BeginCreateCredentialResponse.Builder()
                .setCreateEntries(listOf(entry))
                .build(),
        )
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val container = (application as OpenVaultApplication).container
        val account = container.preferences.account.value
        if (!account.isLoggedIn) {
            callback.onError(NoCredentialException("Chưa đăng nhập tài khoản OpenVault."))
            return
        }

        val job = serviceScope.launch {
            try {
                if (container.sessionManager.lockState.value != VaultLockState.UNLOCKED) {
                    val unlockPendingIntent = createPendingIntent(
                        context = this@VaultCredentialProviderService,
                        intent = PasskeyAuthActivity.createUnlockForGetIntent(this@VaultCredentialProviderService),
                    )
                    val authAction = AuthenticationAction(
                        title = "Mở khóa OpenVault",
                        pendingIntent = unlockPendingIntent,
                    )
                    callback.onResult(
                        BeginGetCredentialResponse.Builder()
                            .setAuthenticationActions(listOf(authAction))
                            .build(),
                    )
                    return@launch
                }

                val response = buildUnlockedGetResponse(
                    context = this@VaultCredentialProviderService,
                    repository = container.repository,
                    request = request,
                )
                if (response == null) {
                    callback.onError(NoCredentialException("Không tìm thấy Passkey hoặc tài khoản khớp trong OpenVault."))
                } else {
                    callback.onResult(response)
                }
            } catch (e: Exception) {
                callback.onError(NoCredentialException(e.message ?: "Lỗi truy vấn kho OpenVault."))
            }
        }

        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    companion object {
        private val requestCodeCounter = AtomicInteger(4000)

        private fun nextRequestCode(): Int = requestCodeCounter.incrementAndGet()

        fun createPendingIntent(context: Context, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                nextRequestCode(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /**
         * Builds the Credential Manager response containing all matching Passkeys & Passwords
         * once the OpenVault vault is unlocked. Shared by both [VaultCredentialProviderService]
         * and [PasskeyAuthActivity] (after unlock via AuthenticationAction).
         */
        suspend fun buildUnlockedGetResponse(
            context: Context,
            repository: VaultRepository,
            request: BeginGetCredentialRequest,
        ): BeginGetCredentialResponse? {
            val entries = mutableListOf<CredentialEntry>()
            val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
            val callingAppInfo = request.callingAppInfo
            val resolvedOrigin = callingAppInfo?.let {
                PrivilegedBrowserAllowlist.resolveOrigin(context, it)
            }

            for (option in request.beginGetCredentialOptions) {
                when (option) {
                    is BeginGetPublicKeyCredentialOption -> {
                        val parsed = PasskeyCryptoEngine.parseGetRequestJson(option.requestJson) ?: continue
                        if (resolvedOrigin != null && !PasskeyCryptoEngine.isValidRpIdForOrigin(parsed.rpId, resolvedOrigin)) {
                            continue
                        }
                        val matches = repository.findPasskeysForRp(
                            rpId = parsed.rpId,
                            allowCredentialIds = parsed.allowCredentialIds,
                        )
                        for ((item, cred) in matches) {
                            val pendingIntent = createPendingIntent(
                                context = context,
                                intent = PasskeyAuthActivity.createPasskeyGetIntent(
                                    context = context,
                                    cipherId = item.id,
                                    credentialId = cred.credentialId,
                                ),
                            )
                            val username = cred.userName?.takeIf { it.isNotBlank() }
                                ?: item.login?.username?.takeIf { it.isNotBlank() }
                                ?: cred.rpId
                            val displayName = cred.userDisplayName?.takeIf { it.isNotBlank() && it != username }
                                ?: item.name.takeIf { it.isNotBlank() }

                            val builder = PublicKeyCredentialEntry.Builder(
                                context = context,
                                username = username,
                                pendingIntent = pendingIntent,
                                beginGetPublicKeyCredentialOption = option,
                            ).setIcon(icon)
                            if (!displayName.isNullOrBlank()) {
                                builder.setDisplayName(displayName)
                            }
                            entries += builder.build()
                        }
                    }

                    is BeginGetPasswordOption -> {
                        val webDomain = resolvedOrigin?.takeIf { it.startsWith("http", ignoreCase = true) }
                        val pkgName = callingAppInfo?.packageName
                        val matches = repository.findAutofillMatches(webDomain, pkgName)
                            .filter { !it.login?.username.isNullOrBlank() && !it.login?.password.isNullOrBlank() }

                        for (item in matches.take(8)) {
                            val login = item.login ?: continue
                            val pendingIntent = createPendingIntent(
                                context = context,
                                intent = PasskeyAuthActivity.createPasswordGetIntent(
                                    context = context,
                                    cipherId = item.id,
                                ),
                            )
                            val entry = PasswordCredentialEntry.Builder(
                                context = context,
                                username = login.username,
                                pendingIntent = pendingIntent,
                                beginGetPasswordOption = option,
                            )
                                .setDisplayName(item.name)
                                .setIcon(icon)
                                .build()
                            entries += entry
                        }
                    }
                }
            }

            if (entries.isEmpty()) return null
            return BeginGetCredentialResponse.Builder()
                .setCredentialEntries(entries)
                .build()
        }
    }
}
