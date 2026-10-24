package com.xuan.fitai.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature

internal data class SheetPane(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(1)
    val height get() = (bottom - top).coerceAtLeast(1)
}

/** Window coordinates; the visible height already excludes the software keyboard. */
internal fun sheetPane(width: Int, height: Int, hinge: SheetPane, vertical: Boolean, gap: Int): SheetPane {
    val w = width.coerceAtLeast(1); val h = height.coerceAtLeast(1)
    if (vertical) {
        val leftEnd = (hinge.left - gap).coerceIn(0, w)
        val rightStart = (hinge.right + gap).coerceIn(0, w)
        return if (leftEnd > w - rightStart) SheetPane(0, 0, leftEnd.coerceAtLeast(1), h)
        else SheetPane(rightStart.coerceAtMost(w - 1), 0, w, h)
    }
    val topEnd = (hinge.top - gap).coerceIn(0, h)
    val bottomStart = (hinge.bottom + gap).coerceIn(0, h)
    return if (topEnd > h - bottomStart) SheetPane(0, 0, w, topEnd.coerceAtLeast(1))
    else SheetPane(0, bottomStart.coerceAtMost(h - 1), w, h)
}

/** Keep Material's full-window scrim, gestures and animation; constrain the sheet to one pane. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    sheetMaxWidth: Dp = 500.dp,
    maxHeightFraction: Float = 0.8f,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val info = LocalAdaptiveInfo.current
    val fold = info.folds.firstOrNull {
        it.isSeparating && it.occlusionType == FoldingFeature.OcclusionType.FULL
    }
    val configuration = LocalConfiguration.current
    val windowHeight = if (info.height > 0.dp) info.height else configuration.screenHeightDp.dp
    val maxContentHeight = (windowHeight * maxHeightFraction - if (dragHandle != null) 36.dp else 0.dp)
        .coerceAtLeast(240.dp)
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density)
    val gap = with(density) { 12.dp.roundToPx() }
    val bounds = fold?.bounds?.let { SheetPane(it.left, it.top, it.right, it.bottom) }
    val modifier = if (fold == null || bounds == null) Modifier else Modifier.layout { measurable, constraints ->
        val pane = sheetPane(constraints.maxWidth, (constraints.maxHeight - keyboard).coerceAtLeast(1),
            bounds, fold.orientation == FoldingFeature.Orientation.VERTICAL, gap)
        // These constraints reach Material's draggable anchors, so expanded/hidden offsets are
        // relative to the chosen pane. Reporting full size keeps its scrim and outside taps intact.
        val placeable = measurable.measure(Constraints(maxWidth = pane.width, maxHeight = pane.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(pane.left + (pane.width - placeable.width) / 2, pane.top)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (fold != null) BottomSheetDefaults.SheetMaxWidth else sheetMaxWidth,
        modifier = modifier,
        containerColor = containerColor,
        dragHandle = dragHandle,
        contentWindowInsets = {
            // The folded layout already removes IME height; avoid subtracting it a second time.
            if (fold != null) WindowInsets.systemBars.union(WindowInsets.displayCutout)
            else BottomSheetDefaults.modalWindowInsets
        },
        content = {
            if (fold != null) {
                content()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight),
                    content = content
                )
            }
        },
    )
}
