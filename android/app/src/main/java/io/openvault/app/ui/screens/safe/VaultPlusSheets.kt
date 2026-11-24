@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.openvault.app.ui.screens.safe

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.data.SafeRepository
import io.openvault.app.data.SafeStatusSnapshot
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Floating bottom-anchored Settings ModalBottomSheet for Vault+.
 * Slides smoothly from the bottom edge and exposes Biometric Unlock,
 * Anti-Screenshot toggle (FLAG_SECURE), Auto-Lock timeout, PIN rotation, and Wipe.
 */
@Composable
fun SafeSettingsBottomSheet(
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showChangePin by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (s.isVi) "Cài Đặt Vault+" else "Vault+ Settings",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${formatBytes(status.usedBytes)} · ${status.itemCount} ${if (s.isVi) "tài liệu" else "documents"}",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = s.close,
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.75f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Section 1: Security & Unlock
                SafeSheetSectionHeader(
                    text = if (s.isVi) "BẢO MẬT & MỞ KHÓA" else "SECURITY & UNLOCK",
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (canBio) {
                            SafeSettingsToggleRow(
                                icon = Icons.Rounded.Fingerprint,
                                iconTint = Color(0xFF0EA5E9),
                                iconBg = if (c.isDark) Color(0xFF0F293B) else Color(0xFFE0F2FE),
                                title = s.vaultPlusEnableBioToggle,
                                subtitle = if (s.isVi) "Xác thực nhanh bằng vân tay hoặc khuôn mặt" else "Fast unlock via fingerprint or face",
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
                                                    onShowMessage(
                                                        if (s.isVi) "Đã bật Mở khoá Sinh Trắc Học cho Vault+."
                                                        else "Vault+ Biometric Unlock enabled.",
                                                    )
                                                },
                                                onError = { onShowMessage(it) },
                                            )
                                        }
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = c.divider.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 68.dp),
                            )
                        }

                        SafeSettingsToggleRow(
                            icon = Icons.Rounded.Security,
                            iconTint = Color(0xFF818CF8),
                            iconBg = if (c.isDark) Color(0xFF1E2242) else Color(0xFFE0E7FF),
                            title = s.vaultPlusAntiScreenshotTitle,
                            subtitle = s.vaultPlusAntiScreenshotDesc,
                            checked = status.flagSecureEnabled,
                            onCheckedChange = { enabled ->
                                safeRepository.setFlagSecureEnabled(enabled)
                            },
                        )

                        HorizontalDivider(
                            color = c.divider.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 68.dp),
                        )

                        // Auto-Lock Timeout Segment Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (c.isDark) Color(0xFF2B2415) else Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = if (s.isVi) "Tự động khóa sau" else "Auto-lock after",
                                    color = c.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = c.accentSoft,
                                ) {
                                    Text(
                                        text = "${status.autoLockMinutes} ${if (s.isVi) "phút" else "min"}",
                                        color = c.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = c.surfaceElevated,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    listOf(1, 3, 5, 10).forEach { mins ->
                                        val active = status.autoLockMinutes == mins
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { safeRepository.updateAutoLockMinutes(mins) }
                                                }
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (active) c.accentButtonBg else Color.Transparent,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = if (s.isVi) "${mins}p" else "${mins}m",
                                                    color = if (active) c.accentButtonFg else c.textSecondary,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Passcode Management
                SafeSheetSectionHeader(
                    text = if (s.isVi) "QUẢN LÝ MÃ KHÓA" else "PASSCODE",
                )

                Surface(
                    onClick = { showChangePin = true },
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.cardBorder),
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
                                text = if (s.isVi) "Đổi mã PIN 6 số" else "Change 6-Digit PIN",
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = if (s.isVi) "Cập nhật mã PIN bảo vệ lớp 2" else "Update your secondary protection PIN",
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.textMuted,
                        )
                    }
                }

                // Section 3: Danger Zone
                SafeSheetSectionHeader(
                    text = if (s.isVi) "VÙNG NGUY HIỂM" else "DANGER ZONE",
                    color = c.danger,
                )

                Surface(
                    onClick = { showWipe = true },
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
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                tint = c.danger,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.vaultPlusWipeCta,
                                color = c.danger,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (s.isVi) "Xóa vĩnh viễn mọi tài liệu khỏi Cloudflare R2 & D1" else "Permanently wipe all files in Cloudflare R2 & D1",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.danger,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showChangePin) {
        SafeChangePinBottomSheet(
            safeRepository = safeRepository,
            onDismiss = { showChangePin = false },
            onChanged = {
                showChangePin = false
                onShowMessage(if (s.isVi) "Đã đổi mã PIN Vault+." else "Vault+ PIN updated.")
            },
        )
    }

    if (showWipe) {
        SafeNuclearWipeBottomSheet(
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

private enum class AddSheetMode {
    MENU,
    PHOTO_GALLERY,
    PDF_BROWSER,
}

/**
 * Floating bottom-anchored Add Document ModalBottomSheet with:
 * - Built-in swipe-expandable PDF browser (+ system SAF fallback)
 * - Built-in swipe-expandable Photo Gallery
 * - Direct RAM Camera capture
 * - New Folder creation
 */
@Composable
fun SafeAddDocumentBottomSheet(
    onDismiss: () -> Unit,
    onPickPdfFromSystem: () -> Unit,
    onFileUriSelected: (Uri) -> Unit,
    onOpenCamera: () -> Unit,
    onCreateFolder: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var mode by remember { mutableStateOf(AddSheetMode.MENU) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "safe_add_sheet_mode",
        ) { currentMode ->
            when (currentMode) {
                AddSheetMode.MENU -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = s.vaultPlusAddDocument,
                                    color = c.textPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (s.isVi) "Mã hóa AES-256-GCM trên thiết bị · Tối đa 5 MB/tệp"
                                    else "On-device AES-256-GCM encryption · Max 5 MB/file",
                                    color = c.textSecondary,
                                    fontSize = 12.5.sp,
                                )
                            }
                            Surface(
                                onClick = {
                                    scope.launch {
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                },
                                shape = CircleShape,
                                color = c.surface,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = s.close,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        SafeAddOptionCard(
                            icon = Icons.Rounded.PictureAsPdf,
                            iconTint = Color(0xFFEF4444),
                            iconBg = if (c.isDark) Color(0xFF33181D) else Color(0xFFFEE2E2),
                            title = s.vaultPlusChoosePdf,
                            subtitle = if (s.isVi) "Mở trình duyệt PDF tích hợp, vuốt lên để mở rộng"
                            else "Open built-in PDF browser, swipe up to expand",
                            onClick = { mode = AddSheetMode.PDF_BROWSER },
                        )

                        SafeAddOptionCard(
                            icon = Icons.Rounded.PhotoLibrary,
                            iconTint = Color(0xFF0EA5E9),
                            iconBg = if (c.isDark) Color(0xFF0F293B) else Color(0xFFE0F2FE),
                            title = s.vaultPlusChoosePhoto,
                            subtitle = if (s.isVi) "Mở thư viện ảnh tích hợp, vuốt lên để mở rộng"
                            else "Open built-in photo gallery, swipe up to expand",
                            onClick = { mode = AddSheetMode.PHOTO_GALLERY },
                        )

                        SafeAddOptionCard(
                            icon = Icons.Rounded.CameraAlt,
                            iconTint = Color(0xFF10B981),
                            iconBg = if (c.isDark) Color(0xFF0E2B23) else Color(0xFFD1FAE5),
                            title = s.vaultPlusTakePhoto,
                            subtitle = if (s.isVi) "Chụp trực tiếp vào RAM (không lưu ra Thư viện máy)"
                            else "Capture straight into RAM without saving to device gallery",
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                    onOpenCamera()
                                }
                            },
                        )

                        SafeAddOptionCard(
                            icon = Icons.Rounded.CreateNewFolder,
                            iconTint = Color(0xFFF59E0B),
                            iconBg = if (c.isDark) Color(0xFF2D2310) else Color(0xFFFEF3C7),
                            title = if (s.isVi) "Tạo thư mục mới" else "Create New Folder",
                            subtitle = if (s.isVi) "Phân loại CCCD, hộ chiếu, tài chính vào thư mục riêng"
                            else "Organize IDs, passports, and documents into folders",
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                    onCreateFolder()
                                }
                            },
                        )
                    }
                }

                AddSheetMode.PHOTO_GALLERY -> {
                    BackHandler { mode = AddSheetMode.MENU }
                    SafeInAppGalleryContent(
                        isPartiallyExpanded = sheetState.currentValue == SheetValue.PartiallyExpanded,
                        onExpandFull = { scope.launch { sheetState.expand() } },
                        onBack = { mode = AddSheetMode.MENU },
                        onClose = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        onPhotoSelected = { uri, sizeBytes ->
                            if (sizeBytes > SafeCrypto.SAFE_MAX_FILE_BYTES) {
                                onShowMessage(
                                    if (s.isVi) "Ảnh vượt quá giới hạn 5 MB (${formatBytes(sizeBytes)})."
                                    else "Photo exceeds 5 MB limit (${formatBytes(sizeBytes)}).",
                                )
                            } else {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                    onFileUriSelected(uri)
                                }
                            }
                        },
                    )
                }

                AddSheetMode.PDF_BROWSER -> {
                    BackHandler { mode = AddSheetMode.MENU }
                    SafeInAppPdfBrowserContent(
                        isPartiallyExpanded = sheetState.currentValue == SheetValue.PartiallyExpanded,
                        onExpandFull = { scope.launch { sheetState.expand() } },
                        onBack = { mode = AddSheetMode.MENU },
                        onClose = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        onOpenSystemPicker = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                onPickPdfFromSystem()
                            }
                        },
                        onPdfSelected = { uri, sizeBytes ->
                            if (sizeBytes > SafeCrypto.SAFE_MAX_FILE_BYTES) {
                                onShowMessage(
                                    if (s.isVi) "Tệp PDF vượt quá giới hạn 5 MB (${formatBytes(sizeBytes)})."
                                    else "PDF exceeds 5 MB limit (${formatBytes(sizeBytes)}).",
                                )
                            } else {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                    onFileUriSelected(uri)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class DevicePdfDocument(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedSec: Long,
)

@Composable
private fun SafeInAppPdfBrowserContent(
    isPartiallyExpanded: Boolean,
    onExpandFull: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onPdfSelected: (Uri, Long) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current

    var permissionVersion by remember { mutableIntStateOf(0) }
    var hasLegacyReadPermission by remember(permissionVersion) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
            else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val legacyPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionVersion += 1
        hasLegacyReadPermission = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasLegacyReadPermission) {
            legacyPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var pdfFiles by remember { mutableStateOf<List<DevicePdfDocument>>(emptyList()) }
    var loadingPdfs by remember { mutableStateOf(false) }

    LaunchedEffect(hasLegacyReadPermission, permissionVersion) {
        if (!hasLegacyReadPermission) return@LaunchedEffect
        loadingPdfs = true
        pdfFiles = withContext(Dispatchers.IO) { queryRecentDevicePdfs(context) }
        loadingPdfs = false
    }

    val filteredPdfs = remember(pdfFiles, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) pdfFiles
        else pdfFiles.filter { it.displayName.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.vaultPlusChoosePdf,
                    color = c.textPrimary,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isPartiallyExpanded) {
                        if (s.isVi) "Vuốt lên để mở toàn màn hình · Tối đa 5 MB" else "Swipe up to expand · Max 5 MB"
                    } else {
                        if (s.isVi) "Chọn tệp PDF cần mã hóa vào Vault+" else "Select a PDF document to encrypt"
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isPartiallyExpanded) {
                Surface(
                    onClick = onExpandFull,
                    shape = CircleShape,
                    color = c.accentSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (s.isVi) "Mở rộng" else "Expand",
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary)
            }
        }

        // Quick search + System Storage Picker pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (s.isVi) "Tìm tệp PDF..." else "Search PDFs...", fontSize = 13.5.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onOpenSystemPicker,
                shape = RoundedCornerShape(14.dp),
                color = c.accentSoft,
                border = BorderStroke(1.dp, c.accent.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (s.isVi) "Bộ nhớ" else "Browse",
                        color = c.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        HorizontalDivider(color = c.divider.copy(alpha = 0.75f))

        when {
            !hasLegacyReadPermission -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (s.isVi) "Cần quyền đọc bộ nhớ để liệt kê tệp PDF hoặc dùng nút Bộ nhớ."
                        else "Storage permission is needed to list PDFs, or tap Browse.",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { legacyPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (s.isVi) "Cấp quyền" else "Allow")
                    }
                }
            }

            loadingPdfs -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            }

            filteredPdfs.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (c.isDark) Color(0xFF33181D) else Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (s.isVi) "Không có tệp PDF hiển thị trực tiếp" else "No Indexed PDFs Found",
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (s.isVi) {
                            "Trên Android 13+, một số thư mục riêng cần mở trực tiếp qua trình chọn tệp hệ thống."
                        } else {
                            "On Android 13+, some folders require choosing via the system file browser."
                        },
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onOpenSystemPicker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accentButtonBg,
                            contentColor = c.accentButtonFg,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (s.isVi) "Mở bộ nhớ thiết bị" else "Open Device Storage",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredPdfs, key = { it.id }) { doc ->
                        val tooLarge = doc.sizeBytes > SafeCrypto.SAFE_MAX_FILE_BYTES
                        val dateText = remember(doc.modifiedSec) {
                            if (doc.modifiedSec <= 0L) ""
                            else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(doc.modifiedSec * 1000L))
                        }
                        Surface(
                            onClick = { onPdfSelected(doc.uri, doc.sizeBytes) },
                            shape = RoundedCornerShape(14.dp),
                            color = c.surface,
                            border = BorderStroke(0.9.dp, c.cardBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (c.isDark) Color(0xFF33181D) else Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = doc.displayName,
                                        color = c.textPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(formatBytes(doc.sizeBytes))
                                            if (dateText.isNotEmpty()) {
                                                append(" · ")
                                                append(dateText)
                                            }
                                        },
                                        color = c.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (tooLarge) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xCCDC2626),
                                    ) {
                                        Text(
                                            text = ">5 MB",
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun queryRecentDevicePdfs(context: Context): List<DevicePdfDocument> {
    val result = mutableListOf<DevicePdfDocument>()
    val seenIds = HashSet<Long>()
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.MIME_TYPE,
    )
    val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ? OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?"
    val selectionArgs = arrayOf("application/pdf", "%.pdf")
    val urisToQuery = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        add(MediaStore.Files.getContentUri("external"))
    }
    for (collectionUri in urisToQuery) {
        runCatching {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && result.size < 150) {
                    val id = cursor.getLong(idIdx)
                    if (!seenIds.add(id)) continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "document.pdf" else "document.pdf"
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val mod = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val itemUri = ContentUris.withAppendedId(collectionUri, id)
                    result.add(DevicePdfDocument(id = id, uri = itemUri, displayName = name, sizeBytes = size, modifiedSec = mod))
                }
            }
        }
    }
    return result.sortedByDescending { it.modifiedSec }
}

