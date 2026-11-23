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

@Immutable
data class OpenVaultColors(
    val isDark: Boolean,
    val canvas: Color,
    val canvasDeep: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassSurfaceActive: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentGlow: Color,
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
    val dockBackground: Color,
)

private val DarkOpenVaultColors = OpenVaultColors(
    isDark = true,
    canvas = Color(0xFF070B14),
    canvasDeep = Color(0xFF04070D),
    orbPrimary = Color(0xFF1D4ED8),
    orbSecondary = Color(0xFF6D28D9),
    orbTertiary = Color(0xFF0284C7),
    glassSurface = Color(0x99131B2E),
    glassSurfaceElevated = Color(0xCC1B263F),
    glassSurfaceActive = Color(0x383B8BFF),
    glassBorderTop = Color(0x40FFFFFF),
    glassBorderBottom = Color(0x14FFFFFF),
    accent = Color(0xFF3B8BFF),
    accentSoft = Color(0x2B3B8BFF),
    accentGlow = Color(0x663B8BFF),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFA0AEC8),
    textMuted = Color(0xFF64748B),
    success = Color(0xFF4ADE80),
    successSoft = Color(0x244ADE80),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0x24FBBF24),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0x24F87171),
    passwordDigit = Color(0xFF60A5FA),
    passwordSymbol = Color(0xFFFB7185),
    dockBackground = Color(0xCC0E1526),
)

private val LightOpenVaultColors = OpenVaultColors(
    isDark = false,
    canvas = Color(0xFFEFF4FB),
    canvasDeep = Color(0xFFE4ECF8),
    orbPrimary = Color(0xFF60A5FA),
    orbSecondary = Color(0xFFA78BFA),
    orbTertiary = Color(0xFF38BDF8),
    glassSurface = Color(0xC7FFFFFF),
    glassSurfaceElevated = Color(0xE6FFFFFF),
    glassSurfaceActive = Color(0x240572EC),
    glassBorderTop = Color(0xF0FFFFFF),
    glassBorderBottom = Color(0x3394A3B8),
    accent = Color(0xFF0572EC),
    accentSoft = Color(0x1A0572EC),
    accentGlow = Color(0x400572EC),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    success = Color(0xFF16A34A),
    successSoft = Color(0x1F16A34A),
    warning = Color(0xFFD97706),
    warningSoft = Color(0x1FD97706),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0x1FDC2626),
    passwordDigit = Color(0xFF0572EC),
    passwordSymbol = Color(0xFFE11D48),
    dockBackground = Color(0xD9FFFFFF),
)

val LocalOpenVaultColors = staticCompositionLocalOf { DarkOpenVaultColors }

private val OpenVaultTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
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
        letterSpacing = 0.2.sp,
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
    val ovColors = if (isDark) DarkOpenVaultColors else LightOpenVaultColors

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = ovColors.accent,
            onPrimary = Color.White,
            background = ovColors.canvas,
            onBackground = ovColors.textPrimary,
            surface = ovColors.glassSurfaceElevated,
            onSurface = ovColors.textPrimary,
            surfaceVariant = ovColors.glassSurface,
            onSurfaceVariant = ovColors.textSecondary,
            error = ovColors.danger,
            outline = ovColors.glassBorderTop,
        )
    } else {
        lightColorScheme(
            primary = ovColors.accent,
            onPrimary = Color.White,
            background = ovColors.canvas,
            onBackground = ovColors.textPrimary,
            surface = ovColors.glassSurfaceElevated,
            onSurface = ovColors.textPrimary,
            surfaceVariant = ovColors.glassSurface,
            onSurfaceVariant = ovColors.textSecondary,
            error = ovColors.danger,
            outline = ovColors.glassBorderBottom,
        )
    }

    CompositionLocalProvider(LocalOpenVaultColors provides ovColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = OpenVaultTypography,
            content = content,
        )
    }
}
