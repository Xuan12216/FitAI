package com.xuan.fitai.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.flowOf

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

data class AdaptiveInfo(val width: Dp = 0.dp, val height: Dp = 0.dp, val folds: List<FoldingFeature> = emptyList()) {
    val useRail get() = width >= 600.dp && height >= 480.dp
    val expanded get() = width >= 840.dp && height >= 480.dp
}
val LocalAdaptiveInfo = staticCompositionLocalOf { AdaptiveInfo() }

@Composable
fun ProvideAdaptiveLayout(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow = remember(activity) { activity?.let { WindowInfoTracker.getOrCreate(context).windowLayoutInfo(it) } ?: flowOf<WindowLayoutInfo?>(null) }
    val info by flow.collectAsStateWithLifecycle(null)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(maxWidth, maxHeight, info?.displayFeatures?.filterIsInstance<FoldingFeature>().orEmpty())) { content() }
    }
}

/** Supporting panes; coordinates are relative to the current window, never to the device display. */
@Composable
fun AdaptivePanes(primary: @Composable () -> Unit, secondary: @Composable () -> Unit, compact: @Composable () -> Unit) {
    val info = LocalAdaptiveInfo.current
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { originX = it.positionInWindow().x; originY = it.positionInWindow().y }) {
        val separating = info.folds.firstOrNull { it.isSeparating }
        val vertical = separating?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
        val horizontal = separating?.takeIf { it.orientation == FoldingFeature.Orientation.HORIZONTAL }
        val hingeLeft = with(density) { ((vertical?.bounds?.left ?: 0) - originX).toDp() }
        val hingeRight = with(density) { ((vertical?.bounds?.right ?: 0) - originX).toDp() }
        val hingeTop = with(density) { ((horizontal?.bounds?.top ?: 0) - originY).toDp() }
        val hingeBottom = with(density) { ((horizontal?.bounds?.bottom ?: 0) - originY).toDp() }
        when {
            vertical != null && hingeLeft >= 280.dp && maxWidth - hingeRight >= 280.dp -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width((hingeLeft - 12.dp).coerceAtLeast(0.dp)).fillMaxHeight()) { primary() }
                Spacer(Modifier.width((hingeRight - hingeLeft + 24.dp).coerceAtLeast(24.dp)))
                Box(Modifier.weight(1f).fillMaxHeight()) { secondary() }
            }
            horizontal != null && hingeTop >= 160.dp && maxHeight - hingeBottom >= 160.dp -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(hingeTop - 12.dp)) { primary() }
                Spacer(Modifier.height((hingeBottom - hingeTop + 24.dp).coerceAtLeast(24.dp)))
                Box(Modifier.weight(1f).fillMaxWidth()) { secondary() }
            }
            info.expanded && maxWidth >= 640.dp -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { primary() }
                Box(Modifier.weight(1f).fillMaxHeight()) { secondary() }
            }
            else -> compact()
        }
    }
}

@Composable
fun AdaptiveContent(maxWidth: Dp = 840.dp, content: @Composable () -> Unit) {
    val info = LocalAdaptiveInfo.current
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
        originX = it.positionInWindow().x; originY = it.positionInWindow().y
    }) {
        val fold = info.folds.firstOrNull { it.isSeparating }
        var start = 0.dp; var end = 0.dp; var top = 0.dp; var bottom = 0.dp
        if (fold != null) {
            if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
                val left = with(density) { (fold.bounds.left - originX).toDp() }.coerceIn(0.dp, this.maxWidth)
                val right = with(density) { (fold.bounds.right - originX).toDp() }.coerceIn(0.dp, this.maxWidth)
                if (left >= this.maxWidth - right) end = this.maxWidth - left + 12.dp else start = right + 12.dp
            } else {
                val upper = with(density) { (fold.bounds.top - originY).toDp() }.coerceIn(0.dp, maxHeight)
                val lower = with(density) { (fold.bounds.bottom - originY).toDp() }.coerceIn(0.dp, maxHeight)
                if (upper >= maxHeight - lower) bottom = maxHeight - upper + 12.dp else top = lower + 12.dp
            }
        }
        Box(Modifier.fillMaxSize().padding(start = start, end = end, top = top, bottom = bottom), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
        }
    }
}

/** Keep modal controls inside one logical display region on separating folds. */
@Composable
fun AdaptiveDialogPlacement() {
    val info = LocalAdaptiveInfo.current
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val fold = info.folds.firstOrNull { it.isSeparating }
    DisposableEffect(view, fold, info.width, info.height) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (window != null && fold != null) {
            val original = android.view.WindowManager.LayoutParams().apply { copyFrom(window.attributes) }
            val width = with(density) { info.width.toPx() }.toInt()
            val height = with(density) { info.height.toPx() }.toInt()
            val margin = with(density) { 24.dp.toPx() }.toInt()
            val attrs = android.view.WindowManager.LayoutParams().apply { copyFrom(window.attributes) }
            attrs.gravity = android.view.Gravity.CENTER
            if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
                val left = fold.bounds.left; val right = width - fold.bounds.right
                val paneWidth = maxOf(left, right)
                val center = if (left >= right) left / 2 else fold.bounds.right + right / 2
                attrs.width = minOf(with(density) { 560.dp.toPx() }.toInt(), paneWidth - margin).coerceAtLeast(1)
                attrs.x = center - width / 2
            } else {
                val top = fold.bounds.top; val bottom = height - fold.bounds.bottom
                val paneHeight = maxOf(top, bottom)
                val center = if (top >= bottom) top / 2 else fold.bounds.bottom + bottom / 2
                attrs.height = (paneHeight - margin).coerceAtLeast(1)
                attrs.y = center - height / 2
            }
            window.attributes = attrs
            onDispose { window.attributes = original }
        } else onDispose { }
    }
}
