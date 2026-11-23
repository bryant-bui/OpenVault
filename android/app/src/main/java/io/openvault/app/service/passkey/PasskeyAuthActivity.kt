package io.openvault.app.service.passkey

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.openvault.app.OpenVaultApplication
import io.openvault.app.core.crypto.PasskeyCryptoEngine
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme
import kotlinx.coroutines.launch

private val PasskeyPurple = Color(0xFF7253ED)

/**
 * Activity invoked by Android 14+ Credential Manager PendingIntents to:
 * 1. Unlock the vault and return matching Passkeys/Passwords (`ACTION_UNLOCK_FOR_GET`)
 * 2. Create & save a new Passkey (`ACTION_CREATE_PASSKEY`)
 * 3. Sign a WebAuthn assertion with a stored Passkey (`ACTION_GET_PASSKEY`)
 * 4. Return a stored password for `PasswordCredentialEntry` (`ACTION_GET_PASSWORD`)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OpenVaultApplication).container
        if (container.preferences.settings.value.flagSecureEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        val action = intent.action.orEmpty()

        fun cancelAndFinish() {
            val resultIntent = Intent()
            when (action) {
                ACTION_CREATE_PASSKEY -> PendingIntentHandler.setCreateCredentialException(
                    resultIntent,
                    CreateCredentialCancellationException("Người dùng đã hủy tạo mã khóa."),
                )
                ACTION_GET_PASSKEY, ACTION_GET_PASSWORD -> PendingIntentHandler.setGetCredentialException(
                    resultIntent,
                    GetCredentialCancellationException("Người dùng đã hủy đăng nhập."),
                )
                ACTION_UNLOCK_FOR_GET -> PendingIntentHandler.setGetCredentialException(
                    resultIntent,
                    GetCredentialCancellationException("Chưa mở khóa OpenVault."),
                )
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        setContent {
            val settings by container.preferences.settings.collectAsState()
            val lockState by container.sessionManager.lockState.collectAsState()
            val scope = rememberCoroutineScope()

            OpenVaultTheme(themeMode = settings.themeMode) {
                if (lockState != VaultLockState.UNLOCKED) {
                    UnlockScreen(
                        repository = container.repository,
                        activity = this@PasskeyAuthActivity,
                        autoTriggerBiometric = true,
                        allowLogout = false,
                        onUnlocked = { },
                    )
                } else {
                    when (action) {
                        ACTION_UNLOCK_FOR_GET -> {
                            LaunchedEffect(Unit) {
                                val beginReq = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
                                val resultIntent = Intent()
                                if (beginReq == null) {
                                    PendingIntentHandler.setGetCredentialException(
                                        resultIntent,
                                        GetCredentialUnknownException("Yêu cầu không hợp lệ."),
                                    )
                                } else {
                                    val resp = VaultCredentialProviderService.buildUnlockedGetResponse(
                                        context = this@PasskeyAuthActivity,
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
                            }
                            BusyPasskeyPlaceholder("Đang tải Passkeys từ OpenVault...")
                        }

                        ACTION_GET_PASSWORD -> {
                            LaunchedEffect(Unit) {
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
                            }
                            BusyPasskeyPlaceholder("Đang điền tài khoản...")
                        }

                        ACTION_CREATE_PASSKEY -> {
                            val providerReq = remember {
                                PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                            }
                            val pkReq = providerReq?.callingRequest as? CreatePublicKeyCredentialRequest
                            val parsed = remember(pkReq) {
                                pkReq?.requestJson?.let { PasskeyCryptoEngine.parseCreateRequestJson(it) }
                            }
                            val origin = remember(providerReq) {
                                providerReq?.callingAppInfo?.let {
                                    PrivilegedBrowserAllowlist.resolveOrigin(this@PasskeyAuthActivity, it)
                                }
                            }

                            var busy by remember { mutableStateOf(false) }
                            var errorText by remember { mutableStateOf<String?>(null) }

                            if (providerReq == null || pkReq == null || parsed == null || origin == null) {
                                LaunchedEffect(Unit) {
                                    val resultIntent = Intent()
                                    PendingIntentHandler.setCreateCredentialException(
                                        resultIntent,
                                        CreateCredentialUnknownException("Yêu cầu tạo Passkey không hợp lệ hoặc nguồn không được tin cậy."),
                                    )
                                    setResult(Activity.RESULT_OK, resultIntent)
                                    finish()
                                }
                            } else {
                                PasskeyConfirmSheet(
                                    title = "Lưu mã khóa (Passkey)",
                                    primaryActionLabel = "Lưu mã khóa vào OpenVault",
                                    rpName = parsed.rpName,
                                    rpId = parsed.rpId,
                                    username = parsed.userName.ifBlank { parsed.userDisplayName },
                                    originLabel = origin,
                                    busy = busy,
                                    errorText = errorText,
                                    onCancel = ::cancelAndFinish,
                                    onConfirm = {
                                        if (busy) return@PasskeyConfirmSheet
                                        busy = true
                                        errorText = null
                                        scope.launch {
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
                                            }.onSuccess { regJson ->
                                                val resultIntent = Intent()
                                                PendingIntentHandler.setCreateCredentialResponse(
                                                    resultIntent,
                                                    CreatePublicKeyCredentialResponse(regJson),
                                                )
                                                setResult(Activity.RESULT_OK, resultIntent)
                                                finish()
                                            }.onFailure { err ->
                                                busy = false
                                                errorText = err.message ?: "Không thể lưu mã khóa vào OpenVault."
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        ACTION_GET_PASSKEY -> {
                            val providerReq = remember {
                                PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                            }
                            val pkOption = remember(providerReq) {
                                providerReq?.credentialOptions
                                    ?.filterIsInstance<GetPublicKeyCredentialOption>()
                                    ?.firstOrNull()
                            }
                            val parsed = remember(pkOption) {
                                pkOption?.requestJson?.let { PasskeyCryptoEngine.parseGetRequestJson(it) }
                            }
                            val origin = remember(providerReq) {
                                providerReq?.callingAppInfo?.let {
                                    PrivilegedBrowserAllowlist.resolveOrigin(this@PasskeyAuthActivity, it)
                                }
                            }
                            val cipherId = intent.getStringExtra(EXTRA_CIPHER_ID).orEmpty()
                            val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID).orEmpty()

                            var resolvedLabel by remember { mutableStateOf<Triple<String, String, String>?>(null) }
                            var busy by remember { mutableStateOf(false) }
                            var errorText by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(cipherId, credentialId) {
                                val pair = container.repository.findPasskeyByIds(cipherId, credentialId)
                                if (pair == null) {
                                    val resultIntent = Intent()
                                    PendingIntentHandler.setGetCredentialException(
                                        resultIntent,
                                        NoCredentialException("Không tìm thấy mã khóa trong kho."),
                                    )
                                    setResult(Activity.RESULT_OK, resultIntent)
                                    finish()
                                } else {
                                    val (item, cred) = pair
                                    resolvedLabel = Triple(
                                        item.name.ifBlank { cred.rpName ?: cred.rpId },
                                        cred.rpId,
                                        cred.userName?.takeIf { it.isNotBlank() } ?: item.login?.username.orEmpty(),
                                    )
                                }
                            }

                            val label = resolvedLabel
                            if (providerReq == null || pkOption == null || parsed == null || origin == null) {
                                LaunchedEffect(Unit) {
                                    val resultIntent = Intent()
                                    PendingIntentHandler.setGetCredentialException(
                                        resultIntent,
                                        GetCredentialUnknownException("Yêu cầu xác thực Passkey không hợp lệ."),
                                    )
                                    setResult(Activity.RESULT_OK, resultIntent)
                                    finish()
                                }
                            } else if (label == null) {
                                BusyPasskeyPlaceholder("Đang chuẩn bị mã khóa...")
                            } else {
                                PasskeyConfirmSheet(
                                    title = "Đăng nhập bằng mã khóa",
                                    primaryActionLabel = "Đăng nhập bằng mã khóa",
                                    rpName = label.first,
                                    rpId = label.second,
                                    username = label.third,
                                    originLabel = origin,
                                    busy = busy,
                                    errorText = errorText,
                                    onCancel = ::cancelAndFinish,
                                    onConfirm = {
                                        if (busy) return@PasskeyConfirmSheet
                                        busy = true
                                        errorText = null
                                        scope.launch {
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
                                            }.onSuccess { authJson ->
                                                val resultIntent = Intent()
                                                PendingIntentHandler.setGetCredentialResponse(
                                                    resultIntent,
                                                    GetCredentialResponse(PublicKeyCredential(authJson)),
                                                )
                                                setResult(Activity.RESULT_OK, resultIntent)
                                                finish()
                                            }.onFailure { err ->
                                                busy = false
                                                errorText = err.message ?: "Không thể ký xác thực Passkey."
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        else -> {
                            LaunchedEffect(Unit) { cancelAndFinish() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusyPasskeyPlaceholder(label: String) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = PasskeyPurple)
            Text(label, color = c.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PasskeyConfirmSheet(
    title: String,
    primaryActionLabel: String,
    rpName: String,
    rpId: String,
    username: String,
    originLabel: String,
    busy: Boolean,
    errorText: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PasskeyPurple),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Text(
                        text = title,
                        color = c.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(Icons.Rounded.Close, contentDescription = "Hủy", tint = c.textSecondary)
                }
            }

            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PasskeyPurple.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (rpName.trim().firstOrNull() ?: rpId.firstOrNull() ?: 'P').uppercase(),
                            color = PasskeyPurple,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = rpName,
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (username.isNotBlank()) {
                            Text(
                                text = username,
                                color = c.textSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = rpId,
                            color = PasskeyPurple,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nguồn đã được xác minh",
                            color = c.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = originLabel,
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText,
                    color = c.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PasskeyPurple,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Đang xử lý...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(primaryActionLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onCancel,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text("Hủy", color = c.textSecondary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
