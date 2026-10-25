package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import com.xuan.fitai.ui.adaptive.AdaptiveModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.model.WeightEntry
import com.xuan.fitai.ui.components.PastDatePickerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WeightTrendDialog(
    profile: UserProfile,
    entries: List<WeightEntry>,
    onDismiss: () -> Unit,
    onSave: suspend (WeightEntry) -> Unit,
    onApply: suspend (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weight by rememberSaveable {
        mutableStateOf(entries.lastOrNull()?.weightKg?.toString() ?: profile.weight.toString())
    }
    var target by rememberSaveable {
        mutableStateOf(entries.lastOrNull()?.targetKg?.toString() ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val parsed = weight.toFloatOrNull()
    val parsedTarget = target.toFloatOrNull()
    val valid = parsed?.let { it.isFinite() && it > 0 && it <= 1000 } == true &&
        (target.isBlank() || parsedTarget?.let { it.isFinite() && it > 0 && it <= 1000 } == true)

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val fieldShape = RoundedCornerShape(14.dp)
        val softFieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sticky Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MonitorWeight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "體重趨勢與目標",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

            // Summary Metrics & Trend Chart Card
            val points = remember(entries) { entries.takeLast(30) }
            val latestEntry = points.lastOrNull()
            val latestTarget = parsedTarget ?: latestEntry?.targetKg

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "最新紀錄",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = latestEntry?.let { "${String.format(Locale.ROOT, "%.1f", it.weightKg)} kg" }
                                    ?: "${String.format(Locale.ROOT, "%.1f", profile.weight)} kg",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (points.size >= 2) {
                            val diff = points.last().weightKg - points.first().weightKg
                            val sign = if (diff > 0) "+" else ""
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "近 ${points.size} 次變化",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$sign${String.format(Locale.ROOT, "%.1f", diff)} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (latestTarget != null && latestEntry != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "距離目標 (${String.format(Locale.ROOT, "%.1f", latestTarget)} kg)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format(Locale.ROOT, "%.1f", abs(latestEntry.weightKg - latestTarget))} kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Trend Chart Canvas
                    if (points.size >= 2) {
                        val lineColor = MaterialTheme.colorScheme.primary
                        val targetLineColor = MaterialTheme.colorScheme.tertiary
                        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow

                        val allValues = buildList {
                            points.forEach { add(it.weightKg) }
                            latestTarget?.let { add(it) }
                        }
                        val minVal = (allValues.minOrNull() ?: 0f) - 0.5f
                        val maxVal = (allValues.maxOrNull() ?: 1f) + 0.5f
                        val span = (maxVal - minVal).coerceAtLeast(1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Y-axis Min/Max Labels
                            Column(
                                modifier = Modifier.height(132.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.ROOT, "%.1f", maxVal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.ROOT, "%.1f", (maxVal + minVal) / 2f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.ROOT, "%.1f", minVal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(132.dp)
                                ) {
                                    val padVertical = 10.dp.toPx()
                                    val usableHeight = (size.height - padVertical * 2).coerceAtLeast(1f)

                                    fun yOf(value: Float): Float {
                                        val norm = ((value - minVal) / span).coerceIn(0f, 1f)
                                        return padVertical + usableHeight * (1f - norm)
                                    }

                                    fun pointOf(index: Int, value: Float): Offset {
                                        val x = size.width * index / (points.size - 1).coerceAtLeast(1)
                                        return Offset(x, yOf(value))
                                    }

                                    // 3 Horizontal Grid Lines
                                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                                        val y = padVertical + usableHeight * fraction
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    // Optional Target Dashed Line
                                    latestTarget?.let { goal ->
                                        val goalY = yOf(goal)
                                        drawLine(
                                            color = targetLineColor,
                                            start = Offset(0f, goalY),
                                            end = Offset(size.width, goalY),
                                            strokeWidth = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                        )
                                    }

                                    // Line & Area Fill Path
                                    val strokePath = Path()
                                    val fillPath = Path()
                                    points.forEachIndexed { idx, item ->
                                        val pt = pointOf(idx, item.weightKg)
                                        if (idx == 0) {
                                            strokePath.moveTo(pt.x, pt.y)
                                            fillPath.moveTo(pt.x, size.height)
                                            fillPath.lineTo(pt.x, pt.y)
                                        } else {
                                            strokePath.lineTo(pt.x, pt.y)
                                            fillPath.lineTo(pt.x, pt.y)
                                        }
                                        if (idx == points.lastIndex) {
                                            fillPath.lineTo(pt.x, size.height)
                                            fillPath.close()
                                        }
                                    }

                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                lineColor.copy(alpha = 0.24f),
                                                lineColor.copy(alpha = 0.0f)
                                            )
                                        )
                                    )

                                    drawPath(
                                        path = strokePath,
                                        color = lineColor,
                                        style = Stroke(
                                            width = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Data Point Dots
                                    points.forEachIndexed { idx, item ->
                                        val pt = pointOf(idx, item.weightKg)
                                        val isLast = idx == points.lastIndex
                                        drawCircle(
                                            color = lineColor,
                                            radius = if (isLast) 5.dp.toPx() else 3.5.dp.toPx(),
                                            center = pt
                                        )
                                        if (!isLast) {
                                            drawCircle(
                                                color = surfaceColor,
                                                radius = 1.8.dp.toPx(),
                                                center = pt
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = points.first().date.removePrefix("${LocalDate.now().year}-"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (latestTarget != null) {
                                        Text(
                                            text = "-- 目標 ${String.format(Locale.ROOT, "%.1f", latestTarget)} kg",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    Text(
                                        text = points.last().date.removePrefix("${LocalDate.now().year}-"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "記錄至少兩天後即可顯示折線趨勢圖；體重紀錄不會自動覆蓋您的每日營養目標。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Input Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("⚖️ 體重 (kg) *") },
                    singleLine = true,
                    shape = fieldShape,
                    colors = softFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("🎯 目標體重 (kg)") },
                    singleLine = true,
                    shape = fieldShape,
                    colors = softFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("紀錄日期：$date")
            }

            // Preview & Sync Profile Card
            if (valid && parsed != null) {
                val preview = profile.copy(weight = parsed)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "同步試算個人營養目標",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "每日 ${preview.targetCalories.toInt()} kcal · 蛋白質 ${preview.targetProteinGrams.toInt()}g · 碳水 ${preview.targetCarbsGrams.toInt()}g · 脂肪 ${preview.targetFatGrams.toInt()}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        FilledTonalButton(
                            onClick = { confirmApply = true },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("套用目標")
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Recent Records Chips
            if (entries.isNotEmpty()) {
                Text(
                    text = "近期紀錄（點選帶入修改）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entries.takeLast(8).reversed().forEach { entry ->
                        val isSelected = entry.date == date
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                date = entry.date
                                weight = entry.weightKg.toString()
                                target = entry.targetKg?.toString() ?: ""
                            },
                            label = { Text("${entry.date.removePrefix("${LocalDate.now().year}-")} · ${entry.weightKg}kg") }
                        )
                    }
                }
            }

            }

            // Sticky Bottom Action Buttons
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("關閉")
                }
                Button(
                    enabled = valid && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                onSave(WeightEntry(date, parsed!!, parsedTarget))
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = "儲存失敗，請再試一次"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("儲存體重紀錄", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (confirmApply) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("更新個人資料與營養目標？") },
            text = {
                Text("會以 ${parsed} kg 更新個人資料，並依目前活動量與目標重新計算。這個操作只套用個人設定；體重紀錄請使用「儲存體重紀錄」。")
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                onApply(parsed!!)
                                confirmApply = false
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = "更新失敗，請再試一次"
                                confirmApply = false
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("確認更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDatePicker) {
        PastDatePickerDialog(
            selectedDate = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
            onDateSelected = { date = it.toString() },
            onDismiss = { showDatePicker = false }
        )
    }
}
