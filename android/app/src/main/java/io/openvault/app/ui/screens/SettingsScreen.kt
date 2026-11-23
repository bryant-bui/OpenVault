package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.LiquidGlassChip
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    repository: VaultRepository,
    activity: FragmentActivity?,
    onShowMessage: (String) -> Unit,
    onLock: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Cài đặt & Bảo mật",
            color = c.textPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )

        // Account & Sync Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, elevated = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(account.email, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Máy chủ: ${account.serverUrl.removePrefix("https://")}\nĐồng bộ gần nhất: $syncTimeText",
                    color = c.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
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
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
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
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentSoft, contentColor = c.accent),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Khóa ngay", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // System Autofill Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tự động điền hệ thống Android", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Chọn OpenVault làm trình điền mật khẩu & gợi ý bàn phím mặc định",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                Text("Mở", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        // Biometrics & PIN Security Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mở khóa nhanh", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                SettingsToggleRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Vân tay / Khuôn mặt (Android Keystore)",
                    subtitle = if (bioSupported) {
                        "Bảo vệ khóa bằng StrongBox / phần cứng sinh trắc học"
                    } else {
                        "Thiết bị chưa cài sinh trắc học bảo mật cao"
                    },
                    checked = settings.biometricEnabled,
                    enabled = bioSupported && activity != null,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            repository.disableBiometric()
                            onShowMessage("Đã tắt mở khóa sinh trắc học.")
                        } else if (activity != null) {
                            val cipher = runCatching { BiometricKeyManager.createBiometricEncryptCipher() }
                                .getOrElse {
                                    onShowMessage("Không thể khởi tạo khóa Keystore: ${it.message}")
                                    return@SettingsToggleRow
                                }
                            BiometricKeyManager.showBiometricPrompt(
                                activity = activity,
                                title = "Kích hoạt mở khóa sinh trắc học",
                                subtitle = "Xác nhận vân tay để bọc khóa kho bằng Android Keystore",
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

                HorizontalDivider(color = c.glassBorderBottom)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Pin, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mã PIN Zero-Knowledge (6 số)", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (settings.pinEnabled) "Đang bật · Tự động đăng nhập ngay khi gõ đủ số"
                            else "PBKDF2 600.000 vòng, không lưu mã PIN gốc",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (settings.pinEnabled) {
                                repository.disablePin()
                                onShowMessage("Đã tắt mở khóa bằng mã PIN.")
                            } else {
                                pinInput = ""
                                pinConfirm = ""
                                pinError = null
                                showPinDialog = true
                            }
                        },
                    ) {
                        Text(
                            text = if (settings.pinEnabled) "Tắt" else "Thiết lập",
                            color = if (settings.pinEnabled) c.danger else c.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (settings.pinEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Xóa PIN sau số lần nhập sai", color = c.textSecondary, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5).forEach { count ->
                                LiquidGlassChip(
                                    label = "$count lần",
                                    selected = settings.pinMaxAttempts == count,
                                    onClick = { repository.preferences.setPinMaxAttempts(count) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Vault Protection Settings Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bảo vệ thiết bị", color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                SettingsToggleRow(
                    icon = Icons.Rounded.Security,
                    title = "Chống chụp màn hình (FLAG_SECURE)",
                    subtitle = "Ẩn nội dung kho khỏi ảnh chụp và màn hình đa nhiệm",
                    checked = settings.flagSecureEnabled,
                    enabled = true,
                    onCheckedChange = { repository.preferences.setFlagSecureEnabled(it) },
                )

                HorizontalDivider(color = c.glassBorderBottom)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tự động khóa khi ẩn ứng dụng", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1 to "1p", 5 to "5p", 10 to "10p", 15 to "15p", 30 to "30p", 0 to "Không").forEach { (min, lbl) ->
                            LiquidGlassChip(
                                label = lbl,
                                selected = settings.autoLockMinutes == min,
                                onClick = { repository.preferences.setAutoLockMinutes(min) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tự động xóa bảng tạm (Clipboard)", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15 to "15s", 30 to "30s", 60 to "60s", 120 to "120s", 0 to "Tắt").forEach { (sec, lbl) ->
                            LiquidGlassChip(
                                label = lbl,
                                selected = settings.clipboardClearSeconds == sec,
                                onClick = { repository.preferences.setClipboardClearSeconds(sec) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Giao diện Liquid Glass", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            LiquidGlassChip(
                                label = mode.labelVi,
                                selected = settings.themeMode == mode,
                                onClick = { repository.preferences.setThemeMode(mode) },
                            )
                        }
                    }
                }
            }
        }

        // Cloudflare R2 Encrypted Backups Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Backup, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sao lưu mã hóa Cloudflare R2", color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Snapshot D1 tự động mỗi ngày lúc 03:00 UTC", color = c.textSecondary, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentSoft, contentColor = c.accent),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (backupBusy) "Đang sao lưu…" else "Sao lưu ngay", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { backups = repository.listR2Backups() }
                                    .onFailure { onShowMessage("Không tải được danh sách: ${it.message}") }
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.glassSurface, contentColor = c.textPrimary),
                    ) {
                        Text("Xem bản lưu")
                    }
                }

                if (backups.isNotEmpty()) {
                    backups.take(5).forEach { b ->
                        Text(
                            text = "• ${b.key.substringAfterLast('/')} (${b.size / 1024} KB)",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // Logout & Version
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            onClick = { showLogoutDialog = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = c.danger)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Đăng xuất & Xóa bộ nhớ cục bộ", color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Xóa khóa RAM, Room cache và thu hồi phiên trên máy này", color = c.textSecondary, fontSize = 12.sp)
                }
            }
        }

        Text(
            text = "OpenVault Android v${BuildConfig.VERSION_NAME} · MIT Open Source",
            color = c.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { if (!pinBusy) showPinDialog = false },
            containerColor = c.glassSurfaceElevated,
            title = { Text("Thiết lập mã PIN 6 số", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Mã PIN được bảo vệ bằng PBKDF2-SHA256 600.000 vòng. Khi nhập đủ 6 số sẽ tự động mở khóa.",
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
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { s -> pinConfirm = s.filter { it.isDigit() }.take(6); pinError = null },
                        label = { Text("Xác nhận mã PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = c.danger, fontSize = 12.5.sp)
                    }
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
                    Text(if (pinBusy) "Đang mã hóa…" else "Lưu mã PIN", color = c.accent, fontWeight = FontWeight.Bold)
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
            containerColor = c.glassSurfaceElevated,
            title = { Text("Đăng xuất khỏi OpenVault?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Toàn bộ cache mã hóa cục bộ, mã PIN và liên kết sinh trắc học trên máy này sẽ được xóa sạch. Dữ liệu trên Cloudflare D1 vẫn giữ nguyên.",
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
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
