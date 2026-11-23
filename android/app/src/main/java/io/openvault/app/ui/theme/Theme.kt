package io.openvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.openvault.app.core.security.AppThemeMode

/**
 * Clean, flat native design tokens inspired by 1Password 8 Mobile:
 * - Pure OLED Black (#000000) + matte charcoal (#1C1C1E) cards in Dark Mode
 * - Clean neutral light gray (#F3F4F6) + pure white (#FFFFFF) cards in Light Mode
 * - Hairline dividers, crisp typography, ZERO glass/blur/aurora noise
 */
@Immutable
data class OpenVaultColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val searchBackground: Color,
    val divider: Color,
    val cardBorder: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentButtonBg: Color,
    val accentButtonFg: Color,
    val fieldLabel: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val passwordDigit: Color,
    val passwordSymbol: Color,
    val navBarBg: Color,
    val navActivePill: Color,
)

private val DarkOnePassColors = OpenVaultColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF252528),
    searchBackground = Color(0xFF242426),
    divider = Color(0xFF2C2C2E),
    cardBorder = Color(0xFF2A2A2D),
    accent = Color(0xFF5BA8FF),
    accentSoft = Color(0x245BA8FF),
    accentButtonBg = Color(0xFF7CB9FF),
    accentButtonFg = Color(0xFF071A33),
    fieldLabel = Color(0xFF9E94E6),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9AA0),
    textMuted = Color(0xFF6E6E73),
    success = Color(0xFF34D399),
    successSoft = Color(0x2034D399),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0x24FBBF24),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0x243D1B1E),
    passwordDigit = Color(0xFF60A5FA),
    passwordSymbol = Color(0xFFFB7185),
    navBarBg = Color(0xFF141416),
    navActivePill = Color(0xFF232B3A),
)

private val LightOnePassColors = OpenVaultColors(
    isDark = false,
    background = Color(0xFFF3F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    searchBackground = Color(0xFFE7E9EE),
    divider = Color(0xFFE8EAEF),
    cardBorder = Color(0xFFE3E6EC),
    accent = Color(0xFF0572EC),
    accentSoft = Color(0x180572EC),
    accentButtonBg = Color(0xFF0572EC),
    accentButtonFg = Color(0xFFFFFFFF),
    fieldLabel = Color(0xFF6958C7),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    textMuted = Color(0xFF9CA3AF),
    success = Color(0xFF16A34A),
    successSoft = Color(0x1A16A34A),
    warning = Color(0xFFD97706),
    warningSoft = Color(0x1AD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0x1ADC2626),
    passwordDigit = Color(0xFF0572EC),
    passwordSymbol = Color(0xFFE11D48),
    navBarBg = Color(0xFFFFFFFF),
    navActivePill = Color(0xFFEAF2FF),
)

val LocalOpenVaultColors = staticCompositionLocalOf { DarkOnePassColors }

private val OnePassTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun OpenVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ovColors = if (isDark) DarkOnePassColors else LightOnePassColors

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = ovColors.accent,
            onPrimary = Color.White,
            background = ovColors.background,
            onBackground = ovColors.textPrimary,
            surface = ovColors.surface,
            onSurface = ovColors.textPrimary,
            surfaceVariant = ovColors.surfaceElevated,
            onSurfaceVariant = ovColors.textSecondary,
            error = ovColors.danger,
            outline = ovColors.divider,
        )
    } else {
        lightColorScheme(
            primary = ovColors.accent,
            onPrimary = Color.White,
            background = ovColors.background,
            onBackground = ovColors.textPrimary,
            surface = ovColors.surface,
            onSurface = ovColors.textPrimary,
            surfaceVariant = ovColors.searchBackground,
            onSurfaceVariant = ovColors.textSecondary,
            error = ovColors.danger,
            outline = ovColors.divider,
        )
    }

    CompositionLocalProvider(LocalOpenVaultColors provides ovColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = OnePassTypography,
            content = content,
        )
    }
}
