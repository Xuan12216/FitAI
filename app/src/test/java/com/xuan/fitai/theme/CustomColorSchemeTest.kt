package com.xuan.fitai.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorSchemeTest {
    @Test fun textHasReadableContrastAcrossHuesAndModes() {
        for (dark in listOf(false, true)) for (hue in 0..359 step 15) {
            val scheme = customColorScheme(hue, dark)
            val pairs = listOf(
                scheme.primary to scheme.onPrimary,
                scheme.secondary to scheme.onSecondary,
                scheme.tertiary to scheme.onTertiary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.surface to scheme.onSurface,
                scheme.surface to scheme.onSurfaceVariant,
            )
            pairs.forEach { (background, foreground) ->
                assertTrue("Contrast too low at hue=$hue, dark=$dark", contrast(background, foreground) >= 4.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val brighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (brighter + 0.05f) / (darker + 0.05f)
    }
}
