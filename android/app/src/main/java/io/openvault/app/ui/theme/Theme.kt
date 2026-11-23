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
import io.openvault.app.core.security.AppLanguage
import io.openvault.app.core.security.AppThemeMode

/**
 * Authentic 1Password 8 Android (Material 3) flat design tokens:
 * - Pure OLED Black (#000000) canvas + matte charcoal (#161719) cards on Home/Items/Watchtower
 * - Elevated matte charcoal (#1C1D1F) on Item Detail, Item Editor, and Settings
 * - Signature Midnight Navy rounded search pill (#1B2336), Cobalt FAB (#0C74F2),
 *   Periwinkle Lavender field labels (#A9A2F8), and Purple Diagonal-Striped Edit Banner
 * - ZERO glass/blur/aurora effects
 */
@Immutable
data class OpenVaultColors(
    val isDark: Boolean,
    val background: Color,
    val sheetBackground: Color,
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
    val editBannerBg: Color,
    val editBannerStripe: Color,
    val editBannerText: Color,
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
    val navActiveContent: Color,
    val navInactiveContent: Color,
)

private val DarkOnePassColors = OpenVaultColors(
    isDark = true,
    background = Color(0xFF000000),
    sheetBackground = Color(0xFF1C1D1F),
    surface = Color(0xFF161719),
    surfaceElevated = Color(0xFF222428),
    searchBackground = Color(0xFF1B2336),
    divider = Color(0xFF2C2E33),
    cardBorder = Color(0xFF33363C),
    accent = Color(0xFF60A8FF),
    accentSoft = Color(0xFF1D314B),
    accentButtonBg = Color(0xFF0C74F2),
    accentButtonFg = Color(0xFFFFFFFF),
    fieldLabel = Color(0xFFA9A2F8),
    editBannerBg = Color(0xFF271B4A),
    editBannerStripe = Color(0xFF3A296B),
    editBannerText = Color(0xFFC4B5FD),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9EA4AE),
    textMuted = Color(0xFF6B7280),
    success = Color(0xFF4ADE80),
    successSoft = Color(0x204ADE80),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0x24FBBF24),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0x263D1B1E),
    passwordDigit = Color(0xFF60A5FA),
    passwordSymbol = Color(0xFFFB7185),
    navBarBg = Color(0xFF141517),
    navActivePill = Color(0xFF1E334D),
    navActiveContent = Color(0xFF9DC7FB),
    navInactiveContent = Color(0xFFB3BAC4),
)

private val LightOnePassColors = OpenVaultColors(
    isDark = false,
    background = Color(0xFFF3F5F8),
    sheetBackground = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF8FAFC),
    searchBackground = Color(0xFFE6ECF7),
    divider = Color(0xFFE5E7EB),
    cardBorder = Color(0xFFD4DAE4),
    accent = Color(0xFF0572EC),
    accentSoft = Color(0xFFE0ECFF),
    accentButtonBg = Color(0xFF0572EC),
    accentButtonFg = Color(0xFFFFFFFF),
    fieldLabel = Color(0xFF6958C7),
    editBannerBg = Color(0xFFEDE9FE),
    editBannerStripe = Color(0xFFDDD6FE),
    editBannerText = Color(0xFF5B21B6),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5F6672),
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
    navActivePill = Color(0xFFDBEAFE),
    navActiveContent = Color(0xFF0572EC),
    navInactiveContent = Color(0xFF5F6672),
)

val LocalOpenVaultColors = staticCompositionLocalOf { DarkOnePassColors }
val LocalShowWebsiteIcons = staticCompositionLocalOf { true }
val LocalAccountAvatar = staticCompositionLocalOf<String?> { null }

private val OnePassTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
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
    language: AppLanguage = AppLanguage.ENGLISH,
    showWebsiteIcons: Boolean = true,
    accountAvatarBase64: String? = null,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ovColors = if (isDark) DarkOnePassColors else LightOnePassColors
    val strings = stringsFor(language)

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

    CompositionLocalProvider(
        LocalOpenVaultColors provides ovColors,
        LocalStrings provides strings,
        LocalShowWebsiteIcons provides showWebsiteIcons,
        LocalAccountAvatar provides accountAvatarBase64,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = OnePassTypography,
            content = content,
        )
    }
}
