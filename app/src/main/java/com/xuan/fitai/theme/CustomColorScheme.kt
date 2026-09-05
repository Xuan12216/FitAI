package com.xuan.fitai.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Custom accent and tinted surfaces, with separate light/dark tones.
internal fun customColorScheme(hue: Int, dark: Boolean, saturation: Float = 0.52f): ColorScheme {
    val satRatio = (saturation / 0.52f).coerceIn(0.2f, 2.0f)
    fun tone(baseSaturation: Float, lightness: Float, offset: Int = 0) =
        Color.hsl(
            ((hue + offset) % 360 + 360).rem(360).toFloat(),
            (baseSaturation * satRatio).coerceIn(0f, 1f),
            lightness
        )
    fun foreground(color: Color) = if (color.luminance() > 0.179f) Color.Black else Color.White
    val primary = tone(0.52f, if (dark) 0.76f else 0.35f)
    val secondary = tone(0.24f, if (dark) 0.76f else 0.35f, 35)
    val tertiary = tone(0.36f, if (dark) 0.78f else 0.35f, 100)
    val primaryContainer = tone(0.30f, if (dark) 0.22f else 0.90f)
    val secondaryContainer = tone(0.24f, if (dark) 0.22f else 0.92f, 35)
    val tertiaryContainer = tone(0.26f, if (dark) 0.22f else 0.92f, 100)
    val surface = tone(0.13f, if (dark) 0.08f else 0.975f)
    val onSurface = tone(0.16f, if (dark) 0.91f else 0.12f)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary, onPrimary = foreground(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = foreground(primaryContainer),
        secondary = secondary, onSecondary = foreground(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = foreground(secondaryContainer),
        tertiary = tertiary, onTertiary = foreground(tertiary),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = foreground(tertiaryContainer),
        background = surface, onBackground = onSurface,
        surface = surface, onSurface = onSurface, surfaceTint = primary,
        surfaceVariant = tone(0.12f, if (dark) 0.22f else 0.90f),
        onSurfaceVariant = tone(0.10f, if (dark) 0.76f else 0.32f),
        surfaceContainerLowest = tone(0.12f, if (dark) 0.06f else 1f),
        surfaceContainerLow = tone(0.12f, if (dark) 0.12f else 1f),
        surfaceContainer = tone(0.12f, if (dark) 0.15f else 0.95f),
        surfaceContainerHigh = tone(0.12f, if (dark) 0.19f else 0.93f),
        surfaceContainerHighest = tone(0.12f, if (dark) 0.23f else 0.90f),
        surfaceBright = tone(0.12f, if (dark) 0.25f else 0.98f),
        surfaceDim = tone(0.12f, if (dark) 0.08f else 0.86f),
        outline = tone(0.10f, if (dark) 0.58f else 0.46f),
        outlineVariant = tone(0.12f, if (dark) 0.30f else 0.83f),
        inverseSurface = tone(0.12f, if (dark) 0.90f else 0.18f),
        inverseOnSurface = tone(0.12f, if (dark) 0.16f else 0.95f),
        inversePrimary = tone(0.52f, if (dark) 0.35f else 0.76f),
    )
}
