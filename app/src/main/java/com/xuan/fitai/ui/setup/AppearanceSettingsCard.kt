package com.xuan.fitai.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.AppearanceSettings
import com.xuan.fitai.data.model.ThemeMode
import com.xuan.fitai.theme.ColorUtils
import com.xuan.fitai.ui.components.SettingsCard
import kotlin.math.roundToInt

private data class ThemePreset(
    val name: String,
    val hue: Int,
    val saturation: Float = 0.52f,
)

private val PresetThemes = listOf(
    ThemePreset("翡翠深林", 160, 0.52f),
    ThemePreset("清新薄荷", 145, 0.58f),
    ThemePreset("湛藍深海", 210, 0.52f),
    ThemePreset("晴空冰藍", 195, 0.60f),
    ThemePreset("魅影紫羅蘭", 275, 0.54f),
    ThemePreset("薰衣草紫", 255, 0.48f),
    ThemePreset("櫻花晨粉", 335, 0.56f),
    ThemePreset("珊瑚赤紅", 5, 0.62f),
    ThemePreset("暖陽蜜柑", 25, 0.65f),
    ThemePreset("琥珀暖金", 45, 0.60f),
    ThemePreset("青檸抹茶", 95, 0.48f),
    ThemePreset("莫蘭迪冷灰", 215, 0.22f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsCard(settings: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    var hue by remember(settings.customHue) { mutableFloatStateOf((settings.customHue ?: 160).toFloat()) }
    var saturation by remember(settings.customSaturation) { mutableFloatStateOf(settings.customSaturation ?: 0.52f) }

    val currentHex = remember(hue, saturation) {
        ColorUtils.hslToHex(hue, saturation, 0.38f)
    }
    var hexInputText by remember(settings.customHex, currentHex) {
        mutableStateOf(settings.customHex ?: currentHex)
    }
    var hexError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    SettingsCard {
        Text("主題外觀", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // 1. 深淺色模式切換
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("外觀模式", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onChange(settings.copy(themeMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "跟隨系統"
                                ThemeMode.LIGHT -> "淺色"
                                ThemeMode.DARK -> "深色"
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // 2. 桌布動態色彩
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("使用桌布動態配色", style = MaterialTheme.typography.titleSmall)
                Text("跟隨手機桌布色彩，自動搭配深淺色模式 (Android 12+)。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.useWallpaperColors, onCheckedChange = {
                onChange(settings.copy(useWallpaperColors = it))
            })
        }

        if (!settings.useWallpaperColors) {
            HorizontalDivider()

            // 3. 精選主題色
            Text("精選風格調色盤", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetThemes.forEach { preset ->
                    val isSelected = settings.customHue == preset.hue &&
                        (settings.customSaturation == null || kotlin.math.abs(settings.customSaturation - preset.saturation) < 0.05f)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val hex = ColorUtils.hslToHex(preset.hue.toFloat(), preset.saturation, 0.38f)
                            onChange(
                                settings.copy(
                                    customHue = preset.hue,
                                    customSaturation = preset.saturation,
                                    customHex = hex
                                )
                            )
                        },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(Color.hsl(preset.hue.toFloat(), preset.saturation, 0.45f), CircleShape)
                            )
                        },
                        label = { Text(preset.name) }
                    )
                }
            }

            HorizontalDivider()

            // 4. 色相滑桿 (Hue Slider)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("色相 (Hue)", style = MaterialTheme.typography.titleSmall)
                    Text("${hue.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(Color.hsl(hue, saturation, 0.45f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        onValueChangeFinished = {
                            val newHex = ColorUtils.hslToHex(hue, saturation, 0.38f)
                            onChange(settings.copy(customHue = hue.roundToInt(), customSaturation = saturation, customHex = newHex))
                        },
                        valueRange = 0f..359f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. 鮮豔度/飽和度滑桿 (Saturation Slider)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("鮮豔度 (Saturation)", style = MaterialTheme.typography.titleSmall)
                    Text("${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    onValueChangeFinished = {
                        val newHex = ColorUtils.hslToHex(hue, saturation, 0.38f)
                        onChange(settings.copy(customHue = hue.roundToInt(), customSaturation = saturation, customHex = newHex))
                    },
                    valueRange = 0.20f..1.00f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("向左調整為柔和莫蘭迪調，向右調整為活力明亮調。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 6. HEX 色碼自訂輸入
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("自訂 HEX 色碼", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = hexInputText,
                        onValueChange = { input ->
                            hexInputText = input
                            hexError = false
                        },
                        label = { Text("色碼 (如 #10B981)") },
                        singleLine = true,
                        isError = hexError,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val parsed = ColorUtils.hexToHsl(hexInputText)
                            if (parsed != null) {
                                focusManager.clearFocus()
                                val (h, s, _) = parsed
                                hue = h.toFloat()
                                saturation = s.coerceIn(0.2f, 1.0f)
                                onChange(
                                    settings.copy(
                                        customHue = h,
                                        customSaturation = saturation,
                                        customHex = hexInputText.trim().let { if (it.startsWith("#")) it else "#$it" }.uppercase()
                                    )
                                )
                            } else {
                                hexError = true
                            }
                        })
                    )
                    Button(
                        onClick = {
                            val parsed = ColorUtils.hexToHsl(hexInputText)
                            if (parsed != null) {
                                focusManager.clearFocus()
                                val (h, s, _) = parsed
                                hue = h.toFloat()
                                saturation = s.coerceIn(0.2f, 1.0f)
                                onChange(
                                    settings.copy(
                                        customHue = h,
                                        customSaturation = saturation,
                                        customHex = hexInputText.trim().let { if (it.startsWith("#")) it else "#$it" }.uppercase()
                                    )
                                )
                            } else {
                                hexError = true
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("套用")
                    }
                }
                if (hexError) {
                    Text("請輸入有效的 6 位元十六進位色碼（例如 #226553）", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 7. 即時預覽卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("配色即時效果預覽", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(currentHex, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text("主要按鈕")
                        }
                        FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text("次要按鈕")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(onClick = {}, label = { Text("健身目標") })
                        AssistChip(onClick = {}, label = { Text("AI 建議") })
                    }
                }
            }

            // 8. 恢復預設
            TextButton(
                onClick = { onChange(AppearanceSettings(themeMode = settings.themeMode, fontScale = settings.fontScale)) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (settings.customHue == null && settings.customSaturation == null) "目前使用 FitAI 經典翡翠配色" else "恢復 FitAI 經典翡翠配色")
            }
        }

        // 色彩階層預覽列
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer,
            ).forEach { color ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

