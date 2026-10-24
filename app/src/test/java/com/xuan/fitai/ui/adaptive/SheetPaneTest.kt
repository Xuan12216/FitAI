package com.xuan.fitai.ui.adaptive

import org.junit.Assert.*
import org.junit.Test

class SheetPaneTest {
    @Test fun verticalFoldKeepsContentInLargerSide() {
        val pane = sheetPane(1600, 1200, SheetPane(700, 0, 724, 1200), true, 12)
        assertTrue(pane.left >= 736); assertEquals(1600, pane.right)
        val left = sheetPane(1600, 1200, SheetPane(900, 0, 924, 1200), true, 12)
        assertEquals(0, left.left); assertTrue(left.right <= 888)
    }
    @Test fun tabletopAndKeyboardSelectAVisiblePane() {
        val hinge = SheetPane(0, 600, 1600, 624)
        val normal = sheetPane(1600, 1400, hinge, false, 12)
        assertTrue(normal.top >= 636)
        val typing = sheetPane(1600, 800, hinge, false, 12)
        assertEquals(0, typing.top); assertTrue(typing.bottom <= 588)
    }
    @Test fun hingeOutsideResizedWindowDoesNotCreateNegativeConstraints() {
        val pane = sheetPane(400, 300, SheetPane(900, 0, 924, 1200), true, 12)
        assertEquals(SheetPane(0, 0, 400, 300), pane)
        assertTrue(pane.width > 0 && pane.height > 0)
    }
}
