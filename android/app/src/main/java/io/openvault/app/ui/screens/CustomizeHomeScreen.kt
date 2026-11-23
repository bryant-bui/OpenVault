package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.security.HomeSectionPref
import io.openvault.app.core.security.HomeSectionType
import io.openvault.app.core.security.SecuritySettings
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings

/**
 * Customize Home Screen (matching 1Password Android):
 * Lets users show/hide and reorder Home sections (Pinned 2FA, Profile, Favorites, Recently Created, Frequently Used).
 */
@Composable
fun CustomizeHomeScreen(
    sections: List<HomeSectionPref>,
    onUpdateSections: (List<HomeSectionPref>) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textPrimary)
                }
                Text(
                    text = s.customizeHomeTitle,
                    color = c.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = { onUpdateSections(SecuritySettings.DEFAULT_HOME_SECTIONS) }) {
                Text(if (s.isVi) "Mặc định" else "Reset", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = s.customizeHomeSubtitle,
                    color = c.textSecondary,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                sections.forEachIndexed { idx, pref ->
                    val type = pref.toTypeOrNull() ?: return@forEachIndexed
                    val (label, icon, iconTint, iconBg) = sectionVisuals(type, s)

                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        backgroundColor = c.surface,
                        borderColor = c.divider,
                        padding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = pref.visible,
                                onCheckedChange = { checked ->
                                    val updated = sections.toMutableList()
                                    updated[idx] = pref.copy(visible = checked)
                                    onUpdateSections(updated)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = c.accentButtonBg,
                                    uncheckedColor = c.textSecondary,
                                    checkmarkColor = Color.White,
                                ),
                            )

                            CategoryIconBadge(
                                icon = icon,
                                tint = iconTint,
                                background = iconBg,
                                size = 34.dp,
                                circular = true,
                            )

                            Text(
                                text = label,
                                color = if (pref.visible) c.textPrimary else c.textSecondary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = idx > 0,
                                    onClick = {
                                        val updated = sections.toMutableList()
                                        val moved = updated.removeAt(idx)
                                        updated.add(idx - 1, moved)
                                        onUpdateSections(updated)
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (idx > 0) c.textPrimary else c.textMuted.copy(alpha = 0.4f),
                                    )
                                }
                                IconButton(
                                    enabled = idx < sections.lastIndex,
                                    onClick = {
                                        val updated = sections.toMutableList()
                                        val moved = updated.removeAt(idx)
                                        updated.add(idx + 1, moved)
                                        onUpdateSections(updated)
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (idx < sections.lastIndex) c.textPrimary else c.textMuted.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private data class SectionVisual(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
)

private fun sectionVisuals(
    type: HomeSectionType,
    s: io.openvault.app.ui.theme.AppStrings,
): SectionVisual = when (type) {
    HomeSectionType.PINNED_TOTP -> SectionVisual(s.pinned2fa, Icons.Rounded.QrCode2, Color(0xFF2E1065), Color(0xFFC4B5FD))
    HomeSectionType.PROFILE -> SectionVisual(s.profile, Icons.Rounded.Person, Color(0xFF0C74F2), Color.White)
    HomeSectionType.FAVORITES -> SectionVisual(s.favorites, Icons.Rounded.Star, Color(0xFF1F1604), Color(0xFFFBBF24))
    HomeSectionType.RECENT -> SectionVisual(s.recentlyCreated, Icons.Rounded.History, Color(0xFF07233B), Color(0xFF60B8FF))
    HomeSectionType.FREQUENT -> SectionVisual(s.frequentlyUsed, Icons.Rounded.KeyboardDoubleArrowRight, Color(0xFF052E16), Color(0xFF4ADE80))
}
