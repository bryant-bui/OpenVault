package io.openvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.openvault.app.core.crypto.TotpCode
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlin.math.abs

enum class MainTab(val labelVi: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Rounded.Home),
    ITEMS("Các mục", Icons.Rounded.ViewAgenda),
    WATCHTOWER("Watchtower", Icons.Rounded.Security),
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
    placeholder: String = "Tìm trong OpenVault",
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(c.searchBackground)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = c.textPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.textSecondary,
                    fontSize = 15.sp,
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Xóa tìm kiếm",
                tint = c.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Authentic 1Password 8 Android Single-Row Top Bar:
 * [ (🔍 Tìm trong OpenVault) ]  [Optional Action]  [⌄ Avatar -> Anchored Dropdown Menu]
 * Designed to stay spacious and uncrowded even on Galaxy Z Fold 3's 320dp outer cover display.
 */
@Composable
fun OnePassTopSearchBar(
    email: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    isSyncing: Boolean = false,
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
        horizontalArrangement = Arrangement.spacedBy(if (fold.isNarrowCover) 6.dp else 10.dp),
    ) {
        if (leadingAction != null) {
            leadingAction()
        }

        OnePassSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
        )

        if (trailingAction != null) {
            trailingAction()
        }

        // Anchored Account Avatar + Dropdown (matching real 1Password Android!)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Tài khoản",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                OnePassAccountAvatar(
                    email = email,
                    onClick = { menuExpanded = true },
                    size = if (fold.isNarrowCover) 34.dp else 36.dp,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = c.surfaceElevated,
                border = BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.widthIn(min = 235.dp, max = 280.dp),
            ) {
                // Account identity header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OnePassAccountAvatar(email = email, onClick = { }, size = 34.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                if (onSync != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isSyncing) "Đang đồng bộ…" else "Đồng bộ ngay",
                                color = c.textPrimary,
                                fontSize = 15.sp,
                            )
                        },
                        trailingIcon = {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = c.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
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
                        text = { Text("Tạo mật khẩu…", color = c.textPrimary, fontSize = 15.sp) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenGenerator()
                        },
                    )
                }

                if (onOpenQrScanner != null) {
                    DropdownMenuItem(
                        text = { Text("Quét mã QR 2FA…", color = c.textPrimary, fontSize = 15.sp) },
                        trailingIcon = {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenQrScanner()
                        },
                    )
                }

                HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                DropdownMenuItem(
                    text = { Text("Cài đặt…", color = c.textPrimary, fontSize = 15.sp) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
                    },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                )

                DropdownMenuItem(
                    text = { Text("Khóa kho", color = c.textPrimary, fontSize = 15.sp) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
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
 * Signature 1Password 8 Android Cobalt-Blue Extended FAB ("+ Mục mới").
 */
@Composable
fun OnePassExtendedFab(
    text: String = "Mục mới",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        color = c.accentButtonBg,
        contentColor = c.accentButtonFg,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
                            .width(if (fold.isNarrowCover) 54.dp else 62.dp)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(pillBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.labelVi,
                            tint = tint,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Text(
                        text = tab.labelVi,
                        color = tint,
                        fontSize = if (fold.isNarrowCover) 11.5.sp else 12.sp,
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
 * Unfolded Samsung Galaxy Z Fold 3 / Tablet Left Navigation Rail (1Password Android large-screen UX).
 */
@Composable
fun OnePassNavigationRail(
    selectedTab: MainTab,
    isSyncing: Boolean,
    onSelect: (MainTab) -> Unit,
    onNewItem: () -> Unit,
    onSync: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(78.dp)
                .fillMaxHeight()
                .background(c.navBarBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Top Cobalt '+' FAB
                Surface(
                    onClick = onNewItem,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = c.accentButtonBg,
                    contentColor = c.accentButtonFg,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = "Mục mới", modifier = Modifier.size(24.dp))
                    }
                }

                // Primary 3 tabs
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    .width(54.dp)
                                    .height(32.dp)
                                    .clip(CircleShape)
                                    .background(pillBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.labelVi,
                                    tint = tint,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Text(
                                text = if (tab == MainTab.WATCHTOWER) "Bảo mật" else tab.labelVi,
                                color = tint,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Bottom utility shortcuts on Z Fold 3 unfolded rail
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onSync, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Đồng bộ", tint = c.navInactiveContent, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onOpenGenerator) {
                    Icon(Icons.Rounded.Key, contentDescription = "Tạo mật khẩu", tint = c.navInactiveContent, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Cài đặt", tint = c.navInactiveContent, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onLock) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Khóa kho", tint = c.navInactiveContent, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Subtle right hairline separating Rail from Master Pane
        Box(
            modifier = Modifier
                .width(0.8.dp)
                .fillMaxHeight()
                .background(c.divider),
        )
    }
}

/**
 * Rounded-square Account Avatar matching 1Password 8 Android.
 */
@Composable
fun OnePassAccountAvatar(
    email: String,
    onClick: () -> Unit,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val initial = email.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (c.isDark) Color(0xFF28354D) else Color(0xFFDBEAFE))
            .border(1.dp, c.accent.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = c.accent,
            fontSize = (size.value * 0.44f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Signature 1Password 8 Android Purple Diagonal-Striped "Editing" Header
 * (✕ | Đang sửa / Tạo mới | [Lưu] solid cobalt pill button).
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.editBannerBg)
            .clipToBounds(),
    ) {
        // Diagonal purple stripes matching real 1Password Android Editor
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
                    contentDescription = "Hủy",
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
                    Text("Lưu", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Clean circular Password Strength Donut Ring (exact 1Password Android Detail indicator!).
 */
@Composable
fun StrengthDonutRing(
    score: Int, // 0..4
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

/**
 * Clean flat filter chip for Settings/Editor options.
 */
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

/**
 * Clean flat OpenVault keyhole crest for Auth/Unlock/Two-Pane Empty headers.
 */
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

/**
 * Clean rounded-square or circular category icon badge matching 1Password 8 Android.
 */
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
 * Crisp 1Password-style rounded-square item icon (with optional gold star badge for favorites).
 */
@Composable
fun ItemIconTile(
    title: String,
    type: CipherType,
    size: Dp = 38.dp,
    showFavoriteStar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (type) {
        CipherType.LOGIN -> ITEM_ICON_COLORS[abs(title.hashCode()) % ITEM_ICON_COLORS.size]
        CipherType.CARD -> Color(0xFF0284C7)
        CipherType.SECURE_NOTE -> Color(0xFFD97706)
        CipherType.IDENTITY -> Color(0xFF22C55E)
    }
    val shape = RoundedCornerShape(size * 0.25f)

    Box(modifier = modifier.size(size)) {
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
                        fontSize = if (initials.length > 1) (size.value * 0.35f).sp else (size.value * 0.44f).sp,
                    )
                }
                CipherType.CARD -> Icon(
                    Icons.Rounded.CreditCard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f),
                )
                CipherType.SECURE_NOTE -> Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f),
                )
                CipherType.IDENTITY -> Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.54f),
                )
            }
        }

        if (showFavoriteStar) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp),
            )
        }
    }
}

