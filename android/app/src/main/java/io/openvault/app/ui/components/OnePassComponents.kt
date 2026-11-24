package io.openvault.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.SecureNoteCategory
import io.openvault.app.core.crypto.TotpCode
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.media.IconLoader
import io.openvault.app.core.media.rememberWebsiteFavicon
import io.openvault.app.ui.theme.AppStrings
import io.openvault.app.ui.theme.LocalAccountAvatar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalShowWebsiteIcons
import io.openvault.app.ui.theme.LocalStrings
import kotlin.math.abs

enum class MainTab(val icon: ImageVector) {
    HOME(Icons.Rounded.Home),
    ITEMS(Icons.Rounded.ViewAgenda),
    WATCHTOWER(Icons.Rounded.Security);

    fun label(s: AppStrings): String = when (this) {
        HOME -> s.tabHome
        ITEMS -> s.tabItems
        WATCHTOWER -> s.tabWatchtower
    }

    fun railLabel(s: AppStrings): String = when (this) {
        HOME -> s.tabHome
        ITEMS -> s.tabItems
        WATCHTOWER -> s.tabSecurityShort
    }
}

/**
 * Crisp matte 1Password 8 Android card container.
 */
@Composable
fun OnePassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val bg = backgroundColor ?: c.surface
    val resolvedBorder = borderColor ?: if (c.isDark) Color.Transparent else c.cardBorder

    Surface(
        modifier = modifier
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = bg,
        border = if (resolvedBorder != Color.Transparent) BorderStroke(0.9.dp, resolvedBorder) else null,
    ) {
        Box(modifier = Modifier.padding(padding), content = content)
    }
}

/**
 * Authentic 1Password 8 Android Midnight-Navy Pill Search Bar.
 */
@Composable
fun OnePassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val hint = placeholder ?: s.searchInVault
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(c.searchBackground)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = c.textPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = hint,
                    color = c.textSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = c.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = s.close,
                tint = c.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Authentic 1Password 8 Android Single-Row Top Bar:
 * [ (🔍 Search...) ]  [Optional Pencil/Action]  [⌄ Avatar -> Anchored Dropdown Menu]
 */
@Composable
fun OnePassTopSearchBar(
    email: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    isSyncing: Boolean = false,
    onSearchActivated: (() -> Unit)? = null,
    onSync: (() -> Unit)? = null,
    onOpenGenerator: (() -> Unit)? = null,
    onOpenQrScanner: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    leadingAction: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var menuExpanded by remember { mutableStateOf(false) }

    val displayName = remember(email) {
        val local = email.substringBefore("@").trim()
        if (local.isNotBlank()) local.replaceFirstChar { it.uppercaseChar() } else "OpenVault"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = fold.horizontalPadding,
                vertical = if (fold.isNarrowCover) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (fold.isNarrowCover) 4.dp else 8.dp),
    ) {
        if (leadingAction != null) {
            leadingAction()
        }

        if (onSearchActivated != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(c.searchBackground)
                    .clickable(onClick = onSearchActivated)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = c.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = placeholder,
                    color = c.textSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            OnePassSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
        }

        if (trailingAction != null) {
            trailingAction()
        }

        // Anchored Account Avatar + Dropdown
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = s.settingsAccounts,
                    tint = c.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                OnePassAccountAvatar(
                    email = email,
                    onClick = { menuExpanded = true },
                    size = if (fold.isNarrowCover) 36.dp else 38.dp,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = c.surfaceElevated,
                border = BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.widthIn(min = 245.dp, max = 290.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OnePassAccountAvatar(email = email, onClick = { }, size = 36.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                if (onSync != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isSyncing) s.syncing else s.syncNow,
                                color = c.textPrimary,
                                fontSize = 15.5.sp,
                            )
                        },
                        trailingIcon = {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = c.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(21.dp))
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            if (!isSyncing) onSync()
                        },
                    )
                }

                if (onOpenGenerator != null) {
                    DropdownMenuItem(
                        text = { Text(s.passwordGenerator, color = c.textPrimary, fontSize = 15.5.sp) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(21.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenGenerator()
                        },
                    )
                }

                if (onOpenQrScanner != null) {
                    DropdownMenuItem(
                        text = { Text(s.scanQr2fa, color = c.textPrimary, fontSize = 15.5.sp) },
                        trailingIcon = {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(21.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenQrScanner()
                        },
                    )
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                DropdownMenuItem(
                    text = { Text(s.settings, color = c.textPrimary, fontSize = 15.5.sp) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(21.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                )

                DropdownMenuItem(
                    text = { Text(s.lockVault, color = c.textPrimary, fontSize = 15.5.sp) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(21.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onLock()
                    },
                )
            }
        }
    }
}

/**
 * Tracks LazyListState scroll direction to collapse Extended FAB to '+' on scroll down
 * and expand it cleanly back to '+ New Item' when at top or scrolling up.
 */
@Composable
fun TrackFabScrollEffect(
    listState: LazyListState,
    onExpandedChange: (Boolean) -> Unit,
) {
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                if (idx == 0 && off <= 24) {
                    onExpandedChange(true)
                } else {
                    val delta = (idx - prevIndex) * 10_000 + (off - prevOffset)
                    if (delta > 16) {
                        onExpandedChange(false)
                    } else if (delta < -16) {
                        onExpandedChange(true)
                    }
                }
                prevIndex = idx
                prevOffset = off
            }
    }
}

