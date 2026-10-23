package com.xuan.fitai.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class FontSizeScale(val scale: Float, val label: String) {
    SMALL(0.85f, "小"), NORMAL(1.0f, "標準"), LARGE(1.15f, "大"), EXTRA_LARGE(1.30f, "特大");

    companion object {
        fun fromScale(scale: Float): FontSizeScale = entries.minBy { kotlin.math.abs(it.scale - scale) }
    }
}

fun normalizedFontScale(scale: Float): Float = if (scale.isFinite()) scale.coerceIn(0.85f, 1.30f) else 1f

data class AppearanceSettings(
    val useWallpaperColors: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customHue: Int? = null,
    val customSaturation: Float? = null,
    val customHex: String? = null,
    val fontScale: Float = 1.0f,
)
