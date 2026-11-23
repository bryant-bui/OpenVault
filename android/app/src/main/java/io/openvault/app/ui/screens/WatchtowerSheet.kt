package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchModeView
import io.openvault.app.ui.components.OnePassTopSearchBar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WatchtowerTabScreen(
    email: String,
    report: WatchtowerReport,
    checkWeak: Boolean = true,
    checkReused: Boolean = true,
    checkTwoFactor: Boolean = true,
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
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var expandWeak by remember { mutableStateOf(false) }
    var expandReused by remember { mutableStateOf(false) }
    var expandMissing2fa by remember { mutableStateOf(false) }
    var animNonce by remember { mutableIntStateOf(0) }

    val targetScore = (report.score * 10).coerceIn(0, 1000)

    // Smooth animated score & arc sweep (0 -> targetScore)
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(targetScore, animNonce) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = (targetScore / 1000f).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        )
    }

    val displayedScore = (animatedProgress.value * 1000f).roundToInt()
    val ratingLabel = when {
        targetScore >= 850 -> s.ratingFantastic
        targetScore >= 700 -> s.ratingVeryGood
        targetScore >= 500 -> s.ratingGood
        else -> s.ratingNeedsWork
    }

    val strongCount = (report.totalLogins - report.weakItems.size).coerceAtLeast(0)
    val strongRatio = if (report.totalLogins > 0) strongCount.toFloat() / report.totalLogins else 1f
    val bottomInset = if (fold.isTwoPane) 28.dp else 108.dp

    val allFlagged = remember(report) {
        (report.weakItems + report.reusedItems + report.missingTotpItems).distinctBy { it.id }
    }

    if (searchActive) {
        OnePassSearchModeView(
            email = email,
            query = query,
            onQueryChange = { query = it },
            placeholder = s.searchAlerts,
            results = filterItems(allFlagged, query),
            selectedItemId = selectedItemId,
            onSelectItem = {
                searchActive = false
                query = ""
                onOpenItem(it)
            },
            onCloseSearch = {
                searchActive = false
                query = ""
            },
            onOpenSettings = onOpenSettings,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onSync = onSync,
            onLock = onLock,
            isSyncing = isSyncing,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        OnePassTopSearchBar(
            email = email,
            query = "",
            onQueryChange = { },
            placeholder = if (fold.isNarrowCover) s.searchShort else s.searchAlerts,
            isSyncing = isSyncing,
            onSearchActivated = { searchActive = true },
            onSync = onSync,
            onOpenGenerator = onOpenGenerator,
            onOpenQrScanner = onOpenQrScanner,
            onOpenSettings = onOpenSettings,
            onLock = onLock,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = fold.horizontalPadding,
                    end = fold.horizontalPadding,
                    top = 8.dp,
                    bottom = bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Geometrically Perfect Circular Semicircular Gauge with Animated Sweep & Counter (Requirement #12!)
                item {
                    val gaugeDiameter = if (fold.isNarrowCover) 212.dp else 236.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // PERFECT SQUARE box prevents oval distortion!
                        Box(
                            modifier = Modifier.size(gaugeDiameter),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeW = 22.dp.toPx()
                                val diameter = size.minDimension - strokeW
                                val topLeft = Offset(
                                    x = (size.width - diameter) / 2f,
                                    y = (size.height - diameter) / 2f,
                                )
                                val arcSize = Size(diameter, diameter)
                                val startDeg = 150f
                                val totalSweep = 240f

                                // Background track arc (perfect circle!)
                                drawArc(
                                    color = if (c.isDark) Color(0xFF1E2024) else Color(0xFFE2E6EE),
                                    startAngle = startDeg,
                                    sweepAngle = totalSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                )

                                // Animated active gradient arc (Green -> Mint -> Cyan -> Blue)
                                val activeSweep = (totalSweep * animatedProgress.value).coerceAtLeast(if (targetScore > 0) 6f else 0f)
                                if (activeSweep > 0f) {
                                    drawArc(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF22C55E),
                                                Color(0xFF2DD4BF),
                                                Color(0xFF38BDF8),
                                                Color(0xFF0C74F2),
                                            ),
                                            startX = topLeft.x,
                                            endX = topLeft.x + diameter,
                                        ),
                                        startAngle = startDeg,
                                        sweepAngle = activeSweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                    )
                                }

                                // Crisp centered radial tick marks along exact circular centerline
                                val ringRadius = diameter / 2f
                                val tickInner = ringRadius - strokeW * 0.28f
                                val tickOuter = ringRadius + strokeW * 0.28f
                                val tickCount = 24
                                val tickColor = if (c.isDark) Color(0x4D000000) else Color(0x4DFFFFFF)
                                for (i in 1 until tickCount) {
                                    val deg = startDeg + (totalSweep * i / tickCount)
                                    val rad = Math.toRadians(deg.toDouble())
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(
                                            x = center.x + (tickInner * cos(rad)).toFloat(),
                                            y = center.y + (tickInner * sin(rad)).toFloat(),
                                        ),
                                        end = Offset(
                                            x = center.x + (tickOuter * cos(rad)).toFloat(),
                                            y = center.y + (tickOuter * sin(rad)).toFloat(),
                                        ),
                                        strokeWidth = 1.6.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }

                                // Crisp centered upward indicator triangle above score
                                val triY = center.y - ringRadius * 0.44f
                                val tri = Path().apply {
                                    moveTo(center.x, triY - 6.dp.toPx())
                                    lineTo(center.x - 6.dp.toPx(), triY + 4.dp.toPx())
                                    lineTo(center.x + 6.dp.toPx(), triY + 4.dp.toPx())
                                    close()
                                }
                                drawPath(tri, color = Color(0xFF38BDF8))
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(
                                    text = "$displayedScore",
                                    color = c.textPrimary,
                                    fontSize = if (fold.isNarrowCover) 42.sp else 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp,
                                )
                                Text(
                                    text = ratingLabel,
                                    color = c.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.3.sp,
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                animNonce++
                                onSync()
                            },
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
                                    text = if (isSyncing) s.syncing else s.rescanAndSync,
                                    color = c.textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        Text(
                            text = String.format(s.watchtowerSummary, report.totalLogins),
                            color = c.textSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
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
                                    text = s.overallPasswordStrength,
                                    color = c.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${(strongRatio * 100).toInt()}${s.strongSuffix}",
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

                if (checkWeak) {
                    item {
                        WatchtowerAlertCard(
                            title = s.weakPasswords,
                            subtitle = s.weakPasswordsSub,
                            emptyText = s.noIssuesFound,
                            count = report.weakItems.size,
                            accentColor = c.danger,
                            tintedBg = if (report.weakItems.isNotEmpty() && c.isDark) Color(0xFF231517) else null,
                            items = report.weakItems,
                            selectedItemId = selectedItemId,
                            expanded = expandWeak,
                            onToggle = { expandWeak = !expandWeak },
                            onOpenItem = onOpenItem,
                        )
                    }
                }

                if (checkReused) {
                    item {
                        WatchtowerAlertCard(
                            title = s.reusedPasswords,
                            subtitle = s.reusedPasswordsSub,
                            emptyText = s.noIssuesFound,
                            count = report.reusedItems.size,
                            accentColor = c.warning,
                            tintedBg = if (report.reusedItems.isNotEmpty() && c.isDark) Color(0xFF221D12) else null,
                            items = report.reusedItems,
                            selectedItemId = selectedItemId,
                            expanded = expandReused,
                            onToggle = { expandReused = !expandReused },
                            onOpenItem = onOpenItem,
                        )
                    }
                }

                if (checkTwoFactor) {
                    item {
                        WatchtowerAlertCard(
                            title = s.missing2fa,
                            subtitle = s.missing2faSub,
                            emptyText = s.noIssuesFound,
                            count = report.missingTotpItems.size,
                            accentColor = c.accent,
                            tintedBg = null,
                            items = report.missingTotpItems.take(30),
                            selectedItemId = selectedItemId,
                            expanded = expandMissing2fa,
                            onToggle = { expandMissing2fa = !expandMissing2fa },
                            onOpenItem = onOpenItem,
                        )
                    }
                }
            }
        }
    }
}

private fun filterItems(items: List<VaultItem>, query: String): List<VaultItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return items.filter {
        it.name.lowercase().contains(q) || it.login?.username?.lowercase()?.contains(q) == true
    }
}

@Composable
private fun WatchtowerAlertCard(
    title: String,
    subtitle: String,
    emptyText: String,
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
                        text = if (count == 0) emptyText else subtitle,
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
