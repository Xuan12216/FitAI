package com.xuan.fitai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun hexToHslValidCodes() {
        val redHsl = ColorUtils.hexToHsl("#FF0000")
        assertNotNull(redHsl)
        assertEquals(0, redHsl!!.first)
        assertEquals(1f, redHsl.second, 0.01f)
        assertEquals(0.5f, redHsl.third, 0.01f)

        val greenHsl = ColorUtils.hexToHsl("00FF00")
        assertNotNull(greenHsl)
        assertEquals(120, greenHsl!!.first)
        assertEquals(1f, greenHsl.second, 0.01f)
        assertEquals(0.5f, greenHsl.third, 0.01f)

        val blueHsl = ColorUtils.hexToHsl("#0000FF")
        assertNotNull(blueHsl)
        assertEquals(240, blueHsl!!.first)
        assertEquals(1f, blueHsl.second, 0.01f)
        assertEquals(0.5f, blueHsl.third, 0.01f)
    }

    @Test
    fun hexToHslInvalidCodes() {
        assertNull(ColorUtils.hexToHsl(""))
        assertNull(ColorUtils.hexToHsl("#FFF"))
        assertNull(ColorUtils.hexToHsl("ZZZZZZ"))
        assertNull(ColorUtils.hexToHsl("#12345"))
    }

    @Test
    fun hslToHexConversion() {
        val redHex = ColorUtils.hslToHex(0f, 1f, 0.5f)
        assertEquals("#FF0000", redHex)

        val greenHex = ColorUtils.hslToHex(120f, 1f, 0.5f)
        assertEquals("#00FF00", greenHex)

        val blueHex = ColorUtils.hslToHex(240f, 1f, 0.5f)
        assertEquals("#0000FF", blueHex)
    }

    @Test
    fun isValidHexValidation() {
        assertTrue(ColorUtils.isValidHex("#10B981"))
        assertTrue(ColorUtils.isValidHex("10B981"))
        assertTrue(ColorUtils.isValidHex("#ffffff"))
        assertFalse(ColorUtils.isValidHex("#12345"))
        assertFalse(ColorUtils.isValidHex("#1234567"))
        assertFalse(ColorUtils.isValidHex("hello!"))
    }
}
