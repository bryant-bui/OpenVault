package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassTopSearchBar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings

sealed interface ItemsScope {
    data object Root : ItemsScope
    data object AllItems : ItemsScope
    data object Favorites : ItemsScope
    data object Trash : ItemsScope
    data class Category(val type: CipherType) : ItemsScope
    data class Folder(val folder: VaultFolder) : ItemsScope
}

@Composable
fun ItemsScreen(
    email: String,
    items: List<VaultItem>,
    folders: List<VaultFolder>,
    isSyncing: Boolean,
    selectedItemId: String? = null,
    onOpenSettings: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenTotpHub: () -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onSelectItem: (VaultItem) -> Unit,
    onCreateFolder: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var scope by remember { mutableStateOf<ItemsScope>(ItemsScope.Root) }
    var query by remember { mutableStateOf("") }
    var categoriesExpanded by remember { mutableStateOf(true) }
    var vaultsExpanded by remember { mutableStateOf(true) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val activeItems = remember(items) { items.filter { !it.isDeleted } }
    val trashItems = remember(items) { items.filter { it.isDeleted } }

    val listTitle = when (val sc = scope) {
        ItemsScope.Root -> s.tabItems
        ItemsScope.AllItems -> s.allItems
        ItemsScope.Favorites -> s.favorites
        ItemsScope.Trash -> s.recentlyDeleted
        is ItemsScope.Category -> sc.type.label(s.isVi)
        is ItemsScope.Folder -> sc.folder.name
    }

    val filteredList = remember(items, scope, query) {
        val q = query.trim().lowercase()
        val base = when (val sc = scope) {
            ItemsScope.Root, ItemsScope.AllItems -> activeItems
            ItemsScope.Favorites -> activeItems.filter { it.favorite }
            ItemsScope.Trash -> trashItems
            is ItemsScope.Category -> activeItems.filter { it.type == sc.type }
            is ItemsScope.Folder -> activeItems.filter { it.folderId == sc.folder.id }
        }
        if (q.isEmpty()) base
        else base.filter { item ->
            item.name.lowercase().contains(q) ||
                item.login?.username?.lowercase()?.contains(q) == true ||
                item.login?.uris?.any { it.uri.lowercase().contains(q) } == true ||
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
            placeholder = if (scope == ItemsScope.Root) {
                if (fold.isNarrowCover) s.searchShort else s.searchAllItems
            } else {
                "${s.searchInPrefix}$listTitle"
            },
            isSyncing = isSyncing,
            onSync = onSync,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
            leadingAction = if (scope != ItemsScope.Root) {
                {
                    IconButton(
                        onClick = {
                            scope = ItemsScope.Root
                            query = ""
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = s.back,
                            tint = c.textPrimary,
                        )
                    }
                }
            } else null,
        )

        if (scope == ItemsScope.Root && query.isBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = fold.horizontalPadding,
                    end = fold.horizontalPadding,
                    top = 4.dp,
                    bottom = bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(vertical = 4.dp),
                    ) {
                        DirectoryRow(
                            icon = Icons.Rounded.Inventory2,
                            iconTint = Color(0xFF1A1C20),
                            iconBg = Color(0xFFA7B1C2),
                            title = s.allItems,
                            count = activeItems.size,
                            onClick = { scope = ItemsScope.AllItems },
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollapsibleSectionHeader(
                            title = s.categories,
                            expanded = categoriesExpanded,
                            onToggle = { categoriesExpanded = !categoriesExpanded },
                        )
                        AnimatedVisibility(visible = categoriesExpanded) {
                            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                                Column {
                                    DirectoryRow(
                                        icon = Icons.Rounded.Key,
                                        iconTint = Color(0xFF042F2E),
                                        iconBg = Color(0xFF5EEAD4),
                                        title = s.logins,
                                        count = activeItems.count { it.type == CipherType.LOGIN },
                                        onClick = { scope = ItemsScope.Category(CipherType.LOGIN) },
                                    )
                                    DirectoryRow(
                                        icon = Icons.Rounded.QrCode2,
                                        iconTint = Color.White,
                                        iconBg = Color(0xFF6366F1),
                                        title = s.twoFactorCodes,
                                        count = activeItems.count { it.hasTotp },
                                        onClick = onOpenTotpHub,
                                    )
                                    DirectoryRow(
                                        icon = Icons.Rounded.CreditCard,
                                        iconTint = Color.White,
                                        iconBg = Color(0xFF0284C7),
                                        title = s.creditCards,
                                        count = activeItems.count { it.type == CipherType.CARD },
                                        onClick = { scope = ItemsScope.Category(CipherType.CARD) },
                                    )
                                    DirectoryRow(
                                        icon = Icons.Rounded.Description,
                                        iconTint = Color(0xFF2E1E04),
                                        iconBg = Color(0xFFFBBF24),
                                        title = s.secureNotes,
                                        count = activeItems.count { it.type == CipherType.SECURE_NOTE },
                                        onClick = { scope = ItemsScope.Category(CipherType.SECURE_NOTE) },
                                    )
                                    DirectoryRow(
                                        icon = Icons.Rounded.Person,
                                        iconTint = Color(0xFF042F1A),
                                        iconBg = Color(0xFF4ADE80),
                                        title = s.identities,
                                        count = activeItems.count { it.type == CipherType.IDENTITY },
                                        onClick = { scope = ItemsScope.Category(CipherType.IDENTITY) },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollapsibleSectionHeader(
                            title = s.vaultsAndFolders,
                            expanded = vaultsExpanded,
                            onToggle = { vaultsExpanded = !vaultsExpanded },
                        )
                        AnimatedVisibility(visible = vaultsExpanded) {
                            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                                Column {
                                    DirectoryRow(
                                        icon = Icons.Rounded.Shield,
                                        iconTint = Color(0xFF0D1E36),
                                        iconBg = Color(0xFF93C5FD),
                                        title = s.personalVault,
                                        count = activeItems.size,
                                        circular = true,
                                        onClick = { scope = ItemsScope.AllItems },
                                    )
                                    folders.forEach { folder ->
                                        DirectoryRow(
                                            icon = Icons.Rounded.Folder,
                                            iconTint = Color.White,
                                            iconBg = Color(0xFF475569),
                                            title = folder.name,
                                            count = activeItems.count { it.folderId == folder.id },
                                            onClick = { scope = ItemsScope.Folder(folder) },
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showNewFolderDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(22.dp))
                                        }
                                        Text(
                                            text = s.newFolder,
                                            color = c.navActiveContent,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                        Column {
                            DirectoryRow(
                                icon = Icons.Rounded.Star,
                                iconTint = Color(0xFF1F1604),
                                iconBg = Color(0xFFFBBF24),
                                title = s.favorites,
                                count = activeItems.count { it.favorite },
                                circular = true,
                                onClick = { scope = ItemsScope.Favorites },
                            )
                            DirectoryRow(
                                icon = Icons.Rounded.DeleteOutline,
                                iconTint = Color(0xFF1A1C20),
                                iconBg = Color(0xFF9CA3AF),
                                title = s.recentlyDeleted,
                                count = trashItems.size,
                                onClick = { scope = ItemsScope.Trash },
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = fold.horizontalPadding,
                    end = fold.horizontalPadding,
                    top = 4.dp,
                    bottom = bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (scope != ItemsScope.Root) {
                    item {
                        Text(
                            text = "$listTitle (${filteredList.size})",
                            color = c.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                item {
                    if (filteredList.isEmpty()) {
                        OnePassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${s.emptyFolderPrefix} \"$listTitle\".",
                                color = c.textSecondary,
                                fontSize = 14.5.sp,
                            )
                        }
                    } else {
                        OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
                            Column {
                                filteredList.forEach { item ->
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
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = c.surfaceElevated,
            title = { Text(s.createFolderTitle, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(s.folderNameLabel) },
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
                    Text(s.create, color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(s.cancel, color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = c.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DirectoryRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    count: Int,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryIconBadge(
            icon = icon,
            tint = iconTint,
            background = iconBg,
            size = 34.dp,
            circular = circular,
        )
        Text(
            text = title,
            color = c.textPrimary,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                color = c.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
