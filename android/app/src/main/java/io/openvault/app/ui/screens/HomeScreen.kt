package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.security.HomeSectionPref
import io.openvault.app.core.security.HomeSectionType
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchModeView
import io.openvault.app.ui.components.OnePassTopSearchBar
import io.openvault.app.ui.components.TotpInlineCountdown
import io.openvault.app.ui.components.TrackFabScrollEffect
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import io.openvault.app.ui.theme.OpenVaultMonoFontFamily
import kotlinx.coroutines.delay

/**
 * Authentic 1Password 8 Android Home Screen:
 * - Single-row Search Pill + Pencil Customize button + Avatar dropdown
 * - User-customizable ordered sections: Pinned 2FA, Profile, Favorites, Recently Created, Frequently Used
 * - By default, only the FIRST visible non-empty collapsible section (excluding Pinned 2FA & Profile) expands; all others start collapsed.
 * - Blue-outlined "✎ Customize" button at bottom
 */
@Composable
fun HomeScreen(
    email: String,
    items: List<VaultItem>,
    homeSections: List<HomeSectionPref>,
    usageCounts: Map<String, Int>,
    isSyncing: Boolean,
    selectedItemId: String? = null,
    onFabExpandedChange: (Boolean) -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenCustomizeHome: () -> Unit,
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
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sectionExpansionOverrides by remember { mutableStateOf<Map<HomeSectionType, Boolean>>(emptyMap()) }
    val listState = rememberLazyListState()

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
    val frequentItems = remember(activeItems, usageCounts) {
        activeItems
            .filter { (usageCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { usageCounts[it.id] ?: 0 }
            .take(8)
    }

    // Determine the first visible non-empty collapsible section (excluding Pinned 2FA and Profile)
    val firstExpandedSection = remember(homeSections, favoriteItems.size, frequentItems.size) {
        homeSections
            .filter { it.visible }
            .mapNotNull { it.toTypeOrNull() }
            .firstOrNull { type ->
                when (type) {
                    HomeSectionType.FAVORITES -> favoriteItems.isNotEmpty()
                    HomeSectionType.RECENT -> true
                    HomeSectionType.FREQUENT -> frequentItems.isNotEmpty()
                    HomeSectionType.PINNED_TOTP, HomeSectionType.PROFILE -> false
                }
            }
    }

    val favoritesExpanded = sectionExpansionOverrides[HomeSectionType.FAVORITES]
        ?: (firstExpandedSection == HomeSectionType.FAVORITES)
    val recentExpanded = sectionExpansionOverrides[HomeSectionType.RECENT]
        ?: (firstExpandedSection == HomeSectionType.RECENT)
    val frequentExpanded = sectionExpansionOverrides[HomeSectionType.FREQUENT]
        ?: (firstExpandedSection == HomeSectionType.FREQUENT)

    TrackFabScrollEffect(listState = listState, onExpandedChange = onFabExpandedChange)

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

    if (searchActive) {
        OnePassSearchModeView(
            email = email,
            query = query,
            onQueryChange = { query = it },
            placeholder = s.searchInVault,
            results = searchResults,
            selectedItemId = selectedItemId,
            onSelectItem = {
                searchActive = false
                query = ""
                onSelectItem(it)
            },
            onCloseSearch = {
                searchActive = false
                query = ""
            },
            onOpenSettings = onOpenSettings,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onSync = onSync,
            onLock = onLock,
            isSyncing = isSyncing,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        OnePassTopSearchBar(
            email = email,
            query = "",
            onQueryChange = { },
            placeholder = if (fold.isNarrowCover) s.searchShort else s.searchInVault,
            isSyncing = isSyncing,
            onSearchActivated = { searchActive = true },
            onSync = onSync,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
            trailingAction = {
                IconButton(
                    onClick = onOpenCustomizeHome,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = s.customize,
                        tint = c.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
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
                                    text = "${s.noMatchingItems} \"$query\"",
                                    color = c.textSecondary,
                                    fontSize = 15.5.sp,
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
                    homeSections.filter { it.visible }.forEach { pref ->
                        when (pref.toTypeOrNull()) {
                            HomeSectionType.PINNED_TOTP -> {
                                if (pinnedTotpItems.isNotEmpty()) {
                                    item(key = "section_totp") {
                                        val cardWidth = if (fold.isNarrowCover) 190.dp else 212.dp
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
                                                                ItemIconTile(
                                                                    title = item.name,
                                                                    type = item.type,
                                                                    size = 26.dp,
                                                                    secureNoteType = item.secureNoteType,
                                                                    customIconBase64 = item.customIconBase64,
                                                                    websiteUrl = item.primaryWebsiteUrl,
                                                                )
                                                                Text(
                                                                    text = item.name,
                                                                    color = c.textPrimary,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text(
                                                                    text = s.oneTimePassword,
                                                                    color = c.fieldLabel,
                                                                    fontSize = 12.5.sp,
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
                                                                        fontFamily = OpenVaultMonoFontFamily,
                                                                        fontSize = 18.sp,
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
                            }

                            HomeSectionType.PROFILE -> {
                                item(key = "section_profile") {
                                    OnePassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        padding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                                        onClick = onOpenProfile,
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
                                                size = 38.dp,
                                                circular = true,
                                            )
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = s.profile,
                                                    color = c.textPrimary,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text = s.profileSubtitle,
                                                    color = c.textSecondary,
                                                    fontSize = 14.5.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HomeSectionType.FAVORITES -> {
                                if (favoriteItems.isNotEmpty()) {
                                    item(key = "section_favorites") {
                                        OnePassCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            padding = PaddingValues(vertical = 6.dp),
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            sectionExpansionOverrides = sectionExpansionOverrides + (
                                                                HomeSectionType.FAVORITES to !favoritesExpanded
                                                            )
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                ) {
                                                    CategoryIconBadge(
                                                        icon = Icons.Rounded.Star,
                                                        tint = Color(0xFF1F1604),
                                                        background = Color(0xFFFBBF24),
                                                        size = 38.dp,
                                                        circular = true,
                                                    )
                                                    Text(
                                                        text = s.favorites,
                                                        color = c.textPrimary,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Icon(
                                                        imageVector = if (favoritesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                        contentDescription = null,
                                                        tint = c.accent,
                                                        modifier = Modifier.size(24.dp),
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
                            }

                            HomeSectionType.RECENT -> {
                                item(key = "section_recent") {
                                    OnePassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        padding = PaddingValues(vertical = 6.dp),
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        sectionExpansionOverrides = sectionExpansionOverrides + (
                                                            HomeSectionType.RECENT to !recentExpanded
                                                        )
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            ) {
                                                CategoryIconBadge(
                                                    icon = Icons.Rounded.History,
                                                    tint = Color(0xFF07233B),
                                                    background = Color(0xFF60B8FF),
                                                    size = 38.dp,
                                                    circular = true,
                                                )
                                                Text(
                                                    text = s.recentlyCreated,
                                                    color = c.textPrimary,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Icon(
                                                    imageVector = if (recentExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                    contentDescription = null,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(24.dp),
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
                                                            text = s.emptyHomeHint,
                                                            color = c.textSecondary,
                                                            fontSize = 14.5.sp,
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
                            }

                            HomeSectionType.FREQUENT -> {
                                if (frequentItems.isNotEmpty()) {
                                    item(key = "section_frequent") {
                                        OnePassCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            padding = PaddingValues(vertical = 6.dp),
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            sectionExpansionOverrides = sectionExpansionOverrides + (
                                                                HomeSectionType.FREQUENT to !frequentExpanded
                                                            )
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                ) {
                                                    CategoryIconBadge(
                                                        icon = Icons.Rounded.KeyboardDoubleArrowRight,
                                                        tint = Color(0xFF052E16),
                                                        background = Color(0xFF4ADE80),
                                                        size = 38.dp,
                                                        circular = true,
                                                    )
                                                    Text(
                                                        text = s.frequentlyUsed,
                                                        color = c.textPrimary,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Icon(
                                                        imageVector = if (frequentExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                        contentDescription = null,
                                                        tint = c.accent,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }

                                                AnimatedVisibility(visible = frequentExpanded) {
                                                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                                        frequentItems.forEach { item ->
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

                            null -> Unit
                        }
                    }

                    // Authentic Blue-Outlined "✎ Customize" Button
                    item(key = "btn_customize") {
                        Surface(
                            onClick = onOpenCustomizeHome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
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
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = c.navActiveContent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "  ${s.customize}",
                                    color = c.navActiveContent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
