package com.xuan.fitai.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

object ColorUtils {
    /**
     * Converts a Hex color string (e.g. "#10B981" or "10B981") into (Hue: 0..359, Saturation: 0f..1f, Lightness: 0f..1f).
     * Returns null if invalid.
     */
    fun hexToHsl(hex: String): Triple<Int, Float, Float>? {
        val cleanHex = hex.trim().removePrefix("#")
        if (cleanHex.length != 6) return null
        val colorInt = cleanHex.toLongOrNull(16)?.toInt() ?: return null
        val r = ((colorInt shr 16) and 0xFF) / 255f
        val g = ((colorInt shr 8) and 0xFF) / 255f
        val b = (colorInt and 0xFF) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val l = (max + min) / 2f

        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
        var h = when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f) * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }
        if (h < 0) h += 360f

        return Triple(h.roundToInt().coerceIn(0, 359), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * Converts HSL values to a standard Hex string (e.g. "#226553").
     */
    fun hslToHex(hue: Float, saturation: Float, lightness: Float): String {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
        val m = lightness - c / 2f

        val (rPrime, gPrime, bPrime) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m).coerceIn(0f, 1f) * 255f).roundToInt()
        val g = ((gPrime + m).coerceIn(0f, 1f) * 255f).roundToInt()
        val b = ((bPrime + m).coerceIn(0f, 1f) * 255f).roundToInt()

        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun isValidHex(hex: String): Boolean {
        val clean = hex.trim().removePrefix("#")
        return clean.length == 6 && clean.all { it in "0123456789abcdefABCDEF" }
    }
}
