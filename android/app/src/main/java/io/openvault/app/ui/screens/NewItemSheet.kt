package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.openvault.app.ui.theme.LocalOpenVaultColors

private data class NewItemTileSpec(
    val title: String,
    val icon: ImageVector,
    val bgColor: Color,
    val onClick: () -> Unit,
)

/**
 * Authentic 1Password 8 "New Item" modal sheet (matching Image 1-right!):
 * - Centered "Mục mới" + right blue "Xong"
 * - Search bar "Bạn muốn thêm gì?"
 * - Clean 2-column Bento grid with colored square icons + top-right '+'
 */
@Composable
fun NewItemSheet(
    onCreateType: (CipherType) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var query by remember { mutableStateOf("") }

    val tiles = remember {
        listOf(
            NewItemTileSpec("Đăng nhập", Icons.Rounded.Lock, Color(0xFF0D9488)) { onCreateType(CipherType.LOGIN) },
            NewItemTileSpec("Ghi chú bảo mật", Icons.Rounded.Description, Color(0xFFD97706)) { onCreateType(CipherType.SECURE_NOTE) },
            NewItemTileSpec("Thẻ tín dụng", Icons.Rounded.CreditCard, Color(0xFF0284C7)) { onCreateType(CipherType.CARD) },
            NewItemTileSpec("Danh tính", Icons.Rounded.Person, Color(0xFF059669)) { onCreateType(CipherType.IDENTITY) },
            NewItemTileSpec("Tạo mật khẩu", Icons.Rounded.Key, Color(0xFF16A34A)) { onOpenGenerator() },
            NewItemTileSpec("Quét mã QR 2FA", Icons.Rounded.QrCodeScanner, Color(0xFF6366F1)) { onOpenQrScanner() },
        )
    }

    val visibleTiles = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) tiles else tiles.filter { it.title.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header: Centered title + Right "Xong"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.size(48.dp))
            Text(
                text = "Mục mới",
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDismiss) {
                Text("Xong", color = c.accent, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        OnePassSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Bạn muốn thêm gì?",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            visibleTiles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { tile ->
                        OnePassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            padding = PaddingValues(14.dp),
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
                                        tint = Color.White,
                                        background = tile.bgColor,
                                        size = 34.dp,
                                    )
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = c.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = tile.title,
                                    color = c.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
