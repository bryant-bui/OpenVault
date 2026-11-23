package io.openvault.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * Adaptive layout metrics specifically tuned for foldable devices like Samsung Galaxy Z Fold 3:
 * - Outer cover screen (SM-F926B folded): ~320dp wide -> compact paddings, crisp single-row top bar, scaled PIN pad
 * - Inner main display (SM-F926B unfolded): ~674-756dp wide -> Left Navigation Rail + Two-Pane Master-Detail layout
 * - Book / half-opened posture: detects physical vertical hinge via Jetpack WindowManager so content never hides in crease
 */
@Immutable
data class FoldableLayoutInfo(
    val windowWidthDp: Dp = 400.dp,
    val windowHeightDp: Dp = 800.dp,
    val isNarrowCover: Boolean = false,
    val isTwoPane: Boolean = false,
    val isBookPosture: Boolean = false,
    val hasVerticalHinge: Boolean = false,
    val hingeWidthDp: Dp = 0.dp,
    val leftPaneWeight: Float = 0.43f,
    val horizontalPadding: Dp = 16.dp,
)

val LocalFoldableLayout = staticCompositionLocalOf { FoldableLayoutInfo() }

@Composable
fun ProvideFoldableLayout(
    activity: Activity?,
    content: @Composable () -> Unit,
) {
    var foldingFeature by remember { mutableStateOf<FoldingFeature?>(null) }

    LaunchedEffect(activity) {
        if (activity != null) {
            runCatching {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect { layoutInfo ->
                        foldingFeature = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                    }
            }
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthDp = maxWidth
        val heightDp = maxHeight
        val isNarrowCover = widthDp < 360.dp
        val isTwoPane = widthDp >= 580.dp

        val verticalHinge = foldingFeature?.takeIf {
            it.orientation == FoldingFeature.Orientation.VERTICAL
        }
        val isBookPosture = verticalHinge?.state == FoldingFeature.State.HALF_OPENED
        val hingeWidthDp = if (verticalHinge != null && verticalHinge.isSeparating) {
            with(density) { verticalHinge.bounds.width().toDp() }.coerceAtLeast(12.dp)
        } else {
            0.dp
        }

        // Compute ideal left-pane ratio respecting the 76dp Navigation Rail + physical fold crease
        val leftWeight = remember(widthDp, verticalHinge, density) {
            if (verticalHinge != null && widthDp >= 580.dp) {
                val railDp = 76f
                val availableW = (widthDp.value - railDp).coerceAtLeast(300f)
                val hingeLeftDp = with(density) { verticalHinge.bounds.left.toDp().value }
                val relativeSplit = ((hingeLeftDp - railDp) / availableW).coerceIn(0.36f, 0.50f)
                relativeSplit
            } else {
                0.43f
            }
        }

        val info = remember(widthDp, heightDp, isNarrowCover, isTwoPane, isBookPosture, verticalHinge, hingeWidthDp, leftWeight) {
            FoldableLayoutInfo(
                windowWidthDp = widthDp,
                windowHeightDp = heightDp,
                isNarrowCover = isNarrowCover,
                isTwoPane = isTwoPane,
                isBookPosture = isBookPosture,
                hasVerticalHinge = verticalHinge != null,
                hingeWidthDp = hingeWidthDp,
                leftPaneWeight = leftWeight,
                horizontalPadding = when {
                    isNarrowCover -> 12.dp
                    isTwoPane -> 18.dp
                    else -> 16.dp
                },
            )
        }

        CompositionLocalProvider(LocalFoldableLayout provides info) {
            content()
        }
    }
}
