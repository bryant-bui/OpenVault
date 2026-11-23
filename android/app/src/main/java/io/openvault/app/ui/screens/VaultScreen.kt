package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.WatchtowerReport
import io.openvault.app.ui.components.BrandMonogramAvatar
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.LiquidGlassChip
import io.openvault.app.ui.theme.LocalOpenVaultColors

enum class VaultFilter(val labelVi: String) {
    ALL("Tất cả"),
    FAVORITES("Yêu thích"),
    LOGINS("Đăng nhập"),
    TOTP("2FA"),
    CARDS("Thẻ"),
    NOTES("Ghi chú"),
    IDENTITIES("Danh tính"),
    TRASH("Thùng rác"),
}

@Composable
fun VaultScreen(
    items: List<VaultItem>,
    folders: List<VaultFolder>,
    watchtower: WatchtowerReport,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onOpenWatchtower: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onQuickCopy: (VaultItem) -> Unit,
    onAddNew: () -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(VaultFilter.ALL) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val filteredItems = remember(items, query, activeFilter, selectedFolderId) {
        val q = query.trim().lowercase()
        items.filter { item ->
            val matchesTrash = if (activeFilter == VaultFilter.TRASH) item.isDeleted else !item.isDeleted
            if (!matchesTrash) return@filter false

            val matchesCategory = when (activeFilter) {
                VaultFilter.ALL, VaultFilter.TRASH -> true
                VaultFilter.FAVORITES -> item.favorite
                VaultFilter.LOGINS -> item.type == CipherType.LOGIN
                VaultFilter.TOTP -> item.hasTotp
                VaultFilter.CARDS -> item.type == CipherType.CARD
                VaultFilter.NOTES -> item.type == CipherType.SECURE_NOTE
                VaultFilter.IDENTITIES -> item.type == CipherType.IDENTITY
            }
            if (!matchesCategory) return@filter false

            if (selectedFolderId != null && item.folderId != selectedFolderId) return@filter false

            if (q.isEmpty()) return@filter true
            item.name.lowercase().contains(q) ||
                item.login?.username?.lowercase()?.contains(q) == true ||
                item.login?.uris?.any { it.uri.lowercase().contains(q) } == true ||
                item.card?.cardholderName?.lowercase()?.contains(q) == true ||
                item.identity?.fullName?.lowercase()?.contains(q) == true ||
                item.notes.lowercase().contains(q)
        }
    }

    val activeItemsCount = remember(items) { items.count { !it.isDeleted } }
    val watchtowerColor = when {
        watchtower.score >= 85 -> c.success
        watchtower.score >= 60 -> c.warning
        else -> c.danger
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top Liquid Glass Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "OpenVault",
                        color = c.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "$activeItemsCount mục được bảo vệ",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Watchtower Score Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(watchtowerColor.copy(alpha = 0.16f))
                            .border(1.dp, watchtowerColor.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onOpenWatchtower)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = "Watchtower",
                            tint = watchtowerColor,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "${watchtower.score}",
                            color = watchtowerColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Sync Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.glassSurface)
                            .border(1.dp, c.glassBorderTop.copy(alpha = 0.25f), CircleShape)
                            .clickable(enabled = !isSyncing, onClick = onSync),
                        contentAlignment = Alignment.Center,
                    ) {
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
                                tint = c.textPrimary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    // Quick Lock Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.glassSurface)
                            .border(1.dp, c.glassBorderTop.copy(alpha = 0.25f), CircleShape)
                            .clickable(onClick = onLock),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Khóa kho",
                            tint = c.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Frosted Glass Search Pill
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cornerRadius = 18.dp,
                padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Tìm tài khoản, website, thẻ, ghi chú…",
                                color = c.textMuted,
                                fontSize = 14.5.sp,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = c.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Xóa tìm kiếm",
                            tint = c.textSecondary,
                            modifier = Modifier
                                .size(19.dp)
                                .clickable { query = "" },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Category Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VaultFilter.entries.forEach { filter ->
                    val icon = when (filter) {
                        VaultFilter.ALL -> Icons.Rounded.Shield
                        VaultFilter.FAVORITES -> Icons.Rounded.Star
                        VaultFilter.LOGINS -> Icons.Rounded.Key
                        VaultFilter.TOTP -> Icons.Rounded.QrCode2
                        VaultFilter.CARDS -> Icons.Rounded.CreditCard
                        VaultFilter.NOTES -> Icons.Rounded.Description
                        VaultFilter.IDENTITIES -> Icons.Rounded.Person
                        VaultFilter.TRASH -> Icons.Rounded.DeleteOutline
                    }
                    LiquidGlassChip(
                        label = filter.labelVi,
                        selected = activeFilter == filter && selectedFolderId == null,
                        leadingIcon = icon,
                        onClick = {
                            activeFilter = filter
                            selectedFolderId = null
                        },
                    )
                }
            }

            // Optional Folders Strip
            if (folders.isNotEmpty() || activeFilter == VaultFilter.ALL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    folders.forEach { folder ->
                        val count = items.count { !it.isDeleted && it.folderId == folder.id }
                        LiquidGlassChip(
                            label = folder.name,
                            selected = selectedFolderId == folder.id,
                            leadingIcon = Icons.Rounded.Folder,
                            badgeCount = count,
                            onClick = {
                                selectedFolderId = if (selectedFolderId == folder.id) null else folder.id
                                if (activeFilter == VaultFilter.TRASH) activeFilter = VaultFilter.ALL
                            },
                        )
                    }
                    LiquidGlassChip(
                        label = "Thư mục mới",
                        selected = false,
                        leadingIcon = Icons.Rounded.CreateNewFolder,
                        onClick = { showNewFolderDialog = true },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vault Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        padding = PaddingValues(26.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (activeFilter == VaultFilter.TRASH) Icons.Rounded.DeleteOutline else Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(42.dp),
                            )
                            Text(
                                text = if (query.isNotBlank()) "Không tìm thấy kết quả" else "Chưa có mục nào",
                                color = c.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (query.isNotBlank()) {
                                    "Thử tìm bằng tên miền, email hoặc chuyển danh mục."
                                } else {
                                    "Nhấn nút + bên dưới để thêm tài khoản, thẻ hoặc ghi chú bảo mật."
                                },
                                color = c.textSecondary,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        VaultItemRowCard(
                            item = item,
                            onClick = { onSelectItem(item) },
                            onQuickCopy = { onQuickCopy(item) },
                        )
                    }
                }
            }
        }

        // Floating Liquid Glass '+' FAB above the floating dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 98.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0A5CE6), c.accent, Color(0xFF6366F1)),
                    ),
                )
                .border(1.2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .clickable(onClick = onAddNew),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "Thêm mục mới",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = c.glassSurfaceElevated,
            title = { Text("Tạo thư mục mới", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Tên thư mục (mã hóa E2EE)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateFolder(trimmed)
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    },
                ) {
                    Text("Tạo", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Hủy", color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun VaultItemRowCard(
    item: VaultItem,
    onClick: () -> Unit,
    onQuickCopy: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMonogramAvatar(title = item.name, type = item.type, size = 44.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        color = c.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.favorite) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Yêu thích",
                            tint = c.warning,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (item.hasTotp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.accentSoft)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "2FA",
                                color = c.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (item.hasPasskeys) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2E8B5CF6))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "Passkey",
                                color = Color(0xFFA78BFA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = item.primarySubtitle,
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onQuickCopy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Sao chép nhanh",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
