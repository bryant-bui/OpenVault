package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.WatchtowerReport
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassTopSearchBar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic 1Password 8 Android Watchtower Screen:
 * - Single-row Midnight-Navy Search Pill + Account Dropdown
 * - Thick semicircular 0..1000 gauge with radial tick marks (matching real Android Watchtower capture)
 * - Outlined "Kiểm tra & đồng bộ" pill button right below gauge
 * - Overall Password Strength bar + expandable alert cards (supports Z Fold 3 selected highlight)
 */
@Composable
fun WatchtowerTabScreen(
    email: String,
    report: WatchtowerReport,
    isSyncing: Boolean = false,
    selectedItemId: String? = null,
    onOpenSettings: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onSync: () -> Unit,
    onLock: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
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
    val bottomInset = if (fold.isTwoPane) 28.dp else 108.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        OnePassTopSearchBar(
            email = email,
            query = query,
            onQueryChange = { query = it },
            placeholder = if (fold.isNarrowCover) "Tìm cảnh báo..." else "Tìm cảnh báo bảo mật",
            isSyncing = isSyncing,
            onSync = onSync,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = fold.horizontalPadding,
                end = fold.horizontalPadding,
                top = 8.dp,
                bottom = bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Iconic 1Password Android Semicircular Tick-Mark Gauge (0..1000)
            item {
                val gaugeSize = if (fold.isNarrowCover) 204.dp else 228.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(gaugeSize, gaugeSize * 0.84f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(gaugeSize)) {
                            val strokeW = 24.dp.toPx()
                            val inset = strokeW / 2f
                            val arcSize = Size(size.width - strokeW, size.height - strokeW)
                            val topLeft = Offset(inset, inset)
                            val startDeg = 148f
                            val totalSweep = 244f

                            // Matte dark track arc
                            drawArc(
                                color = if (c.isDark) Color(0xFF1F2124) else Color(0xFFE2E6EE),
                                startAngle = startDeg,
                                sweepAngle = totalSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )

                            // Vivid Green -> Teal -> Cyan -> Blue progress sweep
                            val sweep = (totalSweep * (score1000 / 1000f)).coerceAtLeast(10f)
                            drawArc(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF22C55E),
                                        Color(0xFF2DD4BF),
                                        Color(0xFF38BDF8),
                                        Color(0xFF0C74F2),
                                    ),
                                ),
                                startAngle = startDeg,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )

                            // Signature 1Password Android subtle radial tick marks along the gauge
                            val tickCount = 24
                            val radiusOuter = (size.minDimension / 2f) - 4.dp.toPx()
                            val radiusInner = radiusOuter - 10.dp.toPx()
                            val tickColor = if (c.isDark) Color(0x44000000) else Color(0x44FFFFFF)
                            for (i in 1 until tickCount) {
                                val deg = startDeg + (totalSweep * i / tickCount)
                                val rad = Math.toRadians(deg.toDouble())
                                val start = Offset(
                                    x = center.x + (radiusInner * cos(rad)).toFloat(),
                                    y = center.y + (radiusInner * sin(rad)).toFloat(),
                                )
                                val end = Offset(
                                    x = center.x + (radiusOuter * cos(rad)).toFloat(),
                                    y = center.y + (radiusOuter * sin(rad)).toFloat(),
                                )
                                drawLine(
                                    color = tickColor,
                                    start = start,
                                    end = end,
                                    strokeWidth = 1.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            Text(
                                text = "$score1000",
                                color = c.textPrimary,
                                fontSize = if (fold.isNarrowCover) 40.sp else 46.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                            )
                            Text(
                                text = ratingLabel,
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                            )
                        }
                    }

                    // Outlined matte action button matching real 1Password Watchtower
                    Surface(
                        onClick = onSync,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        color = c.surface,
                        border = BorderStroke(1.dp, c.cardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    color = c.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = c.textPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = if (isSyncing) "Đang kiểm tra kho…" else "Đồng bộ & quét lại",
                                color = c.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Text(
                        text = "Điểm bảo mật được phân tích hoàn toàn cục bộ từ ${report.totalLogins} tài khoản đăng nhập. Xử lý các mục cảnh báo bên dưới để tăng mức an toàn.",
                        color = c.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            // Overall Password Strength Card
            item {
                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
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
                    tintedBg = if (report.weakItems.isNotEmpty() && c.isDark) Color(0xFF231517) else null,
                    items = filterItems(report.weakItems, query),
                    selectedItemId = selectedItemId,
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
                    tintedBg = if (report.reusedItems.isNotEmpty() && c.isDark) Color(0xFF221D12) else null,
                    items = filterItems(report.reusedItems, query),
                    selectedItemId = selectedItemId,
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
                    selectedItemId = selectedItemId,
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
    selectedItemId: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenItem: (VaultItem) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
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
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "$count",
                    color = if (count > 0) accentColor else c.success,
                    fontSize = 28.sp,
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
                        tint = c.accent,
                    )
                }
            }

            AnimatedVisibility(visible = expanded && items.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    items.forEach { item ->
                        OnePassItemRow(
                            item = item,
                            selected = fold.isTwoPane && item.id == selectedItemId,
                            onClick = { onOpenItem(item) },
                        )
                    }
                }
            }
        }
    }
}
