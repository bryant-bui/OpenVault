package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassAddButton
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.components.TotpInlineCountdown
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

/**
 * 1Password 8 Mobile-inspired Home Screen:
 * - Clean matte canvas, Top-Left Account Avatar, Top-Right Generator/Lock/'+' circle
 * - Large "Trang chủ" headline + flat search bar
 * - Horizontal Pinned 2FA OTP cards (tap to copy code immediately)
 * - Profile / Autofill card, Collapsible Favorites card, Collapsible Recently Updated card
 */
@Composable
fun HomeScreen(
    email: String,
    items: List<VaultItem>,
    isSyncing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenNewItem: () -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onCopyTotp: (title: String, code: String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var query by remember { mutableStateOf("") }
    var favoritesExpanded by remember { mutableStateOf(true) }
    var recentExpanded by remember { mutableStateOf(true) }

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(items) {
        if (items.any { !it.isDeleted && it.hasTotp }) {
            while (true) {
                nowSec = System.currentTimeMillis() / 1000L
                delay(1000L)
            }
        }
    }

    val activeItems = remember(items) { items.filter { !it.isDeleted } }
    val pinnedTotpItems = remember(activeItems) { activeItems.filter { it.hasTotp }.take(8) }
    val favoriteItems = remember(activeItems) { activeItems.filter { it.favorite } }
    val recentItems = remember(activeItems) {
        activeItems.sortedByDescending { it.revisionDate ?: it.creationDate ?: "" }.take(12)
    }

    val searchResults = remember(activeItems, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else activeItems.filter { item ->
            item.name.lowercase().contains(q) ||
                item.login?.username?.lowercase()?.contains(q) == true ||
                item.login?.uris?.any { it.uri.lowercase().contains(q) } == true ||
                item.card?.cardholderName?.lowercase()?.contains(q) == true ||
                item.identity?.fullName?.lowercase()?.contains(q) == true ||
                item.notes.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        // Top Utility Row: Left Avatar | Right Sync + Generator + Lock + '+'
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OnePassAccountAvatar(email = email, onClick = onOpenSettings)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onSync, enabled = !isSyncing, modifier = Modifier.size(36.dp)) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Đồng bộ",
                            tint = c.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onOpenGenerator, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = "Tạo mật khẩu",
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onLock, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Khóa kho",
                        tint = c.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                OnePassAddButton(onClick = onOpenNewItem)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Trang chủ",
                        color = c.textPrimary,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.7).sp,
                    )
                    OnePassSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Tìm trong OpenVault",
                    )
                }
            }

            if (query.isNotBlank()) {
                item {
                    if (searchResults.isEmpty()) {
                        OnePassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Không tìm thấy mục nào khớp \"$query\"",
                                color = c.textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                searchResults.forEachIndexed { idx, item ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            color = c.divider,
                                            thickness = 0.7.dp,
                                            modifier = Modifier.padding(start = 62.dp),
                                        )
                                    }
                                    OnePassItemRow(
                                        item = item,
                                        showFavoriteBadge = true,
                                        onClick = { onSelectItem(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Pinned 2FA Horizontal Strip (exact 1Password 8 Home widget style!)
                if (pinnedTotpItems.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pinnedTotpItems.forEach { item ->
                                val totp = remember(item.login?.totp, nowSec) {
                                    item.login?.totp?.let { TotpEngine.generateFromInput(it, nowSec) }
                                }
                                if (totp != null) {
                                    val dotFormatted = if (totp.code.length == 6) {
                                        "${totp.code.substring(0, 3)} · ${totp.code.substring(3)}"
                                    } else {
                                        totp.formattedCode
                                    }
                                    OnePassCard(
                                        modifier = Modifier.width(196.dp),
                                        cornerRadius = 14.dp,
                                        padding = PaddingValues(13.dp),
                                        onClick = { onCopyTotp(item.name, totp.code) },
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                ItemIconTile(title = item.name, type = item.type, size = 22.dp)
                                                Text(
                                                    text = item.name,
                                                    color = c.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "mã xác thực một lần",
                                                    color = c.fieldLabel,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = dotFormatted,
                                                        color = c.textPrimary,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    TotpInlineCountdown(totp = totp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Profile & Quick Settings Card (matches 1Password 8 Home "Profile" card!)
                item {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = onOpenSettings,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryIconBadge(
                                icon = Icons.Rounded.Person,
                                tint = Color.White,
                                background = c.accent,
                                size = 34.dp,
                                circular = true,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hồ sơ & Cài đặt",
                                    color = c.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = email.ifBlank { "Quản lý sinh trắc học, PIN & tự động điền" },
                                    color = c.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = c.textMuted,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                // Favorites Collapsible Card (matches Image 1-left exactly!)
                if (favoriteItems.isNotEmpty()) {
                    item {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { favoritesExpanded = !favoritesExpanded }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CategoryIconBadge(
                                        icon = Icons.Rounded.Star,
                                        tint = Color.White,
                                        background = Color(0xFFF59E0B),
                                        size = 30.dp,
                                        circular = true,
                                    )
                                    Text(
                                        text = "Yêu thích",
                                        color = c.textPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = if (favoritesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = c.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                AnimatedVisibility(visible = favoritesExpanded) {
                                    Column {
                                        favoriteItems.forEach { item ->
                                            HorizontalDivider(
                                                color = c.divider,
                                                thickness = 0.7.dp,
                                                modifier = Modifier.padding(start = 62.dp),
                                            )
                                            OnePassItemRow(
                                                item = item,
                                                showFavoriteBadge = true,
                                                onClick = { onSelectItem(item) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recently Created / Updated Collapsible Card (matches Image 1-left & Image 5-left!)
                item {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { recentExpanded = !recentExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CategoryIconBadge(
                                    icon = Icons.Rounded.History,
                                    tint = Color.White,
                                    background = Color(0xFF0EA5E9),
                                    size = 30.dp,
                                    circular = true,
                                )
                                Text(
                                    text = "Mới cập nhật",
                                    color = c.textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = if (recentExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            AnimatedVisibility(visible = recentExpanded) {
                                if (recentItems.isEmpty()) {
                                    Column {
                                        HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(onClick = onOpenNewItem)
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = c.accent)
                                            Text(
                                                text = "Chưa có mục nào. Nhấn nút + góc trên để thêm tài khoản đầu tiên.",
                                                color = c.textSecondary,
                                                fontSize = 13.5.sp,
                                            )
                                        }
                                    }
                                } else {
                                    Column {
                                        recentItems.forEach { item ->
                                            HorizontalDivider(
                                                color = c.divider,
                                                thickness = 0.7.dp,
                                                modifier = Modifier.padding(start = 62.dp),
                                            )
                                            OnePassItemRow(
                                                item = item,
                                                showFavoriteBadge = true,
                                                onClick = { onSelectItem(item) },
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
}