private data class DeviceGalleryPhoto(
    val id: Long,
    val uri: Uri,
    val sizeBytes: Long,
)

@Composable
private fun SafeInAppGalleryContent(
    isPartiallyExpanded: Boolean,
    onExpandFull: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPhotoSelected: (Uri, Long) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current

    var permissionVersion by remember { mutableIntStateOf(0) }
    var hasFullAccess by remember(permissionVersion) {
        mutableStateOf(checkFullPhotoPermission(context))
    }
    var hasPartialAccess by remember(permissionVersion) {
        mutableStateOf(checkPartialPhotoPermission(context))
    }
    val hasAnyAccess = hasFullAccess || hasPartialAccess

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionVersion += 1
        hasFullAccess = checkFullPhotoPermission(context)
        hasPartialAccess = checkPartialPhotoPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!hasAnyAccess) {
            permissionLauncher.launch(photoPermissionsToRequest())
        }
    }

    var photos by remember { mutableStateOf<List<DeviceGalleryPhoto>>(emptyList()) }
    var loadingPhotos by remember { mutableStateOf(false) }

    val thumbCache = remember { LruCache<Long, ImageBitmap>(90) }

    LaunchedEffect(hasAnyAccess, permissionVersion) {
        if (!hasAnyAccess) return@LaunchedEffect
        loadingPhotos = true
        photos = withContext(Dispatchers.IO) { queryRecentDevicePhotos(context) }
        loadingPhotos = false
    }

    // fillMaxHeight(0.95f) enables half-screen peek in PartiallyExpanded and full-screen on swipe up
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = s.back,
                    tint = c.textPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (s.isVi) "Thư viện ảnh" else "Photo Gallery",
                    color = c.textPrimary,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isPartiallyExpanded) {
                        if (s.isVi) "Vuốt lên để mở toàn màn hình · Tối đa 5 MB" else "Swipe up to expand · Max 5 MB"
                    } else {
                        if (s.isVi) "Chọn ảnh cần mã hóa vào Vault+" else "Tap a photo to encrypt into Vault+"
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isPartiallyExpanded) {
                Surface(
                    onClick = onExpandFull,
                    shape = CircleShape,
                    color = c.accentSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (s.isVi) "Mở rộng" else "Expand",
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary)
            }
        }

        if (hasPartialAccess && !hasFullAccess) {
            Surface(
                color = c.accentSoft.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (s.isVi) "Đang cấp quyền ảnh giới hạn" else "Limited photo access",
                        color = c.textPrimary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { permissionLauncher.launch(photoPermissionsToRequest()) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (s.isVi) "Chọn thêm ảnh" else "Select more",
                            color = c.accent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = c.divider.copy(alpha = 0.75f))

        when {
            !hasAnyAccess -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (s.isVi) "Cần quyền truy cập ảnh" else "Photo Access Required",
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (s.isVi) {
                            "Cho phép OpenVault đọc ảnh từ thiết bị để hiển thị thư viện riêng trong Vault+."
                        } else {
                            "Allow OpenVault to access device photos to browse your in-app Vault+ gallery."
                        },
                        color = c.textSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                                runCatching { context.startActivity(intent) }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(if (s.isVi) "Mở Cài đặt" else "Open Settings")
                        }
                        Button(
                            onClick = { permissionLauncher.launch(photoPermissionsToRequest()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.accentButtonBg,
                                contentColor = c.accentButtonFg,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = if (s.isVi) "Cấp quyền" else "Allow Access",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            loadingPhotos -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            }

            photos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (s.isVi) "Không tìm thấy ảnh nào trên thiết bị." else "No photos found on this device.",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        SafeGalleryThumbnailTile(
                            photo = photo,
                            thumbCache = thumbCache,
                            isVi = s.isVi,
                            onClick = { onPhotoSelected(photo.uri, photo.sizeBytes) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeGalleryThumbnailTile(
    photo: DeviceGalleryPhoto,
    thumbCache: LruCache<Long, ImageBitmap>,
    isVi: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf(thumbCache.get(photo.id)) }
    val tooLarge = photo.sizeBytes > SafeCrypto.SAFE_MAX_FILE_BYTES

    LaunchedEffect(photo.id) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                loadPhotoThumbnail(context, photo.uri)
            }
            if (loaded != null) {
                thumbCache.put(photo.id, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(0.8.dp, c.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (tooLarge) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xCCDC2626),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
            ) {
                Text(
                    text = if (isVi) ">5 MB" else ">5 MB",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun checkFullPhotoPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private fun checkPartialPhotoPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        false
    }
}

private fun photoPermissionsToRequest(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
    )

    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )
}

private fun queryRecentDevicePhotos(context: Context): List<DeviceGalleryPhoto> {
    val result = mutableListOf<DeviceGalleryPhoto>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.SIZE,
    )
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext() && result.size < 250) {
                val id = cursor.getLong(idIdx)
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                result.add(DeviceGalleryPhoto(id = id, uri = uri, sizeBytes = size))
            }
        }
    }
    return result
}