/**
 * Clean 1Password 8 Android item row (no noisy trailing chevron; highlights smoothly when selected in Two-Pane Z Fold 3 mode).
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
    val iconSize = if (fold.isNarrowCover) 36.dp else 38.dp
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
            showFavoriteStar = showFavoriteBadge && item.favorite,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    color = c.textPrimary,
                    fontSize = if (fold.isNarrowCover) 15.sp else 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasPasskey) {
                    Text(
                        text = "Passkey",
                        color = Color(0xFFC4B5FD),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7253ED).copy(alpha = 0.22f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (item.hasTotp) {
                    Text(
                        text = "2FA",
                        color = c.fieldLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.fieldLabel.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = item.primarySubtitle,
                color = c.textSecondary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Compact green circular pie/arc TOTP countdown matching 1Password 8 Home Pinned Cards.
 */
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

/**
 * 1Password-style monospace password renderer:
 * Letters = Primary, Digits = Blue, Symbols = Rose/Coral.
 */
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
                        Text("Phóng to mật khẩu", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("$title · ${password.length} ký tự", color = c.textSecondary, fontSize = 12.5.sp)
                    }
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Sao chép", tint = c.accent)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Đóng", tint = c.textSecondary)
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
                    LegendDot("Chữ cái", c.textPrimary)
                    LegendDot("Chữ số", c.passwordDigit)
                    LegendDot("Ký tự đặc biệt", c.passwordSymbol)
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
