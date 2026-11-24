package io.openvault.app.ui.screens.safe

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

private enum class SafeViewLayoutMode {
    LIST,
    GRID,
}

private enum class SafeSortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
}

private data class SafeFolderEntry(
    val name: String,
    val itemCount: Int,
    val latestIso: String?,
)

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

            activeViewerItem != null && status.lockState == SafeLockState.UNLOCKED -> {
                VaultPlusViewerScreen(
                    item = activeViewerItem,
                    safeRepository = safeRepository,
                    onBack = { viewingItemId = null },
                    onShowMessage = onShowMessage,
                )
            }

            status.lockState == SafeLockState.NOT_INITIALIZED -> {
                SafeSetupFlow(
                    safeRepository = safeRepository,
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

        if (showingCamera && status.lockState == SafeLockState.UNLOCKED) {
            SafeCameraCaptureDialog(
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
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

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

    // NOTE: Biometrics is NEVER auto-prompted on lock; user must explicitly tap it.
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
            subtitle = s.vaultPlusEnableBioToggle,
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

    fun submitPin(candidate: String) {
        if (busy || isCoolingDown || recoveryRequired || candidate.length != SafeCrypto.SAFE_PIN_LENGTH) return
        busy = true
        errorMsg = null
        scope.launch {
            val res = safeRepository.unlockWithPin(candidate)
            busy = false
            if (!res.success) {
                pin = ""
                errorMsg = res.errorMessage
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val px = with(density) { 10.dp.toPx() }
                for (target in listOf(-px, px, -px * 0.6f, px * 0.6f, 0f)) {
                    shakeOffset.animateTo(target, animationSpec = tween(40))
                }
            }
        }
    }

    val bottomPadding = if (fold.isTwoPane) 20.dp else 86.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Friendly Crest Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (recoveryRequired) c.dangerSoft else c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = if (recoveryRequired) c.danger else c.accent,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    recoveryRequired -> s.vaultPlusRecoveryRequiredTitle
                    isCoolingDown -> s.vaultPlusCooldownTitle
                    else -> s.vaultPlusLockedTitle
                },
                color = c.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            when {
                recoveryRequired -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    OnePassCard(
                        backgroundColor = c.dangerSoft,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(s.vaultPlusRecoverCta, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { showWipeDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
                                border = BorderStroke(1.dp, c.danger.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(s.vaultPlusWipeCta, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                isCoolingDown -> {
                    val remainSec = (((lockedUntil ?: nowMs) - nowMs) / 1000L).coerceAtLeast(0L)
                    val mm = remainSec / 60L
                    val ss = remainSec % 60L
                    Spacer(modifier = Modifier.height(12.dp))
                    OnePassCard(
                        borderColor = Color(0xFFF59E0B).copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = String.format("%02d:%02d", mm, ss),
                                color = Color(0xFFF59E0B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (s.isVi) "Đã nhập sai ${status.failedAttempts}/15 lần. Chờ hết thời gian hoặc khôi phục ngay."
                                else "Failed attempts: ${status.failedAttempts}/15. Wait for timer or use Recovery Key.",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { showRecoveryDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(s.vaultPlusRecoverCta, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        text = s.vaultPlusEnterPinPrompt,
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )

                    val windowFails = status.failedAttempts % SafeCrypto.SAFE_COOLDOWN_AFTER_FAILS
                    val remainingBeforeCooldown = SafeCrypto.SAFE_COOLDOWN_AFTER_FAILS - windowFails
                    if (status.failedAttempts > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = if (s.isVi) {
                                    "Còn $remainingBeforeCooldown lần thử · Tổng sai ${status.failedAttempts}/15"
                                } else {
                                    "$remainingBeforeCooldown attempts left · Total fails ${status.failedAttempts}/15"
                                },
                                color = Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SafePinIndicatorRow(
                        pinLength = pin.length,
                        targetLength = SafeCrypto.SAFE_PIN_LENGTH,
                        isError = errorMsg != null,
                        enabled = !busy,
                        shakeOffsetPx = shakeOffset.value,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier.height(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            busy -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = c.accent,
                                strokeWidth = 2.dp,
                            )

                            errorMsg != null -> Text(
                                text = errorMsg.orEmpty(),
                                color = c.danger,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SafeNumericKeypad(
                        enabled = !busy,
                        showBiometric = canBiometric,
                        onDigit = { d ->
                            errorMsg = null
                            if (pin.length < SafeCrypto.SAFE_PIN_LENGTH) {
                                val next = pin + d
                                pin = next
                                if (next.length == SafeCrypto.SAFE_PIN_LENGTH) {
                                    submitPin(next)
                                }
                            }
                        },
                        onBackspace = {
                            errorMsg = null
                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                        },
                        onBiometricClick = ::triggerBiometric,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showRecoveryDialog = true }) {
                            Text(
                                text = if (s.isVi) "Quên PIN?" else "Forgot PIN?",
                                color = c.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text("·", color = c.textMuted)
                        TextButton(onClick = { showWipeDialog = true }) {
                            Text(
                                text = if (s.isVi) "Đặt lại Két" else "Reset Vault+",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        SafeRecoveryBottomSheet(
            safeRepository = safeRepository,
            onDismiss = { showRecoveryDialog = false },
            onRecovered = { rotated ->
                showRecoveryDialog = false
                onRecoveredWithNewKey(rotated)
            },
        )
    }

    if (showWipeDialog) {
        SafeNuclearWipeBottomSheet(
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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val folders by safeRepository.folders.collectAsState()

    var activeFilter by remember { mutableStateOf(SafeFilterOption.ALL) }
    var activeFolder by remember { mutableStateOf<String?>(null) }
    var viewMode by remember {
        mutableStateOf(
            runCatching { SafeViewLayoutMode.valueOf(safeRepository.preferences.getSafeViewMode()) }
                .getOrDefault(SafeViewLayoutMode.LIST),
        )
    }
    var sortOrder by remember {
        mutableStateOf(
            runCatching { SafeSortOrder.valueOf(safeRepository.preferences.getSafeSortMode()) }
                .getOrDefault(SafeSortOrder.NAME_ASC),
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var searchCompactOverride by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCreateFolderSheet by remember { mutableStateOf(false) }

    var actionFolderTarget by remember { mutableStateOf<SafeFolderEntry?>(null) }
    var renameFolderTarget by remember { mutableStateOf<String?>(null) }

    var actionItemTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var editMetadataTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var moveItemTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var notesItemTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var detailsItemTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var showExportConfirmForItem by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var pendingExportItem by remember { mutableStateOf<DecryptedSafeItem?>(null) }
    var offlineVersion by remember { mutableIntStateOf(0) }

    var uploading by remember { mutableStateOf(false) }
    var remainSec by remember { mutableLongStateOf(safeRepository.session.remainingAutoLockSeconds()) }

    val thumbCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(items) {
        for (item in items) {
            if (item.hasThumbnail && thumbCache[item.id] == null) {
                val bytes = safeRepository.loadThumbnailBytes(item)
                if (bytes != null) {
                    val bmp = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (bmp != null) thumbCache[item.id] = bmp
                }
            }
        }
    }

    LaunchedEffect(status.lockState, status.autoLockMinutes) {
        while (isActive) {
            remainSec = safeRepository.session.remainingAutoLockSeconds()
            delay(1_000L)
        }
    }

    // Track scroll direction to smoothly morph bottom bar (Image 5 -> Image 4)
    LaunchedEffect(viewMode, listState, gridState) {
        var lastIdx = 0
        var lastOffset = 0
        if (viewMode == SafeViewLayoutMode.LIST) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (idx, off) ->
                    val movedDown = idx > lastIdx || (idx == lastIdx && off > lastOffset + 10)
                    val movedUp = idx < lastIdx || (idx == lastIdx && off < lastOffset - 10)
                    when {
                        idx == 0 && off <= 24 -> searchCompactOverride = false
                        movedDown -> searchCompactOverride = true
                        movedUp -> searchCompactOverride = false
                    }
                    lastIdx = idx
                    lastOffset = off
                }
        } else {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (idx, off) ->
                    val movedDown = idx > lastIdx || (idx == lastIdx && off > lastOffset + 10)
                    val movedUp = idx < lastIdx || (idx == lastIdx && off < lastOffset - 10)
                    when {
                        idx == 0 && off <= 24 -> searchCompactOverride = false
                        movedDown -> searchCompactOverride = true
                        movedUp -> searchCompactOverride = false
                    }
                    lastIdx = idx
                    lastOffset = off
                }
        }
    }

    BackHandler(enabled = activeFolder != null) {
        activeFolder = null
    }

    fun handlePickedUri(uri: Uri) {
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

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) handlePickedUri(uri)
    }

    val exportItemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val itemToExport = pendingExportItem ?: return@rememberLauncherForActivityResult
        pendingExportItem = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val plain = safeRepository.loadDocumentBytes(itemToExport)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(plain)
                    } ?: error("Không thể mở tệp đích.")
                }
                plain.fill(0)
            }.onSuccess {
                onShowMessage(if (s.isVi) "Đã tải bản giải mã xuống thiết bị." else "Downloaded decrypted copy.")
            }.onFailure { ex ->
                onShowMessage(ex.message ?: "Download failed")
            }
        }
    }

    val visibleFolders = remember(folders, items, activeFolder, activeFilter, searchQuery, sortOrder) {
        if (activeFolder != null || activeFilter != SafeFilterOption.ALL) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            val mapped = folders.map { name ->
                val inFolder = items.filter { it.deletedAt == null && it.metadata.folderName == name }
                SafeFolderEntry(
                    name = name,
                    itemCount = inFolder.size,
                    latestIso = inFolder.maxOfOrNull { it.updatedAt ?: it.createdAt.orEmpty() },
                )
            }.filter { q.isEmpty() || it.name.lowercase().contains(q) }

            when (sortOrder) {
                SafeSortOrder.NAME_ASC -> mapped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                SafeSortOrder.NAME_DESC -> mapped.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                SafeSortOrder.DATE_DESC -> mapped.sortedByDescending { it.latestIso.orEmpty() }
                SafeSortOrder.DATE_ASC -> mapped.sortedBy { it.latestIso.orEmpty() }
                SafeSortOrder.SIZE_DESC -> mapped.sortedByDescending { it.itemCount }
            }
        }
    }

    val visibleItems = remember(items, activeFilter, activeFolder, searchQuery, sortOrder) {
        val q = searchQuery.trim().lowercase()
        val matching = items.filter { item ->
            val categoryMatch = when (activeFilter) {
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
            if (!categoryMatch) return@filter false

            val folderMatch = when {
                activeFilter == SafeFilterOption.TRASH -> true
                activeFolder != null -> item.metadata.folderName == activeFolder
                activeFilter == SafeFilterOption.ALL && q.isEmpty() -> item.metadata.folderName.isNullOrBlank()
                else -> true
            }
            if (!folderMatch) return@filter false

            if (q.isEmpty()) true
            else {
                item.metadata.title.lowercase().contains(q) ||
                    item.metadata.fileName.lowercase().contains(q) ||
                    (item.metadata.folderName?.lowercase()?.contains(q) == true)
            }
        }

        when (sortOrder) {
            SafeSortOrder.NAME_ASC -> matching.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.metadata.title })
            SafeSortOrder.NAME_DESC -> matching.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.metadata.title })
            SafeSortOrder.DATE_DESC -> matching.sortedByDescending { it.updatedAt ?: it.createdAt.orEmpty() }
            SafeSortOrder.DATE_ASC -> matching.sortedBy { it.updatedAt ?: it.createdAt.orEmpty() }
            SafeSortOrder.SIZE_DESC -> matching.sortedByDescending { it.metadata.originalSize }
        }
    }

    val bottomClearance = if (fold.isTwoPane) 16.dp else 98.dp
    val showExpandedBar = searchQuery.isNotEmpty() || searchFocused || !searchCompactOverride

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Single-line Header (root Vault+ OR inside Folder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeFolder != null) {
                    Surface(
                        onClick = { activeFolder = null },
                        shape = CircleShape,
                        color = c.surface,
                        border = BorderStroke(0.8.dp, c.cardBorder),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary, modifier = Modifier.size(19.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, tint = c.accent, modifier = Modifier.size(21.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeFolder ?: s.tabVaultPlus,
                        color = c.textPrimary,
                        fontSize = if (fold.isNarrowCover) 18.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatBytes(status.usedBytes)} · ${status.itemCount} ${if (s.isVi) "mục" else "files"} · ${String.format("%d:%02d", remainSec / 60L, remainSec % 60L)}",
                        color = c.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    onClick = { scope.launch { runCatching { safeRepository.syncSafe() } } },
                    enabled = !isSyncing,
                    shape = CircleShape,
                    color = c.surface,
                    border = BorderStroke(0.8.dp, c.cardBorder),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = s.syncNow, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Surface(
                    onClick = { showSettingsSheet = true },
                    shape = CircleShape,
                    color = c.surface,
                    border = BorderStroke(0.8.dp, c.cardBorder),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settings, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    onClick = { safeRepository.session.lock() },
                    shape = CircleShape,
                    color = c.accentSoft,
                    border = BorderStroke(0.8.dp, c.accent.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (fold.isNarrowCover) 9.dp else 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = s.vaultPlusLockNow, tint = c.accent, modifier = Modifier.size(15.dp))
                        if (!fold.isNarrowCover) {
                            Text(
                                text = if (s.isVi) "Khóa" else "Lock",
                                color = c.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // Category Filter Chips (when at root)
            if (activeFolder == null) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = fold.horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
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
            }

            // OneDrive-style Sort & Layout Sub-bar ("Tên v" + Create Folder + View Mode Tune button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = sortLabel(sortOrder, s.isVi),
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (c.isDark) Color(0xFF23252A) else Color.White,
                    ) {
                        SafeSortOrder.entries.forEach { order ->
                            val selected = sortOrder == order
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortMenuTitle(order, s.isVi),
                                        color = if (selected) c.accent else c.textPrimary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    sortOrder = order
                                    safeRepository.preferences.setSafeSortMode(order.name)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (activeFolder == null && activeFilter == SafeFilterOption.ALL) {
                        IconButton(
                            onClick = { showCreateFolderSheet = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = if (s.isVi) "Tạo thư mục" else "New Folder",
                                tint = c.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showViewMenu = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = if (s.isVi) "Xem dưới dạng" else "View layout",
                                tint = c.textPrimary,
                                modifier = Modifier.size(21.dp),
                            )
                        }

                        // Exact Image 1 & 2 "Xem dưới dạng: Danh sách / Ô xếp" Popup
                        DropdownMenu(
                            expanded = showViewMenu,
                            onDismissRequest = { showViewMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = if (c.isDark) Color(0xFF23252A) else Color.White,
                        ) {
                            Text(
                                text = if (s.isVi) "Xem dưới dạng:" else "View as:",
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (s.isVi) "Danh sách" else "List",
                                        color = c.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = if (viewMode == SafeViewLayoutMode.LIST) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    if (viewMode == SafeViewLayoutMode.LIST) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = null,
                                        tint = if (viewMode == SafeViewLayoutMode.LIST) c.accent else c.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    viewMode = SafeViewLayoutMode.LIST
                                    safeRepository.preferences.setSafeViewMode(SafeViewLayoutMode.LIST.name)
                                    showViewMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (s.isVi) "Ô xếp" else "Grid",
                                        color = c.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = if (viewMode == SafeViewLayoutMode.GRID) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    if (viewMode == SafeViewLayoutMode.GRID) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        tint = if (viewMode == SafeViewLayoutMode.GRID) c.accent else c.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    viewMode = SafeViewLayoutMode.GRID
                                    safeRepository.preferences.setSafeViewMode(SafeViewLayoutMode.GRID.name)
                                    showViewMenu = false
                                },
                            )
                        }
                    }
                }
            }

            if (visibleFolders.isEmpty() && visibleItems.isEmpty()) {
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
                        modifier = Modifier.padding(bottom = 104.dp),
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(48.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> s.noMatchingItems
                                activeFolder != null -> if (s.isVi) "Thư mục \"$activeFolder\" chưa có tệp" else "Folder \"$activeFolder\" is empty"
                                else -> s.vaultPlusEmptyTitle
                            },
                            color = c.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (activeFolder != null) {
                                if (s.isVi) "Nhấn nút + bên dưới để thêm tài liệu vào thư mục này." else "Tap + below to add documents into this folder."
                            } else {
                                s.vaultPlusEmptyHint
                            },
                            color = c.textSecondary,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (viewMode == SafeViewLayoutMode.GRID) {
                val cols = if (fold.isTwoPane) 4 else 2
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = fold.horizontalPadding,
                        end = fold.horizontalPadding,
                        top = 6.dp,
                        bottom = 184.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(visibleFolders, key = { "folder_${it.name}" }) { folder ->
                        SafeFolderGridTile(
                            folder = folder,
                            isVi = s.isVi,
                            onClick = {
                                safeRepository.session.touchActivity()
                                activeFolder = folder.name
                            },
                            onMoreClick = {
                                safeRepository.session.touchActivity()
                                actionFolderTarget = folder
                            },
                        )
                    }
                    items(visibleItems, key = { it.id }) { item ->
                        SafeDocumentGridTile(
                            item = item,
                            thumbBitmap = thumbCache[item.id],
                            isVi = s.isVi,
                            onClick = {
                                safeRepository.session.touchActivity()
                                onOpenItem(item)
                            },
                            onMoreClick = {
                                safeRepository.session.touchActivity()
                                actionItemTarget = item
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = fold.horizontalPadding,
                        end = fold.horizontalPadding,
                        top = 4.dp,
                        bottom = 184.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visibleFolders, key = { "folder_${it.name}" }) { folder ->
                        SafeFolderListRow(
                            folder = folder,
                            isVi = s.isVi,
                            onClick = {
                                safeRepository.session.touchActivity()
                                activeFolder = folder.name
                            },
                            onMoreClick = {
                                safeRepository.session.touchActivity()
                                actionFolderTarget = folder
                            },
                        )
                    }
                    items(visibleItems, key = { it.id }) { item ->
                        SafeDocumentListRow(
                            item = item,
                            thumbBitmap = thumbCache[item.id],
                            isCachedOffline = remember(item.id, offlineVersion) {
                                safeRepository.isCachedOffline(item.id)
                            },
                            isVi = s.isVi,
                            onClick = {
                                safeRepository.session.touchActivity()
                                onOpenItem(item)
                            },
                            onMoreClick = {
                                safeRepository.session.touchActivity()
                                actionItemTarget = item
                            },
                        )
                    }
                }
            }
        }

        // Morphing Bottom Floating Search + Add Bar (Images 4 & 5)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = fold.horizontalPadding)
                .padding(bottom = bottomClearance),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showExpandedBar,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150)))
                },
                label = "safe_bottom_search_add_morph",
            ) { expanded ->
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = c.searchBackground,
                            border = BorderStroke(1.dp, c.cardBorder.copy(alpha = 0.7f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = c.textPrimary,
                                    modifier = Modifier.size(21.dp),
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (s.isVi) "Tìm kiếm tệp của bạn..." else "Search your files...",
                                            color = c.textSecondary,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            safeRepository.session.touchActivity()
                                            searchQuery = it
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                                        cursorBrush = SolidColor(c.accent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester)
                                            .onFocusChanged { searchFocused = it.isFocused },
                                    )
                                }
                                if (searchQuery.isNotEmpty() || searchFocused) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            searchFocused = false
                                            focusManager.clearFocus()
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = s.close,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                safeRepository.session.touchActivity()
                                showAddSheet = true
                            },
                            shape = CircleShape,
                            color = c.accentButtonBg,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = s.vaultPlusAddDocument,
                                    tint = Color.White,
                                    modifier = Modifier.size(27.dp),
                                )
                            }
                        }
                    }
                } else {
                    // Compact centered Dual-Action Pill when scrolling down (Image 4)
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = if (c.isDark) Color(0xFF284273) else c.accentButtonBg,
                        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.height(50.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    searchCompactOverride = false
                                    searchFocused = true
                                    scope.launch {
                                        delay(120L)
                                        runCatching { searchFocusRequester.requestFocus() }
                                    }
                                },
                                modifier = Modifier.size(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = s.searchShort,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(Color.White.copy(alpha = 0.25f)),
                            )

                            IconButton(
                                onClick = {
                                    safeRepository.session.touchActivity()
                                    showAddSheet = true
                                },
                                modifier = Modifier.size(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = s.vaultPlusAddDocument,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        SafeAddDocumentBottomSheet(
            onDismiss = { showAddSheet = false },
            onPickPdfFromSystem = { pdfPicker.launch(arrayOf("application/pdf")) },
            onFileUriSelected = { uri -> handlePickedUri(uri) },
            onOpenCamera = onOpenCamera,
            onCreateFolder = { showCreateFolderSheet = true },
            onShowMessage = onShowMessage,
        )
    }

    if (pendingUpload != null) {
        val upload = pendingUpload
        SafeMetadataBottomSheet(
            isUploadMode = true,
            fileName = upload.fileName,
            mimeType = upload.mimeType,
            sizeBytes = upload.bytes.size.toLong(),
            previewRawBytes = upload.bytes,
            initialTitle = upload.fileName.substringBeforeLast('.'),
            initialCategory = upload.suggestedCategory,
            initialFolderName = activeFolder,
            initialNotes = "",
            initialExpiresAt = "",
            availableFolders = folders,
            onCreateFolderRequest = { newName ->
                runCatching { safeRepository.createFolder(newName) }
            },
            onDismiss = {
                upload.bytes.fill(0)
                onSetPendingUpload(null)
            },
            onSave = { title, cat, folderName, notes, exp ->
                uploading = true
                scope.launch {
                    runCatching {
                        safeRepository.uploadDocument(
                            title = title,
                            fileName = upload.fileName,
                            mimeType = upload.mimeType,
                            category = cat,
                            folderName = folderName,
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

    if (showSettingsSheet) {
        SafeSettingsBottomSheet(
            status = status,
            safeRepository = safeRepository,
            activity = activity,
            onDismiss = { showSettingsSheet = false },
            onShowMessage = onShowMessage,
        )
    }

    if (showCreateFolderSheet) {
        SafeCreateFolderBottomSheet(
            onDismiss = { showCreateFolderSheet = false },
            onConfirm = { newName ->
                runCatching { safeRepository.createFolder(newName) }
                    .onSuccess {
                        onShowMessage(if (s.isVi) "Đã tạo thư mục \"$newName\"." else "Created folder \"$newName\".")
                    }
                    .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
            },
        )
    }

    if (renameFolderTarget != null) {
        val oldName = renameFolderTarget!!
        SafeCreateFolderBottomSheet(
            initialName = oldName,
            isRename = true,
            onDismiss = { renameFolderTarget = null },
            onConfirm = { newName ->
                scope.launch {
                    runCatching { safeRepository.renameFolder(oldName, newName) }
                        .onSuccess {
                            if (activeFolder == oldName) activeFolder = newName
                            onShowMessage(if (s.isVi) "Đã đổi tên thư mục." else "Folder renamed.")
                        }
                        .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
        )
    }

    if (actionFolderTarget != null) {
        val targetFolder = actionFolderTarget!!
        SafeFolderActionsBottomSheet(
            folderName = targetFolder.name,
            itemCount = targetFolder.itemCount,
            onDismiss = { actionFolderTarget = null },
            onOpen = { activeFolder = targetFolder.name },
            onRename = { renameFolderTarget = targetFolder.name },
            onDeleteFolderOnly = {
                scope.launch {
                    runCatching { safeRepository.deleteFolder(targetFolder.name) }
                        .onSuccess {
                            if (activeFolder == targetFolder.name) activeFolder = null
                            onShowMessage(if (s.isVi) "Đã xóa thư mục (tệp đã chuyển về gốc)." else "Folder removed (files moved to Root).")
                        }
                        .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
        )
    }

    if (actionItemTarget != null) {
        val targetItem = actionItemTarget!!
        SafeItemActionsBottomSheet(
            item = targetItem,
            thumbBitmap = thumbCache[targetItem.id],
            isOfflineCached = remember(targetItem.id, offlineVersion) {
                safeRepository.isCachedOffline(targetItem.id)
            },
            onDismiss = { actionItemTarget = null },
            onDeleteOrTrash = { deleteConfirmTarget = targetItem },
            onRestore = {
                scope.launch {
                    runCatching { safeRepository.restoreItem(targetItem.id) }
                        .onSuccess { onShowMessage(if (s.isVi) "Đã khôi phục tài liệu." else "Restored document.") }
                        .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
            onDownloadExport = { showExportConfirmForItem = targetItem },
            onToggleOffline = { enable ->
                scope.launch {
                    if (enable) {
                        runCatching { safeRepository.downloadForOffline(targetItem) }
                            .onSuccess {
                                offlineVersion += 1
                                onShowMessage(if (s.isVi) "Đã lưu bản mã hóa để xem ngoại tuyến." else "Encrypted file cached offline.")
                            }
                            .onFailure { ex -> onShowMessage(ex.message ?: "Download failed") }
                    } else {
                        safeRepository.clearOfflineCache(targetItem.id)
                        offlineVersion += 1
                        onShowMessage(if (s.isVi) "Đã xóa bản lưu ngoại tuyến trên máy." else "Cleared offline copy.")
                    }
                }
            },
            onToggleFavorite = {
                scope.launch {
                    runCatching { safeRepository.toggleFavorite(targetItem) }
                        .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
            onRenameEdit = { editMetadataTarget = targetItem },
            onMoveFolder = { moveItemTarget = targetItem },
            onOpenNotes = { notesItemTarget = targetItem },
            onOpenDetails = { detailsItemTarget = targetItem },
        )
    }

    if (editMetadataTarget != null) {
        val item = editMetadataTarget!!
        SafeMetadataBottomSheet(
            isUploadMode = false,
            fileName = item.metadata.fileName,
            mimeType = item.metadata.mimeType,
            sizeBytes = item.metadata.originalSize.toLong(),
            initialTitle = item.metadata.title,
            initialCategory = item.category,
            initialFolderName = item.metadata.folderName,
            initialNotes = item.metadata.notes.orEmpty(),
            initialExpiresAt = item.metadata.expiresAt.orEmpty(),
            availableFolders = folders,
            onCreateFolderRequest = { newName -> runCatching { safeRepository.createFolder(newName) } },
            onDismiss = { editMetadataTarget = null },
            onSave = { title, cat, folderName, notes, exp ->
                editMetadataTarget = null
                scope.launch {
                    runCatching {
                        safeRepository.updateItemMetadata(
                            item = item,
                            newTitle = title,
                            newCategory = cat,
                            newNotes = notes,
                            newExpiresAt = exp,
                            newFolderName = folderName,
                        )
                    }.onSuccess {
                        onShowMessage(if (s.isVi) "Đã cập nhật thông tin." else "Updated metadata.")
                    }.onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
        )
    }

    if (moveItemTarget != null) {
        val item = moveItemTarget!!
        SafeMoveFolderBottomSheet(
            itemTitle = item.metadata.title,
            currentFolder = item.metadata.folderName,
            folders = folders,
            onDismiss = { moveItemTarget = null },
            onCreateAndMove = { newFolderName ->
                scope.launch {
                    runCatching {
                        safeRepository.createFolder(newFolderName)
                        safeRepository.moveItemToFolder(item, newFolderName)
                    }.onSuccess {
                        onShowMessage(if (s.isVi) "Đã chuyển vào \"$newFolderName\"." else "Moved to \"$newFolderName\".")
                    }.onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
            onSelectFolder = { target ->
                scope.launch {
                    runCatching { safeRepository.moveItemToFolder(item, target) }
                        .onSuccess {
                            val label = target ?: if (s.isVi) "Thư mục gốc" else "Root"
                            onShowMessage(if (s.isVi) "Đã di chuyển tới $label." else "Moved to $label.")
                        }
                        .onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
        )
    }

    if (notesItemTarget != null) {
        val item = notesItemTarget!!
        SafeQuickNoteBottomSheet(
            item = item,
            onDismiss = { notesItemTarget = null },
            onSaveNotes = { nextNotes ->
                scope.launch {
                    runCatching {
                        safeRepository.updateItemMetadata(
                            item = item,
                            newTitle = item.metadata.title,
                            newCategory = item.category,
                            newNotes = nextNotes,
                            newExpiresAt = item.metadata.expiresAt,
                            newFolderName = item.metadata.folderName,
                        )
                    }.onSuccess {
                        onShowMessage(if (s.isVi) "Đã lưu ghi chú." else "Notes saved.")
                    }.onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                }
            },
        )
    }

    if (detailsItemTarget != null) {
        val item = detailsItemTarget!!
        SafeItemDetailsBottomSheet(
            item = item,
            isOfflineCached = remember(item.id, offlineVersion) { safeRepository.isCachedOffline(item.id) },
            onDismiss = { detailsItemTarget = null },
        )
    }

    if (deleteConfirmTarget != null) {
        val item = deleteConfirmTarget!!
        val permanent = item.deletedAt != null
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text(if (permanent) s.deleteConfirmPermTitle else s.deleteConfirmTrashTitle) },
            text = {
                Text(
                    if (permanent) {
                        if (s.isVi) "Tệp \"${item.metadata.title}\" sẽ bị xóa vĩnh viễn khỏi Cloudflare R2 & D1."
                        else "Permanently delete \"${item.metadata.title}\" from Cloudflare R2 & D1?"
                    } else {
                        if (s.isVi) "Tài liệu sẽ nằm trong Thùng rác 30 ngày trước khi bị xóa tự động."
                        else "Item stays in Trash for 30 days before automatic permanent deletion."
                    },
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                    onClick = {
                        deleteConfirmTarget = null
                        scope.launch {
                            runCatching {
                                if (permanent) safeRepository.hardDeleteItem(item.id)
                                else safeRepository.softDeleteItem(item.id)
                            }.onSuccess {
                                onShowMessage(
                                    if (permanent) {
                                        if (s.isVi) "Đã xóa vĩnh viễn." else "Permanently deleted."
                                    } else {
                                        if (s.isVi) "Đã chuyển vào Thùng rác." else "Moved to Trash."
                                    },
                                )
                            }.onFailure { ex -> onShowMessage(ex.message ?: "Error") }
                        }
                    },
                ) {
                    Text(if (permanent) s.deletePermanently else s.moveToTrash, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) { Text(s.cancel) }
            },
        )
    }

    if (showExportConfirmForItem != null) {
        val item = showExportConfirmForItem!!
        AlertDialog(
            onDismissRequest = { showExportConfirmForItem = null },
            title = { Text(s.vaultPlusExportWarnTitle) },
            text = { Text(s.vaultPlusExportWarnMsg) },
            confirmButton = {
                Button(
                    onClick = {
                        showExportConfirmForItem = null
                        pendingExportItem = item
                        exportItemLauncher.launch(item.metadata.fileName)
                    },
                ) {
                    Text(if (s.isVi) "Tải xuống" else "Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirmForItem = null }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun SafeFolderListRow(
    folder: SafeFolderEntry,
    isVi: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val dateLabel = remember(folder.latestIso, isVi) { formatShortVaultDate(folder.latestIso, isVi) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SafeGoldenFolderIcon(
            itemCount = folder.itemCount,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = folder.name,
                color = c.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("${folder.itemCount} ${if (isVi) "mục" else "items"}")
                    if (dateLabel.isNotEmpty()) {
                        append(" · ")
                        append(dateLabel)
                    }
                },
                color = c.textSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = c.textSecondary)
        }
    }
}

@Composable
private fun SafeDocumentListRow(
    item: DecryptedSafeItem,
    thumbBitmap: ImageBitmap?,
    isCachedOffline: Boolean,
    isVi: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val dateLabel = remember(item.updatedAt, item.createdAt, isVi) {
        formatShortVaultDate(item.updatedAt ?: item.createdAt, isVi)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
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
                    tint = if (item.metadata.mimeType == "application/pdf") Color(0xFFEF4444) else c.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (item.metadata.mimeType == "application/pdf" && thumbBitmap != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xE6FFFFFF))
                        .padding(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = item.metadata.title,
                    color = c.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.favorite) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                }
                if (isCachedOffline) {
                    Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = buildString {
                    append(formatBytes(item.metadata.originalSize.toLong()))
                    if (dateLabel.isNotEmpty()) {
                        append(" · ")
                        append(dateLabel)
                    }
                },
                color = c.textSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onMoreClick) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = c.textSecondary)
        }
    }
}

@Composable
private fun SafeFolderGridTile(
    folder: SafeFolderEntry,
    isVi: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val dateLabel = remember(folder.latestIso, isVi) { formatShortVaultDate(folder.latestIso, isVi) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f),
            contentAlignment = Alignment.Center,
        ) {
            SafeGoldenFolderIcon(
                itemCount = folder.itemCount,
                modifier = Modifier
                    .fillMaxHeight(0.84f)
                    .aspectRatio(1.12f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = folder.name,
            color = c.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (dateLabel.isNotEmpty()) dateLabel else "${folder.itemCount} ${if (isVi) "mục" else "items"}",
            color = c.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SafeDocumentGridTile(
    item: DecryptedSafeItem,
    thumbBitmap: ImageBitmap?,
    isVi: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val dateLabel = remember(item.updatedAt, item.createdAt, isVi) {
        formatShortVaultDate(item.updatedAt ?: item.createdAt, isVi)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
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
                    tint = if (item.metadata.mimeType == "application/pdf") Color(0xFFEF4444) else c.accent,
                    modifier = Modifier.size(40.dp),
                )
            }

            if (item.metadata.mimeType == "application/pdf" && thumbBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xF0FFFFFF),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier
                            .padding(3.dp)
                            .size(14.dp),
                    )
                }
            }

            if (item.favorite) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier
                            .padding(4.dp)
                            .size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.metadata.title,
            color = c.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (dateLabel.isNotEmpty()) dateLabel else formatBytes(item.metadata.originalSize.toLong()),
            color = c.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SafeGoldenFolderIcon(
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tabPath = Path().apply {
                moveTo(w * 0.08f, h * 0.20f)
                quadraticTo(w * 0.08f, h * 0.12f, w * 0.16f, h * 0.12f)
                lineTo(w * 0.39f, h * 0.12f)
                lineTo(w * 0.47f, h * 0.22f)
                lineTo(w * 0.86f, h * 0.22f)
                quadraticTo(w * 0.92f, h * 0.22f, w * 0.92f, h * 0.30f)
                lineTo(w * 0.92f, h * 0.84f)
                quadraticTo(w * 0.92f, h * 0.90f, w * 0.84f, h * 0.90f)
                lineTo(w * 0.16f, h * 0.90f)
                quadraticTo(w * 0.08f, h * 0.90f, w * 0.08f, h * 0.84f)
                close()
            }
            drawPath(tabPath, color = Color(0xFFD99414))

            drawRoundRect(
                color = Color(0xFFFBBF24),
                topLeft = Offset(w * 0.08f, h * 0.26f),
                size = Size(w * 0.84f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
            )
        }
        if (itemCount > 0) {
            Text(
                text = itemCount.toString(),
                color = Color(0xFF78350F),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 7.dp, bottom = 5.dp),
            )
        }
    }
}

private fun sortLabel(order: SafeSortOrder, isVi: Boolean): String = when (order) {
    SafeSortOrder.NAME_ASC, SafeSortOrder.NAME_DESC -> if (isVi) "Tên" else "Name"
    SafeSortOrder.DATE_DESC, SafeSortOrder.DATE_ASC -> if (isVi) "Ngày" else "Date"
    SafeSortOrder.SIZE_DESC -> if (isVi) "Dung lượng" else "Size"
}

private fun sortMenuTitle(order: SafeSortOrder, isVi: Boolean): String = when (order) {
    SafeSortOrder.NAME_ASC -> if (isVi) "Tên (A → Z)" else "Name (A → Z)"
    SafeSortOrder.NAME_DESC -> if (isVi) "Tên (Z → A)" else "Name (Z → A)"
    SafeSortOrder.DATE_DESC -> if (isVi) "Mới nhất trước" else "Newest first"
    SafeSortOrder.DATE_ASC -> if (isVi) "Cũ nhất trước" else "Oldest first"
    SafeSortOrder.SIZE_DESC -> if (isVi) "Dung lượng lớn nhất" else "Largest first"
}

private fun formatShortVaultDate(isoString: String?, isVi: Boolean): String {
    val trimmed = isoString?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val instant = runCatching { Instant.parse(trimmed) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(trimmed.replace(' ', 'T') + "Z") }.getOrNull()
        ?: return ""
    val zdt = instant.atZone(ZoneId.systemDefault())
    val currentYear = Instant.now().atZone(ZoneId.systemDefault()).year
    return if (isVi) {
        if (zdt.year == currentYear) {
            "${zdt.dayOfMonth} Th${zdt.monthValue}"
        } else {
            "${zdt.dayOfMonth} Th${zdt.monthValue}, ${zdt.year}"
        }
    } else {
        val pattern = if (zdt.year == currentYear) "MMM d" else "MMM d, yyyy"
        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(zdt)
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
