package com.xuan.fitai.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.AppearanceSettings
import com.xuan.fitai.ui.components.SettingsCard

@Composable
internal fun AppearanceSettingsCard(settings: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    var hue by remember(settings.customHue) { mutableFloatStateOf((settings.customHue ?: 160).toFloat()) }
    SettingsCard {
        Text("主題配色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("使用桌布動態配色", style = MaterialTheme.typography.titleSmall)
                Text("跟隨手機桌布色彩，自動搭配深淺色模式。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.useWallpaperColors, onCheckedChange = {
                onChange(settings.copy(useWallpaperColors = it))
            })
        }
        if (!settings.useWallpaperColors) {
            HorizontalDivider()
            Text("自訂主色", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("森林" to 160, "海洋" to 210, "紫羅蘭" to 275, "暖橘" to 25).forEach { (name, colorHue) ->
                    FilterChip(
                        selected = settings.customHue == colorHue,
                        onClick = { onChange(settings.copy(customHue = colorHue)) },
                        leadingIcon = {
                            Box(Modifier.size(16.dp).background(Color.hsl(colorHue.toFloat(), 0.52f, 0.45f), CircleShape))
                        },
                        label = { Text(name) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).background(Color.hsl(hue, 0.52f, 0.45f), CircleShape))
                Slider(
                    value = hue, onValueChange = { hue = it },
                    onValueChangeFinished = { onChange(settings.copy(customHue = hue.toInt())) },
                    valueRange = 0f..359f, modifier = Modifier.weight(1f)
                )
            }
            Text("拖曳調整色相，放開後套用並儲存。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onChange(AppearanceSettings()) }, contentPadding = PaddingValues(0.dp)) {
                Text(if (settings.customHue == null) "目前使用 FitAI 預設配色" else "恢復 FitAI 預設配色")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary).forEach { color ->
                Box(Modifier.weight(1f).height(12.dp).background(color, CircleShape))
            }
        }
    }
}
