package io.openvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
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
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlin.math.abs

/**
 * Full-bleed Apple Liquid Glass atmospheric backdrop with gently breathing luminous aurora orbs.
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val transition = rememberInfiniteTransition(label = "liquid_aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora_drift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(c.canvas, c.canvasDeep, c.canvas),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaScale = if (c.isDark) 0.24f else 0.18f

            // Top-left Cobalt orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.orbPrimary.copy(alpha = alphaScale * 1.15f), Color.Transparent),
                    center = Offset(w * (0.18f + drift * 0.12f), h * 0.12f),
                    radius = w * 0.82f,
                ),
                radius = w * 0.82f,
                center = Offset(w * (0.18f + drift * 0.12f), h * 0.12f),
            )

            // Mid-right Violet glass orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.orbSecondary.copy(alpha = alphaScale), Color.Transparent),
                    center = Offset(w * (0.88f - drift * 0.10f), h * 0.42f),
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = Offset(w * (0.88f - drift * 0.10f), h * 0.42f),
            )

            // Bottom-left Cyan refraction orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.orbTertiary.copy(alpha = alphaScale * 0.9f), Color.Transparent),
                    center = Offset(w * 0.25f, h * (0.86f - drift * 0.08f)),
                    radius = w * 0.78f,
                ),
                radius = w * 0.78f,
                center = Offset(w * 0.25f, h * (0.86f - drift * 0.08f)),
            )
        }

        content()
    }
}

/**
 * Translucent Apple Liquid Glass card with luminous specular top-rim gradient border.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    elevated: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val fillBrush = Brush.verticalGradient(
        colors = if (elevated) {
            listOf(
                c.glassSurfaceElevated,
                c.glassSurface,
            )
        } else {
            listOf(
                c.glassSurface.copy(alpha = if (c.isDark) 0.78f else 0.86f),
                c.glassSurface.copy(alpha = if (c.isDark) 0.56f else 0.72f),
            )
        },
    )
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            c.glassBorderTop,
            c.glassBorderBottom,
            c.glassBorderTop.copy(alpha = if (c.isDark) 0.18f else 0.55f),
        ),
    )

    val baseMod = modifier
        .clip(shape)
        .background(fillBrush)
        .border(width = 1.dp, brush = rimBrush, shape = shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Box(modifier = baseMod.padding(padding), content = content)
}

@Composable
fun LiquidGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    badgeCount: Int? = null,
) {
    val c = LocalOpenVaultColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) c.accent else c.glassSurface,
        animationSpec = tween(180),
        label = "chip_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else c.textSecondary,
        animationSpec = tween(180),
        label = "chip_fg",
    )

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) c.accent else c.glassBorderTop.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (badgeCount != null && badgeCount > 0) {
                Text(
                    text = badgeCount.toString(),
                    color = if (selected) Color.White.copy(alpha = 0.85f) else c.textMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

enum class MainTab(val labelVi: String, val icon: ImageVector) {
    VAULT("Kho", Icons.Rounded.Shield),
    TOTP("2FA", Icons.Rounded.QrCode2),
    GENERATOR("Tạo mã", Icons.Rounded.Key),
    SETTINGS("Cài đặt", Icons.Rounded.Tune),
}

/**
 * Floating iOS-style Liquid Glass pill navigation dock hovering above the bottom bar.
 */
@Composable
fun LiquidGlassFloatingDock(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val shape = RoundedCornerShape(30.dp)
    val rimBrush = Brush.linearGradient(
        colors = listOf(c.glassBorderTop, c.glassBorderBottom, c.glassBorderTop.copy(alpha = 0.2f)),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.dockBackground)
                .border(1.dp, rimBrush, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in MainTab.entries) {
                val active = tab == selectedTab
                val pillBg by animateColorAsState(
                    targetValue = if (active) c.accentSoft else Color.Transparent,
                    animationSpec = tween(200),
                    label = "dock_pill",
                )
                val contentTint by animateColorAsState(
                    targetValue = if (active) c.accent else c.textSecondary,
                    animationSpec = tween(200),
                    label = "dock_tint",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pillBg)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.labelVi,
                        tint = contentTint,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = tab.labelVi,
                        color = contentTint,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun OpenVaultBrandCrest(
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0A5CE6), Color(0xFF3B8BFF), Color(0xFF6366F1)),
                ),
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.1f)),
                ),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.62f)) {
            val stroke = this.size.minDimension * 0.15f
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

private val AVATAR_PALETTES = listOf(
    listOf(Color(0xFF2563EB), Color(0xFF38BDF8)),
    listOf(Color(0xFF7C3AED), Color(0xFFC084FC)),
    listOf(Color(0xFF059669), Color(0xFF34D399)),
    listOf(Color(0xFFEA580C), Color(0xFFFB923C)),
    listOf(Color(0xFFDB2777), Color(0xFFF472B6)),
    listOf(Color(0xFF0891B2), Color(0xFF22D3EE)),
)

@Composable
fun BrandMonogramAvatar(
    title: String,
    type: CipherType,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val palette = AVATAR_PALETTES[abs(title.hashCode()) % AVATAR_PALETTES.size]
    val shape = RoundedCornerShape(size * 0.3f)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(palette))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (type) {
            CipherType.LOGIN -> {
                val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(
                    text = letter,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
            CipherType.CARD -> Icon(
                Icons.Rounded.CreditCard,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f),
            )
            CipherType.SECURE_NOTE -> Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f),
            )
            CipherType.IDENTITY -> Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/**
 * 1Password-style monospace password renderer:
 * Letters = Primary, Digits = Cobalt Blue, Symbols = Coral Rose.
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
            text = "•".repeat(password.length.coerceIn(8, 22)),
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

/**
 * 1Password "Show in Large Type" dialog with numbered character tiles for typing on another device.
 */
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
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            cornerRadius = 28.dp,
            elevated = true,
            padding = PaddingValues(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phóng to mật khẩu",
                            color = c.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$title · ${password.length} ký tự",
                            color = c.textSecondary,
                            fontSize = 12.5.sp,
                        )
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.glassSurface)
                                .border(1.dp, c.glassBorderTop.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = ch.toString(),
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${idx + 1}",
                                color = c.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text = label, color = c.textSecondary, fontSize = 11.5.sp)
    }
}

@Composable
fun TotpCountdownRing(
    totp: TotpCode,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val ringColor = when {
        totp.remainingSeconds <= 5 -> c.danger
        totp.remainingSeconds <= 10 -> c.warning
        else -> c.accent
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.5.dp.toPx()
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
