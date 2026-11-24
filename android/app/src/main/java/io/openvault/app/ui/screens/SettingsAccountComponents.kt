@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.KdfConfig
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.StrengthLevel
import io.openvault.app.core.media.GitHubAuthorInfo
import io.openvault.app.core.media.IconLoader
import io.openvault.app.core.security.AccountMeta
import io.openvault.app.core.security.AccountProfileInfo
import io.openvault.app.core.security.SecuritySettings
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun SettingsAccountsContent(
    repository: VaultRepository,
    account: AccountMeta,
    settings: SecuritySettings,
    activeItemCount: Int,
    syncTimeText: String,
    isSyncing: Boolean,
    avatarBusy: Boolean,
    hPad: Dp,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onShowMessage: (String) -> Unit,
    onLock: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val profile = settings.accountProfile

    var showAvatarSheet by remember { mutableStateOf(false) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showChangePwdSheet by remember { mutableStateOf(false) }

    val fallbackName = remember(account.email) {
        val local = account.email.substringBefore("@").trim()
        if (local.isNotBlank()) local.replaceFirstChar { it.uppercaseChar() } else "OpenVault"
    }
    val resolvedName = profile.displayName.takeIf { it.isNotBlank() } ?: fallbackName
    val serverHost = remember(account.serverUrl) {
        runCatching { Uri.parse(account.serverUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: account.serverUrl
    }
    val kdfShort = if (account.kdfConfig.kdfType == KdfConfig.KDF_ARGON2ID) "Argon2id" else "PBKDF2"
    val kdfSummary = if (account.kdfConfig.kdfType == KdfConfig.KDF_ARGON2ID) {
        "Argon2id · ${account.kdfConfig.memoryMiB ?: 64} MiB · ${account.kdfConfig.iterations} iter"
    } else {
        "PBKDF2-SHA256 · ${account.kdfConfig.iterations} iter"
    }

    val heroGradient = if (c.isDark) {
        Brush.linearGradient(listOf(Color(0xFF172542), Color(0xFF111827)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEAF2FF), Color(0xFFF8FAFC)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Elevated Hero Account Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, c.accent.copy(alpha = if (c.isDark) 0.28f else 0.22f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroGradient)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        OnePassAccountAvatar(
                            email = account.email,
                            size = 76.dp,
                            avatarBase64Override = settings.accountAvatarBase64,
                            onClick = {
                                if (!avatarBusy) {
                                    if (settings.accountAvatarBase64.isNullOrBlank()) {
                                        onPickAvatar()
                                    } else {
                                        showAvatarSheet = true
                                    }
                                }
                            },
                        )

                        Surface(
                            onClick = {
                                if (!avatarBusy) {
                                    if (settings.accountAvatarBase64.isNullOrBlank()) {
                                        onPickAvatar()
                                    } else {
                                        showAvatarSheet = true
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = c.accentButtonBg,
                            border = BorderStroke(2.dp, if (c.isDark) Color(0xFF121A2B) else Color.White),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (avatarBusy) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = s.changeAvatar,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = resolvedName,
                            color = c.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = account.email.ifBlank { "OpenVault" },
                            color = c.textSecondary,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile.bio.isNotBlank() || profile.city.isNotBlank()) {
                            Text(
                                text = profile.bio.ifBlank { profile.city },
                                color = c.textMuted,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = c.successSoft,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = c.success,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "E2EE · $syncTimeText",
                                    color = c.success,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = c.divider.copy(alpha = 0.65f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AccountMetricColumn(
                        label = if (s.isVi) "Số mục" else "Vault Items",
                        value = activeItemCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    AccountMetricColumn(
                        label = if (s.isVi) "Mã hóa" else "Encryption",
                        value = kdfShort,
                        modifier = Modifier.weight(1f),
                    )
                    AccountMetricColumn(
                        label = if (s.isVi) "Máy chủ" else "Server",
                        value = serverHost,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }

        // 2. Personal Details Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (s.isVi) "THÔNG TIN CÁ NHÂN" else "PERSONAL INFORMATION",
                color = c.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
            )
            Surface(
                onClick = { showEditProfileSheet = true },
                shape = CircleShape,
                color = c.accentSoft,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Text(
                        text = s.edit,
                        color = c.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val emptyLabel = if (s.isVi) "Chưa cập nhật" else "Not set"
                AccountDetailRow(
                    icon = Icons.Rounded.Badge,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = if (c.isDark) Color(0xFF0E293B) else Color(0xFFE0F2FE),
                    label = if (s.isVi) "Tên hiển thị" else "Display Name",
                    value = profile.displayName.ifBlank { emptyLabel },
                    isPlaceholder = profile.displayName.isBlank(),
                    onClick = { showEditProfileSheet = true },
                )
                HorizontalDivider(color = c.divider.copy(alpha = 0.6f), modifier = Modifier.padding(start = 62.dp))

                AccountDetailRow(
                    icon = Icons.Rounded.Cake,
                    iconTint = Color(0xFFF472B6),
                    iconBg = if (c.isDark) Color(0xFF35172B) else Color(0xFFFCE7F3),
                    label = if (s.isVi) "Ngày tháng năm sinh" else "Date of Birth",
                    value = profile.birthDate.ifBlank { emptyLabel },
                    isPlaceholder = profile.birthDate.isBlank(),
                    onClick = { showEditProfileSheet = true },
                )
                HorizontalDivider(color = c.divider.copy(alpha = 0.6f), modifier = Modifier.padding(start = 62.dp))

                AccountDetailRow(
                    icon = Icons.Rounded.Phone,
                    iconTint = Color(0xFF34D399),
                    iconBg = if (c.isDark) Color(0xFF0E2B23) else Color(0xFFD1FAE5),
                    label = if (s.isVi) "Số điện thoại" else "Phone Number",
                    value = profile.phone.ifBlank { emptyLabel },
                    isPlaceholder = profile.phone.isBlank(),
                    onClick = { showEditProfileSheet = true },
                )
                HorizontalDivider(color = c.divider.copy(alpha = 0.6f), modifier = Modifier.padding(start = 62.dp))

                AccountDetailRow(
                    icon = Icons.Rounded.LocationCity,
                    iconTint = Color(0xFFFBBF24),
                    iconBg = if (c.isDark) Color(0xFF2D2310) else Color(0xFFFEF3C7),
                    label = if (s.isVi) "Nơi sống" else "City / Region",
                    value = profile.city.ifBlank { emptyLabel },
                    isPlaceholder = profile.city.isBlank(),
                    onClick = { showEditProfileSheet = true },
                )
                HorizontalDivider(color = c.divider.copy(alpha = 0.6f), modifier = Modifier.padding(start = 62.dp))

                AccountDetailRow(
                    icon = Icons.Rounded.Home,
                    iconTint = Color(0xFFA78BFA),
                    iconBg = if (c.isDark) Color(0xFF251D3D) else Color(0xFFEDE9FE),
                    label = if (s.isVi) "Địa chỉ" else "Address",
                    value = profile.address.ifBlank { emptyLabel },
                    isPlaceholder = profile.address.isBlank(),
                    onClick = { showEditProfileSheet = true },
                )
                if (profile.bio.isNotBlank()) {
                    HorizontalDivider(color = c.divider.copy(alpha = 0.6f), modifier = Modifier.padding(start = 62.dp))
                    AccountDetailRow(
                        icon = Icons.Rounded.Description,
                        iconTint = Color(0xFF60A5FA),
                        iconBg = c.accentSoft,
                        label = if (s.isVi) "Giới thiệu" else "Bio",
                        value = profile.bio,
                        isPlaceholder = false,
                        onClick = { showEditProfileSheet = true },
                    )
                }
            }
        }

        // 3. Security & Sync Card
        Text(
            text = if (s.isVi) "BẢO MẬT & ĐỒNG BỘ" else "SECURITY & SYNC",
            color = c.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showChangePwdSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (s.isVi) "Đổi mật khẩu chính" else "Change Master Password",
                            color = c.textPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = kdfSummary,
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = c.textMuted,
                    )
                }

                HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { repository.syncNow(force = true) }
                                    .onSuccess {
                                        onShowMessage(if (s.isVi) "Đã đồng bộ kho thành công!" else "Vault synced!")
                                    }
                                    .onFailure { onShowMessage("${it.message}") }
                            }
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = s.syncNow,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Button(
                        onClick = onLock,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = s.lockVault,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 4. Danger Zone Card (Sign Out)
        Surface(
            onClick = onRequestLogout,
            shape = RoundedCornerShape(18.dp),
            color = c.dangerSoft,
            border = BorderStroke(1.dp, c.danger.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.danger.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = c.danger,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.signOut,
                        color = c.danger,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (s.isVi) "Xóa bộ nhớ đệm cục bộ, PIN & khóa vân tay trên thiết bị này"
                        else "Wipes local encrypted cache, PIN, and biometric keys on this device",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    if (showAvatarSheet) {
        AccountAvatarBottomSheet(
            hasAvatar = !settings.accountAvatarBase64.isNullOrBlank(),
            onDismiss = { showAvatarSheet = false },
            onChoosePhoto = {
                showAvatarSheet = false
                onPickAvatar()
            },
            onRemovePhoto = {
                showAvatarSheet = false
                onRemoveAvatar()
            },
        )
    }

    if (showEditProfileSheet) {
        AccountProfileEditSheet(
            initial = profile,
            onDismiss = { showEditProfileSheet = false },
            onSave = { updated ->
                scope.launch {
                    val synced = repository.saveAccountProfile(updated)
                    showEditProfileSheet = false
                    onShowMessage(
                        if (synced) {
                            if (s.isVi) "Đã lưu & đồng bộ thông tin tài khoản!" else "Account profile saved & synced!"
                        } else {
                            if (s.isVi) "Đã lưu thông tin trên máy (chờ đồng bộ)." else "Saved locally (pending sync)."
                        },
                    )
                }
            },
        )
    }

    if (showChangePwdSheet) {
        ChangeMasterPasswordSheet(
            onDismiss = { showChangePwdSheet = false },
            onSubmit = { currentPwd, newPwd, hint ->
                repository.changeMasterPassword(currentPwd, newPwd, hint)
                showChangePwdSheet = false
                onShowMessage(
                    if (s.isVi) "Đã đổi Mật khẩu chính thành công!" else "Master Password updated successfully!",
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutAuthorGitHubCard(isVi: Boolean) {
    val c = LocalOpenVaultColors.current
    val context = LocalContext.current

    val fallbackInfo = remember {
        GitHubAuthorInfo(
            name = "Bryant Bui",
            login = "bryant-bui",
            bio = null,
            avatarUrl = "https://avatars.githubusercontent.com/u/249981187?v=4",
            profileUrl = "https://github.com/bryant-bui",
            repoUrl = "https://github.com/bryant-bui/OpenVault",
            repoFullName = "bryant-bui/OpenVault",
            primaryLanguage = "Kotlin",
            stars = null,
            licenseName = "MIT",
        )
    }
    var authorInfo by remember { mutableStateOf(fallbackInfo) }

    LaunchedEffect(Unit) {
        val remote = IconLoader.fetchGitHubAuthorInfo()
        if (remote != null) authorInfo = remote
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isVi) "TÁC GIẢ & MÃ NGUỒN MỞ" else "AUTHOR & OPEN SOURCE",
                color = c.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = authorInfo.name,
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = CircleShape,
                        color = c.accentSoft,
                    ) {
                        Text(
                            text = "@${authorInfo.login}",
                            color = c.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = authorInfo.bio ?: if (isVi) {
                        "Tác giả & Nhà phát triển OpenVault"
                    } else {
                        "Creator & Maintainer of OpenVault"
                    },
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AboutMetaChip(icon = Icons.Rounded.Code, text = authorInfo.repoFullName)
                if (!authorInfo.primaryLanguage.isNullOrBlank()) {
                    AboutMetaChip(icon = null, text = authorInfo.primaryLanguage!!)
                }
                AboutMetaChip(icon = null, text = "${authorInfo.licenseName} License")
                authorInfo.stars?.takeIf { it > 0 }?.let { stars ->
                    AboutMetaChip(icon = Icons.Rounded.Star, text = "$stars", iconTint = Color(0xFFFBBF24))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { openUrl(authorInfo.profileUrl) },
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "GitHub",
                        color = c.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }

                Button(
                    onClick = { openUrl(authorInfo.repoUrl) },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (isVi) "Mã nguồn" else "Repository",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutMetaChip(
    icon: ImageVector?,
    text: String,
    iconTint: Color? = null,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        shape = CircleShape,
        color = c.surfaceElevated,
        border = BorderStroke(0.7.dp, c.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: c.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                color = c.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AccountMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountDetailRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = c.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = value,
                color = if (isPlaceholder) c.textMuted else c.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AccountAvatarBottomSheet(
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = s.changeAvatar,
                color = c.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Surface(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onChoosePhoto()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                border = BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = c.accent)
                    Text(s.chooseIconImage, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (hasAvatar) {
                Surface(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onRemovePhoto()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = c.dangerSoft,
                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = c.danger)
                        Text(s.removeAvatar, color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProfileEditSheet(
    initial: AccountProfileInfo,
    onDismiss: () -> Unit,
    onSave: (AccountProfileInfo) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var displayName by remember { mutableStateOf(initial.displayName) }
    var birthDate by remember { mutableStateOf(initial.birthDate) }
    var phone by remember { mutableStateOf(initial.phone) }
    var city by remember { mutableStateOf(initial.city) }
    var address by remember { mutableStateOf(initial.address) }
    var bio by remember { mutableStateOf(initial.bio) }
    var saving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (s.isVi) "Cập Nhật Thông Tin Tài Khoản" else "Edit Account Profile",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Được mã hóa E2EE và đồng bộ cùng két của bạn"
                        else "Encrypted E2EE and synced with your vault",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                Surface(
                    onClick = { if (!saving) scope.launch { sheetState.hide(); onDismiss() } },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(if (s.isVi) "Tên hiển thị" else "Display Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text(if (s.isVi) "Ngày tháng năm sinh (DD/MM/YYYY)" else "Date of Birth (YYYY-MM-DD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (s.isVi) "Số điện thoại" else "Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(if (s.isVi) "Nơi sống (Thành phố / Tỉnh)" else "City / Region") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (s.isVi) "Địa chỉ chi tiết" else "Street Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(if (s.isVi) "Giới thiệu ngắn" else "Short Bio") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        onSave(
                            AccountProfileInfo(
                                displayName = displayName,
                                birthDate = birthDate,
                                phone = phone,
                                city = city,
                                address = address,
                                bio = bio,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = if (s.isVi) "Lưu Thông Tin (E2EE)" else "Save Profile (E2EE)",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeMasterPasswordSheet(
    onDismiss: () -> Unit,
    onSubmit: suspend (currentPwd: String, newPwd: String, hint: String?) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val strength = remember(newPwd) { PasswordGenerator.estimateStrength(newPwd) }
    val strengthColor = when (strength.level) {
        StrengthLevel.WEAK -> c.danger
        StrengthLevel.FAIR -> c.warning
        StrengthLevel.GOOD -> c.accent
        StrengthLevel.STRONG, StrengthLevel.EXCELLENT -> c.success
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (s.isVi) "Đổi Mật Khẩu Chính" else "Change Master Password",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Khóa két của bạn sẽ được bọc lại an toàn bằng mật khẩu mới"
                        else "Your vault key will be securely re-wrapped with your new password",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                Surface(
                    onClick = { if (!busy) scope.launch { sheetState.hide(); onDismiss() } },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = currentPwd,
                    onValueChange = { currentPwd = it; errorMsg = null },
                    label = { Text(if (s.isVi) "Mật khẩu chính hiện tại" else "Current Master Password") },
                    singleLine = true,
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                imageVector = if (showCurrent) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it; errorMsg = null },
                    label = { Text(if (s.isVi) "Mật khẩu chính mới (≥ 8 ký tự)" else "New Master Password (≥ 8 chars)") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                imageVector = if (showNew) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (newPwd.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { ((strength.level.score + 1) / 5f).coerceIn(0.15f, 1f) },
                            color = strengthColor,
                            trackColor = c.divider.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                        )
                        Text(
                            text = "${strength.level.label(s.isVi)} · ${strength.entropyBits} bits",
                            color = strengthColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                OutlinedTextField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it; errorMsg = null },
                    label = { Text(if (s.isVi) "Xác nhận mật khẩu mới" else "Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text(if (s.isVi) "Gợi ý mật khẩu (tùy chọn)" else "Password Hint (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = c.danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    enabled = !busy && currentPwd.isNotEmpty() && newPwd.isNotEmpty() && confirmPwd.isNotEmpty(),
                    onClick = {
                        if (newPwd.length < 8) {
                            errorMsg = if (s.isVi) "Mật khẩu mới phải có ít nhất 8 ký tự." else "New password must be at least 8 characters."
                            return@Button
                        }
                        if (newPwd != confirmPwd) {
                            errorMsg = if (s.isVi) "Mật khẩu xác nhận không khớp." else "Passwords do not match."
                            return@Button
                        }
                        busy = true
                        errorMsg = null
                        scope.launch {
                            runCatching { onSubmit(currentPwd, newPwd, hint) }
                                .onFailure { ex ->
                                    busy = false
                                    errorMsg = ex.message ?: if (s.isVi) "Đổi mật khẩu thất bại." else "Password change failed."
                                }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = if (s.isVi) "Cập Nhật Mật Khẩu Chính" else "Update Master Password",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