/**
 * Signature 1Password 8 Android Cobalt-Blue Extended FAB ("+ New Item") that smoothly
 * collapses to a compact "+" square button while scrolling down.
 */
@Composable
fun OnePassExtendedFab(
    text: String? = null,
    expanded: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val horizontalPad by animateDpAsState(
        targetValue = if (expanded) 20.dp else 15.dp,
        animationSpec = tween(220),
        label = "fab_pad",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .defaultMinSize(minWidth = 54.dp),
        shape = RoundedCornerShape(16.dp),
        color = c.accentButtonBg,
        contentColor = c.accentButtonFg,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = text ?: s.newItem,
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(tween(220)) + fadeIn(tween(180)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = text ?: s.newItem,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Authentic 1Password 8 Android 3-tab Bottom Navigation Bar (for Cover / Compact screens).
 */
@Composable
fun OnePassBottomBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.navBarBg)
            .navigationBarsPadding(),
    ) {
        if (!c.isDark) {
            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (fold.isNarrowCover) 4.dp else 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selectedTab
                val label = tab.label(s)
                val tint by animateColorAsState(
                    targetValue = if (active) c.navActiveContent else c.navInactiveContent,
                    animationSpec = tween(150),
                    label = "nav_tint",
                )
                val pillBg by animateColorAsState(
                    targetValue = if (active) c.navActivePill else Color.Transparent,
                    animationSpec = tween(150),
                    label = "nav_pill",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (fold.isNarrowCover) 54.dp else 64.dp)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(pillBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Text(
                        text = label,
                        color = tint,
                        fontSize = if (fold.isNarrowCover) 12.sp else 12.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Dedicated 1Password 8 Android Search Mode View (Image 17):
 * - Top bar with ← Back + live input + blue underline + clear button
 * - Centered dark magnifying-glass "Search" empty state when query is blank
 * - Instant live results card as the user types
 */
@Composable
fun OnePassSearchModeView(
    email: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    results: List<VaultItem>,
    selectedItemId: String? = null,
    onSelectItem: (VaultItem) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    isSyncing: Boolean = false,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onCloseSearch)

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        // Elevated Search Header with signature blue bottom underline (Image 17)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (c.isDark) Color(0xFF141517) else c.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = s.back,
                        tint = c.textPrimary,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = c.textSecondary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }
                } else {
                    OnePassAccountAvatar(
                        email = email,
                        size = 34.dp,
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(c.accentButtonBg.copy(alpha = 0.85f)),
            )
        }

        if (query.isBlank()) {
            // Centered Magnifying-Glass Empty Search State (exact Image 17!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Canvas(modifier = Modifier.size(86.dp)) {
                        val ringRadius = size.minDimension * 0.30f
                        val ringCenter = Offset(size.width * 0.42f, size.height * 0.40f)
                        // Glass lens fill
                        drawCircle(
                            color = if (c.isDark) Color(0xFF23252A) else Color(0xFFE5E7EB),
                            radius = ringRadius,
                            center = ringCenter,
                        )
                        // Outer lens ring
                        drawCircle(
                            color = if (c.isDark) Color(0xFF4B505A) else Color(0xFF9CA3AF),
                            radius = ringRadius,
                            center = ringCenter,
                            style = Stroke(width = 6.dp.toPx()),
                        )
                        // Subtle inner specular arc
                        drawArc(
                            color = if (c.isDark) Color(0xFF343840) else Color(0xFFF3F4F6),
                            startAngle = 205f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(ringCenter.x - ringRadius * 0.65f, ringCenter.y - ringRadius * 0.65f),
                            size = Size(ringRadius * 1.3f, ringRadius * 1.3f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                        // Handle
                        drawLine(
                            color = if (c.isDark) Color(0xFF4B505A) else Color(0xFF9CA3AF),
                            start = Offset(ringCenter.x + ringRadius * 0.72f, ringCenter.y + ringRadius * 0.72f),
                            end = Offset(size.width * 0.84f, size.height * 0.84f),
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    Text(
                        text = if (s.isVi) "Tìm Kiếm" else "Search",
                        color = c.textPrimary.copy(alpha = 0.88f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Tìm Theo Tên Tài Khoản, Email, Trang Web Hoặc Thư Mục." else "Search For An Item, Category, Or Folder.",
                        color = c.textSecondary,
                        fontSize = 15.5.sp,
                        lineHeight = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = fold.horizontalPadding,
                        end = fold.horizontalPadding,
                        top = 12.dp,
                        bottom = 48.dp,
                    ),
                ) {
                    item {
                        if (results.isEmpty()) {
                            OnePassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${s.noMatchingItems} \"$query\"",
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
                                    results.forEach { item ->
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
    }
}

/**
 * Refined Slim Navigation Rail for Unfolded Galaxy Z Fold 3 / Tablets (Requirement #18 — Image 29 polish):
 * Clean minimal vertical rail with top OpenVault crest, centered 3 pill destinations, and sleek bottom Cobalt '+'.
 */
@Composable
fun OnePassNavigationRail(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    onNewItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(if (c.isDark) Color(0xFF0E0F12) else c.navBarBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            OpenVaultBrandCrest(size = 34.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MainTab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    val tint by animateColorAsState(
                        targetValue = if (active) c.navActiveContent else c.navInactiveContent,
                        animationSpec = tween(150),
                        label = "rail_tint",
                    )
                    val pillBg by animateColorAsState(
                        targetValue = if (active) c.navActivePill else Color.Transparent,
                        animationSpec = tween(150),
                        label = "rail_pill",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(pillBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label(s),
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Text(
                            text = tab.railLabel(s),
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Surface(
                onClick = onNewItem,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = c.accentButtonBg,
                contentColor = c.accentButtonFg,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = s.newItem, modifier = Modifier.size(23.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .width(0.8.dp)
                .fillMaxHeight()
                .background(c.divider),
        )
    }
}

/**
 * Rounded-square Account Avatar matching 1Password 8 Android — automatically shows custom user photo when set!
 */
@Composable
fun OnePassAccountAvatar(
    email: String,
    onClick: (() -> Unit)? = null,
    size: Dp = 36.dp,
    avatarBase64Override: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val avatarB64 = avatarBase64Override ?: LocalAccountAvatar.current
    val bitmap = remember(avatarB64) { IconLoader.decodeBase64Bitmap(avatarB64) }
    val initial = email.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    val shape = RoundedCornerShape(size * 0.28f)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (c.isDark) Color(0xFF28354D) else Color(0xFFDBEAFE))
            .border(1.dp, c.accent.copy(alpha = 0.4f), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initial,
                color = c.accent,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Signature 1Password 8 Android Purple Diagonal-Striped "Editing" Header.
 */
@Composable
fun EditingStripedHeader(
    title: String,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.editBannerBg)
            .clipToBounds(),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stripeWidth = 14.dp.toPx()
            val spacing = 28.dp.toPx()
            val h = size.height
            var x = -h
            while (x < size.width + h) {
                drawLine(
                    color = c.editBannerStripe,
                    start = Offset(x, h),
                    end = Offset(x + h, 0f),
                    strokeWidth = stripeWidth,
                )
                x += spacing
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = s.cancel,
                    tint = c.editBannerText,
                    modifier = Modifier.size(23.dp),
                )
            }

            Text(
                text = title,
                color = c.editBannerText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = onSave,
                enabled = saveEnabled && !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accentButtonBg,
                    contentColor = c.accentButtonFg,
                    disabledContainerColor = c.accentButtonBg.copy(alpha = 0.4f),
                    disabledContentColor = c.accentButtonFg.copy(alpha = 0.6f),
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(s.save, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Clean circular Password Strength Donut Ring.
 */
@Composable
fun StrengthDonutRing(
    score: Int,
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val sweep = ((score + 1) / 5f).coerceIn(0.2f, 1f) * 360f
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 3.dp.toPx()
        val inset = strokeW / 2f
        val arcSize = Size(this.size.width - strokeW, this.size.height - strokeW)
        drawArc(
            color = c.divider,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun OnePassChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) c.accentButtonBg else c.surfaceElevated,
        border = if (selected) null else BorderStroke(0.8.dp, c.divider),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else c.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun OpenVaultBrandCrest(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF0C74F2)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val stroke = this.size.minDimension * 0.16f
            drawCircle(
                color = Color.White,
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = Color.White,
                radius = this.size.minDimension * 0.14f,
                center = Offset(center.x, center.y - this.size.height * 0.06f),
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y),
                end = Offset(center.x, center.y + this.size.height * 0.24f),
                strokeWidth = this.size.minDimension * 0.13f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun CategoryIconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 34.dp,
    circular: Boolean = false,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

private val ITEM_ICON_COLORS = listOf(
    Color(0xFF0C74F2),
    Color(0xFF0D9488),
    Color(0xFF7C3AED),
    Color(0xFFDC2626),
    Color(0xFFD97706),
    Color(0xFF0891B2),
    Color(0xFF4F46E5),
    Color(0xFF059669),
)

/**
 * 3-Tier 1Password-style rounded-square Item Icon:
 * 1) Custom user-uploaded image (E2EE compressed WebP stored in `_ov_icon`)
 * 2) Automatic cached Website Favicon (when enabled & login has a URL)
 * 3) Crisp brand-colored monogram / category icon fallback
 */
@Composable
fun ItemIconTile(
    title: String,
    type: CipherType,
    size: Dp = 42.dp,
    secureNoteType: Int = 0,
    showFavoriteStar: Boolean = false,
    customIconBase64: String? = null,
    websiteUrl: String? = null,
    showDropdownBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val showWebsiteIcons = LocalShowWebsiteIcons.current
    val customBitmap = remember(customIconBase64) { IconLoader.decodeBase64Bitmap(customIconBase64) }
    val faviconBitmap = rememberWebsiteFavicon(
        websiteUrl = if (customBitmap == null && type == CipherType.LOGIN) websiteUrl else null,
        enabled = showWebsiteIcons,
    )

    val noteCat = if (type == CipherType.SECURE_NOTE) SecureNoteCategory.fromCode(secureNoteType) else null
    val bgColor = when (type) {
        CipherType.LOGIN -> ITEM_ICON_COLORS[abs(title.hashCode()) % ITEM_ICON_COLORS.size]
        CipherType.CARD -> Color(0xFF0284C7)
        CipherType.SECURE_NOTE -> when (noteCat) {
            SecureNoteCategory.SOFTWARE -> Color(0xFF0284C7)
            SecureNoteCategory.PASSPORT -> Color(0xFF7C3AED)
            SecureNoteCategory.ID_CARD -> Color(0xFF059669)
            else -> Color(0xFFD97706)
        }
        CipherType.IDENTITY -> Color(0xFF22C55E)
    }
    val shape = RoundedCornerShape(size * 0.25f)

    Box(
        modifier = modifier
            .size(size)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        when {
            customBitmap != null -> {
                Image(
                    bitmap = customBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                )
            }

            faviconBitmap != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Color.White)
                        .padding(size * 0.14f),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = faviconBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center,
                ) {
                    when (type) {
                        CipherType.LOGIN -> {
                            val initials = title.trim()
                                .split(Regex("\\s+"))
                                .take(2)
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .joinToString("")
                                .ifBlank { "?" }
                            Text(
                                text = initials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (initials.length > 1) (size.value * 0.36f).sp else (size.value * 0.45f).sp,
                            )
                        }
                        CipherType.CARD -> Icon(
                            Icons.Rounded.CreditCard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(size * 0.56f),
                        )
                        CipherType.SECURE_NOTE -> Icon(
                            when (noteCat) {
                                SecureNoteCategory.SOFTWARE -> Icons.Rounded.Terminal
                                SecureNoteCategory.PASSPORT -> Icons.Rounded.Public
                                SecureNoteCategory.ID_CARD -> Icons.Rounded.Badge
                                else -> Icons.Rounded.Description
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(size * 0.56f),
                        )
                        CipherType.IDENTITY -> Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(size * 0.56f),
                        )
                    }
                }
            }
        }

        if (showFavoriteStar && !showDropdownBadge) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(15.dp),
            )
        }

        if (showDropdownBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1C1D1F))
                    .border(1.dp, Color(0xFF3E4148), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Clean 1Password 8 Android item row (auto-shows custom icon or real website favicon!).
 */
@Composable
fun OnePassItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    showFavoriteBadge: Boolean = false,
    selected: Boolean = false,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
    val iconSize = if (fold.isNarrowCover) 40.dp else 42.dp
    val hasPasskey = item.login?.fido2Credentials?.isNotEmpty() == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (selected) {
                    it.padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accentSoft)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                } else {
                    it.clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ItemIconTile(
            title = item.name,
            type = item.type,
            size = iconSize,
            secureNoteType = item.secureNoteType,
            showFavoriteStar = showFavoriteBadge && item.favorite,
            customIconBase64 = item.customIconBase64,
            websiteUrl = item.primaryWebsiteUrl,
        )

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
                    fontSize = if (fold.isNarrowCover) 16.sp else 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasPasskey) {
                    Text(
                        text = "Passkey",
                        color = Color(0xFFC4B5FD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7253ED).copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (item.hasTotp) {
                    Text(
                        text = "2FA",
                        color = c.fieldLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.fieldLabel.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = item.primarySubtitle,
                color = c.textSecondary,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TotpInlineCountdown(
    totp: TotpCode,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val arcColor = when {
        totp.remainingSeconds <= 5 -> c.danger
        totp.remainingSeconds <= 10 -> c.warning
        else -> c.success
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            drawCircle(color = arcColor.copy(alpha = 0.22f))
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * totp.progress.coerceIn(0f, 1f),
                useCenter = true,
            )
        }
        Text(
            text = "${totp.remainingSeconds}",
            color = arcColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun TotpCountdownRing(
    totp: TotpCode,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val ringColor = when {
        totp.remainingSeconds <= 5 -> c.danger
        totp.remainingSeconds <= 10 -> c.warning
        else -> c.success
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = ringColor.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * totp.progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${totp.remainingSeconds}",
            color = ringColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun SyntaxColoredPasswordText(
    password: String,
    masked: Boolean = false,
    fontSize: TextUnit = 15.sp,
    maxLines: Int = 3,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    if (masked) {
        Text(
            text = "•".repeat(password.length.coerceIn(8, 18)),
            color = c.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    val annotated = buildAnnotatedString {
        for (ch in password) {
            val color = when {
                ch.isDigit() -> c.passwordDigit
                !ch.isLetter() -> c.passwordSymbol
                else -> c.textPrimary
            }
            withStyle(
                SpanStyle(
                    color = color,
                    fontWeight = if (ch.isDigit() || !ch.isLetter()) FontWeight.SemiBold else FontWeight.Medium,
                ),
            ) {
                append(ch)
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = fontSize * 1.45f,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LargeTypePasswordDialog(
    title: String,
    password: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        OnePassCard(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 18.dp,
            backgroundColor = c.surfaceElevated,
            borderColor = c.cardBorder,
            padding = PaddingValues(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.largeType, color = c.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("$title · ${password.length} ${if (s.isVi) "Ký Tự" else "Chars"}", color = c.textSecondary, fontSize = 13.5.sp)
                    }
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = s.copy, tint = c.accent)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    password.forEachIndexed { idx, ch ->
                        val color = when {
                            ch.isDigit() -> c.passwordDigit
                            !ch.isLetter() -> c.passwordSymbol
                            else -> c.textPrimary
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.surface)
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = ch.toString(),
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(text = "${idx + 1}", color = c.textMuted, fontSize = 10.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(if (s.isVi) "Chữ cái" else "Letters", c.textPrimary)
                    LegendDot(if (s.isVi) "Chữ số" else "Digits", c.passwordDigit)
                    LegendDot(if (s.isVi) "Ký tự đặc biệt" else "Symbols", c.passwordSymbol)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val c = LocalOpenVaultColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, color = c.textSecondary, fontSize = 11.5.sp)
    }
}
