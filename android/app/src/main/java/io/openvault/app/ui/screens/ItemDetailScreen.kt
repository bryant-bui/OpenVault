package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.BrandMonogramAvatar
import io.openvault.app.ui.components.LargeTypePasswordDialog
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.SyntaxColoredPasswordText
import io.openvault.app.ui.components.TotpCountdownRing
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

@Composable
fun ItemDetailScreen(
    item: VaultItem,
    folder: VaultFolder?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onHardDelete: () -> Unit,
    onCopy: (label: String, value: String, sensitive: Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val context = LocalContext.current

    var showPassword by remember { mutableStateOf(false) }
    var showCardSecrets by remember { mutableStateOf(false) }
    var showLargeType by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 1-second live TOTP ticker
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(item.login?.totp) {
        if (!item.login?.totp.isNullOrBlank()) {
            while (true) {
                nowSec = System.currentTimeMillis() / 1000L
                delay(1000L)
            }
        }
    }

    val liveTotp = remember(item.login?.totp, nowSec) {
        item.login?.totp?.takeIf { it.isNotBlank() }?.let { TotpEngine.generateFromInput(it, nowSec) }
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = c.textPrimary,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Yêu thích",
                            tint = if (item.favorite) c.warning else c.textSecondary,
                        )
                    }
                    if (!item.isDeleted) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Chỉnh sửa", tint = c.accent)
                        }
                    } else {
                        IconButton(onClick = onRestore) {
                            Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Khôi phục", tint = c.success)
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Xóa", tint = c.danger)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hero Glass Header
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevated = true,
                    padding = PaddingValues(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        BrandMonogramAvatar(title = item.name, type = item.type, size = 56.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.name,
                                color = c.textPrimary,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetaPill(item.type.labelVi, c.accent, c.accentSoft)
                                if (folder != null) {
                                    MetaPill(folder.name, c.textSecondary, c.glassSurface)
                                }
                                if (item.isDeleted) {
                                    MetaPill("Trong thùng rác", c.danger, c.dangerSoft)
                                }
                            }
                        }
                    }
                }

                // LOGIN CONTENT
                if (item.type == CipherType.LOGIN && item.login != null) {
                    val login = item.login
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            if (login.username.isNotBlank()) {
                                DetailCopyRow(
                                    label = "Tên đăng nhập / Email",
                                    value = login.username,
                                    onCopy = { onCopy("Tên đăng nhập", login.username, false) },
                                )
                            }

                            if (login.password.isNotBlank()) {
                                if (login.username.isNotBlank()) {
                                    HorizontalDivider(color = c.glassBorderBottom)
                                }
                                val strength = remember(login.password) {
                                    PasswordGenerator.estimateStrength(login.password)
                                }
                                val strengthColor = when {
                                    strength.level.score >= 3 -> c.success
                                    strength.level.score == 2 -> c.warning
                                    else -> c.danger
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCopy("Mật khẩu", login.password, true) }
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Mật khẩu", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        MetaPill(
                                            "${strength.level.labelVi} · ${strength.entropyBits} bit",
                                            strengthColor,
                                            strengthColor.copy(alpha = 0.16f),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SyntaxColoredPasswordText(
                                            password = login.password,
                                            masked = !showPassword,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = "Hiện/Ẩn",
                                                tint = c.textSecondary,
                                            )
                                        }
                                        IconButton(onClick = { showLargeType = true }) {
                                            Icon(
                                                Icons.Rounded.Fullscreen,
                                                contentDescription = "Phóng to",
                                                tint = c.textSecondary,
                                            )
                                        }
                                        IconButton(onClick = { onCopy("Mật khẩu", login.password, true) }) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Sao chép",
                                                tint = c.accent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live 2FA TOTP Card
                    if (liveTotp != null) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            elevated = true,
                            padding = PaddingValues(16.dp),
                            onClick = { onCopy("Mã xác thực 2FA", liveTotp.code, true) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Mã xác thực 2FA (TOTP)",
                                        color = c.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = liveTotp.formattedCode,
                                        color = c.textPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    TotpCountdownRing(totp = liveTotp, size = 46.dp)
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Sao chép mã 2FA",
                                        tint = c.accent,
                                    )
                                }
                            }
                        }
                    }

                    // Websites
                    if (login.uris.isNotEmpty()) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                login.uris.forEachIndexed { idx, uri ->
                                    if (idx > 0) HorizontalDivider(color = c.glassBorderBottom)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onCopy("Địa chỉ Web", uri.uri, false) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (idx == 0) "Trang web / Ứng dụng" else "URI #${idx + 1}",
                                                color = c.textSecondary,
                                                fontSize = 12.sp,
                                            )
                                            Text(
                                                text = uri.uri,
                                                color = c.accent,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        if (uri.uri.startsWith("http://") || uri.uri.startsWith("https://")) {
                                            IconButton(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.uri)))
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                                    contentDescription = "Mở trình duyệt",
                                                    tint = c.accent,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Stored Passkeys indicator (synced for Phase 4!)
                    if (login.fido2Credentials.isNotEmpty()) {
                        LiquidGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            padding = PaddingValues(16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Khóa truy cập (Passkeys)",
                                    color = c.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                login.fido2Credentials.forEach { pk ->
                                    Text(
                                        text = "${pk.rpName ?: pk.rpId} · ${pk.userName ?: pk.credentialId.take(12)}",
                                        color = c.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // PAYMENT CARD CONTENT (Virtual Liquid Glass Card Preview!)
                if (item.type == CipherType.CARD && item.card != null) {
                    val card = item.card
                    val cleanNum = card.number.filter { it.isDigit() }
                    val displayNum = if (showCardSecrets) {
                        cleanNum.chunked(4).joinToString("  ").ifBlank { card.number }
                    } else if (cleanNum.length >= 4) {
                        "••••  ••••  ••••  ${cleanNum.takeLast(4)}"
                    } else {
                        "••••  ••••"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F295C), Color(0xFF1E40AF), Color(0xFF5B21B6)),
                                ),
                            )
                            .border(1.2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                            .padding(22.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = card.brand.ifBlank { "Payment Card" }.uppercase(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                )
                                IconButton(
                                    onClick = { showCardSecrets = !showCardSecrets },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        if (showCardSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Hiện/Ẩn số thẻ",
                                        tint = Color.White,
                                    )
                                }
                            }
                            Text(
                                text = displayNum,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("CHỦ THẺ", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                                    Text(
                                        card.cardholderName.ifBlank { "—" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("HẾT HẠN", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                                    Text(
                                        "${card.expMonth.ifBlank { "--" }}/${card.expYear.takeLast(2).ifBlank { "--" }}",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }

                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = PaddingValues(0.dp)) {
                        Column {
                            if (card.number.isNotBlank()) {
                                DetailCopyRow("Số thẻ", displayNum) { onCopy("Số thẻ", card.number, true) }
                            }
                            if (card.code.isNotBlank()) {
                                HorizontalDivider(color = c.glassBorderBottom)
                                DetailCopyRow("Mã bảo mật (CVV)", if (showCardSecrets) card.code else "•••") {
                                    onCopy("CVV", card.code, true)
                                }
                            }
                            if (card.cardholderName.isNotBlank()) {
                                HorizontalDivider(color = c.glassBorderBottom)
                                DetailCopyRow("Tên chủ thẻ", card.cardholderName) {
                                    onCopy("Chủ thẻ", card.cardholderName, false)
                                }
                            }
                        }
                    }
                }

                // IDENTITY CONTENT
                if (item.type == CipherType.IDENTITY && item.identity != null) {
                    val id = item.identity
                    val rows = listOfNotNull(
                        id.fullName.takeIf { it.isNotBlank() }?.let { "Họ và tên" to it },
                        id.email.takeIf { it.isNotBlank() }?.let { "Email" to it },
                        id.phone.takeIf { it.isNotBlank() }?.let { "Số điện thoại" to it },
                        id.company.takeIf { it.isNotBlank() }?.let { "Công ty" to it },
                        id.username.takeIf { it.isNotBlank() }?.let { "Tên người dùng" to it },
                        listOf(id.address1, id.address2, id.city, id.state, id.postalCode, id.country)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                            ?.let { "Địa chỉ" to it },
                        id.passportNumber.takeIf { it.isNotBlank() }?.let { "Số hộ chiếu" to it },
                        id.licenseNumber.takeIf { it.isNotBlank() }?.let { "Giấy phép / CCCD" to it },
                    )
                    if (rows.isNotEmpty()) {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = PaddingValues(0.dp)) {
                            Column {
                                rows.forEachIndexed { idx, (label, value) ->
                                    if (idx > 0) HorizontalDivider(color = c.glassBorderBottom)
                                    DetailCopyRow(label, value) { onCopy(label, value, false) }
                                }
                            }
                        }
                    }
                }

                // NOTES
                if (item.notes.isNotBlank()) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        padding = PaddingValues(16.dp),
                        onClick = { onCopy("Ghi chú", item.notes, true) },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Ghi chú bảo mật", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = item.notes,
                                color = c.textPrimary,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }

                // CUSTOM FIELDS
                if (item.fields.isNotEmpty()) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, padding = PaddingValues(0.dp)) {
                        Column {
                            item.fields.forEachIndexed { idx, f ->
                                if (idx > 0) HorizontalDivider(color = c.glassBorderBottom)
                                val displayVal = if (f.type == 1 && !showPassword) "••••••••" else f.value
                                DetailCopyRow(f.name.ifBlank { "Trường tùy chỉnh" }, displayVal) {
                                    onCopy(f.name.ifBlank { "Giá trị" }, f.value, f.type == 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLargeType && !item.login?.password.isNullOrBlank()) {
        LargeTypePasswordDialog(
            title = item.name,
            password = item.login!!.password,
            onCopy = { onCopy("Mật khẩu", item.login.password, true) },
            onDismiss = { showLargeType = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.glassSurfaceElevated,
            title = {
                Text(
                    if (item.isDeleted) "Xóa vĩnh viễn?" else "Chuyển vào thùng rác?",
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    if (item.isDeleted) {
                        "Mục \"${item.name}\" sẽ bị xóa vĩnh viễn khỏi Cloudflare D1 và không thể khôi phục."
                    } else {
                        "Mục \"${item.name}\" sẽ được chuyển vào Thùng rác và có thể khôi phục sau."
                    },
                    color = c.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        if (item.isDeleted) onHardDelete() else onSoftDelete()
                    },
                ) {
                    Text(
                        if (item.isDeleted) "Xóa vĩnh viễn" else "Xóa",
                        color = c.danger,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Hủy", color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MetaPill(text: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Sao chép $label",
            tint = c.accent,
            modifier = Modifier.size(18.dp),
        )
    }
}
