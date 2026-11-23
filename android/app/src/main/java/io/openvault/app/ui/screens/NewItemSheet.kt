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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.ui.components.CategoryIconBadge
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings

private data class NewItemTileSpec(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val bgColor: Color,
    val onClick: () -> Unit,
)

/**
 * Authentic 1Password 8 Android "Add items" modal sheet:
 * - Elevated #1C1D1F canvas, ✕ Close + bold "Thêm mục mới"
 * - Adaptive 2-column Bento grid on Cover/Phone, 3-column Bento grid on unfolded Galaxy Z Fold 3
 */
@Composable
fun NewItemSheet(
    onCreateType: (CipherType) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var query by remember { mutableStateOf("") }

    val tiles = remember(s) {
        listOf(
            NewItemTileSpec(s.logins, Icons.Rounded.Lock, Color(0xFF042F2E), Color(0xFF5EEAD4)) { onCreateType(CipherType.LOGIN) },
            NewItemTileSpec(s.secureNotes, Icons.Rounded.Description, Color(0xFF2E1E04), Color(0xFFFBBF24)) { onCreateType(CipherType.SECURE_NOTE) },
            NewItemTileSpec(s.creditCards, Icons.Rounded.CreditCard, Color.White, Color(0xFF0284C7)) { onCreateType(CipherType.CARD) },
            NewItemTileSpec(s.identities, Icons.Rounded.Person, Color(0xFF042F1A), Color(0xFF4ADE80)) { onCreateType(CipherType.IDENTITY) },
            NewItemTileSpec(s.passwordGenerator, Icons.Rounded.Key, Color.White, Color(0xFF16A34A)) { onOpenGenerator() },
            NewItemTileSpec(s.scanQr2fa.removeSuffix("…"), Icons.Rounded.QrCodeScanner, Color.White, Color(0xFF6366F1)) { onOpenQrScanner() },
        )
    }

    val visibleTiles = remember(query, tiles) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) tiles else tiles.filter { it.title.lowercase().contains(q) }
    }

    val columns = if (fold.isTwoPane) 3 else 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header: ←  New Item (matching Image 20)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary)
            }
            Text(
                text = s.newItem,
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OnePassSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = s.whatToAddPlaceholder,
                )

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
                                padding = PaddingValues(15.dp),
                                onClick = tile.onClick,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(18.dp),
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
                                            Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    Text(
                                        text = tile.title,
                                        color = c.textPrimary,
                                        fontSize = if (fold.isNarrowCover) 14.sp else 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
