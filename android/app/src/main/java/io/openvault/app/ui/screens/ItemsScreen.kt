package io.openvault.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassAddButton
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.theme.LocalOpenVaultColors

sealed interface ItemsScope {
    data object Root : ItemsScope
    data object AllItems : ItemsScope
    data object Favorites : ItemsScope
    data object Trash : ItemsScope
    data class Category(val type: CipherType) : ItemsScope
    data class Folder(val folder: VaultFolder) : ItemsScope
}

/**
 * 1Password 8 Mobile-inspired "Items" Tab (matching Image 5-middle):
 * - Root directory: Vaults/Folders card (+ New Folder), Categories card, Favorites & Recently Deleted card
 * - Tapping any Vault/Category drills into a crisp grouped list of items
 */
@Composable
fun ItemsScreen(
    email: String,
    items: List<VaultItem>,
    folders: List<VaultFolder>,
    isSyncing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenNewItem: () -> Unit,
    onOpenTotpHub: () -> Unit,
    onSync: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var scope by remember { mutableStateOf<ItemsScope>(ItemsScope.Root) }
    var query by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val activeItems = remember(items) { items.filter { !it.isDeleted } }
    val trashItems = remember(items) { items.filter { it.isDeleted } }

    val listTitle = when (val s = scope) {
        ItemsScope.Root -> "Các mục"
        ItemsScope.AllItems -> "Tất cả các mục"
        ItemsScope.Favorites -> "Yêu thích"
        ItemsScope.Trash -> "Đã xóa gần đây"
        is ItemsScope.Category -> s.type.labelVi
        is ItemsScope.Folder -> s.folder.name
    }

    val filteredList = remember(items, scope, query) {
        val q = query.trim().lowercase()
        val base = when (val s = scope) {
            ItemsScope.Root, ItemsScope.AllItems -> activeItems
            ItemsScope.Favorites -> activeItems.filter { it.favorite }
            ItemsScope.Trash -> trashItems
            is ItemsScope.Category -> activeItems.filter { it.type == s.type }
            is ItemsScope.Folder -> activeItems.filter { it.folderId == s.folder.id }
        }
        if (q.isEmpty()) base
        else base.filter { item ->
            item.name.lowercase().contains(q) ||
                item.login?.username?.lowercase()?.contains(q) == true ||
                item.login?.uris?.any { it.uri.lowercase().contains(q) } == true ||
                item.notes.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        // Header Row (1Password 8 style: Avatar or Back | Centered Title | Sync + '+')
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (scope == ItemsScope.Root) {
                OnePassAccountAvatar(email = email, onClick = onOpenSettings)
            } else {
                IconButton(
                    onClick = {
                        scope = ItemsScope.Root
                        query = ""
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = c.accent,
                    )
                }
            }

            Text(
                text = listTitle,
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onSync, enabled = !isSyncing, modifier = Modifier.size(34.dp)) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Đồng bộ", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                OnePassAddButton(onClick = onOpenNewItem)
            }
        }

        OnePassSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = if (scope == ItemsScope.Root) "Tìm tất cả các mục" else "Tìm trong $listTitle",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (scope == ItemsScope.Root && query.isBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // SECTION 1: Vaults & Folders
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("Kho & Thư mục")
                        OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                            Column {
                                DirectoryRow(
                                    icon = Icons.Rounded.Shield,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFF0572EC),
                                    title = "Tất cả các mục",
                                    count = activeItems.size,
                                    onClick = { scope = ItemsScope.AllItems },
                                )
                                folders.forEach { folder ->
                                    HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                    DirectoryRow(
                                        icon = Icons.Rounded.Folder,
                                        iconTint = Color.White,
                                        iconBg = Color(0xFF475569),
                                        title = folder.name,
                                        count = activeItems.count { it.folderId == folder.id },
                                        onClick = { scope = ItemsScope.Folder(folder) },
                                    )
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showNewFolderDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        text = "Thư mục mới",
                                        color = c.accent,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Categories (exact 1Password 8 colorful square icons)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("Danh mục")
                        OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                            Column {
                                DirectoryRow(
                                    icon = Icons.Rounded.Key,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFF0D9488),
                                    title = "Đăng nhập",
                                    count = activeItems.count { it.type == CipherType.LOGIN },
                                    onClick = { scope = ItemsScope.Category(CipherType.LOGIN) },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                DirectoryRow(
                                    icon = Icons.Rounded.QrCode2,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFF6366F1),
                                    title = "Mã xác thực 2FA",
                                    count = activeItems.count { it.hasTotp },
                                    onClick = onOpenTotpHub,
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                DirectoryRow(
                                    icon = Icons.Rounded.CreditCard,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFF0284C7),
                                    title = "Thẻ tín dụng",
                                    count = activeItems.count { it.type == CipherType.CARD },
                                    onClick = { scope = ItemsScope.Category(CipherType.CARD) },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                DirectoryRow(
                                    icon = Icons.Rounded.Description,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFFD97706),
                                    title = "Ghi chú bảo mật",
                                    count = activeItems.count { it.type == CipherType.SECURE_NOTE },
                                    onClick = { scope = ItemsScope.Category(CipherType.SECURE_NOTE) },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                                DirectoryRow(
                                    icon = Icons.Rounded.Person,
                                    iconTint = Color.White,
                                    iconBg = Color(0xFF059669),
                                    title = "Danh tính",
                                    count = activeItems.count { it.type == CipherType.IDENTITY },
                                    onClick = { scope = ItemsScope.Category(CipherType.IDENTITY) },
                                )
                            }
                        }
                    }
                }

                // SECTION 3: Favorites & Recently Deleted
                item {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            DirectoryRow(
                                icon = Icons.Rounded.Star,
                                iconTint = Color.White,
                                iconBg = Color(0xFFF59E0B),
                                title = "Yêu thích",
                                count = activeItems.count { it.favorite },
                                onClick = { scope = ItemsScope.Favorites },
                            )
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp, modifier = Modifier.padding(start = 58.dp))
                            DirectoryRow(
                                icon = Icons.Rounded.DeleteOutline,
                                iconTint = Color.White,
                                iconBg = Color(0xFF6B7280),
                                title = "Đã xóa gần đây",
                                count = trashItems.size,
                                onClick = { scope = ItemsScope.Trash },
                            )
                        }
                    }
                }
            }
        } else {
            // Filtered Item List View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            ) {
                item {
                    if (filteredList.isEmpty()) {
                        OnePassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Chưa có mục nào trong \"$listTitle\".",
                                color = c.textSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                            Column {
                                filteredList.forEachIndexed { idx, item ->
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
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = c.surface,
            title = { Text("Tạo thư mục mới", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Tên thư mục (mã hóa E2EE)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
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
private fun SectionHeading(text: String) {
    val c = LocalOpenVaultColors.current
    Text(
        text = text,
        color = c.textPrimary,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun DirectoryRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconBadge(icon = icon, tint = iconTint, background = iconBg, size = 32.dp)
        Text(
            text = title,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                color = c.textSecondary,
                fontSize = 14.sp,
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
