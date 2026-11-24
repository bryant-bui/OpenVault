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
import androidx.compose.material.icons.rounded.GridView
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
import androidx.compose.runtime.mutableStateMapOf
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
import io.openvault.app.core.crypto.RecoveryCodeUtils
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
import io.openvault.app.ui.theme.LocalStrings
import io.openvault.app.ui.theme.formatLocalTimestamp
import kotlinx.coroutines.delay

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
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current

    var showPassword by remember(item.id) { mutableStateOf(false) }
    var showCardSecrets by remember(item.id) { mutableStateOf(false) }
    var showLargeType by remember(item.id) { mutableStateOf(false) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    var overflowOpen by remember(item.id) { mutableStateOf(false) }
    val recoveryRevealMap = remember(item.id) { mutableStateMapOf<String, Boolean>() }

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

    val visibleCustomFields = remember(item.fields) { item.visibleFields }
    val recoveryFields = remember(visibleCustomFields) {
        visibleCustomFields.filter { RecoveryCodeUtils.isRecoveryCodesField(it.name) }
    }
    val normalCustomFields = remember(visibleCustomFields) {
        visibleCustomFields.filterNot { RecoveryCodeUtils.isRecoveryCodesField(it.name) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
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
                        contentDescription = s.back,
                        tint = c.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = s.favorites,
                        tint = if (item.favorite) c.warning else c.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (!item.isDeleted) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = s.edit,
                            tint = c.textPrimary,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                } else {
                    IconButton(onClick = onRestore) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash,
                            contentDescription = s.restore,
                            tint = c.success,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = c.textPrimary,
                            modifier = Modifier.size(24.dp),
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
                                text = { Text(s.largeType, color = c.textPrimary, fontSize = 15.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = c.textSecondary) },
                                onClick = {
                                    overflowOpen = false
                                    showLargeType = true
                                },
                            )
                        }
                        if (item.isDeleted) {
                            DropdownMenuItem(
                                text = { Text(s.restore, color = c.success, fontSize = 15.5.sp) },
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
                                    text = if (item.isDeleted) s.deletePermanently else s.moveToTrash,
                                    color = c.danger,
                                    fontSize = 15.5.sp,
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
                // Context Vault Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnePassAccountAvatar(email = email, size = 24.dp)
                    Text(
                        text = ownerName,
                        color = c.textSecondary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("|", color = c.divider, fontSize = 14.5.sp)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = folder?.name ?: s.personalVault,
                        color = c.textSecondary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isDeleted) {
                        Text(
                            text = s.trashBadge,
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

                // Item Hero Row (custom icon OR website favicon OR split-category icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ItemIconTile(
                        title = item.name,
                        type = item.type,
                        size = if (fold.isNarrowCover) 54.dp else 62.dp,
                        secureNoteType = item.secureNoteType,
                        showFavoriteStar = item.favorite,
                        customIconBase64 = item.customIconBase64,
                        websiteUrl = item.primaryWebsiteUrl,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.name,
                            color = c.textPrimary,
                            fontSize = if (fold.isNarrowCover) 22.sp else 25.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp,
                        )
                        if (item.type == CipherType.SECURE_NOTE) {
                            Text(
                                text = item.secureNoteCategory.label(s.isVi),
                                color = c.accent,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.accentSoft)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                // LOGIN PRIMARY OUTLINED BOX (Username, Password, TOTP, Passkeys grouped at top!)
                if (item.type == CipherType.LOGIN && item.login != null) {
                    val login = item.login
                    if (
                        login.username.isNotBlank() ||
                        login.password.isNotBlank() ||
                        liveTotp != null ||
                        login.fido2Credentials.isNotEmpty()
                    ) {
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
                                        label = s.username,
                                        value = login.username,
                                        onCopy = { onCopy(s.username, login.username, false) },
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
                                            .clickable { onCopy(s.password, login.password, true) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = s.password,
                                                color = c.fieldLabel,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = strength.level.label(s.isVi),
                                                    color = c.textSecondary,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                StrengthDonutRing(
                                                    score = strength.level.score,
                                                    color = strengthColor,
                                                    size = 18.dp,
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
                                                fontSize = 16.5.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .width(1.dp)
                                                    .height(22.dp)
                                                    .background(c.divider),
                                            )
                                            IconButton(
                                                onClick = { showPassword = !showPassword },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Toggle password",
                                                    tint = c.textSecondary,
                                                    modifier = Modifier.size(21.dp),
                                                )
                                            }
                                            if (!fold.isNarrowCover) {
                                                IconButton(
                                                    onClick = { showLargeType = true },
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Fullscreen,
                                                        contentDescription = s.largeType,
                                                        tint = c.textSecondary,
                                                        modifier = Modifier.size(21.dp),
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onCopy(s.password, login.password, true) },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ContentCopy,
                                                    contentDescription = s.copy,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(20.dp),
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
                                            .clickable { onCopy("2FA", liveTotp.code, true) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = s.oneTimePassword,
                                                color = c.fieldLabel,
                                                fontSize = 14.sp,
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
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                TotpInlineCountdown(totp = liveTotp)
                                            }
                                        }
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = s.copy,
                                            tint = c.accent,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    hasPrev = true
                                }

                                if (login.fido2Credentials.isNotEmpty()) {
                                    if (hasPrev) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Key,
                                                contentDescription = null,
                                                tint = Color(0xFFA78BFA),
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = s.passkeys,
                                                color = c.fieldLabel,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        login.fido2Credentials.forEach { pk ->
                                            val pkText = "${pk.rpName ?: pk.rpId} · ${pk.userName ?: pk.credentialId.take(12)}"
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF7253ED).copy(alpha = 0.12f))
                                                    .clickable { onCopy(s.passkeys, pk.userName ?: pk.rpId, false) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = pkText,
                                                    color = c.textPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Icon(
                                                    Icons.Rounded.ContentCopy,
                                                    contentDescription = s.copy,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Websites: Clickable links ONLY (no "website" title label)
                    if (login.uris.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            login.uris.forEach { uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onCopy(s.website, uri.uri, false) }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = uri.uri,
                                        color = c.accent,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
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
                                                contentDescription = "Open link",
                                                tint = c.textSecondary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                )
                                IconButton(onClick = { showCardSecrets = !showCardSecrets }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (showCardSecrets) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle card",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Text(
                                text = displayNum,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (fold.isNarrowCover) 18.sp else 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(if (s.isVi) "CHỦ THẺ" else "CARDHOLDER", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
                                    Text(card.cardholderName.ifBlank { "—" }, color = Color.White, fontSize = 15.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (s.isVi) "HẾT HẠN" else "EXPIRES", color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp)
                                    Text(
                                        "${card.expMonth.ifBlank { "--" }}/${card.expYear.takeLast(2).ifBlank { "--" }}",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp,
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
                                val numLabel = if (s.isVi) "Số Thẻ" else "Card Number"
                                DetailFieldRow(numLabel, displayNum) { onCopy(numLabel, card.number, true) }
                            }
                            if (card.code.isNotBlank()) {
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                DetailFieldRow("CVV", if (showCardSecrets) card.code else "•••") { onCopy("CVV", card.code, true) }
                            }
                            if (card.cardholderName.isNotBlank()) {
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                val holderLabel = if (s.isVi) "Chủ Thẻ" else "Cardholder"
                                DetailFieldRow(holderLabel, card.cardholderName) { onCopy(holderLabel, card.cardholderName, false) }
                            }
                        }
                    }
                }

                // IDENTITY SECTION
                if (item.type == CipherType.IDENTITY && item.identity != null) {
                    val id = item.identity
                    val rows = listOfNotNull(
                        id.fullName.takeIf { it.isNotBlank() }?.let { (if (s.isVi) "Họ Và Tên" else "Full Name") to it },
                        id.email.takeIf { it.isNotBlank() }?.let { "Email" to it },
                        id.phone.takeIf { it.isNotBlank() }?.let { (if (s.isVi) "Điện Thoại" else "Phone") to it },
                        id.company.takeIf { it.isNotBlank() }?.let { (if (s.isVi) "Công Ty" else "Company") to it },
                        id.username.takeIf { it.isNotBlank() }?.let { s.username to it },
                        listOf(id.address1, id.address2, id.city, id.state, id.postalCode, id.country)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                            ?.let { (if (s.isVi) "Địa Chỉ" else "Address") to it },
                        id.passportNumber.takeIf { it.isNotBlank() }?.let { (if (s.isVi) "Hộ Chiếu / CCCD" else "Passport / ID") to it },
                        id.licenseNumber.takeIf { it.isNotBlank() }?.let { (if (s.isVi) "Giấy Phép" else "License") to it },
                    )
                    if (rows.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = s.identificationHeader,
                                color = c.textPrimary,
                                fontSize = 13.5.sp,
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

                // RECOVERY CODES TABLE UI (4x5 bordered grid on standard screens, 2x10 on narrow fold cover)
                recoveryFields.forEachIndexed { rIdx, rcField ->
                    val fieldKey = "${rcField.name}_$rIdx"
                    val showCodes = recoveryRevealMap[fieldKey] ?: (rcField.type != 1)
                    val slots = remember(rcField.value) { RecoveryCodeUtils.parseRecoveryCodesToSlots(rcField.value) }
                    val filledCount = remember(slots) { RecoveryCodeUtils.countFilledRecoveryCodes(slots) }
                    val cols = if (fold.isNarrowCover) 2 else 4
                    val title = rcField.name.ifBlank { s.recoveryCodesTitle }

                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        borderColor = c.cardBorder,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(19.dp),
                                )
                                Text(
                                    text = title,
                                    color = c.fieldLabel,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$filledCount/20",
                                    color = c.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c.accentSoft)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                                IconButton(
                                    onClick = { recoveryRevealMap[fieldKey] = !showCodes },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = if (showCodes) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showCodes) s.hideCodes else s.showCodes,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val allText = slots.filter { it.isNotBlank() }.joinToString(" ")
                                        if (allText.isNotBlank()) onCopy(title, allText, true)
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = s.copyAll,
                                        tint = c.accent,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                            val chunkedRows = slots.chunked(cols)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                chunkedRows.forEachIndexed { rowIdx, rowCells ->
                                    if (rowIdx > 0) HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        rowCells.forEachIndexed { colIdx, code ->
                                            val slotIdx = rowIdx * cols + colIdx
                                            if (colIdx > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(0.7.dp)
                                                        .height(44.dp)
                                                        .background(c.divider),
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .let { baseMod ->
                                                        if (code.isNotBlank()) {
                                                            baseMod.clickable {
                                                                onCopy("$title #${slotIdx + 1}", code, true)
                                                            }
                                                        } else {
                                                            baseMod
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = "%02d".format(slotIdx + 1),
                                                    color = c.textMuted,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp,
                                                )
                                                Text(
                                                    text = when {
                                                        code.isBlank() -> "----"
                                                        !showCodes -> "••••"
                                                        else -> code
                                                    },
                                                    color = if (code.isBlank()) c.textMuted.copy(alpha = 0.45f) else c.textPrimary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (code.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // STANDARD CUSTOM FIELDS (excludes hidden internal `_ov_icon` & `_ov_account_avatar`)
                if (normalCustomFields.isNotEmpty()) {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        borderColor = c.cardBorder,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            normalCustomFields.forEachIndexed { idx, f ->
                                if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                val isHidden = f.type == 1
                                val displayVal = if (isHidden && !showPassword) "••••••••" else f.value
                                val label = f.name.ifBlank { if (s.isVi) "Trường Tùy Chỉnh" else "Custom Field" }
                                DetailFieldRow(
                                    label = label,
                                    value = displayVal,
                                ) {
                                    onCopy(label, f.value, isHidden)
                                }
                            }
                        }
                    }
                }

                // NOTES
                if (item.notes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCopy(s.notes, item.notes, true) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.notes, color = c.fieldLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = item.notes, color = c.textPrimary, fontSize = 16.sp, lineHeight = 23.sp)
                    }
                }

                // Device Local Timezone Timestamps
                if (!item.revisionDate.isNullOrBlank() || !item.creationDate.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!item.revisionDate.isNullOrBlank()) {
                            Text(
                                text = "${s.lastEditedPrefix}${formatLocalTimestamp(item.revisionDate, s.isVi)}",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (!item.creationDate.isNullOrBlank()) {
                            Text(
                                text = "${s.addedPrefix}${formatLocalTimestamp(item.creationDate, s.isVi)}",
                                color = c.textMuted,
                                fontSize = 13.sp,
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
            onCopy = { onCopy(s.password, item.login.password, true) },
            onDismiss = { showLargeType = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surfaceElevated,
            title = {
                Text(
                    if (item.isDeleted) s.deleteConfirmPermTitle else s.deleteConfirmTrashTitle,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "\"${item.name}\" ${if (item.isDeleted) s.deleteConfirmPermMsg else s.deleteConfirmTrashMsg}",
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
                        if (item.isDeleted) s.deletePermanently else s.delete,
                        color = c.danger,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(s.cancel, color = c.textSecondary)
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
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = c.textPrimary, fontSize = 16.5.sp)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = s.copy,
            tint = c.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}
