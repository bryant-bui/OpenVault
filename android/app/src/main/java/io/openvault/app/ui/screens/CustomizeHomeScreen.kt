package io.openvault.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.openvault.app.core.security.HomeSectionPref
import io.openvault.app.core.security.HomeSectionType
import io.openvault.app.core.security.SecuritySettings
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings

/**
 * Customize Home Screen:
 * Hold and drag rows vertically to reorder Home sections, toggle visibility checkboxes,
 * and tap the primary Save button to commit changes.
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
    val haptic = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { 10.dp.toPx() }

    var draftSections by remember(sections) { mutableStateOf(sections) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = s.close,
                        tint = c.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = s.customizeHomeTitle,
                    color = c.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Button(
                onClick = {
                    onUpdateSections(draftSections)
                    onBack()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accentButtonBg,
                    contentColor = c.accentButtonFg,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .height(40.dp),
            ) {
                Text(s.save, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = draggedId == null),
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
                    fontSize = 14.5.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )

                draftSections.forEach { pref ->
                    val type = pref.toTypeOrNull() ?: return@forEach
                    key(pref.id) {
                        val (label, icon, iconTint, iconBg) = sectionVisuals(type, s)
                        val isDragged = draggedId == pref.id
                        val scale by animateFloatAsState(
                            targetValue = if (isDragged) 1.03f else 1f,
                            label = "drag_scale",
                        )

                        OnePassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 2f else 0f)
                                .onSizeChanged { itemHeights[pref.id] = it.height }
                                .graphicsLayer {
                                    translationY = if (isDragged) dragOffsetY else 0f
                                    scaleX = scale
                                    scaleY = scale
                                    shadowElevation = if (isDragged) 18f else 0f
                                }
                                .pointerInput(pref.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = pref.id
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val currentIdx = draftSections.indexOfFirst { it.id == pref.id }
                                            if (currentIdx == -1) return@detectDragGesturesAfterLongPress
                                            val ownHeight = (itemHeights[pref.id] ?: 160).toFloat() + spacingPx
                                            if (dragOffsetY > ownHeight * 0.55f && currentIdx < draftSections.lastIndex) {
                                                val targetIdx = currentIdx + 1
                                                val targetId = draftSections[targetIdx].id
                                                val swapStep = (itemHeights[targetId] ?: ownHeight.toInt()).toFloat() + spacingPx
                                                val updated = draftSections.toMutableList()
                                                val moved = updated.removeAt(currentIdx)
                                                updated.add(targetIdx, moved)
                                                draftSections = updated
                                                dragOffsetY -= swapStep
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } else if (dragOffsetY < -ownHeight * 0.55f && currentIdx > 0) {
                                                val targetIdx = currentIdx - 1
                                                val targetId = draftSections[targetIdx].id
                                                val swapStep = (itemHeights[targetId] ?: ownHeight.toInt()).toFloat() + spacingPx
                                                val updated = draftSections.toMutableList()
                                                val moved = updated.removeAt(currentIdx)
                                                updated.add(targetIdx, moved)
                                                draftSections = updated
                                                dragOffsetY += swapStep
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDragEnd = {
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                    )
                                },
                            cornerRadius = 14.dp,
                            backgroundColor = if (isDragged) c.surfaceElevated else c.surface,
                            borderColor = if (isDragged) c.accent else c.divider,
                            padding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = pref.visible,
                                    onCheckedChange = { checked ->
                                        val currentIdx = draftSections.indexOfFirst { it.id == pref.id }
                                        if (currentIdx >= 0) {
                                            val updated = draftSections.toMutableList()
                                            updated[currentIdx] = pref.copy(visible = checked)
                                            draftSections = updated
                                        }
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
                                    size = 38.dp,
                                    circular = true,
                                )

                                Text(
                                    text = label,
                                    color = if (pref.visible) c.textPrimary else c.textSecondary,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )

                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = if (s.isVi) "Nhấn Giữ Và Kéo" else "Hold And Drag",
                                    tint = if (isDragged) c.accent else c.textSecondary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { draftSections = SecuritySettings.DEFAULT_HOME_SECTIONS }) {
                        Text(
                            text = s.resetDefault,
                            color = c.textSecondary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
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