private fun loadPhotoThumbnail(context: Context, uri: Uri): ImageBitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(280, 280), null).asImageBitmap()
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
        }
    }.getOrNull()
}

@Composable
private fun SafeAddOptionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = c.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = c.textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textMuted,
            )
        }
    }
}

@Composable
private fun SafeSheetSectionHeader(
    text: String,
    color: Color = LocalOpenVaultColors.current.textMuted,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SafeSettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.5.sp,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SafeChangePinBottomSheet(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var revealPins by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = !busy,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (s.isVi) "Đổi Mã PIN Vault+" else "Change Vault+ PIN",
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Nhập PIN hiện tại và mã PIN 6 số mới" else "Enter current PIN and your new 6-digit PIN",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                IconButton(onClick = { revealPins = !revealPins }) {
                    Icon(
                        imageVector = if (revealPins) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = c.textSecondary,
                    )
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            val vis = if (revealPins) VisualTransformation.None else PasswordVisualTransformation()

            OutlinedTextField(
                value = currentPin,
                onValueChange = { currentPin = it.filter(Char::isDigit).take(6); err = null },
                label = { Text(if (s.isVi) "PIN hiện tại" else "Current PIN") },
                trailingIcon = {
                    Text("${currentPin.length}/6", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                },
                visualTransformation = vis,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(6); err = null },
                label = { Text(if (s.isVi) "PIN mới (6 số)" else "New 6-digit PIN") },
                trailingIcon = {
                    Text("${newPin.length}/6", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                },
                visualTransformation = vis,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); err = null },
                label = { Text(s.vaultPlusPinConfirmLabel) },
                trailingIcon = {
                    Text("${confirmPin.length}/6", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                },
                visualTransformation = vis,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (err != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.dangerSoft,
                    border = BorderStroke(1.dp, c.danger.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = err.orEmpty(),
                        color = c.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            Button(
                enabled = !busy && currentPin.length == 6 && newPin.length == 6 && confirmPin.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                                sheetState.hide()
                                onChanged()
                            }
                            .onFailure { ex ->
                                busy = false
                                err = ex.message
                            }
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accentButtonFg, strokeWidth = 2.dp)
                } else {
                    Text(if (s.isVi) "Cập Nhật Mã PIN" else "Update PIN", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun SafeNuclearWipeBottomSheet(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onWiped: () -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var masterPassword by remember { mutableStateOf("") }
    var revealPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = !busy,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.dangerSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.danger, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.vaultPlusWipeCta,
                        color = c.danger,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Thao tác không thể hoàn tác" else "Irreversible action",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                Surface(
                    onClick = {
                        if (!busy) scope.launch { sheetState.hide(); onDismiss() }
                    },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.dangerSoft,
                border = BorderStroke(1.dp, c.danger.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (s.isVi) {
                        "Hành động này sẽ xóa vĩnh viễn toàn bộ tài liệu, thư mục, cấu hình PIN và SafeKey của Vault+ trên Cloudflare R2 & D1."
                    } else {
                        "This permanently deletes all Vault+ files, folders, PIN configuration, and SafeKey from Cloudflare R2 & D1."
                    },
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }

            OutlinedTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it; err = null },
                label = { Text(s.masterPassword) },
                trailingIcon = {
                    IconButton(onClick = { revealPassword = !revealPassword }) {
                        Icon(
                            imageVector = if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (err != null) {
                Text(err.orEmpty(), color = c.danger, fontSize = 13.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(s.cancel, fontWeight = FontWeight.Medium)
                }

                Button(
                    enabled = !busy && masterPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp),
                    onClick = {
                        busy = true
                        err = null
                        scope.launch {
                            runCatching { safeRepository.resetVaultPlus(masterPassword) }
                                .onSuccess {
                                    busy = false
                                    sheetState.hide()
                                    onWiped()
                                }
                                .onFailure { ex ->
                                    busy = false
                                    err = ex.message
                                }
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.deletePermanently, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SafeRecoveryBottomSheet(
    safeRepository: SafeRepository,
    onDismiss: () -> Unit,
    onRecovered: (String?) -> Unit,
) {
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var recoveryKey by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var rotateKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = !busy,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.vaultPlusRecoverCta,
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Nhập Recovery Key OVRK-... và tạo mã PIN mới"
                        else "Enter your OVRK-... Recovery Key and choose a new PIN",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            OutlinedTextField(
                value = recoveryKey,
                onValueChange = { recoveryKey = it; err = null },
                label = { Text("Recovery Key (OVRK-XXXX-...)") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(6); err = null },
                label = { Text(if (s.isVi) "Mã PIN mới (6 số)" else "New 6-digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); err = null },
                label = { Text(s.vaultPlusPinConfirmLabel) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { rotateKey = !rotateKey }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rotateKey, onCheckedChange = { rotateKey = it })
                Text(
                    text = if (s.isVi) "Tạo Recovery Key mới sau khi khôi phục" else "Generate a new Recovery Key",
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                )
            }
            if (err != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.dangerSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(err.orEmpty(), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
            Button(
                enabled = !busy && recoveryKey.isNotBlank() && newPin.length == 6 && confirmPin.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                                sheetState.hide()
                                onRecovered(newKey)
                            }
                            .onFailure { ex ->
                                busy = false
                                err = ex.message
                            }
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accentButtonFg, strokeWidth = 2.dp)
                } else {
                    Text(s.restore, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
