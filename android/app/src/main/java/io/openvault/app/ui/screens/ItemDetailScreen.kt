package io.openvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.LargeTypePasswordDialog
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.StrengthDonutRing
import io.openvault.app.ui.components.SyntaxColoredPasswordText
import io.openvault.app.ui.components.TotpInlineCountdown
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

/**
 * Authentic 1Password 8 Android Item Detail Screen:
 * - Elevated matte charcoal (#1C1D1F) canvas with clean top toolbar (← | ★ | ✎ | ⋮)
 * - Context bar: [Avatar] account | [Vault] Personal
 * - Large rounded-square icon + bold title
 * - Hairline-outlined primary credentials box (username + password w/ StrengthDonutRing + 2FA)
 * - Secondary fields (websites, passkeys, notes) laid cleanly on canvas + centered timestamps
 * - Seamlessly docks as Right Pane on unfolded Galaxy Z Fold 3 (showBackButton = false)
 */
@Composable
fun ItemDetailScreen(
    item: VaultItem,
    email: String = "",
    folder: VaultFolder?,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onHardDelete: () -> Unit,
    onCopy: (label: String, value: String, sensitive: Boolean) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current

    var showPassword by remember(item.id) { mutableStateOf(false) }
    var showCardSecrets by remember(item.id) { mutableStateOf(false) }
    var showLargeType by remember(item.id) { mutableStateOf(false) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    var overflowOpen by remember(item.id) { mutableStateOf(false) }

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

    val ownerName = remember(email) {
        val local = email.substringBefore("@").trim()
        if (local.isNotBlank()) local.replaceFirstChar { it.uppercaseChar() } else "OpenVault"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 1Password Android Detail Top Action Bar (← | ★ | ✎ | ⋮)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = c.textPrimary,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Yêu thích",
                        tint = if (item.favorite) c.warning else c.textPrimary,
                    )
                }
                if (!item.isDeleted) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Chỉnh sửa",
                            tint = c.textPrimary,
                        )
                    }
                } else {
                    IconButton(onClick = onRestore) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash,
                            contentDescription = "Khôi phục",
                            tint = c.success,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Thêm tùy chọn",
                            tint = c.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceElevated,
                        border = BorderStroke(1.dp, c.cardBorder),
                    ) {
                        if (!item.login?.password.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Phóng to mật khẩu", color = c.textPrimary, fontSize = 14.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = c.textSecondary) },
                                onClick = {
                                    overflowOpen = false
                                    showLargeType = true
                                },
                            )
                        }
                        if (item.isDeleted) {
                            DropdownMenuItem(
                                text = { Text("Khôi phục mục", color = c.success, fontSize = 14.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = null, tint = c.success) },
                                onClick = {
                                    overflowOpen = false
                                    onRestore()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (item.isDeleted) "Xóa vĩnh viễn" else "Chuyển vào thùng rác",
                                    color = c.danger,
                                    fontSize = 14.5.sp,
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = c.danger) },
                            onClick = {
                                overflowOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (fold.isNarrowCover) 14.dp else 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Context Vault Row: [Avatar] Hoang | [Vault] Personal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnePassAccountAvatar(email = email, onClick = { }, size = 22.dp)
                    Text(
                        text = ownerName,
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("|", color = c.divider, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = folder?.name ?: "Cá nhân",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isDeleted) {
                        Text(
                            text = "Thùng rác",
                            color = c.danger,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.dangerSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                // Item Hero Row: [56dp Icon]  Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ItemIconTile(
                        title = item.name,
                        type = item.type,
                        size = if (fold.isNarrowCover) 50.dp else 58.dp,
                        showFavoriteStar = item.favorite,
                    )
                    Text(
                        text = item.name,
                        color = c.textPrimary,
                        fontSize = if (fold.isNarrowCover) 21.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                // LOGIN PRIMARY OUTLINED BOX (Exact 1Password Android hairline-bordered card!)
                if (item.type == CipherType.LOGIN && item.login != null) {
                    val login = item.login
                    if (login.username.isNotBlank() || login.password.isNotBlank() || liveTotp != null) {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = c.cardBorder,
                            padding = PaddingValues(0.dp),
                        ) {
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
                                    if (hasPrev) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
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
                                            .padding(horizontal = 16.dp, vertical = 13.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "mật khẩu",
                                                color = c.fieldLabel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = strength.level.labelVi,
                                                    color = c.textSecondary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                StrengthDonutRing(
                                                    score = strength.level.score,
                                                    color = strengthColor,
                                                    size = 17.dp,
                                                )
                                            }
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
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .width(1.dp)
                                                    .height(20.dp)
                                                    .background(c.divider),
                                            )
                                            IconButton(
                                                onClick = { showPassword = !showPassword },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Hiện/Ẩn",
                                                    tint = c.textSecondary,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            if (!fold.isNarrowCover) {
                                                IconButton(
                                                    onClick = { showLargeType = true },
                                                    modifier = Modifier.size(34.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Fullscreen,
                                                        contentDescription = "Phóng to",
                                                        tint = c.textSecondary,
                                                        modifier = Modifier.size(19.dp),
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onCopy("mật khẩu", login.password, true) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
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
                                    if (hasPrev) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    val dotFormatted = if (liveTotp.code.length == 6) {
                                        "${liveTotp.code.substring(0, 3)} · ${liveTotp.code.substring(3)}"
                                    } else {
                                        liveTotp.formattedCode
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onCopy("mã 2FA", liveTotp.code, true) }
                                            .padding(horizontal = 16.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = "mã xác thực một lần (2FA)",
                                                color = c.fieldLabel,
                                                fontSize = 13.sp,
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

                    // Websites directly on canvas (exact 1Password Android Detail rhythm!)
                    if (login.uris.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            login.uris.forEachIndexed { idx, uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onCopy("trang web", uri.uri, false) }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = if (idx == 0) "trang web" else "trang web #${idx + 1}",
                                            color = c.fieldLabel,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = uri.uri,
                                            color = c.accent,
                                            fontSize = 15.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (uri.uri.startsWith("http://") || uri.uri.startsWith("https://")) {
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.uri)))
                                                }
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = "Mở trình duyệt",
                                                tint = c.textSecondary,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Passkeys stored
                    if (login.fido2Credentials.isNotEmpty()) {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = Color(0xFF7253ED).copy(alpha = 0.45f),
                            padding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Rounded.Key, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                    Text(
                                        "khóa truy cập (passkeys)",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
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

                // CARD SECTION
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
                        borderColor = Color.Transparent,
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
                                fontSize = if (fold.isNarrowCover) 17.sp else 19.sp,
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

                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        borderColor = c.cardBorder,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            if (card.number.isNotBlank()) {
                                DetailFieldRow("số thẻ", displayNum) { onCopy("số thẻ", card.number, true) }
                            }
                            if (card.code.isNotBlank()) {
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                DetailFieldRow("mã bảo mật (cvv)", if (showCardSecrets) card.code else "•••") {
                                    onCopy("CVV", card.code, true)
                                }
                            }
                            if (card.cardholderName.isNotBlank()) {
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                DetailFieldRow("chủ thẻ", card.cardholderName) { onCopy("chủ thẻ", card.cardholderName, false) }
                            }
                        }
                    }
                }

                // IDENTITY SECTION (exact 1Password Android section header + outlined primary card!)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "THÔNG TIN ĐỊNH DANH",
                                color = c.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                            OnePassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 12.dp,
                                backgroundColor = c.surface,
                                borderColor = c.cardBorder,
                                padding = PaddingValues(0.dp),
                            ) {
                                Column {
                                    rows.forEachIndexed { idx, (label, value) ->
                                        if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                        DetailFieldRow(label, value) { onCopy(label, value, false) }
                                    }
                                }
                            }
                        }
                    }
                }

                // NOTES cleanly rendered directly on elevated canvas
                if (item.notes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCopy("ghi chú", item.notes, true) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("ghi chú", color = c.fieldLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = item.notes, color = c.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }

                // CUSTOM FIELDS
                if (item.fields.isNotEmpty()) {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        borderColor = c.cardBorder,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            item.fields.forEachIndexed { idx, f ->
                                if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                val displayVal = if (f.type == 1 && !showPassword) "••••••••" else f.value
                                DetailFieldRow(f.name.ifBlank { "trường tùy chỉnh" }, displayVal) {
                                    onCopy(f.name.ifBlank { "giá trị" }, f.value, f.type == 1)
                                }
                            }
                        }
                    }
                }

                // Centered muted timestamps (exact 1Password Android Detail footer!)
                if (!item.revisionDate.isNullOrBlank() || !item.creationDate.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (!item.revisionDate.isNullOrBlank()) {
                            Text(
                                text = "Chỉnh sửa lần cuối: ${item.revisionDate.take(19).replace('T', ' ')} UTC",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (!item.creationDate.isNullOrBlank()) {
                            Text(
                                text = "Đã thêm: ${item.creationDate.take(19).replace('T', ' ')} UTC",
                                color = c.textMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
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
            containerColor = c.surfaceElevated,
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = c.textPrimary, fontSize = 15.5.sp)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Sao chép",
            tint = c.accent,
            modifier = Modifier.size(18.dp),
        )
    }
}
