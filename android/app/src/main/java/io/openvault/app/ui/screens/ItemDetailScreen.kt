package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
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
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.LargeTypePasswordDialog
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.SyntaxColoredPasswordText
import io.openvault.app.ui.components.TotpInlineCountdown
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Clean 1Password Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại", tint = c.accent)
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
                    TextButton(onClick = onEdit) {
                        Text("Sửa", color = c.accent, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Khôi phục", tint = c.success)
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Xóa", tint = c.danger)
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
            // 1Password Context Vault Bar + Item Hero
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        text = folder?.name ?: "Personal",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("·", color = c.textMuted)
                    Text(text = item.type.labelVi, color = c.textSecondary, fontSize = 13.sp)
                    if (item.isDeleted) {
                        Text(
                            text = "Thùng rác",
                            color = c.danger,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.dangerSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    ItemIconTile(
                        title = item.name,
                        type = item.type,
                        size = 52.dp,
                        showFavoriteStar = item.favorite,
                    )
                    Text(
                        text = item.name,
                        color = c.textPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // LOGIN GROUPED CARD (Exact 1Password 8 Amazon Detail layout!)
            if (item.type == CipherType.LOGIN && item.login != null) {
                val login = item.login
                if (login.username.isNotBlank() || login.password.isNotBlank() || liveTotp != null) {
                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column {
                        var hasPrev = false

                        if (login.username.isNotBlank()) {
                            DetailFieldRow(
                                label = "tên đăng nhập",
                                value = login.username,
                                onCopy = { onCopy("tên đăng nhập", login.username, false) },
                            )
                            hasPrev = true
                        }

                        if (login.password.isNotBlank()) {
                            if (hasPrev) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
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
                                    .clickable { onCopy("mật khẩu", login.password, true) }
                                    .padding(horizontal = 15.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "mật khẩu",
                                        color = c.fieldLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = strength.level.labelVi,
                                        color = strengthColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SyntaxColoredPasswordText(
                                        password = login.password,
                                        masked = !showPassword,
                                        fontSize = 15.5.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "Hiện/Ẩn",
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    IconButton(onClick = { showLargeType = true }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            Icons.Rounded.Fullscreen,
                                            contentDescription = "Phóng to",
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    IconButton(onClick = { onCopy("mật khẩu", login.password, true) }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Sao chép",
                                            tint = c.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            hasPrev = true
                        }

                        if (liveTotp != null) {
                            if (hasPrev) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            val dotFormatted = if (liveTotp.code.length == 6) {
                                "${liveTotp.code.substring(0, 3)} · ${liveTotp.code.substring(3)}"
                            } else {
                                liveTotp.formattedCode
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCopy("mã 2FA", liveTotp.code, true) }
                                    .padding(horizontal = 15.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = "mã xác thực một lần (2FA)",
                                        color = c.fieldLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = dotFormatted,
                                            color = c.textPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        TotpInlineCountdown(totp = liveTotp)
                                    }
                                }
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Sao chép 2FA",
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                }

                // Websites card
                if (login.uris.isNotEmpty()) {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            login.uris.forEachIndexed { idx, uri ->
                                if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCopy("trang web", uri.uri, false) }
                                        .padding(horizontal = 15.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = if (idx == 0) "trang web" else "trang web #${idx + 1}",
                                            color = c.fieldLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = uri.uri,
                                            color = c.accent,
                                            fontSize = 15.sp,
                                        )
                                    }
                                    if (uri.uri.startsWith("http://") || uri.uri.startsWith("https://")) {
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.uri)))
                                                }
                                            },
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = "Mở trình duyệt",
                                                tint = c.accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Passkeys stored
                if (login.fido2Credentials.isNotEmpty()) {
                    OnePassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("khóa truy cập (passkeys)", color = c.fieldLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            login.fido2Credentials.forEach { pk ->
                                Text(
                                    text = "${pk.rpName ?: pk.rpId} · ${pk.userName ?: pk.credentialId.take(12)}",
                                    color = c.textPrimary,
                                    fontSize = 14.5.sp,
                                )
                            }
                        }
                    }
                }
            }

            // CARD SECTION (Clean matte card + grouped fields)
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

                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF172554),
                    cornerRadius = 16.dp,
                    padding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = card.brand.ifBlank { "CREDIT CARD" }.uppercase(),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                            IconButton(onClick = { showCardSecrets = !showCardSecrets }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    if (showCardSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Hiện/Ẩn",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = displayNum,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("CHỦ THẺ", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                Text(card.cardholderName.ifBlank { "—" }, color = Color.White, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("HẾT HẠN", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
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

                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column {
                        if (card.number.isNotBlank()) {
                            DetailFieldRow("số thẻ", displayNum) { onCopy("số thẻ", card.number, true) }
                        }
                        if (card.code.isNotBlank()) {
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            DetailFieldRow("mã bảo mật (cvv)", if (showCardSecrets) card.code else "•••") {
                                onCopy("CVV", card.code, true)
                            }
                        }
                        if (card.cardholderName.isNotBlank()) {
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            DetailFieldRow("chủ thẻ", card.cardholderName) { onCopy("chủ thẻ", card.cardholderName, false) }
                        }
                    }
                }
            }

            // IDENTITY SECTION
            if (item.type == CipherType.IDENTITY && item.identity != null) {
                val id = item.identity
                val rows = listOfNotNull(
                    id.fullName.takeIf { it.isNotBlank() }?.let { "họ và tên" to it },
                    id.email.takeIf { it.isNotBlank() }?.let { "email" to it },
                    id.phone.takeIf { it.isNotBlank() }?.let { "điện thoại" to it },
                    id.company.takeIf { it.isNotBlank() }?.let { "công ty" to it },
                    id.username.takeIf { it.isNotBlank() }?.let { "tên người dùng" to it },
                    listOf(id.address1, id.address2, id.city, id.state, id.postalCode, id.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .takeIf { it.isNotBlank() }
                        ?.let { "địa chỉ" to it },
                    id.passportNumber.takeIf { it.isNotBlank() }?.let { "hộ chiếu / cccd" to it },
                    id.licenseNumber.takeIf { it.isNotBlank() }?.let { "giấy phép" to it },
                )
                if (rows.isNotEmpty()) {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            rows.forEachIndexed { idx, (label, value) ->
                                if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                                DetailFieldRow(label, value) { onCopy(label, value, false) }
                            }
                        }
                    }
                }
            }

            // NOTES
            if (item.notes.isNotBlank()) {
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCopy("ghi chú", item.notes, true) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ghi chú", color = c.fieldLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        Text(text = item.notes, color = c.textPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }

            // CUSTOM FIELDS
            if (item.fields.isNotEmpty()) {
                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column {
                        item.fields.forEachIndexed { idx, f ->
                            if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            val displayVal = if (f.type == 1 && !showPassword) "••••••••" else f.value
                            DetailFieldRow(f.name.ifBlank { "trường tùy chỉnh" }, displayVal) {
                                onCopy(f.name.ifBlank { "giá trị" }, f.value, f.type == 1)
                            }
                        }
                    }
                }
            }

            if (!item.revisionDate.isNullOrBlank()) {
                Text(
                    text = "Cập nhật gần nhất: ${item.revisionDate.take(19).replace('T', ' ')} UTC",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLargeType && !item.login?.password.isNullOrBlank()) {
        LargeTypePasswordDialog(
            title = item.name,
            password = item.login!!.password,
            onCopy = { onCopy("mật khẩu", item.login.password, true) },
            onDismiss = { showLargeType = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
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
                        "Mục \"${item.name}\" sẽ bị xóa vĩnh viễn khỏi Cloudflare D1."
                    } else {
                        "Mục \"${item.name}\" sẽ chuyển sang Đã xóa gần đây và có thể khôi phục."
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
private fun DetailFieldRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = c.textPrimary, fontSize = 15.sp)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Sao chép",
            tint = c.accent,
            modifier = Modifier.size(18.dp),
        )
    }
}
