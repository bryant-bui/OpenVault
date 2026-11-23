package io.openvault.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.media.IconLoader
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.theme.LocalAccountAvatar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

/**
 * Dedicated Profile Screen inspired by 1Password 8 Android:
 * - Midnight-navy curved hero banner with custom vector keychain art
 * - Account avatar row with direct gallery photo picker
 * - Green-accented "Personal details" card -> Default Identity selector
 * - Blue-accented "Payment details" card -> Default Payment Card selector
 */
@Composable
fun ProfileScreen(
    email: String,
    items: List<VaultItem>,
    defaultIdentityId: String?,
    defaultCardId: String?,
    onSelectDefaultIdentity: (String?) -> Unit,
    onSelectDefaultCard: (String?) -> Unit,
    onUpdateAccountAvatar: (String?) -> Unit,
    onCreateItemOfType: (CipherType) -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentAvatar = LocalAccountAvatar.current

    val identities = remember(items) { items.filter { !it.isDeleted && it.type == CipherType.IDENTITY } }
    val cards = remember(items) { items.filter { !it.isDeleted && it.type == CipherType.CARD } }
    val selectedIdentity = remember(identities, defaultIdentityId) {
        identities.firstOrNull { it.id == defaultIdentityId }
    }
    val selectedCard = remember(cards, defaultCardId) {
        cards.firstOrNull { it.id == defaultCardId }
    }

    var pickerMode by remember { mutableStateOf<CipherType?>(null) }
    var showAvatarOptions by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = IconLoader.compressPickedImageToBase64(context, uri, targetPx = 160, quality = 82)
                if (b64 != null) {
                    onUpdateAccountAvatar(b64)
                    onShowMessage(if (s.isVi) "Đã cập nhật ảnh đại diện!" else "Profile avatar updated!")
                } else {
                    onShowMessage(if (s.isVi) "Không thể đọc ảnh đã chọn." else "Could not process selected photo.")
                }
            }
        }
    }

    val ownerName = remember(email) {
        val local = email.substringBefore("@").trim()
        if (local.isNotBlank()) local.replaceFirstChar { it.uppercaseChar() } else "OpenVault"
    }

    val heroBg = if (c.isDark) Color(0xFF151E34) else Color(0xFFE7F0FE)
    val hPad = if (fold.isNarrowCover) 14.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Curved Midnight-Navy Hero Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(heroBg),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textPrimary)
                            }
                            Text(
                                text = s.profile,
                                color = c.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = hPad, end = hPad, top = 6.dp, bottom = 30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = s.profileHeroTitle,
                                    color = c.textPrimary,
                                    fontSize = if (fold.isNarrowCover) 19.sp else 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = if (fold.isNarrowCover) 24.sp else 28.sp,
                                )
                                Text(
                                    text = s.profileHeroDesc,
                                    color = c.textSecondary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                )
                            }

                            ProfileKeychainArtwork(
                                modifier = Modifier.size(if (fold.isNarrowCover) 114.dp else 136.dp),
                            )
                        }
                    }

                    // Smooth curved transition into sheetBackground
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .align(Alignment.BottomCenter),
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            quadraticTo(size.width / 2f, size.height * 1.9f, size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = c.sheetBackground)
                    }
                }

                // Body content constrained cleanly for Z Fold 3
                Column(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .padding(horizontal = hPad, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Account Identity Row with Avatar photo picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (currentAvatar != null) {
                                    showAvatarOptions = true
                                } else {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OnePassAccountAvatar(email = email, size = 42.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ownerName,
                                color = c.textPrimary,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (email.isNotBlank()) {
                                Text(
                                    text = email,
                                    color = c.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Text(s.changeAvatar, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Card 1: Personal Details (Green top stripe)
                    ProfileAccentCard(
                        topAccent = Color(0xFF16A34A),
                        title = s.personalDetailsTitle,
                        description = s.personalDetailsDesc,
                        heroIcon = Icons.Rounded.AccountBalanceWallet,
                        heroTint = Color(0xFF4F8A70),
                        rowBadgeIcon = Icons.Rounded.Badge,
                        rowBadgeTint = Color(0xFF042F1A),
                        rowBadgeBg = Color(0xFF4ADE80),
                        rowLabel = s.defaultIdentity,
                        actionText = selectedIdentity?.name ?: s.select,
                        onClick = { pickerMode = CipherType.IDENTITY },
                    )

                    // Card 2: Payment Details (Blue top stripe)
                    ProfileAccentCard(
                        topAccent = Color(0xFF0C74F2),
                        title = s.paymentDetailsTitle,
                        description = s.paymentDetailsDesc,
                        heroIcon = Icons.Rounded.AccountBalanceWallet,
                        heroTint = Color(0xFF3E7CB1),
                        rowBadgeIcon = Icons.Rounded.CreditCard,
                        rowBadgeTint = Color.White,
                        rowBadgeBg = Color(0xFF0284C7),
                        rowLabel = s.defaultPayment,
                        actionText = selectedCard?.name ?: s.select,
                        onClick = { pickerMode = CipherType.CARD },
                    )

                    Text(
                        text = s.profileFooterTitle,
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }

    if (pickerMode != null) {
        val isIdentity = pickerMode == CipherType.IDENTITY
        val candidates = if (isIdentity) identities else cards
        AlertDialog(
            onDismissRequest = { pickerMode = null },
            containerColor = c.surfaceElevated,
            title = {
                Text(
                    text = if (isIdentity) s.defaultIdentity else s.defaultPayment,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (candidates.isEmpty()) {
                        Text(
                            text = if (s.isVi) "Bạn chưa có mục nào thuộc loại này." else "You haven't created any items of this type yet.",
                            color = c.textSecondary,
                            fontSize = 14.sp,
                        )
                    } else {
                        candidates.forEach { candidate ->
                            val isSelected = candidate.id == (if (isIdentity) defaultIdentityId else defaultCardId)
                            OnePassItemRow(
                                item = candidate,
                                selected = isSelected,
                                onClick = {
                                    if (isIdentity) onSelectDefaultIdentity(candidate.id) else onSelectDefaultCard(candidate.id)
                                    pickerMode = null
                                },
                            )
                        }
                    }

                    HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val target = pickerMode!!
                                pickerMode = null
                                onCreateItemOfType(target)
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(19.dp))
                        Text(
                            text = if (isIdentity) s.createIdentity else s.createCard,
                            color = c.accent,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                if ((isIdentity && defaultIdentityId != null) || (!isIdentity && defaultCardId != null)) {
                    TextButton(
                        onClick = {
                            if (isIdentity) onSelectDefaultIdentity(null) else onSelectDefaultCard(null)
                            pickerMode = null
                        },
                    ) {
                        Text(s.clearSelection, color = c.danger)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerMode = null }) {
                    Text(s.close, color = c.textSecondary)
                }
            },
        )
    }

    if (showAvatarOptions) {
        AlertDialog(
            onDismissRequest = { showAvatarOptions = false },
            containerColor = c.surfaceElevated,
            title = { Text(s.changeAvatar, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showAvatarOptions = false
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.chooseIconImage, color = c.accent, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showAvatarOptions = false
                            onUpdateAccountAvatar(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.removeAvatar, color = c.danger, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarOptions = false }) {
                    Text(s.cancel, color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun ProfileAccentCard(
    topAccent: Color,
    title: String,
    description: String,
    heroIcon: ImageVector,
    heroTint: Color,
    rowBadgeIcon: ImageVector,
    rowBadgeTint: Color,
    rowBadgeBg: Color,
    rowLabel: String,
    actionText: String,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    OnePassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = c.surface,
        borderColor = c.divider,
        padding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(topAccent),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = title,
                        color = c.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = description,
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = heroTint,
                    modifier = Modifier.size(54.dp),
                )
            }
            HorizontalDivider(color = c.divider, thickness = 0.8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryIconBadge(
                    icon = rowBadgeIcon,
                    tint = rowBadgeTint,
                    background = rowBadgeBg,
                    size = 30.dp,
                )
                Text(
                    text = rowLabel,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = actionText,
                    color = c.navActiveContent,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Clean original vector keychain artwork (ring + house tag + payment card + cyan person fob).
 */
@Composable
private fun ProfileKeychainArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Metallic keychain ring
        drawCircle(
            color = Color(0xFFD5DCE8),
            radius = w * 0.19f,
            center = Offset(w * 0.56f, h * 0.28f),
            style = Stroke(width = w * 0.045f),
        )

        // Pink house tag (left)
        rotate(degrees = -26f, pivot = Offset(w * 0.24f, h * 0.44f)) {
            val house = Path().apply {
                moveTo(w * 0.14f, h * 0.42f)
                lineTo(w * 0.26f, h * 0.28f)
                lineTo(w * 0.38f, h * 0.42f)
                lineTo(w * 0.34f, h * 0.56f)
                lineTo(w * 0.18f, h * 0.56f)
                close()
            }
            drawPath(house, color = Color(0xFFF472B6))
        }

        // Amber identity card behind
        rotate(degrees = -18f, pivot = Offset(w * 0.42f, h * 0.65f)) {
            drawRoundRect(
                color = Color(0xFFFBBF24),
                topLeft = Offset(w * 0.26f, h * 0.46f),
                size = Size(w * 0.28f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            )
        }

        // Cobalt credit card center
        rotate(degrees = 8f, pivot = Offset(w * 0.54f, h * 0.67f)) {
            drawRoundRect(
                color = Color(0xFF3B82F6),
                topLeft = Offset(w * 0.40f, h * 0.46f),
                size = Size(w * 0.29f, h * 0.42f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            )
            // Magnetic stripe
            drawRect(
                color = Color(0xFF1D4ED8),
                topLeft = Offset(w * 0.45f, h * 0.46f),
                size = Size(w * 0.06f, h * 0.42f),
            )
            // Silver chip
            drawRoundRect(
                color = Color(0xFFDBEAFE),
                topLeft = Offset(w * 0.56f, h * 0.72f),
                size = Size(w * 0.08f, h * 0.08f),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f),
            )
        }

        // Cyan person fob (right)
        rotate(degrees = 20f, pivot = Offset(w * 0.78f, h * 0.56f)) {
            drawRoundRect(
                color = Color(0xFF22D3EE),
                topLeft = Offset(w * 0.64f, h * 0.40f),
                size = Size(w * 0.30f, h * 0.34f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.045f,
                center = Offset(w * 0.79f, h * 0.53f),
            )
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.70f, h * 0.59f),
                size = Size(w * 0.18f, h * 0.13f),
            )
        }
    }
}
