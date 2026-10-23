package com.xuan.fitai

import android.graphics.Rect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.xuan.fitai.ui.adaptive.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()
    private class Fold(override val bounds: Rect, override val orientation: FoldingFeature.Orientation) : FoldingFeature {
        override val state = FoldingFeature.State.HALF_OPENED
        override val isSeparating = true
        override val occlusionType = FoldingFeature.OcclusionType.FULL
    }

    @Test fun narrowWindowUsesCompactContent() {
        compose.setContent {
            CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(400.dp, 800.dp)) {
                AdaptivePanes(primary = { Box(Modifier.testTag("primary")) }, secondary = { Box(Modifier.testTag("secondary")) }, compact = { Box(Modifier.testTag("compact")) })
            }
        }
        compose.onNodeWithTag("compact").assertExists()
        compose.onNodeWithTag("primary").assertDoesNotExist()
    }

    @Test fun expandedWindowShowsBothPanes() {
        compose.setContent {
            CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(1000.dp, 800.dp)) {
                Box(Modifier.fillMaxSize()) {
                    AdaptivePanes(primary = { Box(Modifier.fillMaxSize().testTag("primary")) }, secondary = { Box(Modifier.fillMaxSize().testTag("secondary")) }, compact = { Box(Modifier.testTag("compact")) })
                }
            }
        }
        val left = compose.onNodeWithTag("primary").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        compose.onNodeWithTag("compact").assertDoesNotExist()
    }

    @Test fun tabletopKeepsControlsAboveOrBelowHinge() {
        var hingeTop = 0f
        var hingeBottom = 0f
        compose.setContent {
            val density = LocalDensity.current
            val fold = with(density) { Fold(Rect(0, 300.dp.roundToPx(), 800.dp.roundToPx(), 324.dp.roundToPx()), FoldingFeature.Orientation.HORIZONTAL) }
            hingeTop = fold.bounds.top.toFloat(); hingeBottom = fold.bounds.bottom.toFloat()
            CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(800.dp, 700.dp, listOf(fold))) {
                Box(Modifier.fillMaxSize()) {
                    AdaptivePanes(primary = { Box(Modifier.fillMaxSize().testTag("primary")) }, secondary = { Box(Modifier.fillMaxSize().testTag("secondary")) }, compact = { Box(Modifier.testTag("compact")) })
                }
            }
        }
        val upper = compose.onNodeWithTag("primary").fetchSemanticsNode().boundsInWindow
        val lower = compose.onNodeWithTag("secondary").fetchSemanticsNode().boundsInWindow
        assertTrue(upper.bottom <= hingeTop)
        assertTrue(lower.top >= hingeBottom)
    }

    @Test fun verticalHingeSeparatesPanesAndSinglePaneUsesOneSide() {
        var leftEdge = 0f
        var rightEdge = 0f
        val singlePane = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            val fold = with(density) { Fold(Rect(700.dp.roundToPx(), 0, 724.dp.roundToPx(), 1200.dp.roundToPx()), FoldingFeature.Orientation.VERTICAL) }
            leftEdge = fold.bounds.left.toFloat(); rightEdge = fold.bounds.right.toFloat()
            CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(1600.dp, 1200.dp, listOf(fold))) {
                if (singlePane.value) AdaptiveContent { Box(Modifier.fillMaxSize().testTag("single")) }
                else AdaptivePanes(primary = { Box(Modifier.fillMaxSize().testTag("primary")) }, secondary = { Box(Modifier.fillMaxSize().testTag("secondary")) }, compact = { Box(Modifier.testTag("compact")) })
            }
        }
        assertTrue(compose.onNodeWithTag("primary").fetchSemanticsNode().boundsInWindow.right <= leftEdge)
        assertTrue(compose.onNodeWithTag("secondary").fetchSemanticsNode().boundsInWindow.left >= rightEdge)
        compose.runOnIdle { singlePane.value = true }
        val bounds = compose.onNodeWithTag("single").fetchSemanticsNode().boundsInWindow
        assertTrue(bounds.right <= leftEdge || bounds.left >= rightEdge)
    }

    @Test fun editingSurvivesWidthChange() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            var value by rememberSaveable { mutableStateOf("") }
            CompositionLocalProvider(LocalAdaptiveInfo provides AdaptiveInfo(if (expanded.value) 1000.dp else 400.dp, 800.dp)) {
                AdaptiveContent { OutlinedTextField(value, { value = it }, Modifier.testTag("input")) }
            }
        }
        compose.onNodeWithTag("input").performTextInput("早餐燕麥")
        compose.runOnIdle { expanded.value = true }
        compose.onNodeWithTag("input").assertTextContains("早餐燕麥")
    }
}
