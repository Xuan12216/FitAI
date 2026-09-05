package com.xuan.fitai.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.ui.components.ThinkingContent
import java.time.format.DateTimeFormatter

@Composable
internal fun CalorieOverview(profile: UserProfile, consumed: Float) {
    val remaining = profile.targetCalories - consumed
    val progress by animateFloatAsState(
        (consumed / profile.targetCalories.coerceAtLeast(1f)).coerceIn(0f, 1f), label = "calories"
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("今日熱量", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (remaining >= 0) "還可攝取" else "已超過目標",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(kotlin.math.abs(remaining).toInt().toString(),
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("kcal", style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        strokeWidth = 9.dp, strokeCap = StrokeCap.Round
                    )
                    Icon(Icons.Default.LocalFireDepartment, "熱量進度", Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth()) {
                OverviewValue("已攝取", "${consumed.toInt()} kcal", Modifier.weight(1f))
                OverviewValue("每日目標", "${profile.targetCalories.toInt()} kcal", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun NutritionOverview(profile: UserProfile, meals: List<Meal>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("營養進度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            NutrientRow("蛋白質", meals.sumOf { it.protein.toDouble() }.toFloat(), profile.targetProteinGrams, MaterialTheme.colorScheme.primary)
            NutrientRow("碳水化合物", meals.sumOf { it.carbs.toDouble() }.toFloat(), profile.targetCarbsGrams, MaterialTheme.colorScheme.secondary)
            NutrientRow("脂肪", meals.sumOf { it.fat.toDouble() }.toFloat(), profile.targetFatGrams, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun NutrientRow(label: String, current: Float, target: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${current.toInt()} / ${target.toInt()} g", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (current / target.coerceAtLeast(1f)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = color,
            trackColor = color.copy(alpha = 0.12f), strokeCap = StrokeCap.Round
        )
    }
}

@Composable
internal fun WaterOverview(consumed: Int, target: Int, busy: Boolean, onAdd: (Int) -> Unit, onSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("今日飲水", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onSettings) { Text("目標設定") }
            }
            Text("$consumed / $target ml", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { (consumed.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSecondaryContainer,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
            )
            Text(if (consumed >= target) "今日目標已達成" else "喝完一杯，輕點一下就記錄。",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onAdd(250) }, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text("+250 ml")
                }
                OutlinedButton(onClick = { onAdd(500) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("+500 ml") }
            }
        }
    }
}

@Composable
internal fun WeeklyOverview(days: List<DailySummary>) {
    if (days.isEmpty()) return
    var selectedDate by rememberSaveable(days.last().date.toString()) { mutableStateOf(days.last().date.toString()) }
    val selected = days.firstOrNull { it.date.toString() == selectedDate } ?: days.last()
    val maxCalories = days.maxOf { it.calories }.coerceAtLeast(1f)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("近七日回顧", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("點選日期查看飲食紀錄", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    val active = day.date == selected.date
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab, onClick = { selectedDate = day.date.toString() })
                            .semantics { contentDescription = "${day.date}，${day.mealCount} 餐，${day.calories.toInt()} 大卡" }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.height(64.dp).width(12.dp), contentAlignment = Alignment.BottomCenter) {
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
            Text("數值僅包含已記錄的餐點。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DailyAdviceCard(advice: String, generating: Boolean, hasModel: Boolean, onRefresh: () -> Unit, onStop: () -> Unit, onSetup: () -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("AI 營養建議", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (hasModel) IconButton(onClick = if (generating) onStop else onRefresh) {
                    Icon(if (generating) Icons.Default.Stop else Icons.Default.Refresh, if (generating) "停止生成" else "重新產生建議")
                }
            }
            if (hasModel) {
                ThinkingContent(rawText = advice.ifBlank { if (generating) "正在整理今天的飲食建議…" else "點選重新產生，取得今日建議。" }, isGenerating = generating)
            } else {
                Text("啟用 AI，根據今天的飲食紀錄取得建議。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSetup, contentPadding = PaddingValues(0.dp)) { Text("設定 AI 助理") }
            }
        }
    }
}
