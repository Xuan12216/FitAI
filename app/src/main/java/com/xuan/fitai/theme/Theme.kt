package com.xuan.fitai.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MintLight,
    onPrimary = InkGreen,
    primaryContainer = Forest,
    onPrimaryContainer = PaleMint,
    secondary = BlueLight,
    onSecondary = InkBlue,
    secondaryContainer = DeepBlue,
    onSecondaryContainer = PaleBlue,
    tertiary = PeachLight,
    background = Night,
    surface = Night,
    onBackground = PaleMint,
    onSurface = PaleMint,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Night,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    outlineVariant = Forest,
)

private val LightColorScheme = lightColorScheme(
    primary = Evergreen,
    onPrimary = White,
    primaryContainer = PaleMint,
    onPrimaryContainer = InkGreen,
    secondary = Ocean,
    onSecondary = White,
    secondaryContainer = PaleBlue,
    onSecondaryContainer = InkBlue,
    tertiary = Clay,
    background = Canvas,
    surface = Canvas,
    onBackground = InkGreen,
    onSurface = InkGreen,
    onSurfaceVariant = MutedGreen,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = SoftGreen,
    surfaceContainerHigh = SoftGreen,
    outlineVariant = BorderGreen,
)

// Material 3 Expressive uses a broader range of rounded shapes to make
// information groups and primary actions easier to scan and touch.
private val FitAIShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
  )

@Composable
fun FitAITheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  customHue: Int? = null,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      customHue != null -> remember(customHue, darkTheme) { customColorScheme(customHue, darkTheme) }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = FitAIShapes,
    content = content,
  )
}
