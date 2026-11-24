@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.network.R2BackupItemDto
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Redesigned Backup & Import/Export sub-page matching the elevated card styling of Accounts.
 */
@Composable
fun SettingsBackupContent(
    activeItemCount: Int,
    backups: List<R2BackupItemDto>,
    backupBusy: Boolean,
    backupsLoading: Boolean,
    backupsLoadedOnce: Boolean,
    importExportBusy: Boolean,
    hPad: Dp,
    onTriggerR2Backup: () -> Unit,
    onRefreshR2History: () -> Unit,
    onImportVault: () -> Unit,
    onExportEncrypted: () -> Unit,
    onExportPlaintext: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current

    val heroGradient = if (c.isDark) {
        Brush.linearGradient(listOf(Color(0xFF1A2140), Color(0xFF111827)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFF8FAFC)))
    }
    val heroBorder = Color(0xFF818CF8).copy(alpha = if (c.isDark) 0.30f else 0.25f)
    val indigoBadgeBg = if (c.isDark) Color(0xFF282454) else Color(0xFFE0E7FF)
    val indigoBadgeTint = if (c.isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Elevated Cloudflare R2 Hero Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, heroBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroGradient)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(indigoBadgeBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = indigoBadgeTint,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = s.r2BackupTitle,
                            color = c.textPrimary,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = s.r2BackupSub,
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(shape = CircleShape, color = c.successSoft) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = c.success,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Zero-Knowledge E2EE",
                                color = c.success,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Surface(shape = CircleShape, color = c.accentSoft) {
                        Text(
                            text = "$activeItemCount ${if (s.isVi) "mục đang bảo vệ" else "protected items"}",
                            color = c.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }

                    if (backups.isNotEmpty()) {
                        Surface(shape = CircleShape, color = indigoBadgeBg) {
                            Text(
                                text = "${backups.size} ${if (s.isVi) "bản sao lưu" else "snapshots"}",
                                color = indigoBadgeTint,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = c.divider.copy(alpha = 0.65f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onTriggerR2Backup,
                        enabled = !backupBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(46.dp),
                    ) {
                        if (backupBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = s.backingUp,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = s.backupNow,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRefreshR2History,
                        enabled = !backupsLoading,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, c.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = c.surfaceElevated,
                            contentColor = c.textPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(46.dp),
                    ) {
                        if (backupsLoading) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = c.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = s.viewHistory,
                                color = c.textPrimary,
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
        }

        // 2. R2 Snapshot History Card
        AnimatedVisibility(visible = backupsLoadedOnce || backups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (s.isVi) "LỊCH SỬ BẢN SAO LƯU R2" else "R2 SNAPSHOT HISTORY",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                    )
                    Surface(shape = CircleShape, color = c.accentSoft) {
                        Text(
                            text = "${backups.size}",
                            color = c.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    border = BorderStroke(1.dp, c.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (backups.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = c.textMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = if (s.isVi) "Chưa có bản sao lưu R2 nào trên máy chủ." else "No R2 snapshots found on the server yet.",
                                color = c.textSecondary,
                                fontSize = 13.5.sp,
                            )
                        }
                    } else {
                        val shown = backups.take(10)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            shown.forEachIndexed { idx, item ->
                                val sizeText = formatBackupBytes(item.size)
                                val timeText = formatBackupTimestamp(item.uploaded)
                                val subtitleText = if (timeText != null) "$sizeText · $timeText" else sizeText
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(indigoBadgeBg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = indigoBadgeTint,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = item.key.substringAfterLast('/'),
                                            color = c.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = subtitleText,
                                            color = c.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (idx < shown.lastIndex) {
                                    HorizontalDivider(
                                        color = c.divider.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 62.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Import & Export Vault Section
        Text(
            text = if (s.isVi) "NHẬP & XUẤT DỮ LIỆU KHO" else "IMPORT & EXPORT VAULT",
            color = c.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
        )

        if (importExportBusy) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = c.accent,
                    trackColor = c.accentSoft,
                )
                Text(
                    text = if (s.isVi) "Đang xử lý tệp dữ liệu…" else "Processing vault file…",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BackupActionRow(
                    icon = Icons.Rounded.UploadFile,
                    iconTint = Color(0xFF38BDF8),
                    iconBg = if (c.isDark) Color(0xFF0E293B) else Color(0xFFE0F2FE),
                    title = s.importVaultBtn,
                    subtitle = s.importVaultSub,
                    enabled = !importExportBusy,
                    onClick = onImportVault,
                )
                HorizontalDivider(
                    color = c.divider.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 66.dp),
                )
                BackupActionRow(
                    icon = Icons.Rounded.EnhancedEncryption,
                    iconTint = c.success,
                    iconBg = c.successSoft,
                    title = s.exportEncryptedBtn,
                    subtitle = s.exportEncryptedSub,
                    badgeText = if (s.isVi) "Khuyên dùng" else "Recommended",
                    enabled = !importExportBusy,
                    onClick = onExportEncrypted,
                )
            }
        }

        // Danger-Tinted Unencrypted Export Card
        Surface(
            onClick = { if (!importExportBusy) onExportPlaintext() },
            enabled = !importExportBusy,
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
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = c.danger,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = s.exportPlaintextBtn,
                        color = c.danger,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = s.exportPlaintextSub,
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.danger.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(shape = CircleShape, color = c.successSoft) {
                        Text(
                            text = badgeText,
                            color = c.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
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

private fun formatBackupBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${(bytes / 1024.0).let { if (it >= 10) it.toInt().toString() else String.format(Locale.US, "%.1f", it) }} KB"
    else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
}

private fun formatBackupTimestamp(iso: String): String? {
    val clean = iso.trim()
    if (clean.isEmpty()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    val parsed: Date? = patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(clean)
        }.getOrNull()
    }
    return if (parsed != null) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(parsed)
    } else {
        clean.take(16).replace('T', ' ')
    }
}
