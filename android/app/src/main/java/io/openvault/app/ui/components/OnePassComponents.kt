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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlin.math.abs

enum class MainTab(val labelVi: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Rounded.Home),
    ITEMS("Các mục", Icons.Rounded.ViewAgenda),
    WATCHTOWER("Watchtower", Icons.Rounded.Security),
}

/**
 * Crisp matte 1Password 8 grouped card container (NO glass, NO gradients, NO heavy shadows).
 */
@Composable
fun OnePassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    backgroundColor: Color? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val bg = backgroundColor ?: c.surface
    val borderColor = if (c.isDark) Color.Transparent else c.cardBorder

    Surface(
        modifier = modifier
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = bg,
        border = if (borderColor != Color.Transparent) BorderStroke(0.8.dp, borderColor) else null,
    ) {
        Box(modifier = Modifier.padding(padding), content = content)
    }
}

/**
 * Authentic 1Password 8 flat matte search bar ("Search in OpenVault").
 */
@Composable
fun OnePassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Tìm trong OpenVault",
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.searchBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.textSecondary,
                    fontSize = 15.sp,
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
 * Clean 1Password 8 3-tab Bottom Navigation Bar (Home, Items, Watchtower).
 */
@Composable
fun OnePassBottomBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.navBarBg)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = c.divider, thickness = 0.7.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selectedTab
                val tint by animateColorAsState(
                    targetValue = if (active) c.accent else c.textSecondary,
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
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
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
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Top-left circular Account Avatar button (taps open Account & Settings, matching 1Password 8).
 */
@Composable
fun OnePassAccountAvatar(
    email: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val initial = email.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = c.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Top-right circular '+' button (sky-blue circle in Dark mode, Cobalt in Light mode — exact 1P8 look).
 */
@Composable
fun OnePassAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.accentButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Thêm mục mới",
            tint = c.accentButtonFg,
            modifier = Modifier.size(20.dp),
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
        color = if (selected) c.accent else c.searchBackground,
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
 * Clean flat OpenVault keyhole crest for Auth/Unlock headers.
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
            .background(Color(0xFF0572EC)),
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
 * Clean rounded-square category icon tile matching 1Password 8's distinct category icons.
 */
@Composable
fun CategoryIconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 32.dp,
    circular: Boolean = false,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(9.dp)
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
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

private val ITEM_ICON_COLORS = listOf(
    Color(0xFF2563EB),
    Color(0xFF0D9488),
    Color(0xFF7C3AED),
    Color(0xFFDC2626),
    Color(0xFFD97706),
    Color(0xFF0891B2),
    Color(0xFF4F46E5),
    Color(0xFF059669),
)

/**
 * Crisp 1Password-style rounded-square item icon (with optional gold star corner badge for favorites!).
 */
@Composable
fun ItemIconTile(
    title: String,
    type: CipherType,
    size: Dp = 36.dp,
    showFavoriteStar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (type) {
        CipherType.LOGIN -> ITEM_ICON_COLORS[abs(title.hashCode()) % ITEM_ICON_COLORS.size]
        CipherType.CARD -> Color(0xFF0284C7)
        CipherType.SECURE_NOTE -> Color(0xFFD97706)
        CipherType.IDENTITY -> Color(0xFF059669)
    }
    val shape = RoundedCornerShape(9.dp)

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
                        fontSize = if (initials.length > 1) (size.value * 0.36f).sp else (size.value * 0.44f).sp,
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
 * Exact 1Password 8 compact item row inside a grouped card:
 * [36dp icon]  Title + badges      [>]
 *              subtitle (username)
 */
@Composable
fun OnePassItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    showFavoriteBadge: Boolean = false,
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
        ItemIconTile(
            title = item.name,
            type = item.type,
            size = 36.dp,
            showFavoriteStar = showFavoriteBadge && item.favorite,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.hasTotp) {
                    Text(
                        text = "2FA",
                        color = c.fieldLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.fieldLabel.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
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

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Compact green circular pie/arc TOTP countdown exactly like 1Password 8 Home Pinned Cards (728 · 395 ◔ 15).
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
            text = "•".repeat(password.length.coerceIn(8, 20)),
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
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            cornerRadius = 18.dp,
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
                                .background(c.searchBackground)
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
