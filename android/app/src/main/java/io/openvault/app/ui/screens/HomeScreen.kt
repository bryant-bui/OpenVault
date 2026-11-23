package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassTopSearchBar
import io.openvault.app.ui.components.TotpInlineCountdown
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

/**
 * Authentic 1Password 8 Android Home Screen:
 * - Single-row Midnight-Navy Search Pill + Account Avatar Dropdown (zero clutter on 320dp Z Fold 3 cover screen)
 * - Horizontal Pinned 2FA OTP cards (tap to copy immediately)
 * - Profile card, collapsible Favorites & Recently Created cards on pure OLED black (#000000)
 * - Subtle blue-outlined quick action button ("Tạo mật khẩu mạnh") matching 1Password Android's Customize button
 */
@Composable
fun HomeScreen(
    email: String,
    items: List<VaultItem>,
    isSyncing: Boolean,
    selectedItemId: String? = null,
    onOpenSettings: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenNewItem: () -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onCopyTotp: (title: String, code: String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
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
        activeItems.sortedByDescending { it.revisionDate ?: it.creationDate ?: "" }.take(14)
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

    val bottomInset = if (fold.isTwoPane) 28.dp else 156.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        OnePassTopSearchBar(
            email = email,
            query = query,
            onQueryChange = { query = it },
            placeholder = if (fold.isNarrowCover) "Tìm trong kho..." else "Tìm trong OpenVault",
            isSyncing = isSyncing,
            onSync = onSync,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = fold.horizontalPadding,
                end = fold.horizontalPadding,
                top = 4.dp,
                bottom = bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (query.isNotBlank()) {
                item {
                    if (searchResults.isEmpty()) {
                        OnePassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Không tìm thấy mục nào khớp \"$query\"",
                                color = c.textSecondary,
                                fontSize = 14.5.sp,
                            )
                        }
                    } else {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(vertical = 6.dp),
                        ) {
                            Column {
                                searchResults.forEach { item ->
                                    OnePassItemRow(
                                        item = item,
                                        showFavoriteBadge = true,
                                        selected = fold.isTwoPane && item.id == selectedItemId,
                                        onClick = { onSelectItem(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Pinned 2FA Horizontal Strip
                if (pinnedTotpItems.isNotEmpty()) {
                    item {
                        val cardWidth = if (fold.isNarrowCover) 182.dp else 204.dp
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
                                        modifier = Modifier.width(cardWidth),
                                        cornerRadius = 16.dp,
                                        padding = PaddingValues(14.dp),
                                        onClick = { onCopyTotp(item.name, totp.code) },
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                ItemIconTile(title = item.name, type = item.type, size = 24.dp)
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
                                                    text = "mã xác thực 2FA",
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

                // Profile Card (exact 1Password Android Home "Profile" card!)
                item {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                        onClick = onOpenSettings,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CategoryIconBadge(
                                icon = Icons.Rounded.Person,
                                tint = Color(0xFF0C74F2),
                                background = Color.White,
                                size = 34.dp,
                                circular = true,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Hồ sơ & Cài đặt",
                                    color = c.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = email.ifBlank { "Thiết lập vân tay, mã PIN, Autofill & Passkey" },
                                    color = c.textSecondary,
                                    fontSize = 13.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Favorites Collapsible Card
                if (favoriteItems.isNotEmpty()) {
                    item {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            padding = PaddingValues(vertical = 6.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { favoritesExpanded = !favoritesExpanded }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CategoryIconBadge(
                                        icon = Icons.Rounded.Star,
                                        tint = Color(0xFF1F1604),
                                        background = Color(0xFFFBBF24),
                                        size = 34.dp,
                                        circular = true,
                                    )
                                    Text(
                                        text = "Yêu thích",
                                        color = c.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
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
                                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                        favoriteItems.forEach { item ->
                                            OnePassItemRow(
                                                item = item,
                                                showFavoriteBadge = true,
                                                selected = fold.isTwoPane && item.id == selectedItemId,
                                                onClick = { onSelectItem(item) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Recently Created / Updated Collapsible Card (exact 1Password Android card!)
                item {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(vertical = 6.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { recentExpanded = !recentExpanded }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CategoryIconBadge(
                                    icon = Icons.Rounded.History,
                                    tint = Color(0xFF07233B),
                                    background = Color(0xFF60B8FF),
                                    size = 34.dp,
                                    circular = true,
                                )
                                Text(
                                    text = "Mới tạo & cập nhật",
                                    color = c.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onOpenNewItem)
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = c.accent)
                                        Text(
                                            text = "Chưa có mục nào. Nhấn \"+ Mục mới\" để thêm tài khoản đầu tiên.",
                                            color = c.textSecondary,
                                            fontSize = 13.5.sp,
                                        )
                                    }
                                } else {
                                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                        recentItems.forEach { item ->
                                            OnePassItemRow(
                                                item = item,
                                                showFavoriteBadge = true,
                                                selected = fold.isTwoPane && item.id == selectedItemId,
                                                onClick = { onSelectItem(item) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtle Blue-Outlined Quick Action Button (exact 1Password Android "Customize" button style!)
                item {
                    Surface(
                        onClick = onOpenGenerator,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (c.isDark) Color(0xFF10131A) else c.surface,
                        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.72f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = c.navActiveContent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "  Tạo mật khẩu mạnh",
                                color = c.navActiveContent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
