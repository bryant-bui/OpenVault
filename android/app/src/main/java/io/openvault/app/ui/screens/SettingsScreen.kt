package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.BuildConfig
import io.openvault.app.core.media.IconLoader
import io.openvault.app.core.network.R2BackupItemDto
import io.openvault.app.core.security.AppLanguage
import io.openvault.app.core.security.AppThemeMode
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsPage {
    ROOT,
    ACCOUNTS,
    GENERAL,
    SECURITY,
    PRIVACY,
    AUTOFILL,
    BACKUP,
    ADVANCED,
    ABOUT,
}

/**
 * Clean Hierarchical 1Password 8 Android Settings (matching Images 10–15):
 * - Root Menu with colorful circular pastel icons & short titles
 * - Focused sub-pages: Accounts (with avatar photo picker), General, Security, Privacy,
 *   Autofill (visual setup guide + Passkey provider), Backup & R2, Advanced (Language + Allow screenshots), About
 */
@Composable
fun SettingsScreen(
    repository: VaultRepository,
    activity: FragmentActivity?,
    showCloseButton: Boolean = true,
    onOpenWelcomeTour: (() -> Unit)? = null,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onLock: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by repository.preferences.settings.collectAsState()
    val account by repository.preferences.account.collectAsState()
    val isSyncing by repository.isSyncing.collectAsState()
    val bioSupported = remember { BiometricKeyManager.isBiometricAvailable(context) }

    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinBusy by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showPinAttemptsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var aboutInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    var backupBusy by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<R2BackupItemDto>>(emptyList()) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = IconLoader.compressPickedImageToBase64(context, uri, targetPx = 160, quality = 82)
                if (b64 != null) {
                    repository.preferences.setAccountAvatarBase64(b64)
                    onShowMessage(if (s.isVi) "Đã cập nhật ảnh đại diện!" else "Account avatar updated!")
                } else {
                    onShowMessage(if (s.isVi) "Không thể đọc ảnh." else "Could not process image.")
                }
            }
        }
    }

    BackHandler(enabled = page != SettingsPage.ROOT) {
        page = SettingsPage.ROOT
    }

    val syncTimeText = remember(account.lastSyncMs, s.isVi) {
        if (account.lastSyncMs <= 0L) {
            if (s.isVi) "Chưa đồng bộ" else "Not synced yet"
        } else {
            SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault()).format(Date(account.lastSyncMs))
        }
    }

    val pageTitle = when (page) {
        SettingsPage.ROOT -> s.settings.removeSuffix("…")
        SettingsPage.ACCOUNTS -> s.settingsAccounts
        SettingsPage.GENERAL -> s.settingsGeneral
        SettingsPage.SECURITY -> s.settingsSecurity
        SettingsPage.PRIVACY -> s.settingsPrivacy
        SettingsPage.AUTOFILL -> s.settingsAutofill
        SettingsPage.BACKUP -> s.settingsBackup
        SettingsPage.ADVANCED -> s.settingsAdvanced
        SettingsPage.ABOUT -> s.settingsAbout
    }

    val hPad = if (fold.isNarrowCover) 16.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Clean Top Bar (✕ on Root, ← on Sub-page)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (page != SettingsPage.ROOT) {
                IconButton(onClick = { page = SettingsPage.ROOT }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary)
                }
            } else if (showCloseButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textPrimary)
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = pageTitle,
                color = c.textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                when (page) {
                    SettingsPage.ROOT -> {
                        SettingsRootRow(
                            icon = Icons.Rounded.Person,
                            iconTint = Color(0xFF064E3B),
                            iconBg = Color(0xFF6EE7B7),
                            title = s.settingsAccounts,
                            hPad = hPad,
                            onClick = { page = SettingsPage.ACCOUNTS },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsRootRow(
                            icon = Icons.Rounded.Settings,
                            iconTint = Color(0xFF0C4A6E),
                            iconBg = Color(0xFF60B8FF),
                            title = s.settingsGeneral,
                            hPad = hPad,
                            onClick = { page = SettingsPage.GENERAL },
                        )
                        SettingsRootRow(
                            icon = Icons.Rounded.Lock,
                            iconTint = Color(0xFF451A03),
                            iconBg = Color(0xFFFCD34D),
                            title = s.settingsSecurity,
                            hPad = hPad,
                            onClick = { page = SettingsPage.SECURITY },
                        )
                        SettingsRootRow(
                            icon = Icons.Rounded.Security,
                            iconTint = Color(0xFF064E3B),
                            iconBg = Color(0xFF6EE7B7),
                            title = s.settingsPrivacy,
                            hPad = hPad,
                            onClick = { page = SettingsPage.PRIVACY },
                        )
                        SettingsRootRow(
                            icon = Icons.Rounded.AutoFixHigh,
                            iconTint = Color(0xFF1F2937),
                            iconBg = Color(0xFFCBD5E1),
                            title = s.settingsAutofill,
                            hPad = hPad,
                            onClick = { page = SettingsPage.AUTOFILL },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsRootRow(
                            icon = Icons.Rounded.CloudUpload,
                            iconTint = Color(0xFF2E1065),
                            iconBg = Color(0xFFC4B5FD),
                            title = s.settingsBackup,
                            hPad = hPad,
                            onClick = { page = SettingsPage.BACKUP },
                        )
                        SettingsRootRow(
                            icon = Icons.Rounded.Tune,
                            iconTint = Color(0xFF083344),
                            iconBg = Color(0xFF67E8F9),
                            title = s.settingsAdvanced,
                            hPad = hPad,
                            onClick = { page = SettingsPage.ADVANCED },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

                        SettingsRootRow(
                            icon = Icons.Rounded.Info,
                            iconTint = Color(0xFF0C4A6E),
                            iconBg = Color(0xFF7DD3FC),
                            title = s.settingsAbout,
                            hPad = hPad,
                            onClick = { page = SettingsPage.ABOUT },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsPage.ACCOUNTS -> {
                        Column(
                            modifier = Modifier.padding(horizontal = hPad, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                OnePassAccountAvatar(
                                    email = account.email,
                                    size = 56.dp,
                                    onClick = {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = account.email.ifBlank { "OpenVault" },
                                        color = c.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = if (s.isVi) "Tự động đồng bộ · $syncTimeText" else "Auto-sync active · $syncTimeText",
                                        color = c.textSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = c.accent, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(s.changeAvatar, color = c.textPrimary, fontSize = 13.5.sp)
                                }
                                if (!settings.accountAvatarBase64.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { repository.preferences.setAccountAvatarBase64(null) },
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text(s.removeAvatar, color = c.danger, fontSize = 13.5.sp)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { repository.syncNow(force = true) }
                                                .onSuccess {
                                                    onShowMessage(if (s.isVi) "Đã đồng bộ thành công!" else "Vault synced!")
                                                }
                                                .onFailure { onShowMessage("${it.message}") }
                                        }
                                    },
                                    enabled = !isSyncing,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(s.syncNow, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Button(
                                    onClick = onLock,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(s.lockVault, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogoutDialog = true }
                                .padding(horizontal = hPad, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = c.danger)
                            Text(s.signOut, color = c.danger, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    SettingsPage.GENERAL -> {
                        SettingsToggleItem(
                            title = s.prefillUsername,
                            checked = settings.prefillUsername,
                            hPad = hPad,
                            onCheckedChange = { repository.preferences.setPrefillUsername(it) },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                        SettingsSectionCaption(s.sectionAppearance, hPad)

                        SettingsValueRow(
                            title = s.themeLabel,
                            value = if (s.isVi) settings.themeMode.labelVi else settings.themeMode.labelEn,
                            hPad = hPad,
                            onClick = { showThemeDialog = true },
                        )
                    }

                    SettingsPage.SECURITY -> {
                        SettingsSectionCaption(s.sectionUnlock, hPad)

                        SettingsToggleItem(
                            title = s.unlockBiometrics,
                            checked = settings.biometricEnabled,
                            enabled = bioSupported && activity != null,
                            hPad = hPad,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    repository.disableBiometric()
                                    onShowMessage(if (s.isVi) "Đã tắt mở khóa sinh trắc học." else "Biometric unlock disabled.")
                                } else if (activity != null) {
                                    val cipher = runCatching { BiometricKeyManager.createBiometricEncryptCipher() }
                                        .getOrElse {
                                            onShowMessage("Keystore error: ${it.message}")
                                            return@SettingsToggleItem
                                        }
                                    BiometricKeyManager.showBiometricPrompt(
                                        activity = activity,
                                        title = s.unlockBiometrics,
                                        subtitle = account.email,
                                        negativeText = s.cancel,
                                        cipher = cipher,
                                        onSuccess = { authed ->
                                            runCatching { repository.enableBiometricWithCipher(authed) }
                                                .onSuccess {
                                                    onShowMessage(if (s.isVi) "Đã bật vân tay!" else "Biometrics enabled!")
                                                }
                                                .onFailure { onShowMessage("${it.message}") }
                                        },
                                        onError = { msg -> onShowMessage(msg) },
                                    )
                                }
                            },
                        )

                        SettingsToggleItem(
                            title = s.unlockWithPin,
                            checked = settings.pinEnabled,
                            hPad = hPad,
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    repository.disablePin()
                                    onShowMessage(if (s.isVi) "Đã tắt mã PIN." else "PIN disabled.")
                                } else {
                                    pinInput = ""
                                    pinConfirm = ""
                                    pinError = null
                                    showPinDialog = true
                                }
                            },
                        )

                        if (settings.pinEnabled) {
                            SettingsValueRow(
                                title = s.pinAttemptLimit,
                                value = "${settings.pinMaxAttempts} ${s.attemptsSuffix}",
                                hPad = hPad,
                                onClick = { showPinAttemptsDialog = true },
                            )
                        }

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(top = 6.dp))
                        SettingsSectionCaption(s.sectionAutoLock, hPad)

                        SettingsValueRow(
                            title = s.autoLockOnExit,
                            value = if (settings.autoLockMinutes == 0) s.never else "${settings.autoLockMinutes} ${s.minutesSuffix}",
                            hPad = hPad,
                            onClick = { showAutoLockDialog = true },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(top = 6.dp))
                        SettingsSectionCaption(s.sectionClipboard, hPad)

                        SettingsToggleItem(
                            title = s.clearClipboard,
                            subtitle = if (settings.clipboardClearSeconds > 0) {
                                String.format(s.clearClipboardNote, settings.clipboardClearSeconds)
                            } else {
                                s.clearClipboardOff
                            },
                            checked = settings.clipboardClearSeconds > 0,
                            hPad = hPad,
                            onCheckedChange = { enabled ->
                                repository.preferences.setClipboardClearSeconds(if (enabled) 30 else 0)
                            },
                        )
                        if (settings.clipboardClearSeconds > 0) {
                            SettingsValueRow(
                                title = if (s.isVi) "Thời gian tự xóa" else "Clear after",
                                value = "${settings.clipboardClearSeconds} ${s.secondsSuffix}",
                                hPad = hPad,
                                onClick = { showClipboardDialog = true },
                            )
                        }
                    }

                    SettingsPage.PRIVACY -> {
                        SettingsToggleItem(
                            title = s.showWebsiteIcons,
                            subtitle = s.showWebsiteIconsSub,
                            checked = settings.showWebsiteIcons,
                            hPad = hPad,
                            onCheckedChange = { repository.preferences.setShowWebsiteIcons(it) },
                        )

                        HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(top = 6.dp))
                        SettingsSectionCaption(s.sectionWatchtower, hPad)

                        SettingsToggleItem(
                            title = s.wtWeakToggle,
                            checked = settings.checkWeakPasswords,
                            hPad = hPad,
                            onCheckedChange = { repository.preferences.setWatchtowerChecks(weak = it) },
                        )
                        SettingsToggleItem(
                            title = s.wtReusedToggle,
                            checked = settings.checkReusedPasswords,
                            hPad = hPad,
                            onCheckedChange = { repository.preferences.setWatchtowerChecks(reused = it) },
                        )
                        SettingsToggleItem(
                            title = s.wt2faToggle,
                            checked = settings.checkTwoFactor,
                            hPad = hPad,
                            onCheckedChange = { repository.preferences.setWatchtowerChecks(twoFactor = it) },
                        )
                    }

                    SettingsPage.AUTOFILL -> {
                        // Authentic 1Password "Set up AutoFill" visual guide (Image 14!)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = hPad, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                AutofillHeroIllustration(modifier = Modifier.size(width = 190.dp, height = 96.dp))
                            }

                            Text(
                                text = s.autofillGuideTitle,
                                color = c.textPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = s.autofillGuideDesc,
                                color = c.textSecondary,
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = s.autofillGuideToSetUp,
                                color = c.textSecondary,
                                fontSize = 14.sp,
                            )

                            AutofillStepRow(1, s.autofillStep1)
                            AutofillStepRow(2, s.autofillStep2)
                            AutofillStepRow(3, s.autofillStep3)

                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Text(s.openSettings, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            HorizontalDivider(color = c.divider, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Android 14+ Passkey Provider Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface)
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            val intent = Intent(Settings.ACTION_CREDENTIAL_PROVIDER).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                                }
                                        } else {
                                            onShowMessage(s.passkeyUnsupported)
                                        }
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CategoryIconBadge(
                                    icon = Icons.Rounded.Key,
                                    tint = Color.White,
                                    background = Color(0xFF7253ED),
                                    size = 38.dp,
                                    circular = true,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.passkeyProviderTitle, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) s.passkeyProviderSub else s.passkeyUnsupported,
                                        color = c.textSecondary,
                                        fontSize = 12.5.sp,
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.BACKUP -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = hPad, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(s.r2BackupTitle, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(s.r2BackupSub, color = c.textSecondary, fontSize = 13.5.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        backupBusy = true
                                        scope.launch {
                                            runCatching { repository.triggerR2Backup() }
                                                .onSuccess { res ->
                                                    onShowMessage("R2 snapshot created (${res.ciphersCount} items)!")
                                                    runCatching { backups = repository.listR2Backups() }
                                                }
                                                .onFailure { onShowMessage("${it.message}") }
                                            backupBusy = false
                                        }
                                    },
                                    enabled = !backupBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                ) {
                                    Text(if (backupBusy) s.backingUp else s.backupNow, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            runCatching { backups = repository.listR2Backups() }
                                                .onFailure { onShowMessage("${it.message}") }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(s.viewHistory)
                                }
                            }

                            if (backups.isNotEmpty()) {
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                backups.take(10).forEach { b ->
                                    Text(
                                        text = "• ${b.key.substringAfterLast('/')} (${b.size / 1024} KB)",
                                        color = c.textSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.ADVANCED -> {
                        // Exact Image 15: Language + Allow screenshots!
                        SettingsValueRow(
                            title = s.language,
                            value = settings.language.labelNative,
                            hPad = hPad,
                            onClick = { showLanguageDialog = true },
                        )

                        SettingsToggleItem(
                            title = s.allowScreenshots,
                            subtitle = s.allowScreenshotsSub,
                            checked = !settings.flagSecureEnabled,
                            hPad = hPad,
                            onCheckedChange = { allow ->
                                repository.preferences.setFlagSecureEnabled(!allow)
                            },
                        )
                    }

                    SettingsPage.ABOUT -> {
                        // Rich About Screen matching Image 16!
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AboutHeroBanner(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = hPad, vertical = 26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OpenVaultBrandCrest(size = 44.dp)
                                    Text(
                                        text = "OpenVault",
                                        color = c.textPrimary,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.6).sp,
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "OpenVault for Android ${BuildConfig.VERSION_NAME}",
                                    color = c.textPrimary,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Build ${BuildConfig.VERSION_CODE}, on PRODUCTION channel",
                                    color = c.textSecondary,
                                    fontSize = 14.sp,
                                )

                                // Outlined Version + Release Notes box (Image 16!)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.surface)
                                        .clickable {
                                            aboutInfoDialog = (if (s.isVi) "Ghi chú phát hành" else "Release Notes") to
                                                "v${BuildConfig.VERSION_NAME}\n" +
                                                "• Vault Door 90° rotating keyhole unlock animation\n" +
                                                "• 5-slide onboarding welcome carousel\n" +
                                                "• Dedicated Search mode & animated Watchtower gauge\n" +
                                                "• Floating Detail overlay & refined Navigation Rail on Z Fold 3\n" +
                                                "• Real-time auto-sync with Cloudflare Workers + D1\n" +
                                                "• Profile screen, Home Customize, Favicons & Custom Icons\n" +
                                                "• Passkey deletion & +add more custom fields\n" +
                                                "• English (default) & Vietnamese language support"
                                        }
                                        .padding(horizontal = 18.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Version ${BuildConfig.VERSION_NAME}",
                                        color = c.textPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (s.isVi) "Ghi chú phát hành" else "Release Notes",
                                        color = c.accent,
                                        fontSize = 15.sp,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "© 2026 OpenVault. MIT Open Source.",
                                    color = c.textSecondary,
                                    fontSize = 13.5.sp,
                                )

                                AboutLinkText(if (s.isVi) "Điều khoản sử dụng (MIT)" else "Terms of Service") {
                                    aboutInfoDialog = "MIT License" to
                                        "OpenVault is free, open-source software licensed under the MIT License. You own your vault, your Cloudflare Worker, and your encrypted backups."
                                }
                                AboutLinkText(if (s.isVi) "Chính sách bảo mật Zero-Knowledge" else "Privacy Policy") {
                                    aboutInfoDialog = "Zero-Knowledge Privacy" to
                                        "Every vault item, password, 2FA secret, Passkey private key, and custom icon is encrypted locally on your device using AES-256-CBC + HMAC-SHA256 (Bitwarden Type 2) before leaving your phone. Your Master Password never leaves your device."
                                }
                                AboutLinkText(if (s.isVi) "Ghi công & Thư viện mở" else "Credits & Acknowledgements") {
                                    aboutInfoDialog = "Credits & Acknowledgements" to
                                        "Built with Kotlin, Jetpack Compose, Material 3, AndroidX Credentials (FIDO2/Passkeys), Room, CameraX, ZXing, BouncyCastle Argon2id, OkHttp, and Cloudflare Workers + D1 + R2."
                                }
                                if (onOpenWelcomeTour != null) {
                                    AboutLinkText(if (s.isVi) "Xem lại màn hình giới thiệu" else "Replay Welcome Tour") {
                                        onOpenWelcomeTour()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = s.language,
            options = AppLanguage.entries.map { it to it.labelNative },
            selected = settings.language,
            onSelect = {
                repository.preferences.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = s.themeLabel,
            options = AppThemeMode.entries.map { it to (if (s.isVi) it.labelVi else it.labelEn) },
            selected = settings.themeMode,
            onSelect = {
                repository.preferences.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showAutoLockDialog) {
        SingleChoiceDialog(
            title = s.autoLockOnExit,
            options = listOf(1, 5, 10, 15, 30, 0).map { m ->
                m to if (m == 0) s.never else "$m ${s.minutesSuffix}"
            },
            selected = settings.autoLockMinutes,
            onSelect = {
                repository.preferences.setAutoLockMinutes(it)
                showAutoLockDialog = false
            },
            onDismiss = { showAutoLockDialog = false },
        )
    }

    if (showClipboardDialog) {
        SingleChoiceDialog(
            title = s.clearClipboard,
            options = listOf(15, 30, 60, 90, 120).map { sec ->
                sec to "$sec ${s.secondsSuffix}"
            },
            selected = settings.clipboardClearSeconds,
            onSelect = {
                repository.preferences.setClipboardClearSeconds(it)
                showClipboardDialog = false
            },
            onDismiss = { showClipboardDialog = false },
        )
    }

    if (showPinAttemptsDialog) {
        SingleChoiceDialog(
            title = s.pinAttemptLimit,
            options = listOf(3, 5).map { cnt -> cnt to "$cnt ${s.attemptsSuffix}" },
            selected = settings.pinMaxAttempts,
            onSelect = {
                repository.preferences.setPinMaxAttempts(it)
                showPinAttemptsDialog = false
            },
            onDismiss = { showPinAttemptsDialog = false },
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { if (!pinBusy) showPinDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text(s.unlockWithPin, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (s.isVi) "Mã PIN 6 số bọc bằng PBKDF2-SHA256 600.000 vòng. Nhập đủ 6 số sẽ tự động mở khóa."
                        else "6-digit PIN wrapped with PBKDF2-SHA256 (600,000 rounds). Automatically unlocks on the 6th digit.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { txt -> pinInput = txt.filter { it.isDigit() }.take(6); pinError = null },
                        label = { Text(if (s.isVi) "Nhập PIN (6 số)" else "Enter 6-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { txt -> pinConfirm = txt.filter { it.isDigit() }.take(6); pinError = null },
                        label = { Text(if (s.isVi) "Xác nhận PIN" else "Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    if (pinError != null) Text(pinError!!, color = c.danger, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !pinBusy,
                    onClick = {
                        if (pinInput.length != 6) {
                            pinError = if (s.isVi) "Vui lòng nhập đủ 6 chữ số." else "Please enter exactly 6 digits."
                            return@TextButton
                        }
                        if (pinInput != pinConfirm) {
                            pinError = if (s.isVi) "Mã PIN không khớp." else "PINs do not match."
                            return@TextButton
                        }
                        pinBusy = true
                        scope.launch {
                            runCatching { repository.enablePin(pinInput) }
                                .onSuccess {
                                    pinBusy = false
                                    showPinDialog = false
                                    onShowMessage(if (s.isVi) "Đã bật mã PIN 6 số!" else "6-digit PIN enabled!")
                                }
                                .onFailure {
                                    pinBusy = false
                                    pinError = it.message
                                }
                        }
                    },
                ) {
                    Text(if (pinBusy) "…" else s.save, color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !pinBusy, onClick = { showPinDialog = false }) {
                    Text(s.cancel, color = c.textSecondary)
                }
            },
        )
    }

    aboutInfoDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { aboutInfoDialog = null },
            containerColor = c.surfaceElevated,
            title = { Text(title, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(body, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { aboutInfoDialog = null }) {
                    Text(s.done, color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text(s.signOutConfirmTitle, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(s.signOutConfirmMsg, color = c.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch { repository.logout(allDevices = false) }
                    },
                ) {
                    Text(s.signOut, color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(s.cancel, color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun AboutLinkText(label: String, onClick: () -> Unit) {
    val c = LocalOpenVaultColors.current
    Text(
        text = label,
        color = c.accent,
        fontSize = 14.5.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Illustrated teal-cyan About banner (original Compose vector art inspired by Image 16):
 * Giant soft keyhole watermark + floating blue credit card, golden key, pink padlock, and document.
 */
@Composable
private fun AboutHeroBanner(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF6BC7D9))) {
        val w = size.width
        val h = size.height

        // Soft lighter circle glow + keyhole silhouette backdrop
        drawCircle(
            color = Color(0xFF9DE2EC),
            radius = h * 0.92f,
            center = Offset(w * 0.50f, h * 0.58f),
        )
        drawRoundRect(
            color = Color(0xFF7CD3E3),
            topLeft = Offset(w * 0.43f, 0f),
            size = Size(w * 0.14f, h),
            cornerRadius = CornerRadius(12f, 12f),
        )

        // Left: tilted blue credit card
        rotate(degrees = 14f, pivot = Offset(w * 0.10f, h * 0.78f)) {
            drawRoundRect(
                color = Color(0xFF3366CC),
                topLeft = Offset(w * 0.01f, h * 0.46f),
                size = Size(w * 0.19f, h * 0.58f),
                cornerRadius = CornerRadius(14f, 14f),
            )
            drawRect(
                color = Color(0xFF1D4499),
                topLeft = Offset(w * 0.05f, h * 0.46f),
                size = Size(w * 0.04f, h * 0.58f),
            )
            drawRoundRect(
                color = Color(0xFF93C5FD),
                topLeft = Offset(w * 0.12f, h * 0.80f),
                size = Size(w * 0.05f, h * 0.12f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        // Mid-left: golden key
        rotate(degrees = -12f, pivot = Offset(w * 0.34f, h * 0.84f)) {
            drawRoundRect(
                color = Color(0xFFF5B942),
                topLeft = Offset(w * 0.22f, h * 0.80f),
                size = Size(w * 0.16f, h * 0.10f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawCircle(
                color = Color(0xFFFCD34D),
                radius = h * 0.15f,
                center = Offset(w * 0.38f, h * 0.84f),
            )
            drawCircle(
                color = Color(0xFFD9941E),
                radius = h * 0.11f,
                center = Offset(w * 0.38f, h * 0.84f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Center-right: pink matte square padlock with keyhole
        val lockSize = h * 0.42f
        val lockLeft = w * 0.52f
        val lockTop = h * 0.58f
        drawRoundRect(
            color = Color(0xFFF49AC2),
            topLeft = Offset(lockLeft, lockTop),
            size = Size(lockSize, lockSize),
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawCircle(
            color = Color(0xFF1E293B),
            radius = lockSize * 0.14f,
            center = Offset(lockLeft + lockSize / 2f, lockTop + lockSize * 0.42f),
        )
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(lockLeft + lockSize * 0.43f, lockTop + lockSize * 0.48f),
            size = Size(lockSize * 0.14f, lockSize * 0.26f),
            cornerRadius = CornerRadius(6f, 6f),
        )

        // Far-right: crisp curved document sheet
        val doc = Path().apply {
            moveTo(w * 0.82f, h)
            quadraticTo(w * 0.90f, h * 0.86f, w * 0.91f, h * 0.50f)
            lineTo(w * 1.02f, h * 0.50f)
            lineTo(w * 1.02f, h)
            close()
        }
        drawPath(doc, color = Color(0xFFE5E7EB))
    }
}

@Composable
private fun SettingsRootRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    hPad: Dp,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CategoryIconBadge(icon = icon, tint = iconTint, background = iconBg, size = 38.dp, circular = true)
        Text(text = title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun SettingsSectionCaption(text: String, hPad: Dp) {
    val c = LocalOpenVaultColors.current
    Text(
        text = text,
        color = c.textSecondary,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = hPad, end = hPad, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    hPad: Dp,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        Text(text = value, color = c.textSecondary, fontSize = 13.5.sp)
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    hPad: Dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = hPad, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) c.textPrimary else c.textMuted,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun AutofillStepRow(number: Int, text: String) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$number", color = c.navActiveContent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = text, color = c.textPrimary, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Original vector AutoFill hero graphic (tilted password pill + circular refresh arrow, inspired by Image 14).
 */
@Composable
private fun AutofillHeroIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Teal swoosh arcs around password pill
        drawArc(
            color = Color(0xFF4FD1C5),
            startAngle = 210f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.06f),
            size = Size(w * 0.56f, h * 0.88f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xFF4FD1C5),
            startAngle = 30f,
            sweepAngle = 135f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.06f),
            size = Size(w * 0.56f, h * 0.88f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        // Arrowhead at top
        val arrow = Path().apply {
            moveTo(w * 0.44f, h * 0.03f)
            lineTo(w * 0.58f, h * 0.10f)
            lineTo(w * 0.45f, h * 0.19f)
            close()
        }
        drawPath(arrow, color = Color(0xFF4FD1C5))

        // Tilted blue password pill with 7 dots
        rotate(degrees = -12f, pivot = center) {
            val pillW = w * 0.84f
            val pillH = h * 0.36f
            val left = (w - pillW) / 2f
            val top = (h - pillH) / 2f
            drawRoundRect(
                color = Color(0xFF7FA6E8),
                topLeft = Offset(left, top),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
            )
            val dotCount = 7
            val dotRadius = pillH * 0.16f
            val step = (pillW - pillH) / (dotCount - 1)
            for (i in 0 until dotCount) {
                drawCircle(
                    color = Color(0xFF1D4ED8),
                    radius = dotRadius,
                    center = Offset(left + pillH / 2f + step * i, center.y),
                )
            }
        }
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = { Text(title, color = c.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = c.accent),
                        )
                        Text(text = label, color = c.textPrimary, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = c.textSecondary)
            }
        },
    )
}
