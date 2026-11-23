package io.openvault.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.components.TotpCountdownRing
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

@Composable
fun TotpScreen(
    items: List<VaultItem>,
    onBack: () -> Unit,
    onScanQrToAdd: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onCopyCode: (title: String, code: String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
    var query by remember { mutableStateOf("") }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val totpItems = remember(items, query) {
        val q = query.trim().lowercase()
        items.filter { !it.isDeleted && it.hasTotp }.filter { item ->
            if (q.isEmpty()) true
            else item.name.lowercase().contains(q) ||
                item.login?.username?.lowercase()?.contains(q) == true
        }
    }

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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại", tint = c.textPrimary)
                }
                Text(
                    text = "Mã xác thực 2FA",
                    color = c.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onScanQrToAdd) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Quét QR", tint = c.accent)
            }
        }
        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
            ) {
                OnePassSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Tìm mã 2FA",
                    modifier = Modifier.padding(horizontal = fold.horizontalPadding, vertical = 10.dp),
                )

                if (totpItems.isEmpty()) {
                    OnePassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = fold.horizontalPadding, vertical = 8.dp),
                        backgroundColor = c.surface,
                        padding = PaddingValues(24.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = c.accent, modifier = Modifier.size(40.dp))
                            Text("Chưa có mã 2FA nào", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Bấm biểu tượng Quét QR ở góc trên phải để thêm mã xác thực 2 bước mới.",
                                color = c.textSecondary,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = fold.horizontalPadding,
                            end = fold.horizontalPadding,
                            top = 4.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(totpItems, key = { it.id }) { item ->
                            val totp = remember(item.login?.totp, nowSec) {
                                item.login?.totp?.let { TotpEngine.generateFromInput(it, nowSec) }
                            }
                            if (totp != null) {
                                val dotFormatted = if (totp.code.length == 6) {
                                    "${totp.code.substring(0, 3)} · ${totp.code.substring(3)}"
                                } else {
                                    totp.formattedCode
                                }
                                OnePassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = c.surface,
                                    borderColor = c.cardBorder,
                                    padding = PaddingValues(15.dp),
                                    onClick = { onCopyCode(item.name, totp.code) },
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            ItemIconTile(title = item.name, type = item.type, size = 36.dp)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onOpenItem(item) },
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    color = c.textPrimary,
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = item.primarySubtitle,
                                                    color = c.textSecondary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            TotpCountdownRing(totp = totp, size = 38.dp)
                                        }

                                        HorizontalDivider(color = c.divider, thickness = 0.7.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = dotFormatted,
                                                color = if (totp.remainingSeconds <= 5) c.danger else c.textPrimary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 25.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Sao chép",
                                                tint = c.accent,
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
