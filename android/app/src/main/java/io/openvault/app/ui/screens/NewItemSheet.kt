@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.SecureNoteCategory
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

private data class NewItemTileSpec(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val bgColor: Color,
    val onClick: () -> Unit,
)

/**
 * Floating bottom-anchored "Add Items" ModalBottomSheet matching Vault+ bottom sheets:
 * slides smoothly up from the bottom edge with the live Home/Items canvas dimmed behind.
 */
@Composable
fun NewItemSheet(
    onCreateType: (CipherType, Int) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    var query by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }

    fun dismissWith(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            runCatching { sheetState.hide() }
            action()
        }
    }

    val tiles = remember(s) {
        listOf(
            NewItemTileSpec(s.logins, Icons.Rounded.Lock, Color(0xFF042F2E), Color(0xFF5EEAD4)) { onCreateType(CipherType.LOGIN, 0) },
            NewItemTileSpec(s.categorySoftware, Icons.Rounded.Terminal, Color(0xFF075985), Color(0xFFBAE6FD)) {
                onCreateType(CipherType.SECURE_NOTE, SecureNoteCategory.SOFTWARE.typeCode)
            },
            NewItemTileSpec(s.categoryNotes, Icons.Rounded.Description, Color(0xFF2E1E04), Color(0xFFFBBF24)) {
                onCreateType(CipherType.SECURE_NOTE, SecureNoteCategory.NOTE.typeCode)
            },
            NewItemTileSpec(s.categoryPassport, Icons.Rounded.Public, Color(0xFF4C1D95), Color(0xFFDDD6FE)) {
                onCreateType(CipherType.SECURE_NOTE, SecureNoteCategory.PASSPORT.typeCode)
            },
            NewItemTileSpec(s.categoryIdCard, Icons.Rounded.Badge, Color(0xFF064E3B), Color(0xFFA7F3D0)) {
                onCreateType(CipherType.SECURE_NOTE, SecureNoteCategory.ID_CARD.typeCode)
            },
            NewItemTileSpec(s.creditCards, Icons.Rounded.CreditCard, Color.White, Color(0xFF0284C7)) { onCreateType(CipherType.CARD, 0) },
            NewItemTileSpec(s.identities, Icons.Rounded.Person, Color(0xFF042F1A), Color(0xFF4ADE80)) { onCreateType(CipherType.IDENTITY, 0) },
            NewItemTileSpec(s.passwordGenerator, Icons.Rounded.Key, Color.White, Color(0xFF16A34A)) { onOpenGenerator() },
            NewItemTileSpec(s.scanQr2fa.removeSuffix("…"), Icons.Rounded.QrCodeScanner, Color.White, Color(0xFF6366F1)) { onOpenQrScanner() },
        )
    }

    val visibleTiles = remember(query, tiles) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) tiles else tiles.filter { it.title.lowercase().contains(q) }
    }

    val columns = if (fold.isTwoPane) 3 else 2

    ModalBottomSheet(
        onDismissRequest = { if (!closing) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Floating Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.newItem,
                        color = c.textPrimary,
                        fontSize = 18.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = s.whatToAddPlaceholder,
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    onClick = { dismissWith(onDismiss) },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = s.close,
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.75f))

            // Scrollable Bento Grid Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = fold.horizontalPadding, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OnePassSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = s.whatToAddPlaceholder,
                    )

                    if (visibleTiles.isEmpty()) {
                        Text(
                            text = "${s.noMatchingItems} \"$query\"",
                            color = c.textSecondary,
                            fontSize = 14.5.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 24.dp),
                        )
                    } else {
                        visibleTiles.chunked(columns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowItems.forEach { tile ->
                                    OnePassCard(
                                        modifier = Modifier.weight(1f),
                                        cornerRadius = 16.dp,
                                        backgroundColor = c.surface,
                                        borderColor = c.cardBorder,
                                        padding = PaddingValues(14.dp),
                                        onClick = { dismissWith(tile.onClick) },
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                CategoryIconBadge(
                                                    icon = tile.icon,
                                                    tint = tile.iconTint,
                                                    background = tile.bgColor,
                                                    size = 36.dp,
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    tint = c.textSecondary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Text(
                                                text = tile.title,
                                                color = c.textPrimary,
                                                fontSize = if (fold.isNarrowCover) 14.5.sp else 15.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
