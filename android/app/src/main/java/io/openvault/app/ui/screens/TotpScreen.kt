package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.ui.components.BrandMonogramAvatar
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.TotpCountdownRing
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.delay

@Composable
fun TotpScreen(
    items: List<VaultItem>,
    onScanQrToAdd: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onCopyCode: (title: String, code: String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Trình xác thực 2FA",
                        color = c.textPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    )
                    Text(
                        text = "${totpItems.size} mã TOTP đồng bộ E2EE",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onScanQrToAdd)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = "Quét QR",
                        tint = c.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Quét QR",
                        color = c.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cornerRadius = 18.dp,
                padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(19.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Tìm mã 2FA…", color = c.textMuted, fontSize = 14.5.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = c.textPrimary, fontSize = 14.5.sp),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Xóa",
                            tint = c.textSecondary,
                            modifier = Modifier.size(18.dp).clickable { query = "" },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (totpItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 26.dp,
                        padding = PaddingValues(26.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = c.accent, modifier = Modifier.size(44.dp))
                            Text(
                                text = "Chưa có mã 2FA nào",
                                color = c.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Quét mã QR 2FA từ Google, GitHub, Cloudflare hoặc ngân hàng để đồng bộ tức thì với Chrome Extension.",
                                color = c.textSecondary,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(totpItems, key = { it.id }) { item ->
                        val secret = item.login?.totp.orEmpty()
                        val totp = remember(secret, nowSec) {
                            TotpEngine.generateFromInput(secret, nowSec)
                        }
                        if (totp != null) {
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 22.dp,
                                elevated = true,
                                padding = PaddingValues(16.dp),
                                onClick = { onCopyCode(item.name, totp.code) },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        BrandMonogramAvatar(title = item.name, type = item.type, size = 38.dp)
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
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TotpCountdownRing(totp = totp, size = 42.dp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = totp.formattedCode,
                                            color = if (totp.remainingSeconds <= 5) c.danger else c.textPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.5.sp,
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(c.accentSoft)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = "Sao chép",
                                                tint = c.accent,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Text("Chép mã", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick QR Scanner FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 98.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0A5CE6), c.accent, Color(0xFF6366F1))))
                .border(1.2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .clickable(onClick = onScanQrToAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = "Quét mã QR 2FA",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
