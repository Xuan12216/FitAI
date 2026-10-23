package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.xuan.fitai.data.model.ModelLoadState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import java.time.LocalDate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.ui.components.ThinkingContent
import java.time.format.DateTimeFormatter

@Composable
internal fun ModelStatusBanner(state: ModelLoadState, modelName: String?, onSetup: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSetup)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        color = when (state) {
            ModelLoadState.Loaded -> MaterialTheme.colorScheme.surfaceContainerHighest
            ModelLoadState.Loading -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state == ModelLoadState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFF59E0B)
                )
            } else {
                Box(Modifier.size(12.dp).background(
                    if (state == ModelLoadState.Loaded) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    CircleShape
                ))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        ModelLoadState.Loading -> "Gemma 4 地端推論引擎背景暖機中…"
                        ModelLoadState.Loaded -> "Gemma 4 離線就緒 (${modelName ?: "已載入"})"
                        is ModelLoadState.Failed -> "本地模型載入失敗（點擊前往設定）"
                        ModelLoadState.NotFound -> "尚未載入本地模型（點擊前往設定）"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (state) {
                        ModelLoadState.Loading -> "模型與快取準備中 · 其它功能可正常使用"
                        ModelLoadState.Loaded -> "可開始使用 AI 助理 · 資料在裝置上處理"
                        else -> "前往模型設定檢查或載入模型"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TodayProgressCard(
    profile: UserProfile,
    meals: List<Meal>,
    waterConsumed: Int,
    waterTarget: Int,
    waterBusy: Boolean,
    onAddWater: (Int) -> Unit,
    onSettings: () -> Unit,
    servingMl: Int = 250
) {
    val consumed = meals.sumOf { it.calories.toDouble() }.toFloat()
    val remaining = profile.targetCalories - consumed
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("今日進度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (remaining >= 0) "還可攝取" else "已超過目標",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${kotlin.math.abs(remaining).toInt()} kcal", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = if (remaining < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("已攝取 / 每日目標", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${consumed.toInt()} / ${profile.targetCalories.toInt()} kcal",
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            LinearProgressIndicator(
                progress = { (consumed / profile.targetCalories.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = if (remaining < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NutrientProgress("蛋白質", meals.sumOf { it.protein.toDouble() }.toFloat(), profile.targetProteinGrams,
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                NutrientProgress("碳水", meals.sumOf { it.carbs.toDouble() }.toFloat(), profile.targetCarbsGrams,
                    MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                NutrientProgress("脂肪", meals.sumOf { it.fat.toDouble() }.toFloat(), profile.targetFatGrams,
                    MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("今日飲水", style = MaterialTheme.typography.labelLarge)
                    Text("$waterConsumed / $waterTarget ml", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSettings) { Text("目標設定") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { (waterConsumed.toFloat() / waterTarget.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                )
                FilledTonalButton(onClick = { onAddWater(servingMl) }, enabled = !waterBusy) { Text("+$servingMl ml") }
                OutlinedButton(onClick = { onAddWater(servingMl * 2) }, enabled = !waterBusy) { Text("+${servingMl * 2} ml") }
            }
        }
    }
}

@Composable
private fun NutrientProgress(label: String, current: Float, target: Float, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${current.toInt()} / ${target.toInt()} g", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { (current / target.coerceAtLeast(1f)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp), color = color,
            trackColor = color.copy(alpha = 0.12f), strokeCap = StrokeCap.Round
        )
    }
}

@Composable
internal fun WeeklyOverview(days: List<DailySummary>, targetCalories: Float = 0f, selectedDate: LocalDate? = null, onSelect: (LocalDate) -> Unit = {}) {
    if (days.isEmpty()) return
    val chosenDate = selectedDate ?: days.last().date
    val selected = days.firstOrNull { it.date == chosenDate } ?: days.last()
    val maxCalories = maxOf(days.maxOf { it.calories }, targetCalories).coerceAtLeast(1f)
    val goalColor = MaterialTheme.colorScheme.outline
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("近七日回顧", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("點選日期查看飲食紀錄", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    val active = day.date == chosenDate
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(day.date) })
                            .semantics { contentDescription = "${day.date}，${day.mealCount} 餐，${day.calories.toInt()} 大卡" }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.height(64.dp).width(12.dp).drawBehind {
                            if (targetCalories > 0) {
                                val y = size.height * (1f - targetCalories / maxCalories)
                                drawLine(goalColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                            }
                        }, contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height((64 * day.calories / maxCalories).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                        }
                        Text(if (day == days.last()) "今" else day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                "${selected.date.format(DateTimeFormatter.ofPattern("M/d"))}  ·  " +
                    if (selected.mealCount == 0) "尚無紀錄" else "${selected.calories.toInt()} kcal  ·  ${selected.mealCount} 餐",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
            )
            if (targetCalories > 0) Text("虛線：目前每日目標 ${targetCalories.toInt()} kcal；點日期查看當日明細。", style = MaterialTheme.typography.labelSmall)
            Text("數值僅包含已記錄的餐點。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DailyAdviceCard(advice: String, generating: Boolean, hasModel: Boolean, onRefresh: () -> Unit, onStop: () -> Unit, onSetup: () -> Unit, isModelLoading: Boolean = false, title: String = "", onAsk: (() -> Unit)? = null, onRecord: (() -> Unit)? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("AI 營養建議", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                if (hasModel) IconButton(onClick = if (generating) onStop else onRefresh) {
                    Icon(if (generating) Icons.Default.Stop else Icons.Default.Refresh, if (generating) "停止生成" else "重新產生建議")
                }
            }
            if (hasModel && !generating && (onAsk != null || onRecord != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onAsk?.let { TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) { Text("詢問替代方案") } }
                    onRecord?.let { TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("記錄餐點") } }
                }
            }
            if (hasModel) {
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ThinkingContent(
                    rawText = advice.ifBlank { if (generating) "正在整理今天的飲食建議…" else "點選重新產生，取得今日建議。" },
                    isGenerating = generating,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow }
                )
                if (hasOverflow || expanded) {
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (expanded) "收起建議" else "展開完整建議")
                    }
                }
            } else if (isModelLoading) {
                Text("背景暖機中，完成後會自動產生營養建議。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("啟用 AI，根據今天的飲食紀錄取得建議。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSetup, contentPadding = PaddingValues(0.dp)) { Text("設定 AI 助理") }
            }
        }
    }
}
