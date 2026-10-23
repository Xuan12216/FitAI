package com.xuan.fitai.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.WorkoutPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek

@Composable
internal fun WorkoutSessionTools(viewModel: WorkoutViewModel, plans: List<WorkoutPlan>) {
    val recordError by viewModel.recordError.collectAsState()
    val logs by viewModel.setHistory.collectAsState()
    val deadline by viewModel.restDeadline.collectAsState()
    var seconds by remember { mutableIntStateOf(0) }
    var showRecord by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deadline) {
        while (deadline != null) {
            seconds = ((deadline!! - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            if (seconds == 0) break
            delay(250)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("訓練紀錄與組間休息", style = MaterialTheme.typography.titleMedium)
            recordError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            deadline?.let {
                Text(if (seconds > 0) "休息剩餘 ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "休息結束，可以開始下一組",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { duration -> AssistChip(onClick = { viewModel.startRest(duration) }, label = { Text("$duration 秒") }) }
                if (deadline != null) AssistChip(onClick = viewModel::cancelRest, label = { Text("取消計時") })
            }
            FilledTonalButton(onClick = { showRecord = true }, enabled = plans.isNotEmpty()) { Text("記錄一組重量與次數") }
            val start = LocalDate.now().with(DayOfWeek.MONDAY).toString()
            Text("本週已記錄 ${logs.count { it.date >= start && it.date <= LocalDate.now().toString() }} 組；各週獨立保留，不會覆蓋過去紀錄。", style = MaterialTheme.typography.bodySmall)
            logs.take(5).forEach { entry ->
                Text("${entry.date} · ${entry.exerciseName} · 第 ${entry.setNumber} 組 · ${if (entry.weightKg > 0) "${entry.weightKg} kg" else "未填重量"} × ${entry.reps} 次", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showRecord && plans.isNotEmpty()) {
        var planId by rememberSaveable { mutableIntStateOf(plans.first().id) }
        var number by rememberSaveable { mutableStateOf("1") }
        var weight by rememberSaveable { mutableStateOf("0") }
        var reps by rememberSaveable { mutableStateOf("10") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val plan = plans.firstOrNull { it.id == planId } ?: plans.first()
        val scope = rememberCoroutineScope()
        val valid = number.toIntOrNull()?.let { it in 1..plan.sets } == true && weight.toFloatOrNull()?.let { it.isFinite() && it >= 0 && it <= 1000 } == true && reps.toIntOrNull()?.let { it in 1..1000 } == true
        AlertDialog(onDismissRequest = { showRecord = false }, title = { Text("記錄訓練組數") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plans.forEach { p -> FilterChip(selected = p.id == plan.id, onClick = { planId = p.id; number = "1" }, label = { Text(p.exerciseName) }) }
                }
                val last = logs.firstOrNull { it.planId == plan.id && it.date < LocalDate.now().toString() }
                last?.let { Text("上次：${it.weightKg} kg × ${it.reps} 次（${it.date}）", style = MaterialTheme.typography.bodySmall) }
                Text("同一天、同動作、同組數再次儲存會更新該組。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(number, { number = it }, label = { Text("第幾組（1–${plan.sets}）") })
                OutlinedTextField(weight, { weight = it }, label = { Text("重量 kg（徒手可填 0）") })
                OutlinedTextField(reps, { reps = it }, label = { Text("實際次數") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = valid && !busy, onClick = {
            busy = true; scope.launch {
                try { viewModel.recordSet(plan, number.toInt(), weight.toFloat(), reps.toInt()); showRecord = false; viewModel.startRest(60) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "儲存失敗，請再試一次" }
                finally { busy = false }
            }
        }) { Text("儲存並休息 60 秒") } }, dismissButton = { TextButton(onClick = { showRecord = false }) { Text("取消") } })
    }
}
