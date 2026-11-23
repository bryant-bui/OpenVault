package io.openvault.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.WatchtowerReport
import io.openvault.app.ui.components.BrandMonogramAvatar
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.theme.LocalOpenVaultColors

@Composable
fun WatchtowerScreen(
    report: WatchtowerReport,
    onBack: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val scoreColor = when {
        report.score >= 85 -> c.success
        report.score >= 60 -> c.warning
        else -> c.danger
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại", tint = c.textPrimary)
                }
                Text(
                    text = "Watchtower — Sức khỏe bảo mật",
                    color = c.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Score Gauge Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevated = true,
                    padding = PaddingValues(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sw = 8.dp.toPx()
                                val arcSize = Size(size.width - sw, size.height - sw)
                                val tl = Offset(sw / 2f, sw / 2f)
                                drawArc(
                                    color = scoreColor.copy(alpha = 0.2f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = tl,
                                    size = arcSize,
                                    style = Stroke(width = sw),
                                )
                                drawArc(
                                    color = scoreColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * (report.score / 100f),
                                    useCenter = false,
                                    topLeft = tl,
                                    size = arcSize,
                                    style = Stroke(width = sw, cap = StrokeCap.Round),
                                )
                            }
                            Text(
                                text = "${report.score}",
                                color = scoreColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = when {
                                    report.score >= 85 -> "Kho mật khẩu rất an toàn"
                                    report.score >= 60 -> "Cần cải thiện vài tài khoản"
                                    else -> "Cần tăng cường bảo mật ngay"
                                },
                                color = c.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Đang phân tích ${report.totalLogins} tài khoản đăng nhập hoàn toàn cục bộ trên thiết bị.",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                WatchtowerSection(
                    title = "Mật khẩu yếu (${report.weakItems.size})",
                    accent = c.danger,
                    items = report.weakItems,
                    emptyText = "Tuyệt vời! Không có mật khẩu yếu.",
                    onOpenItem = onOpenItem,
                )

                WatchtowerSection(
                    title = "Mật khẩu dùng lại (${report.reusedItems.size})",
                    accent = c.warning,
                    items = report.reusedItems,
                    emptyText = "Mọi tài khoản đều dùng mật khẩu riêng biệt.",
                    onOpenItem = onOpenItem,
                )

                WatchtowerSection(
                    title = "Chưa bật 2FA TOTP (${report.missingTotpItems.size})",
                    accent = c.accent,
                    items = report.missingTotpItems.take(15),
                    emptyText = "Tất cả tài khoản đều đã có mã 2FA.",
                    onOpenItem = onOpenItem,
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun WatchtowerSection(
    title: String,
    accent: Color,
    items: List<VaultItem>,
    emptyText: String,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (items.isEmpty()) {
                Text(text = emptyText, color = c.textSecondary, fontSize = 13.5.sp)
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(item) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BrandMonogramAvatar(title = item.name, type = item.type, size = 36.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = c.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.primarySubtitle, color = c.textSecondary, fontSize = 12.5.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textMuted)
                    }
                }
            }
        }
    }
}
