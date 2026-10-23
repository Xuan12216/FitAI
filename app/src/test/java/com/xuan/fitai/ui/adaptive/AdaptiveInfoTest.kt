package com.xuan.fitai.ui.adaptive
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
class AdaptiveInfoTest {
    @Test fun usesWindowWidthAndHeightBreakpoints() {
        assertFalse(AdaptiveInfo(599.dp, 800.dp).useRail)
        assertTrue(AdaptiveInfo(600.dp, 800.dp).useRail)
        assertFalse(AdaptiveInfo(839.dp, 800.dp).expanded)
        assertTrue(AdaptiveInfo(840.dp, 800.dp).expanded)
        assertFalse(AdaptiveInfo(900.dp, 400.dp).expanded)
        assertFalse(AdaptiveInfo(900.dp, 400.dp).useRail)
    }
}
