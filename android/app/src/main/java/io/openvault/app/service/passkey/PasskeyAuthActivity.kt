package io.openvault.app.service.passkey

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.openvault.app.OpenVaultApplication
import io.openvault.app.core.crypto.PasskeyCryptoEngine
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme
import io.openvault.app.ui.theme.ProvideFoldableLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PasskeyPurple = Color(0xFF7253ED)

/**
 * Streamlined Android 14+ Credential Manager Activity:
 * - When OpenVault is ALREADY UNLOCKED: completes Passkey sign-in / creation / password fill
 *   immediately on a translucent window with ZERO extra confirmation screen.
 * - When OpenVault is LOCKED: presents [UnlockScreen] (auto-triggering BiometricPrompt,
 *   with 6-digit auto-unlock PIN and Master Password), then completes immediately on unlock.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : FragmentActivity() {

    companion object {
        private const val ACTION_CREATE_PASSKEY = "io.openvault.app.action.PASSKEY_CREATE"
        private const val ACTION_GET_PASSKEY = "io.openvault.app.action.PASSKEY_GET"
        private const val ACTION_GET_PASSWORD = "io.openvault.app.action.PASSWORD_GET"
        private const val ACTION_UNLOCK_FOR_GET = "io.openvault.app.action.PASSKEY_UNLOCK_FOR_GET"

        private const val EXTRA_CIPHER_ID = "ov_pk_cipher_id"
        private const val EXTRA_CREDENTIAL_ID = "ov_pk_credential_id"

        fun createPasskeyCreateIntent(context: Context): Intent =
            Intent(context, PasskeyAuthActivity::class.java).apply {
                action = ACTION_CREATE_PASSKEY
            }

        fun createPasskeyGetIntent(
            context: Context,
            cipherId: String,
            credentialId: String,
        ): Intent = Intent(context, PasskeyAuthActivity::class.java).apply {
            action = ACTION_GET_PASSKEY
            putExtra(EXTRA_CIPHER_ID, cipherId)
            putExtra(EXTRA_CREDENTIAL_ID, credentialId)
        }

        fun createPasswordGetIntent(
            context: Context,
            cipherId: String,
        ): Intent = Intent(context, PasskeyAuthActivity::class.java).apply {
            action = ACTION_GET_PASSWORD
            putExtra(EXTRA_CIPHER_ID, cipherId)
        }

        fun createUnlockForGetIntent(context: Context): Intent =
            Intent(context, PasskeyAuthActivity::class.java).apply {
                action = ACTION_UNLOCK_FOR_GET
            }
    }

    private sealed interface ActionOutcome {
        data object Completed : ActionOutcome
        data class RecoverableError(val message: String) : ActionOutcome
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as OpenVaultApplication).container
        if (container.preferences.settings.value.flagSecureEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        val action = intent.action.orEmpty()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelAndFinish(action)
                }
            },
        )

        // Fast zero-flicker silent path when OpenVault is already unlocked for instant local actions
        val alreadyUnlocked = container.sessionManager.enforceAutoLockIfExpired()
        if (alreadyUnlocked && (action == ACTION_GET_PASSKEY || action == ACTION_GET_PASSWORD || action == ACTION_UNLOCK_FOR_GET)) {
            lifecycleScope.launch {
                val outcome = executeAction(action)
                if (outcome is ActionOutcome.RecoverableError) {
                    renderComposeFlow(action, initialError = outcome.message)
                }
            }
            return
        }

        renderComposeFlow(action, initialError = null)
    }

    private fun renderComposeFlow(action: String, initialError: String?) {
        enableEdgeToEdge()
        val container = (application as OpenVaultApplication).container

        setContent {
            val settings by container.preferences.settings.collectAsState()
            val lockState by container.sessionManager.lockState.collectAsState()

            var retryNonce by remember { mutableIntStateOf(0) }
            var errorMessage by remember { mutableStateOf(initialError) }
            var showSlowProgressPill by remember { mutableStateOf(false) }

            OpenVaultTheme(themeMode = settings.themeMode) {
                ProvideFoldableLayout(activity = this@PasskeyAuthActivity) {
                    if (lockState != VaultLockState.UNLOCKED) {
                        UnlockScreen(
                            repository = container.repository,
                            activity = this@PasskeyAuthActivity,
                            autoTriggerBiometric = true,
                            allowLogout = false,
                            onUnlocked = { },
                        )
                    } else if (errorMessage != null) {
                        PasskeyErrorDialog(
                            message = errorMessage!!,
                            onRetry = {
                                errorMessage = null
                                retryNonce++
                            },
                            onCancel = { cancelAndFinish(action) },
                        )
                    } else {
                        LaunchedEffect(action, retryNonce) {
                            val pillJob = launch {
                                // Only reveal a subtle pill if saving to Cloudflare D1 takes > 180ms
                                delay(180)
                                showSlowProgressPill = true
                            }
                            val outcome = executeAction(action)
                            pillJob.cancel()
                            showSlowProgressPill = false
                            if (outcome is ActionOutcome.RecoverableError) {
                                errorMessage = outcome.message
                            }
                        }

                        if (showSlowProgressPill) {
                            val label = when (action) {
                                ACTION_CREATE_PASSKEY -> "Đang lưu mã khóa vào OpenVault..."
                                ACTION_UNLOCK_FOR_GET -> "Đang tải mã khóa..."
                                else -> "Đang xác thực mã khóa..."
                            }
                            FloatingProgressPill(label)
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeAction(action: String): ActionOutcome {
        val container = (application as OpenVaultApplication).container
        return when (action) {
            ACTION_UNLOCK_FOR_GET -> {
                val beginReq = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
                val resultIntent = Intent()
                if (beginReq == null) {
                    PendingIntentHandler.setGetCredentialException(
                        resultIntent,
                        GetCredentialUnknownException("Yêu cầu không hợp lệ."),
                    )
                } else {
                    val resp = VaultCredentialProviderService.buildUnlockedGetResponse(
                        context = this,
                        repository = container.repository,
                        request = beginReq,
                    )
                    if (resp == null) {
                        PendingIntentHandler.setGetCredentialException(
                            resultIntent,
                            NoCredentialException("Không tìm thấy Passkey khớp trong OpenVault."),
                        )
                    } else {
                        PendingIntentHandler.setBeginGetCredentialResponse(resultIntent, resp)
                    }
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                ActionOutcome.Completed
            }

            ACTION_GET_PASSWORD -> {
                val cipherId = intent.getStringExtra(EXTRA_CIPHER_ID).orEmpty()
                val item = container.repository.findItemById(cipherId)
                val resultIntent = Intent()
                val login = item?.login
                if (login == null || login.username.isBlank() || login.password.isBlank()) {
                    PendingIntentHandler.setGetCredentialException(
                        resultIntent,
                        NoCredentialException("Không tìm thấy tài khoản trong OpenVault."),
                    )
                } else {
                    PendingIntentHandler.setGetCredentialResponse(
                        resultIntent,
                        GetCredentialResponse(PasswordCredential(login.username, login.password)),
                    )
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                ActionOutcome.Completed
            }

            ACTION_GET_PASSKEY -> {
                val providerReq = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                val pkOption = providerReq?.credentialOptions
                    ?.filterIsInstance<GetPublicKeyCredentialOption>()
                    ?.firstOrNull()
                val parsed = pkOption?.requestJson?.let { PasskeyCryptoEngine.parseGetRequestJson(it) }
                val origin = providerReq?.callingAppInfo?.let {
                    PrivilegedBrowserAllowlist.resolveOrigin(this, it)
                }
                val cipherId = intent.getStringExtra(EXTRA_CIPHER_ID).orEmpty()
                val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID).orEmpty()

                if (providerReq == null || pkOption == null || parsed == null || origin == null) {
                    val resultIntent = Intent()
                    PendingIntentHandler.setGetCredentialException(
                        resultIntent,
                        GetCredentialUnknownException("Yêu cầu xác thực Passkey không hợp lệ."),
                    )
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                    return ActionOutcome.Completed
                }

                runCatching {
                    val (_, cred) = container.repository.findPasskeyByIds(cipherId, credentialId)
                        ?: error("Không tìm thấy mã khóa trong kho.")
                    val signed = PasskeyCryptoEngine.signAssertion(
                        credential = cred,
                        challengeB64Url = parsed.challengeB64Url,
                        origin = origin,
                        clientDataHashOverride = pkOption.clientDataHash,
                        packageName = providerReq.callingAppInfo.packageName,
                    )
                    signed.authenticationResponseJson
                }.fold(
                    onSuccess = { authJson ->
                        val resultIntent = Intent()
                        PendingIntentHandler.setGetCredentialResponse(
                            resultIntent,
                            GetCredentialResponse(PublicKeyCredential(authJson)),
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                        ActionOutcome.Completed
                    },
                    onFailure = { err ->
                        ActionOutcome.RecoverableError(err.message ?: "Không thể ký xác thực Passkey.")
                    },
                )
            }

            ACTION_CREATE_PASSKEY -> {
                val providerReq = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                val pkReq = providerReq?.callingRequest as? CreatePublicKeyCredentialRequest
                val parsed = pkReq?.requestJson?.let { PasskeyCryptoEngine.parseCreateRequestJson(it) }
                val origin = providerReq?.callingAppInfo?.let {
                    PrivilegedBrowserAllowlist.resolveOrigin(this, it)
                }

                if (providerReq == null || pkReq == null || parsed == null || origin == null) {
                    val resultIntent = Intent()
                    PendingIntentHandler.setCreateCredentialException(
                        resultIntent,
                        CreateCredentialUnknownException("Yêu cầu tạo Passkey không hợp lệ hoặc nguồn không được tin cậy."),
                    )
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                    return ActionOutcome.Completed
                }

                runCatching {
                    val created = PasskeyCryptoEngine.createPasskey(
                        rpId = parsed.rpId,
                        rpName = parsed.rpName,
                        userHandleB64Url = parsed.userIdB64Url,
                        userName = parsed.userName,
                        userDisplayName = parsed.userDisplayName,
                        challengeB64Url = parsed.challengeB64Url,
                        origin = origin,
                        discoverable = parsed.discoverable,
                        packageName = providerReq.callingAppInfo.packageName,
                    )
                    container.repository.savePasskeyCredential(
                        rpId = parsed.rpId,
                        rpName = parsed.rpName,
                        origin = origin,
                        packageName = providerReq.callingAppInfo.packageName,
                        userName = parsed.userName,
                        credential = created.credential,
                    )
                    created.registrationResponseJson
                }.fold(
                    onSuccess = { regJson ->
                        val resultIntent = Intent()
                        PendingIntentHandler.setCreateCredentialResponse(
                            resultIntent,
                            CreatePublicKeyCredentialResponse(regJson),
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                        ActionOutcome.Completed
                    },
                    onFailure = { err ->
                        ActionOutcome.RecoverableError(err.message ?: "Không thể lưu mã khóa lên OpenVault.")
                    },
                )
            }

            else -> {
                cancelAndFinish(action)
                ActionOutcome.Completed
            }
        }
    }

    private fun cancelAndFinish(action: String) {
        val resultIntent = Intent()
        when (action) {
            ACTION_CREATE_PASSKEY -> PendingIntentHandler.setCreateCredentialException(
                resultIntent,
                CreateCredentialCancellationException("Người dùng đã hủy tạo mã khóa."),
            )
            else -> PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialCancellationException("Người dùng đã hủy đăng nhập."),
            )
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
private fun FloatingProgressPill(label: String) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 36.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = c.surface,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    color = PasskeyPurple,
                    strokeWidth = 2.4.dp,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PasskeyErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Không thể hoàn tất Passkey",
                    color = c.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    color = c.textSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                        Text("Hủy", color = c.textSecondary)
                    }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PasskeyPurple,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Thử lại", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
