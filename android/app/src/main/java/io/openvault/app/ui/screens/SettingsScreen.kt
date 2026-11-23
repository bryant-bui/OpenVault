package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.BuildConfig
import io.openvault.app.core.network.R2BackupItemDto
import io.openvault.app.core.security.AppThemeMode
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Authentic 1Password 8 Android Settings Screen:
 * - Elevated matte charcoal (#1C1D1F) canvas, ✕ Close + bold "Cài đặt" top bar
 * - Flat grouped rows separated by clean full-width section dividers
 * - Colorful pastel circular icon badges (matching real 1Password Android Settings capture!)
 * - Centered max-width layout on unfolded Galaxy Z Fold 3
 */
@Composable
fun SettingsScreen(
    repository: VaultRepository,
    activity: FragmentActivity?,
    showCloseButton: Boolean = true,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onLock: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by repository.preferences.settings.collectAsState()
    val account by repository.preferences.account.collectAsState()
    val isSyncing by repository.isSyncing.collectAsState()
    val bioSupported = remember { BiometricKeyManager.isBiometricAvailable(context) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinBusy by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var backupBusy by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<R2BackupItemDto>>(emptyList()) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val syncTimeText = remember(account.lastSyncMs) {
        if (account.lastSyncMs <= 0L) "Chưa đồng bộ"
        else SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault()).format(Date(account.lastSyncMs))
    }

    val hPad = if (fold.isNarrowCover) 14.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 1Password Android Settings Top Bar: ✕  Cài đặt
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showCloseButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = "Đóng", tint = c.textPrimary)
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
            }
            Text(
                text = "Cài đặt",
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
                // SECTION 1: ACCOUNT
                Column(
                    modifier = Modifier.padding(horizontal = hPad, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CategoryIconBadge(
                            icon = Icons.Rounded.Person,
                            tint = Color(0xFF064E3B),
                            background = Color(0xFF6EE7B7),
                            size = 38.dp,
                            circular = true,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(account.email.ifBlank { "Tài khoản OpenVault" }, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${account.serverUrl.removePrefix("https://")} · Đồng bộ: $syncTimeText",
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { repository.syncNow(force = true) }
                                        .onSuccess { onShowMessage("Đã đồng bộ kho mới nhất từ Cloudflare D1!") }
                                        .onFailure { onShowMessage("Lỗi đồng bộ: ${it.message}") }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Đồng bộ", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = onLock,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Khóa kho", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                // SECTION 2: AUTOFILL & PASSKEY
                SettingsActionRow(
                    icon = Icons.Rounded.AutoFixHigh,
                    iconTint = Color(0xFF1F2937),
                    iconBg = Color(0xFFCBD5E1),
                    title = "Tự động điền Android (Autofill)",
                    subtitle = "Chọn OpenVault làm trình điền mật khẩu & gợi ý bàn phím",
                    hPad = hPad,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                    },
                )

                SettingsActionRow(
                    icon = Icons.Rounded.Key,
                    iconTint = Color(0xFF2E1065),
                    iconBg = Color(0xFFC4B5FD),
                    title = "Nhà cung cấp Passkey (Android 14+)",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        "Bật OpenVault trong Mật khẩu, mã khóa và tự động điền"
                    } else {
                        "Thiết bị đang chạy Android dưới 14 (chỉ hỗ trợ Autofill)"
                    },
                    hPad = hPad,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val intent = Intent(Settings.ACTION_CREDENTIAL_PROVIDER).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            },
                                        )
                                    }.onFailure {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                    }
                                }
                        } else {
                            onShowMessage("Passkey hệ thống yêu cầu Android 14 trở lên.")
                        }
                    },
                )

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                // SECTION 3: SECURITY (Biometric, PIN, Auto-lock, Clipboard)
                SettingsSwitchRow(
                    icon = Icons.Rounded.Fingerprint,
                    iconTint = Color(0xFF064E3B),
                    iconBg = Color(0xFF6EE7B7),
                    title = "Vân tay / Khuôn mặt",
                    subtitle = if (bioSupported) "Khóa phần cứng Android Keystore (StrongBox)" else "Thiết bị chưa cài sinh trắc học",
                    checked = settings.biometricEnabled,
                    enabled = bioSupported && activity != null,
                    hPad = hPad,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            repository.disableBiometric()
                            onShowMessage("Đã tắt mở khóa sinh trắc học.")
                        } else if (activity != null) {
                            val cipher = runCatching { BiometricKeyManager.createBiometricEncryptCipher() }
                                .getOrElse {
                                    onShowMessage("Không khởi tạo được Keystore: ${it.message}")
                                    return@SettingsSwitchRow
                                }
                            BiometricKeyManager.showBiometricPrompt(
                                activity = activity,
                                title = "Bật mở khóa sinh trắc học",
                                subtitle = "Xác nhận vân tay để lưu khóa an toàn",
                                negativeText = "Hủy",
                                cipher = cipher,
                                onSuccess = { authed ->
                                    runCatching { repository.enableBiometricWithCipher(authed) }
                                        .onSuccess { onShowMessage("Đã bật mở khóa bằng sinh trắc học!") }
                                        .onFailure { onShowMessage("Lỗi lưu khóa: ${it.message}") }
                                },
                                onError = { msg -> onShowMessage(msg) },
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = hPad, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CategoryIconBadge(
                        icon = Icons.Rounded.Pin,
                        tint = Color(0xFF451A03),
                        background = Color(0xFFFCD34D),
                        size = 38.dp,
                        circular = true,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mã PIN 6 số (tự động mở khóa)", color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Normal)
                        Text(
                            if (settings.pinEnabled) "Đang bật · PBKDF2 600.000 vòng" else "Mở khóa nhanh không cần gõ mật khẩu chính",
                            color = c.textSecondary,
                            fontSize = 12.5.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (settings.pinEnabled) {
                                repository.disablePin()
                                onShowMessage("Đã tắt mã PIN.")
                            } else {
                                pinInput = ""
                                pinConfirm = ""
                                pinError = null
                                showPinDialog = true
                            }
                        },
                    ) {
                        Text(
                            text = if (settings.pinEnabled) "Tắt" else "Bật",
                            color = if (settings.pinEnabled) c.danger else c.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (settings.pinEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = hPad + 52.dp, end = hPad, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Xóa PIN nếu sai", color = c.textSecondary, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5).forEach { count ->
                                OnePassChip("$count lần", settings.pinMaxAttempts == count) {
                                    repository.preferences.setPinMaxAttempts(count)
                                }
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    icon = Icons.Rounded.Security,
                    iconTint = Color(0xFF0C4A6E),
                    iconBg = Color(0xFF7DD3FC),
                    title = "Chống chụp màn hình (FLAG_SECURE)",
                    subtitle = "Ẩn nội dung kho trên ảnh chụp và màn hình đa nhiệm",
                    checked = settings.flagSecureEnabled,
                    enabled = true,
                    hPad = hPad,
                    onCheckedChange = { repository.preferences.setFlagSecureEnabled(it) },
                )

                Column(
                    modifier = Modifier.padding(horizontal = hPad, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tự động khóa khi ẩn ứng dụng", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "1p", 5 to "5p", 10 to "10p", 15 to "15p", 30 to "30p", 0 to "Không").forEach { (min, lbl) ->
                                OnePassChip(lbl, settings.autoLockMinutes == min) { repository.preferences.setAutoLockMinutes(min) }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tự xóa bảng tạm (Clipboard)", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15 to "15s", 30 to "30s", 60 to "60s", 120 to "120s", 0 to "Tắt").forEach { (sec, lbl) ->
                                OnePassChip(lbl, settings.clipboardClearSeconds == sec) { repository.preferences.setClipboardClearSeconds(sec) }
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                // SECTION 4: APPEARANCE
                Column(
                    modifier = Modifier.padding(horizontal = hPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CategoryIconBadge(
                            icon = Icons.Rounded.Palette,
                            tint = Color(0xFF831843),
                            background = Color(0xFFF9A8D4),
                            size = 38.dp,
                            circular = true,
                        )
                        Text("Giao diện", color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Normal)
                    }
                    Row(
                        modifier = Modifier
                            .padding(start = 52.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            AppThemeMode.DARK to "Tối (OLED)",
                            AppThemeMode.LIGHT to "Sáng",
                            AppThemeMode.SYSTEM to "Hệ thống",
                        ).forEach { (mode, lbl) ->
                            OnePassChip(lbl, settings.themeMode == mode) { repository.preferences.setThemeMode(mode) }
                        }
                    }
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                // SECTION 5: CLOUDFLARE R2 BACKUP
                Column(
                    modifier = Modifier.padding(horizontal = hPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CategoryIconBadge(
                            icon = Icons.Rounded.Backup,
                            tint = Color(0xFF083344),
                            background = Color(0xFF67E8F9),
                            size = 38.dp,
                            circular = true,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sao lưu mã hóa Cloudflare R2", color = c.textPrimary, fontSize = 15.5.sp)
                            Text("Snapshot tự động hằng ngày lúc 03:00 UTC", color = c.textSecondary, fontSize = 12.5.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.padding(start = 52.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                backupBusy = true
                                scope.launch {
                                    runCatching { repository.triggerR2Backup() }
                                        .onSuccess { res ->
                                            onShowMessage("Đã tạo bản sao lưu R2 (${res.ciphersCount} mục)!")
                                            runCatching { backups = repository.listR2Backups() }
                                        }
                                        .onFailure { onShowMessage("Lỗi sao lưu: ${it.message}") }
                                    backupBusy = false
                                }
                            },
                            enabled = !backupBusy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accentSoft, contentColor = c.navActiveContent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (backupBusy) "Đang lưu…" else "Sao lưu ngay", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { backups = repository.listR2Backups() }
                                        .onFailure { onShowMessage("Không tải được danh sách: ${it.message}") }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                        ) {
                            Text("Lịch sử")
                        }
                    }
                    if (backups.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = 52.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            backups.take(5).forEach { b ->
                                Text("• ${b.key.substringAfterLast('/')} (${b.size / 1024} KB)", color = c.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                // SECTION 6: SIGN OUT & VERSION
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    iconTint = Color.White,
                    iconBg = c.danger,
                    title = "Đăng xuất khỏi thiết bị này",
                    subtitle = "Xóa cache Room, mã PIN và liên kết vân tay cục bộ",
                    titleColor = c.danger,
                    hPad = hPad,
                    onClick = { showLogoutDialog = true },
                )

                Text(
                    text = "OpenVault Android v${BuildConfig.VERSION_NAME} · Tối ưu Galaxy Z Fold · MIT",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 20.dp),
                )
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { if (!pinBusy) showPinDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text("Thiết lập mã PIN 6 số", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Mã PIN được bọc bằng PBKDF2-SHA256 600.000 vòng. Nhập đủ 6 số sẽ tự động mở khóa.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { s -> pinInput = s.filter { it.isDigit() }.take(6); pinError = null },
                        label = { Text("Nhập mã PIN (6 chữ số)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { s -> pinConfirm = s.filter { it.isDigit() }.take(6); pinError = null },
                        label = { Text("Xác nhận mã PIN") },
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
                            pinError = "Vui lòng nhập đúng 6 chữ số."
                            return@TextButton
                        }
                        if (pinInput != pinConfirm) {
                            pinError = "Mã PIN xác nhận không khớp."
                            return@TextButton
                        }
                        pinBusy = true
                        scope.launch {
                            runCatching { repository.enablePin(pinInput) }
                                .onSuccess {
                                    pinBusy = false
                                    showPinDialog = false
                                    onShowMessage("Đã bật mở khóa bằng mã PIN 6 số!")
                                }
                                .onFailure {
                                    pinBusy = false
                                    pinError = it.message
                                }
                        }
                    },
                ) {
                    Text(if (pinBusy) "Đang lưu…" else "Lưu PIN", color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !pinBusy, onClick = { showPinDialog = false }) {
                    Text("Hủy", color = c.textSecondary)
                }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text("Đăng xuất khỏi OpenVault?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Toàn bộ cache mã hóa cục bộ, mã PIN và liên kết vân tay trên máy này sẽ bị xóa sạch.",
                    color = c.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch { repository.logout(allDevices = false) }
                    },
                ) {
                    Text("Đăng xuất", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Hủy", color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    hPad: androidx.compose.ui.unit.Dp,
    titleColor: Color? = null,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryIconBadge(icon = icon, tint = iconTint, background = iconBg, size = 38.dp, circular = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor ?: c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Normal)
            Text(subtitle, color = c.textSecondary, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    hPad: androidx.compose.ui.unit.Dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryIconBadge(icon = icon, tint = iconTint, background = iconBg, size = 38.dp, circular = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Normal)
            Text(subtitle, color = c.textSecondary, fontSize = 12.5.sp)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
