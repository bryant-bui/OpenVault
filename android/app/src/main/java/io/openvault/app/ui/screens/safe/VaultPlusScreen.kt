package io.openvault.app.ui.screens.safe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.core.crypto.SafeDocumentCategory
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.core.security.SafeLockState
import io.openvault.app.data.DecryptedSafeItem
import io.openvault.app.data.SafeRepository
import io.openvault.app.data.SafeStatusSnapshot
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private enum class SafeFilterOption {
    ALL,
    FAVORITES,
    ID_CARD,
    PASSPORT,
    DRIVER_LICENSE,
    MEDICAL,
    FINANCIAL,
    PHOTO,
    TRASH,
}

private data class PendingUploadPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val suggestedCategory: SafeDocumentCategory,
)

@Composable
fun VaultPlusScreen(
    safeRepository: SafeRepository,
    activity: FragmentActivity,
    onCopySensitive: (label: String, value: String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()

    val status by safeRepository.status.collectAsState()
    val items by safeRepository.items.collectAsState()
    val isSyncing by safeRepository.isSyncing.collectAsState()

    var pendingRecoveryKeyDisplay by remember { mutableStateOf<String?>(null) }
    var viewingItemId by remember { mutableStateOf<String?>(null) }
    var showingCamera by remember { mutableStateOf(false) }
    var pendingUpload by remember { mutableStateOf<PendingUploadPayload?>(null) }

    LaunchedEffect(Unit) {
        runCatching { safeRepository.syncSafe() }
    }

    // Periodic idle auto-lock ticker while unlocked
    LaunchedEffect(status.lockState) {
        while (isActive && status.lockState == SafeLockState.UNLOCKED) {
            delay(1_000L)
            if (!safeRepository.session.enforceAutoLockIfExpired()) {
                viewingItemId = null
                showingCamera = false
                pendingUpload?.bytes?.fill(0)
                pendingUpload = null
            }
        }
    }

    val activeViewerItem = viewingItemId?.let { id -> items.firstOrNull { it.id == id } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        when {
            pendingRecoveryKeyDisplay != null -> {
                SafeRecoveryKeyCreatedScreen(
                    recoveryKey = pendingRecoveryKeyDisplay!!,
                    onCopy = { onCopySensitive("Vault+ Recovery Key", pendingRecoveryKeyDisplay!!) },
                    onDone = { pendingRecoveryKeyDisplay = null },
                )
            }

            showingCamera -> {
                SafeCameraCaptureScreen(
                    onCaptured = { capturedBytes ->
                        showingCamera = false
                        pendingUpload = PendingUploadPayload(
                            fileName = "scan_${System.currentTimeMillis()}.jpg",
                            mimeType = "image/jpeg",
                            bytes = capturedBytes,
                            suggestedCategory = SafeDocumentCategory.ID_CARD,
                        )
                    },
                    onCancel = { showingCamera = false },
                    onError = { onShowMessage(it) },
                )
            }

            activeViewerItem != null && status.lockState == SafeLockState.UNLOCKED -> {
                VaultPlusViewerScreen(
                    item = activeViewerItem,
                    safeRepository = safeRepository,
                    onBack = { viewingItemId = null },
                    onShowMessage = onShowMessage,
                )
            }

            status.lockState == SafeLockState.NOT_INITIALIZED -> {
                SafeSetupScreen(
                    safeRepository = safeRepository,
                    activity = activity,
                    onSetupCreated = { recoveryFormatted ->
                        pendingRecoveryKeyDisplay = recoveryFormatted
                    },
                    onShowMessage = onShowMessage,
                )
            }

            status.lockState == SafeLockState.LOCKED -> {
                SafeLockedScreen(
                    status = status,
                    safeRepository = safeRepository,
                    activity = activity,
                    onSyncRefresh = { scope.launch { runCatching { safeRepository.syncSafe() } } },
                    onRecoveredWithNewKey = { newRecKey ->
                        if (newRecKey != null) {
                            pendingRecoveryKeyDisplay = newRecKey
                        } else {
                            onShowMessage(if (s.isVi) "Đã khôi phục và mở khóa Vault+!" else "Vault+ recovered and unlocked!")
                        }
                    },
                    onShowMessage = onShowMessage,
                )
            }

            else -> {
                SafeUnlockedWorkspace(
                    status = status,
                    items = items,
                    isSyncing = isSyncing,
                    safeRepository = safeRepository,
                    activity = activity,
                    pendingUpload = pendingUpload,
                    onSetPendingUpload = { pendingUpload = it },
                    onOpenCamera = { showingCamera = true },
                    onOpenItem = { viewingItemId = it.id },
                    onShowMessage = onShowMessage,
                )
            }
        }
    }
}

@Composable
private fun SafeSetupScreen(
    safeRepository: SafeRepository,
    activity: FragmentActivity,
    onSetupCreated: (String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val canBio = remember { BiometricKeyManager.isBiometricAvailable(context) }
    var enableBiometric by remember { mutableStateOf(canBio) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = fold.horizontalPadding, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text(s.vaultPlusSetupTitle, color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(s.vaultPlusSubtitle, color = c.textSecondary, fontSize = 13.5.sp)
                }
            }

            OnePassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.vaultPlusSetupDesc, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = if (s.isVi) "• Khóa SafeKey riêng biệt, đồng bộ mã hóa qua Cloudflare R2 & D1.\n• Tự động khóa sau 3 phút, chống chụp màn hình (FLAG_SECURE)."
                        else "• Independent SafeKey with encrypted Cloudflare R2 & D1 storage.\n• Auto-locks after 3 minutes and blocks screenshots (FLAG_SECURE).",
                        color = c.textMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            OnePassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(SafeCrypto.SAFE_PIN_LENGTH)
                            pin = clean
                            errorMsg = null
                        },
                        label = { Text(s.vaultPlusPinLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(SafeCrypto.SAFE_PIN_LENGTH)
                            confirmPin = clean
                            errorMsg = null
                        },
                        label = { Text(s.vaultPlusPinConfirmLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (canBio) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = c.accent)
                                Text(s.vaultPlusEnableBioToggle, color = c.textPrimary, fontSize = 14.sp)
                            }
                            Switch(checked = enableBiometric, onCheckedChange = { enableBiometric = it })
                        }
                    }

                    if (errorMsg != null) {
                        Text(errorMsg.orEmpty(), color = c.danger, fontSize = 13.sp)
                    }

                    Button(
                        enabled = !busy && pin.length == 6 && confirmPin.length == 6,
                        onClick = {
                            if (pin != confirmPin) {
                                errorMsg = if (s.isVi) "Hai mã PIN không khớp." else "PINs do not match."
                                return@Button
                            }
                            errorMsg = null
                            if (enableBiometric && canBio) {
                                val encryptCipher = runCatching { BiometricKeyManager.createSafeBiometricEncryptCipher() }.getOrNull()
                                if (encryptCipher != null) {
                                    BiometricKeyManager.showBiometricPrompt(
                                        activity = activity,
                                        title = s.vaultPlusSetupTitle,
                                        subtitle = s.vaultPlusEnableBioToggle,
                                        negativeText = s.cancel,
                                        cipher = encryptCipher,
                                        onSuccess = { authed ->
                                            busy = true
                                            scope.launch {
                                                runCatching { safeRepository.setupVaultPlus(pin, authed) }
                                                    .onSuccess { rec ->
                                                        busy = false
                                                        onSetupCreated(rec)
                                                    }
                                                    .onFailure { ex ->
                                                        busy = false
                                                        errorMsg = ex.message
                                                    }
                                            }
                                        },
                                        onError = { msg ->
                                            onShowMessage(msg)
                                        },
                                    )
                                    return@Button
                                }
                            }
                            busy = true
                            scope.launch {
                                runCatching { safeRepository.setupVaultPlus(pin, null) }
                                    .onSuccess { rec ->
                                        busy = false
                                        onSetupCreated(rec)
                                    }
                                    .onFailure { ex ->
                                        busy = false
                                        errorMsg = ex.message
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accentButtonFg, strokeWidth = 2.dp)
                        } else {
                            Text(s.vaultPlusCreateAction, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeRecoveryKeyCreatedScreen(
    recoveryKey: String,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val fold = LocalFoldableLayout.current
    var confirmedSaved by remember { mutableStateOf(false) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val text = """
                    |OPENVAULT VAULT+ EMERGENCY RECOVERY KIT
                    |=========================================
                    |Recovery Key:
                    |$recoveryKey
                    |
                    |Keep this key offline in a safe place. If you forget your 6-digit
                    |Vault+ PIN or exceed 15 failed PIN attempts, this key is required
                    |to restore access to your encrypted Vault+ documents.
                """.trimMargin()
                out.write(text.toByteArray(StandardCharsets.UTF_8))
            }
        }.onSuccess {
            confirmedSaved = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = fold.horizontalPadding, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp))
            Text(s.vaultPlusRecoveryTitle, color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(s.vaultPlusRecoveryDesc, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)

            OnePassCard(
                backgroundColor = if (c.isDark) Color(0xFF161922) else Color(0xFFF3F6FC),
                borderColor = c.accent.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = recoveryKey,
                    color = c.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.copy)
                }
                OutlinedButton(
                    onClick = { saveFileLauncher.launch("OpenVault-VaultPlus-RecoveryKey.txt") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.vaultPlusSaveRecoveryFile, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { confirmedSaved = !confirmedSaved }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = confirmedSaved, onCheckedChange = { confirmedSaved = it })
                Spacer(Modifier.width(6.dp))
                Text(s.vaultPlusRecoverySavedCheck, color = c.textPrimary, fontSize = 13.5.sp)
            }

            Button(
                enabled = confirmedSaved,
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(s.vaultPlusFinishSetup, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SafeLockedScreen(
    status: SafeStatusSnapshot,
    safeRepository: SafeRepository,
    activity: FragmentActivity,
    onSyncRefresh: () -> Unit,
    onRecoveredWithNewKey: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    val lockedUntil = status.lockedUntilMs
    val isCoolingDown = lockedUntil != null && lockedUntil > nowMs
    val recoveryRequired = status.recoveryRequired

    // Live cooldown countdown ticker
    LaunchedEffect(lockedUntil) {
        if (lockedUntil != null) {
            while (isActive) {
                nowMs = System.currentTimeMillis()
                if (nowMs >= lockedUntil) {
                    onSyncRefresh()
                    break
                }
                delay(1_000L)
            }
        }
    }

    val canBiometric = status.biometricEnabled &&
        !isCoolingDown &&
        !recoveryRequired &&
        BiometricKeyManager.isBiometricAvailable(context)

    fun triggerBiometric() {
        if (!canBiometric || busy) return
        val blob = safeRepository.preferences.getSafeBiometricBlob() ?: return
        val decryptCipher = runCatching { BiometricKeyManager.createSafeBiometricDecryptCipher(blob) }.getOrNull()
        if (decryptCipher == null) {
            safeRepository.disableBiometric()
            onShowMessage(
                if (s.isVi) "Khóa sinh trắc học đã thay đổi, vui lòng nhập PIN Vault+."
                else "Biometric key changed; please unlock with Vault+ PIN.",
            )
            return
        }
        BiometricKeyManager.showBiometricPrompt(
            activity = activity,
            title = s.tabVaultPlus,
            subtitle = s.unlockWithBiometricsCta,
            negativeText = s.usePin,
            cipher = decryptCipher,
            onSuccess = { authed ->
                busy = true
                scope.launch {
                    val ok = safeRepository.unlockWithBiometricCipher(authed)
                    busy = false
                    if (!ok) {
                        errorMsg = if (s.isVi) "Không thể mở khóa bằng sinh trắc học." else "Biometric unlock failed."
                    }
                }
            },
            onError = { msg -> onShowMessage(msg) },
        )
    }

    LaunchedEffect(canBiometric) {
        if (canBiometric) {
            delay(180L)
            triggerBiometric()
        }
    }

    fun submitPin(candidate: String) {
        if (busy || isCoolingDown || recoveryRequired || candidate.length != 6) return
        busy = true
        errorMsg = null
        scope.launch {
            val res = safeRepository.unlockWithPin(candidate)
            busy = false
            if (!res.success) {
                pin = ""
                errorMsg = res.errorMessage
                val px = with(density) { 10.dp.toPx() }
                for (target in listOf(-px, px, -px * 0.6f, px * 0.6f, 0f)) {
                    shakeOffset.animateTo(target, animationSpec = tween(42))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (recoveryRequired) c.danger.copy(alpha = 0.16f) else c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = if (recoveryRequired) c.danger else c.accent,
                    modifier = Modifier.size(38.dp),
                )
            }

            Text(
                text = when {
                    recoveryRequired -> s.vaultPlusRecoveryRequiredTitle
                    isCoolingDown -> s.vaultPlusCooldownTitle
                    else -> s.vaultPlusLockedTitle
                },
                color = c.textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            if (recoveryRequired) {
                OnePassCard(
                    backgroundColor = c.danger.copy(alpha = 0.10f),
                    borderColor = c.danger.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = s.vaultPlusRecoveryRequiredDesc,
                            color = c.textPrimary,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                        )
                        Button(
                            onClick = { showRecoveryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(s.vaultPlusRecoverCta, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { showWipeDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
                            border = BorderStroke(1.dp, c.danger.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(s.vaultPlusWipeCta, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (isCoolingDown) {
                val remainSec = (((lockedUntil ?: nowMs) - nowMs) / 1000L).coerceAtLeast(0L)
                val mm = remainSec / 60L
                val ss = remainSec % 60L
                OnePassCard(
                    borderColor = Color(0xFFF59E0B).copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = String.format("%02d:%02d", mm, ss),
                            color = Color(0xFFF59E0B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (s.isVi) "Đã nhập sai ${status.failedAttempts}/15 lần. Chờ hết thời gian hoặc khôi phục ngay."
                            else "Failed attempts: ${status.failedAttempts}/15. Wait for timer or use Recovery Key.",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { showRecoveryDialog = true }) {
                            Text(s.vaultPlusRecoverCta, color = c.accent)
                        }
                    }
                }
            } else {
                Text(
                    text = s.vaultPlusEnterPinPrompt,
                    color = c.textSecondary,
                    fontSize = 14.sp,
                )

                val windowFails = status.failedAttempts % SafeCrypto.SAFE_COOLDOWN_AFTER_FAILS
                val remainingBeforeCooldown = SafeCrypto.SAFE_COOLDOWN_AFTER_FAILS - windowFails
                if (status.failedAttempts > 0) {
                    Text(
                        text = if (s.isVi) {
                            "Còn $remainingBeforeCooldown lần thử trước khi tạm khóa 15p • Tổng sai ${status.failedAttempts}/15"
                        } else {
                            "$remainingBeforeCooldown attempts before 15m wait • Total fails ${status.failedAttempts}/15"
                        },
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Box(modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }) {
                    SafeSegmentedPinField(
                        pin = pin,
                        enabled = !busy,
                        onPinChange = { next ->
                            pin = next
                            errorMsg = null
                            if (next.length == SafeCrypto.SAFE_PIN_LENGTH) {
                                submitPin(next)
                            }
                        },
                    )
                }

                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                }
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = c.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                }

                if (canBiometric) {
                    OutlinedButton(
                        onClick = { triggerBiometric() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = c.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(s.unlockWithBiometricsCta)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { showRecoveryDialog = true }) {
                        Text(
                            if (s.isVi) "Quên PIN?" else "Forgot PIN?",
                            color = c.accent,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(onClick = { showWipeDialog = true }) {
                        Text(
                            if (s.isVi) "Đặt lại Két" else "Reset Vault+",
                            color = c.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        SafeRecoveryDialog(
            safeRepository = safeRepository,
            onDismiss = { showRecoveryDialog = false },
            onRecovered = { rotated ->
                showRecoveryDialog = false
                onRecoveredWithNewKey(rotated)
            },
        )
    }

    if (showWipeDialog) {
        SafeNuclearWipeDialog(
            safeRepository = safeRepository,
            onDismiss = { showWipeDialog = false },
            onWiped = {
                showWipeDialog = false
                onShowMessage(if (s.isVi) "Đã xóa toàn bộ dữ liệu Vault+." else "Vault+ completely wiped.")
            },
        )
    }
}

@Composable
private fun SafeSegmentedPinField(
    pin: String,
    enabled: Boolean,
    onPinChange: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { runCatching { focusRequester.requestFocus() } },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = pin,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() }.take(SafeCrypto.SAFE_PIN_LENGTH)
                onPinChange(clean)
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .size(width = 240.dp, height = 52.dp)
                .focusRequester(focusRequester),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until SafeCrypto.SAFE_PIN_LENGTH) {
                if (i == 3) {
                    Text("-", color = c.textMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                val filled = i < pin.length
                val active = i == pin.length
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .border(
                            width = if (active) 1.8.dp else 1.dp,
                            color = if (active) c.accent else c.cardBorder,
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (filled) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(c.textPrimary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeUnlockedWorkspace(
    status: SafeStatusSnapshot,
    items: List<DecryptedSafeItem>,
    isSyncing: Boolean,
    safeRepository: SafeRepository,
    activity: FragmentActivity,
    pendingUpload: PendingUploadPayload?,
    onSetPendingUpload: (PendingUploadPayload?) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenItem: (DecryptedSafeItem) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()

    var activeFilter by remember { mutableStateOf(SafeFilterOption.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var remainSec by remember { mutableLongStateOf(safeRepository.session.remainingAutoLockSeconds()) }

    LaunchedEffect(status.lockState, status.autoLockMinutes) {
        while (isActive) {
            remainSec = safeRepository.session.remainingAutoLockSeconds()
            delay(1_000L)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { readPickedDocument(context, uri) }
            loaded.onSuccess { payload ->
                if (payload.bytes.size > SafeCrypto.SAFE_MAX_FILE_BYTES) {
                    onShowMessage(
                        if (s.isVi) "Tệp vượt quá giới hạn tối đa 5 MB (${formatBytes(payload.bytes.size.toLong())})."
                        else "File exceeds 5 MB maximum (${formatBytes(payload.bytes.size.toLong())}).",
                    )
                } else {
                    onSetPendingUpload(payload)
                }
            }.onFailure { ex ->
                onShowMessage(ex.message ?: "Error reading file")
            }
        }
    }

    val filtered = remember(items, activeFilter) {
        items.filter { item ->
            when (activeFilter) {
                SafeFilterOption.TRASH -> item.deletedAt != null
                SafeFilterOption.ALL -> item.deletedAt == null
                SafeFilterOption.FAVORITES -> item.deletedAt == null && item.favorite
                SafeFilterOption.ID_CARD -> item.deletedAt == null && item.category == SafeDocumentCategory.ID_CARD
                SafeFilterOption.PASSPORT -> item.deletedAt == null && item.category == SafeDocumentCategory.PASSPORT
                SafeFilterOption.DRIVER_LICENSE -> item.deletedAt == null && item.category == SafeDocumentCategory.DRIVER_LICENSE
                SafeFilterOption.MEDICAL -> item.deletedAt == null && item.category == SafeDocumentCategory.MEDICAL
                SafeFilterOption.FINANCIAL -> item.deletedAt == null && item.category == SafeDocumentCategory.FINANCIAL
                SafeFilterOption.PHOTO -> item.deletedAt == null && item.category == SafeDocumentCategory.PHOTO
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.tabVaultPlus, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${formatBytes(status.usedBytes)} · ${status.itemCount} ${if (s.isVi) "mục" else "files"} · ${String.format("%d:%02d", remainSec / 60L, remainSec % 60L)}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    enabled = !isSyncing,
                    onClick = { scope.launch { runCatching { safeRepository.syncSafe() } } },
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = s.syncNow, tint = c.textSecondary)
                    }
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = s.settings, tint = c.textSecondary)
                }
                Surface(
                    onClick = { safeRepository.session.lock() },
                    shape = RoundedCornerShape(10.dp),
                    color = c.accentSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = s.vaultPlusLockNow, tint = c.accent, modifier = Modifier.size(16.dp))
                        Text(s.vaultPlusLockNow, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = fold.horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                items(SafeFilterOption.entries) { opt ->
                    val selected = opt == activeFilter
                    Surface(
                        onClick = {
                            safeRepository.session.touchActivity()
                            activeFilter = opt
                        },
                        shape = CircleShape,
                        color = if (selected) c.accentButtonBg else c.surface,
                        border = if (selected) null else BorderStroke(0.8.dp, c.cardBorder),
                    ) {
                        Text(
                            text = filterChipLabel(opt, s.isVi),
                            color = if (selected) c.accentButtonFg else c.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 72.dp),
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
                        Text(s.vaultPlusEmptyTitle, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(s.vaultPlusEmptyHint, color = c.textSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = fold.horizontalPadding,
                        end = fold.horizontalPadding,
                        top = 4.dp,
                        bottom = 152.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        SafeDocumentRowCard(
                            item = item,
                            safeRepository = safeRepository,
                            isVi = s.isVi,
                            onClick = {
                                safeRepository.session.touchActivity()
                                onOpenItem(item)
                            },
                        )
                    }
                }
            }
        }

        // Dedicated Vault+ Add Document FAB
        Surface(
            onClick = {
                safeRepository.session.touchActivity()
                showAddSheet = true
            },
            shape = RoundedCornerShape(16.dp),
            color = c.accentButtonBg,
            contentColor = c.accentButtonFg,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = fold.horizontalPadding, bottom = if (fold.isTwoPane) 20.dp else 84.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(s.vaultPlusAddDocument, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showAddSheet) {
        AlertDialog(
            onDismissRequest = { showAddSheet = false },
            title = { Text(s.vaultPlusAddDocument) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnePassCard(
                        onClick = {
                            showAddSheet = false
                            filePicker.launch(arrayOf("application/pdf", "image/*"))
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = c.accent)
                            Text(s.vaultPlusChooseFile, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    OnePassCard(
                        onClick = {
                            showAddSheet = false
                            onOpenCamera()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = c.accent)
                            Text(s.vaultPlusTakePhoto, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSheet = false }) { Text(s.cancel) }
            },
        )
    }

    if (pendingUpload != null) {
        val upload = pendingUpload
        SafeMetadataEditDialog(
            initialTitle = upload.fileName.substringBeforeLast('.'),
            initialCategory = upload.suggestedCategory,
            initialNotes = "",
            initialExpiresAt = "",
            isVi = s.isVi,
            onDismiss = {
                upload.bytes.fill(0)
                onSetPendingUpload(null)
            },
            onSave = { title, cat, notes, exp ->
                uploading = true
                scope.launch {
                    runCatching {
                        safeRepository.uploadDocument(
                            title = title,
                            fileName = upload.fileName,
                            mimeType = upload.mimeType,
                            category = cat,
                            notes = notes,
                            expiresAt = exp,
                            rawBytes = upload.bytes,
                        )
                    }.onSuccess {
                        upload.bytes.fill(0)
                        onSetPendingUpload(null)
                        uploading = false
                        onShowMessage(if (s.isVi) "Đã mã hóa và lưu vào Vault+!" else "Encrypted & saved to Vault+!")
                    }.onFailure { ex ->
                        uploading = false
                        onShowMessage(ex.message ?: "Upload failed")
                    }
                }
            },
        )
    }

    if (uploading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (s.isVi) "Đang mã hóa & tải lên…" else "Encrypting & uploading…") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }

    if (showSettingsDialog) {
        SafeSettingsDialog(
            status = status,
            safeRepository = safeRepository,
            activity = activity,
            onDismiss = { showSettingsDialog = false },
            onShowMessage = onShowMessage,
        )
    }
}

@Composable
private fun SafeDocumentRowCard(
    item: DecryptedSafeItem,
    safeRepository: SafeRepository,
    isVi: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var thumbCompose by remember(item.id, item.hasThumbnail) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.id, item.hasThumbnail) {
        if (item.hasThumbnail) {
            val bytes = safeRepository.loadThumbnailBytes(item)
            if (bytes != null) {
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bmp != null) thumbCompose = bmp.asImageBitmap()
            }
        }
    }

    OnePassCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbCompose != null) {
                    Image(
                        bitmap = thumbCompose!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (item.metadata.mimeType == "application/pdf") {
                            Icons.Rounded.PictureAsPdf
                        } else {
                            categoryIcon(item.category)
                        },
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.metadata.title,
                        color = c.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.favorite) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                    }
                }
                Text(
                    text = "${categoryLabel(item.category, isVi)} · ${formatBytes(item.metadata.originalSize.toLong())}",
                    color = c.textSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.metadata.expiresAt.isNullOrBlank()) {
                    Text(
                        text = (if (isVi) "HSD: " else "Exp: ") + item.metadata.expiresAt,
                        color = c.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeSettingsDialog(
    status: SafeStatusSnapshot,
    safeRepository: SafeRepository,
    activity: FragmentActivity,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canBio = remember { BiometricKeyManager.isBiometricAvailable(context) }

    var showChangePin by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vault+ ${s.settings.removeSuffix("…")}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (canBio) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.vaultPlusEnableBioToggle, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(
                            checked = status.biometricEnabled,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    safeRepository.disableBiometric()
                                } else {
                                    val cipher = runCatching { BiometricKeyManager.createSafeBiometricEncryptCipher() }.getOrNull()
                                    if (cipher != null) {
                                        BiometricKeyManager.showBiometricPrompt(
                                            activity = activity,
                                            title = s.tabVaultPlus,
                                            subtitle = s.vaultPlusEnableBioToggle,
                                            negativeText = s.cancel,
                                            cipher = cipher,
                                            onSuccess = { authed ->
                                                safeRepository.enableBiometricWithCipher(authed)
                                                onShowMessage(if (s.isVi) "Đã bật sinh trắc học cho Vault+." else "Vault+ biometrics enabled.")
                                            },
                                            onError = { onShowMessage(it) },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (s.isVi) "Tự động khóa sau" else "Auto-lock after",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5, 10).forEach { mins ->
                            val active = status.autoLockMinutes == mins
                            Surface(
                                onClick = {
                                    scope.launch {
                                        runCatching { safeRepository.updateAutoLockMinutes(mins) }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) c.accentButtonBg else c.surface,
                                border = if (active) null else BorderStroke(0.8.dp, c.cardBorder),
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = if (active) c.accentButtonFg else c.textPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showChangePin = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (s.isVi) "Đổi mã PIN 6 số" else "Change 6-Digit PIN")
                }

                OutlinedButton(
                    onClick = { showWipe = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.vaultPlusWipeCta)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.done) }
        },
    )

    if (showChangePin) {
        SafeChangePinDialog(
            safeRepository = safeRepository,
            onDismiss = { showChangePin = false },
            onChanged = {
                showChangePin = false
                onShowMessage(if (s.isVi) "Đã đổi mã PIN Vault+." else "Vault+ PIN updated.")
            },
        )
    }

    if (showWipe) {
        SafeNuclearWipeDialog(
            safeRepository = safeRepository,
            onDismiss = { showWipe = false },
            onWiped = {
                showWipe = false
                onDismiss()
                onShowMessage(if (s.isVi) "Đã xóa toàn bộ Vault+." else "Vault+ wiped.")
            },
        )
    }
}

@Composable
private fun SafeChangePinDialog(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.isVi) "Đổi Mã PIN Vault+" else "Change Vault+ PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(if (s.isVi) "PIN hiện tại (6 số)" else "Current 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(if (s.isVi) "PIN mới (6 số)" else "New 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(s.vaultPlusPinConfirmLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (err != null) Text(err.orEmpty(), color = c.danger, fontSize = 12.5.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && currentPin.length == 6 && newPin.length == 6 && confirmPin.length == 6,
                onClick = {
                    if (newPin != confirmPin) {
                        err = if (s.isVi) "PIN mới không khớp." else "New PINs do not match."
                        return@Button
                    }
                    busy = true
                    err = null
                    scope.launch {
                        runCatching { safeRepository.changePin(currentPin, newPin) }
                            .onSuccess {
                                busy = false
                                onChanged()
                            }
                            .onFailure { ex ->
                                busy = false
                                err = ex.message
                            }
                    }
                },
            ) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

@Composable
private fun SafeRecoveryDialog(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onRecovered: (String?) -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    var recoveryKey by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var rotateKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.vaultPlusRecoverCta) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = recoveryKey,
                    onValueChange = { recoveryKey = it; err = null },
                    label = { Text("Recovery Key (OVRK-XXXX-...)") },
                    minLines = 2,
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(6); err = null },
                    label = { Text(if (s.isVi) "Mã PIN mới (6 số)" else "New 6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); err = null },
                    label = { Text(s.vaultPlusPinConfirmLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rotateKey, onCheckedChange = { rotateKey = it })
                    Text(
                        if (s.isVi) "Tạo Recovery Key mới sau khi khôi phục" else "Generate a new Recovery Key",
                        fontSize = 13.sp,
                    )
                }
                if (err != null) Text(err.orEmpty(), color = c.danger, fontSize = 12.5.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && recoveryKey.isNotBlank() && newPin.length == 6 && confirmPin.length == 6,
                onClick = {
                    if (newPin != confirmPin) {
                        err = if (s.isVi) "Hai mã PIN không khớp." else "PINs do not match."
                        return@Button
                    }
                    busy = true
                    err = null
                    scope.launch {
                        runCatching { safeRepository.recoverWithRecoveryKey(recoveryKey, newPin, rotateKey) }
                            .onSuccess { newKey ->
                                busy = false
                                onRecovered(newKey)
                            }
                            .onFailure { ex ->
                                busy = false
                                err = ex.message
                            }
                    }
                },
            ) {
                Text(s.restore)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

@Composable
private fun SafeNuclearWipeDialog(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onWiped: () -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    var masterPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.vaultPlusWipeCta, color = c.danger) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (s.isVi) "Hành động này sẽ xóa vĩnh viễn toàn bộ tài liệu trong Vault+ trên Cloudflare R2 & D1. Nhập Master Password để xác nhận."
                    else "This permanently deletes all Vault+ files in Cloudflare R2 & D1. Enter your Master Password to confirm.",
                    fontSize = 13.5.sp,
                )
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it; err = null },
                    label = { Text(s.masterPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (err != null) Text(err.orEmpty(), color = c.danger, fontSize = 12.5.sp)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && masterPassword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        runCatching { safeRepository.resetVaultPlus(masterPassword) }
                            .onSuccess {
                                busy = false
                                onWiped()
                            }
                            .onFailure { ex ->
                                busy = false
                                err = ex.message
                            }
                    }
                },
            ) {
                Text(s.deletePermanently, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

@Composable
private fun SafeCameraCaptureScreen(
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    BackHandler(onBack = onCancel)

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (s.isVi) "Cần quyền Camera để chụp tài liệu trực tiếp vào RAM (không lưu ra Thư viện)."
                else "Camera permission is required to capture documents straight into RAM.",
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel) { Text(s.cancel) }
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(if (s.isVi) "Cấp quyền Camera" else "Grant Camera")
                }
            }
        }
        return
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var capturing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = Color.White)
            }
            Text(
                text = if (s.isVi) "Chụp trực tiếp vào RAM (Không lưu Gallery)" else "Direct RAM Capture (No Gallery Save)",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.size(40.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Surface(
                onClick = {
                    if (capturing) return@Surface
                    capturing = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = try {
                                    extractBoundedJpegBytes(image)
                                } finally {
                                    image.close()
                                }
                                capturing = false
                                if (bytes != null) onCaptured(bytes)
                                else onError(if (s.isVi) "Không thể xử lý ảnh chụp." else "Failed to process captured image.")
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                onError(exception.message ?: "Camera error")
                            }
                        },
                    )
                },
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color(0xFF0572EC), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

private fun extractBoundedJpegBytes(image: ImageProxy): ByteArray? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0 && raw.size <= SafeCrypto.SAFE_MAX_FILE_BYTES) {
        return raw
    }
    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw.takeIf { it.size <= SafeCrypto.SAFE_MAX_FILE_BYTES }
    val rotated = if (rotation != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else {
        decoded
    }
    return try {
        var quality = 88
        var best: ByteArray
        do {
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
            best = out.toByteArray()
            quality -= 12
        } while (best.size > SafeCrypto.SAFE_MAX_FILE_BYTES && quality >= 35)
        best.takeIf { it.size <= SafeCrypto.SAFE_MAX_FILE_BYTES }
    } finally {
        rotated.recycle()
    }
}

private fun readPickedDocument(context: Context, uri: Uri): Result<PendingUploadPayload> = runCatching {
    val cr = context.contentResolver
    val mime = cr.getType(uri) ?: "application/octet-stream"
    require(mime == "application/pdf" || mime.startsWith("image/")) {
        "Chỉ hỗ trợ tệp PDF hoặc Ảnh (JPEG, PNG, WebP, HEIC)."
    }

    var fileName = "document"
    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
        }
    }

    val bytes = cr.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            require(total <= SafeCrypto.SAFE_MAX_FILE_BYTES) {
                "Tệp vượt quá giới hạn tối đa 5 MB."
            }
            out.write(buf, 0, n)
        }
        out.toByteArray()
    } ?: error("Không thể đọc nội dung tệp.")

    val suggestedCat = if (mime.startsWith("image/")) {
        SafeDocumentCategory.PHOTO
    } else {
        SafeDocumentCategory.ID_CARD
    }
    PendingUploadPayload(fileName, mime, bytes, suggestedCat)
}

private fun filterChipLabel(opt: SafeFilterOption, isVi: Boolean): String = when (opt) {
    SafeFilterOption.ALL -> if (isVi) "Tất cả" else "All"
    SafeFilterOption.FAVORITES -> if (isVi) "Yêu thích" else "Favorites"
    SafeFilterOption.ID_CARD -> if (isVi) "CCCD" else "ID Card"
    SafeFilterOption.PASSPORT -> if (isVi) "Hộ chiếu" else "Passport"
    SafeFilterOption.DRIVER_LICENSE -> if (isVi) "Bằng lái" else "License"
    SafeFilterOption.MEDICAL -> if (isVi) "Y tế" else "Medical"
    SafeFilterOption.FINANCIAL -> if (isVi) "Tài chính" else "Finance"
    SafeFilterOption.PHOTO -> if (isVi) "Ảnh" else "Photos"
    SafeFilterOption.TRASH -> if (isVi) "Thùng rác" else "Trash"
}

private fun categoryIcon(category: SafeDocumentCategory): ImageVector = when (category) {
    SafeDocumentCategory.ID_CARD -> Icons.Rounded.Badge
    SafeDocumentCategory.PASSPORT -> Icons.Rounded.Flight
    SafeDocumentCategory.DRIVER_LICENSE -> Icons.Rounded.DirectionsCar
    SafeDocumentCategory.MEDICAL -> Icons.Rounded.LocalHospital
    SafeDocumentCategory.FINANCIAL -> Icons.Rounded.Payments
    SafeDocumentCategory.PHOTO -> Icons.Rounded.Image
    SafeDocumentCategory.OTHER -> Icons.Rounded.Description
}
