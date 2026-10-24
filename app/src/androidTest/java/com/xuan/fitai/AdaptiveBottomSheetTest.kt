package com.xuan.fitai

import android.graphics.Rect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.xuan.fitai.ui.adaptive.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class AdaptiveBottomSheetTest {
    @get:Rule val compose = createComposeRule()
    private class Fold(override val bounds: Rect, override val orientation: FoldingFeature.Orientation) : FoldingFeature {
        override val state = FoldingFeature.State.HALF_OPENED
        override val isSeparating = true
        override val occlusionType = FoldingFeature.OcclusionType.FULL
    }

    @Test fun sheetStaysOnOneSideOfVerticalHinge() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(2560.dp, 2000.dp,
                    listOf(Fold(Rect(1200, 0, 1224, 2000), FoldingFeature.Orientation.VERTICAL)))) {
                    AdaptiveModalBottomSheet({}, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Box(Modifier.fillMaxWidth().height(900.dp).testTag("sheet"))
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInWindow
        assertTrue("$bounds intersects vertical hinge", bounds.right <= 1200 || bounds.left >= 1224)
    }

    @Test fun sheetMovesAcrossPostureChangeWithoutLosingInput() {
        val horizontal = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                val fold = if (horizontal.value) Fold(Rect(0, 800, 2560, 824), FoldingFeature.Orientation.HORIZONTAL)
                    else Fold(Rect(1200, 0, 1224, 2000), FoldingFeature.Orientation.VERTICAL)
                CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(2560.dp, 2000.dp, listOf(fold))) {
                    AdaptiveModalBottomSheet({}, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Column(Modifier.fillMaxWidth().height(900.dp).verticalScroll(rememberScrollState()).testTag("sheet")) {
                            var value by rememberSaveable { mutableStateOf("") }
                            OutlinedTextField(value, { value = it }, Modifier.testTag("input"))
                        }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInWindow
        assertTrue("$bounds intersects horizontal hinge", bounds.bottom <= 800 || bounds.top >= 824)
        compose.onNodeWithTag("input").performTextInput("晚餐牛肉")
        compose.runOnIdle { horizontal.value = false }
        compose.onNodeWithTag("input").assertTextContains("晚餐牛肉")
        val moved = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInWindow
        assertTrue("$moved intersects vertical hinge", moved.right <= 1200 || moved.left >= 1224)
    }

    private fun showDismissibleSheet() {
        compose.setContent {
            MaterialTheme {
                var shown by remember { mutableStateOf(true) }
                CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(2560.dp, 2000.dp,
                    listOf(Fold(Rect(1200, 0, 1224, 2000), FoldingFeature.Orientation.VERTICAL)))) {
                    if (shown) AdaptiveModalBottomSheet({ shown = false }, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Box(Modifier.fillMaxWidth().height(900.dp).testTag("sheet"))
                    }
                }
            }
        }
    }

    @Test fun tappingOutsideSelectedPaneStillDismisses() {
        showDismissibleSheet()
        val sheet = compose.onNodeWithTag("sheet")
        val bounds = sheet.fetchSemanticsNode().boundsInWindow
        sheet.performTouchInput { click(Offset(50f - bounds.left, 50f)) }
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    @Test fun sheetStillSupportsDragToDismiss() {
        showDismissibleSheet()
        compose.onNodeWithTag("sheet").performTouchInput { swipeDown(durationMillis = 500) }
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }
}
