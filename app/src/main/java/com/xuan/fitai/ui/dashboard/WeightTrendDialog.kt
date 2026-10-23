package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.model.WeightEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun WeightTrendDialog(profile: UserProfile, entries: List<WeightEntry>, onDismiss: () -> Unit,
    onSave: suspend (WeightEntry) -> Unit, onApply: suspend (Float) -> Unit) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weight by rememberSaveable { mutableStateOf(entries.lastOrNull()?.weightKg?.toString() ?: profile.weight.toString()) }
    var target by rememberSaveable { mutableStateOf(entries.lastOrNull()?.targetKg?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parsed = weight.toFloatOrNull()
    val parsedTarget = target.toFloatOrNull()
    val valid = parsed?.let { it.isFinite() && it > 0 && it <= 1000 } == true &&
        (target.isBlank() || parsedTarget?.let { it.isFinite() && it > 0 && it <= 1000 } == true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("體重趨勢與目標") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
            val points = entries.takeLast(30)
            val color = MaterialTheme.colorScheme.primary
            if (points.size >= 2) Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val low = points.minOf { it.weightKg }; val span = (points.maxOf { it.weightKg } - low).coerceAtLeast(1f)
                points.zipWithNext().forEachIndexed { index, (a, b) ->
                    fun point(i: Int, w: Float) = Offset(size.width * i / (points.size - 1), size.height * (0.9f - 0.8f * (w - low) / span))
                    drawLine(color, point(index, a.weightKg), point(index + 1, b.weightKg), strokeWidth = 4f)
                }
            }
            Text(if (points.isEmpty()) "記錄至少兩天後顯示趨勢；體重紀錄不會自動更動營養目標。" else "最近 ${points.size} 次：${points.first().weightKg} → ${points.last().weightKg} kg")
            entries.lastOrNull()?.let { latest -> latest.targetKg?.let { goal ->
                Text("目標 $goal kg · 距離目標 ${String.format(java.util.Locale.ROOT, "%.1f", kotlin.math.abs(latest.weightKg - goal))} kg")
            } }
            TextButton(onClick = { showDatePicker = true }) { Text(date) }
            OutlinedTextField(weight, { weight = it }, label = { Text("體重 kg") })
            OutlinedTextField(target, { target = it }, label = { Text("目標體重 kg（選填）") })
            if (valid) {
                val preview = profile.copy(weight = parsed!!)
                Text("若套用到個人資料：每日目標 ${preview.targetCalories.toInt()} kcal；蛋白質 ${preview.targetProteinGrams.toInt()}g、碳水 ${preview.targetCarbsGrams.toInt()}g、脂肪 ${preview.targetFatGrams.toInt()}g。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { confirmApply = true }, enabled = !busy) { Text("預覽並更新個人目標") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            entries.takeLast(10).reversed().forEach { entry ->
                TextButton(onClick = { date = entry.date; weight = entry.weightKg.toString(); target = entry.targetKg?.toString() ?: "" }) { Text("${entry.date} · ${entry.weightKg} kg（點選修改）") }
            }
        }
    }, confirmButton = { TextButton(enabled = valid && !busy, onClick = {
        busy = true; scope.launch {
            try { onSave(WeightEntry(date, parsed!!, parsedTarget)); onDismiss() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "儲存失敗，請再試一次" }
            finally { busy = false }
        }
    }) { Text("儲存體重紀錄") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("關閉") } })
    if (confirmApply) AlertDialog(onDismissRequest = { confirmApply = false }, title = { Text("更新個人資料與營養目標？") },
        text = { Text("會以 ${parsed} kg 更新個人資料，並依目前活動量與目標重新計算。這個操作只套用個人設定；體重紀錄請使用「儲存體重紀錄」。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try { onApply(parsed!!); confirmApply = false; onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "更新失敗，請再試一次"; confirmApply = false }
                finally { busy = false }
            }
        }) { Text("確認更新") } }, dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("取消") } })
    if (showDatePicker) com.xuan.fitai.ui.components.PastDatePickerDialog(LocalDate.parse(date), { date = it.toString() }) { showDatePicker = false }

}
