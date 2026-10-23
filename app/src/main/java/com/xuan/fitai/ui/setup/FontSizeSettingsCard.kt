package com.xuan.fitai.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.AppearanceSettings
import com.xuan.fitai.data.model.FontSizeScale
import com.xuan.fitai.theme.LocalAppFontScale
import com.xuan.fitai.ui.components.SettingsCard
import kotlin.math.abs
import kotlin.math.roundToInt

/** GemAgora's app-wide presets, fine adjustment and preview, with compact-width wrapping. */
@Composable
internal fun FontSizeSettingsCard(settings: AppearanceSettings, onChange: (AppearanceSettings) -> Unit) {
    var sliderScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale.coerceIn(0.85f, 1.30f)) }
    val currentScale = FontSizeScale.fromScale(sliderScale)
    val percentage = (sliderScale * 100).roundToInt()
    fun choose(option: FontSizeScale) { sliderScale = option.scale; onChange(settings.copy(fontScale = option.scale)) }
    SettingsCard {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("字體大小與閱讀排版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("${currentScale.label} · $percentage%", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text("調整全 App 介面、AI 建議與對話文字大小；會搭配手機的系統字體設定。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 400.dp) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FontSizeScale.entries.forEachIndexed { index, option ->
                    SegmentedButton(selected = abs(sliderScale - option.scale) < 0.025f, onClick = { choose(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, FontSizeScale.entries.size)) { Text(option.label) }
                }
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FontSizeScale.entries.forEach { option ->
                    FilterChip(selected = abs(sliderScale - option.scale) < 0.025f, onClick = { choose(option) }, label = { Text(option.label) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Slider(value = sliderScale, onValueChange = { sliderScale = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { onChange(settings.copy(fontScale = sliderScale)) },
                valueRange = 0.85f..1.30f, steps = 8, modifier = Modifier.weight(1f))
            Text("A", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("較小 (85%)", style = MaterialTheme.typography.labelSmall)
            Text("標準 (100%)", style = MaterialTheme.typography.labelSmall)
            Text("特大 (130%)", style = MaterialTheme.typography.labelSmall)
        }
        val density = LocalDensity.current
        val appliedScale = LocalAppFontScale.current
        // The parent already applies the saved app scale. Replace it with the slider value once.
        val previewDensity = remember(density.density, density.fontScale, appliedScale, sliderScale) {
            Density(density.density, density.fontScale / appliedScale * sliderScale)
        }
        CompositionLocalProvider(LocalDensity provides previewDensity) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("即時文字排版效果預覽 · $percentage%", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("每日營養建議", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("下一餐增加一份優質蛋白質，搭配蔬菜與全穀類，讓每次記錄都更容易閱讀。", style = MaterialTheme.typography.bodyLarge)
                    Text("餐點紀錄、訓練計畫與 AI 對話都會套用。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
