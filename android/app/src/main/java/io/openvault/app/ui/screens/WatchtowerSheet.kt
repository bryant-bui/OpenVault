package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.WatchtowerReport
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassAddButton
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.theme.LocalOpenVaultColors

/**
 * 1Password 8 Mobile Watchtower Tab (matching Image 1-middle):
 * - Signature Green -> Turquoise -> Blue semi-circular 0..1000 Arc Gauge
 * - "Overall Password Strength" segmented color bar
 * - Clean alert cards with bold issue counts (Weak, Reused, Missing 2FA)
 */
@Composable
fun WatchtowerTabScreen(
    email: String,
    report: WatchtowerReport,
    onOpenSettings: () -> Unit,
    onOpenNewItem: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var query by remember { mutableStateOf("") }
    var expandWeak by remember { mutableStateOf(false) }
    var expandReused by remember { mutableStateOf(false) }
    var expandMissing2fa by remember { mutableStateOf(false) }

    val score1000 = (report.score * 10).coerceIn(0, 1000)
    val ratingLabel = when {
        score1000 >= 850 -> "TUYỆT VỜI"
        score1000 >= 700 -> "RẤT TỐT"
        score1000 >= 500 -> "KHÁ TỐT"
        else -> "CẦN CẢI THIỆN"
    }

    val strongCount = (report.totalLogins - report.weakItems.size).coerceAtLeast(0)
    val strongRatio = if (report.totalLogins > 0) strongCount.toFloat() / report.totalLogins else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OnePassAccountAvatar(email = email, onClick = onOpenSettings)
            OnePassAddButton(onClick = onOpenNewItem)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Watchtower",
                        color = c.textPrimary,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.7).sp,
                    )
                    OnePassSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Tìm cảnh báo bảo mật",
                    )
                }
            }

            // Iconic 1Password Semi-Circular Arc Gauge (0..1000)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(220.dp, 180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(204.dp)) {
                            val strokeW = 24.dp.toPx()
                            val inset = strokeW / 2f
                            val arcSize = Size(size.width - strokeW, size.height - strokeW)
                            val topLeft = Offset(inset, inset)

                            // Track arc
                            drawArc(
                                color = if (c.isDark) Color(0xFF23252A) else Color(0xFFE2E6EE),
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )

                            // Active Green -> Cyan -> Blue gradient arc
                            val sweep = (240f * (score1000 / 1000f)).coerceAtLeast(8f)
                            drawArc(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF22C55E),
                                        Color(0xFF2DD4BF),
                                        Color(0xFF38BDF8),
                                        Color(0xFF3B8BFF),
                                    ),
                                ),
                                startAngle = 150f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )

                            // Small centered upward triangle indicator (1P signature detail)
                            val triY = size.height * 0.27f
                            val tri = Path().apply {
                                moveTo(center.x, triY - 7.dp.toPx())
                                lineTo(center.x - 6.dp.toPx(), triY + 4.dp.toPx())
                                lineTo(center.x + 6.dp.toPx(), triY + 4.dp.toPx())
                                close()
                            }
                            drawPath(tri, color = Color(0xFF38BDF8))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            Text(
                                text = "$score1000",
                                color = c.textPrimary,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                            )
                            Text(
                                text = ratingLabel,
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                            )
                        }
                    }

                    Text(
                        text = "Điểm bảo mật được phân tích hoàn toàn cục bộ trên thiết bị từ ${report.totalLogins} tài khoản đăng nhập. Xử lý các cảnh báo bên dưới để nâng cao điểm an toàn.",
                        color = c.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // Overall Password Strength Card (exact segmented color bar from Image 1-middle!)
            item {
                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(15.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Độ mạnh mật khẩu tổng thể",
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${(strongRatio * 100).toInt()}% mạnh",
                                color = c.success,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            val strongWeight = (strongRatio * 0.72f).coerceAtLeast(0.15f)
                            val reusedWeight = if (report.reusedItems.isNotEmpty()) 0.14f else 0.06f
                            val weakWeight = if (report.weakItems.isNotEmpty()) 0.14f else 0.04f
                            Box(
                                modifier = Modifier
                                    .weight(strongWeight)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                                    .background(Color(0xFF22C55E)),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.10f)
                                    .fillMaxSize()
                                    .background(Color(0xFF84CC16)),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(reusedWeight)
                                    .fillMaxSize()
                                    .background(Color(0xFFFACC15)),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(weakWeight)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                                    .background(Color(0xFFEF4444)),
                            )
                        }
                    }
                }
            }

            // Issue Cards
            item {
                WatchtowerAlertCard(
                    title = "Mật khẩu yếu",
                    subtitle = "Nên đổi sang mật khẩu ngẫu nhiên tối thiểu 16 ký tự",
                    count = report.weakItems.size,
                    accentColor = c.danger,
                    tintedBg = if (report.weakItems.isNotEmpty() && c.isDark) Color(0xFF261719) else null,
                    items = filterItems(report.weakItems, query),
                    expanded = expandWeak,
                    onToggle = { expandWeak = !expandWeak },
                    onOpenItem = onOpenItem,
                )
            }

            item {
                WatchtowerAlertCard(
                    title = "Mật khẩu dùng lại",
                    subtitle = "Tránh dùng chung mật khẩu giữa nhiều dịch vụ",
                    count = report.reusedItems.size,
                    accentColor = c.warning,
                    tintedBg = if (report.reusedItems.isNotEmpty() && c.isDark) Color(0xFF252014) else null,
                    items = filterItems(report.reusedItems, query),
                    expanded = expandReused,
                    onToggle = { expandReused = !expandReused },
                    onOpenItem = onOpenItem,
                )
            }

            item {
                WatchtowerAlertCard(
                    title = "Tài khoản chưa bật 2FA (TOTP)",
                    subtitle = "Bật xác thực 2 bước để bảo vệ ngay cả khi lộ mật khẩu",
                    count = report.missingTotpItems.size,
                    accentColor = c.accent,
                    tintedBg = null,
                    items = filterItems(report.missingTotpItems, query).take(25),
                    expanded = expandMissing2fa,
                    onToggle = { expandMissing2fa = !expandMissing2fa },
                    onOpenItem = onOpenItem,
                )
            }
        }
    }
}

private fun filterItems(items: List<VaultItem>, query: String): List<VaultItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return items
    return items.filter {
        it.name.lowercase().contains(q) || it.login?.username?.lowercase()?.contains(q) == true
    }
}

@Composable
private fun WatchtowerAlertCard(
    title: String,
    subtitle: String,
    count: Int,
    accentColor: Color,
    tintedBg: Color?,
    items: List<VaultItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    OnePassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = tintedBg,
        padding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = items.isNotEmpty(), onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "$count",
                    color = if (count > 0) accentColor else c.success,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        color = c.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (count == 0) "Tuyệt vời! Không phát hiện vấn đề nào." else subtitle,
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                if (items.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = c.textSecondary,
                    )
                }
            }

            AnimatedVisibility(visible = expanded && items.isNotEmpty()) {
                Column {
                    items.forEach { item ->
                        HorizontalDivider(
                            color = c.divider,
                            thickness = 0.7.dp,
                            modifier = Modifier.padding(start = 62.dp),
                        )
                        OnePassItemRow(item = item, onClick = { onOpenItem(item) })
                    }
                }
            }
        }
    }
}
