package io.openvault.app.ui.screens.safe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlin.math.roundToInt

/**
 * Centered 3+3 PIN indicator row using BoxWithConstraints so slots never clip
 * or wrap unevenly on narrow cover displays or tablets.
 */
@Composable
fun SafePinIndicatorRow(
    pinLength: Int,
    targetLength: Int = SafeCrypto.SAFE_PIN_LENGTH,
    isError: Boolean = false,
    enabled: Boolean = true,
    shakeOffsetPx: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val inputBg = if (c.isDark) Color(0xFF181A20) else Color.White

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffsetPx.roundToInt(), 0) },
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 310.dp
        val innerGap = if (compact) 5.dp else 8.dp
        val splitWidth = if (compact) 14.dp else 18.dp
        val availableForSlots = (maxWidth - splitWidth - innerGap * 4).coerceAtLeast(180.dp)
        val slotWidth = (availableForSlots / targetLength.toFloat()).coerceIn(32.dp, 46.dp)
        val slotHeight = (slotWidth * 1.18f).coerceIn(40.dp, 54.dp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            for (i in 0 until targetLength) {
                if (i == 3) {
                    Box(
                        modifier = Modifier.width(splitWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = 2.5.dp)
                                .clip(CircleShape)
                                .background(c.textMuted.copy(alpha = 0.55f)),
                        )
                    }
                } else if (i > 0) {
                    Spacer(modifier = Modifier.width(innerGap))
                }

                val filled = i < pinLength
                val active = enabled && !isError && i == pinLength
                val dotScale by animateFloatAsState(
                    targetValue = if (filled) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "safe_pin_dot_$i",
                )
                val borderColor by animateColorAsState(
                    targetValue = when {
                        isError -> c.danger
                        active -> c.accent
                        filled -> c.accent.copy(alpha = 0.45f)
                        else -> c.cardBorder
                    },
                    animationSpec = tween(160),
                    label = "safe_pin_border_$i",
                )
                val containerBg by animateColorAsState(
                    targetValue = when {
                        isError -> c.dangerSoft
                        active -> if (c.isDark) Color(0xFF1E2432) else Color(0xFFF3F8FF)
                        else -> inputBg
                    },
                    animationSpec = tween(160),
                    label = "safe_pin_bg_$i",
                )

                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .size(width = slotWidth, height = slotHeight)
                        .clip(shape)
                        .background(containerBg)
                        .border(
                            width = if (active || isError) 1.7.dp else 1.dp,
                            color = borderColor,
                            shape = shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (filled) {
                        Box(
                            modifier = Modifier
                                .scale(dotScale)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(if (isError) c.danger else c.textPrimary),
                        )
                    } else if (active) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .clip(CircleShape)
                                .background(c.accent),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom 3x4 numeric keypad for Vault+ PIN entry & setup.
 * Keeps keys symmetric with adaptive spacing and optional explicit biometric trigger.
 */
@Composable
fun SafeNumericKeypad(
    enabled: Boolean = true,
    showBiometric: Boolean = false,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val keyBg = if (c.isDark) Color(0xFF1D2027) else Color.White

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 310.dp
        val hSpacing = if (compact) 14.dp else 22.dp
        val keySize = ((maxWidth - hSpacing * 2 - 24.dp) / 3f).coerceIn(52.dp, 68.dp)
        val vSpacing = if (compact) 10.dp else 13.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(vSpacing),
        ) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(hSpacing)) {
                    row.forEach { digit ->
                        SafeKeypadDigitButton(
                            label = digit,
                            size = keySize,
                            enabled = enabled,
                            background = keyBg,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(digit)
                            },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBiometric) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBiometricClick()
                        },
                        enabled = enabled,
                        shape = CircleShape,
                        color = c.accentSoft,
                        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.28f)),
                        modifier = Modifier.size(keySize),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = s.vaultPlusEnableBioToggle,
                                tint = c.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(keySize))
                }

                SafeKeypadDigitButton(
                    label = "0",
                    size = keySize,
                    enabled = enabled,
                    background = keyBg,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit("0")
                    },
                )

                Box(
                    modifier = Modifier
                        .size(keySize)
                        .clip(CircleShape)
                        .clickable(enabled = enabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBackspace()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = s.back,
                        tint = c.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeKeypadDigitButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    background: Color,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.dp, c.cardBorder.copy(alpha = 0.72f)),
        shadowElevation = if (c.isDark) 0.dp else 1.5.dp,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (enabled) c.textPrimary else c.textMuted,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
