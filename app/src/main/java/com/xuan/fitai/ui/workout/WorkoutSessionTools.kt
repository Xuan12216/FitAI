package com.xuan.fitai.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import com.xuan.fitai.ui.adaptive.AdaptiveModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.WorkoutPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutSessionTools(
    viewModel: WorkoutViewModel,
    plans: List<WorkoutPlan>
) {
    val recordError by viewModel.recordError.collectAsState()
    val logs by viewModel.setHistory.collectAsState()
    val deadline by viewModel.restDeadline.collectAsState()
    var seconds by remember { mutableIntStateOf(0) }
    var totalRestSeconds by rememberSaveable { mutableIntStateOf(60) }
    var showRecord by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(deadline) {
        while (deadline != null) {
            seconds = ((deadline!! - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            if (seconds == 0) break
            delay(250)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "訓練紀錄與組間休息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { showRecord = true },
                    enabled = plans.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("記錄組數")
                }
            }

            recordError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Active Rest Timer Banner
            if (deadline != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (seconds > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (seconds > 0) {
                                    "組間休息倒數  ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                                } else {
                                    "🔔 休息結束，可以開始下一組！"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            TextButton(
                                onClick = viewModel::cancelRest,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("結束計時")
                            }
                        }
                        if (seconds > 0 && totalRestSeconds > 0) {
                            LinearProgressIndicator(
                                progress = { (seconds.toFloat() / totalRestSeconds).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Quick Rest Duration Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "快速計時：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(30, 60, 90).forEach { duration ->
                    AssistChip(
                        onClick = {
                            totalRestSeconds = duration
                            viewModel.startRest(duration)
                        },
                        label = { Text("${duration} 秒") }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val start = LocalDate.now().with(DayOfWeek.MONDAY).toString()
            val weeklySetCount = logs.count { it.date >= start && it.date <= LocalDate.now().toString() }
            Text(
                text = "本週已完成 $weeklySetCount 組訓練",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (logs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    logs.take(5).forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.exerciseName} · 第 ${entry.setNumber} 組",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = entry.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (entry.weightKg > 0) "${entry.weightKg} kg" else "徒手"} × ${entry.reps} 次",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecord && plans.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var planId by rememberSaveable { mutableIntStateOf(plans.first().id) }
        var number by rememberSaveable { mutableStateOf("1") }
        var weight by rememberSaveable { mutableStateOf("0") }
        var reps by rememberSaveable { mutableStateOf("10") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val plan = plans.firstOrNull { it.id == planId } ?: plans.first()
        val scope = rememberCoroutineScope()
        val valid = number.toIntOrNull()?.let { it in 1..plan.sets } == true &&
            weight.toFloatOrNull()?.let { it.isFinite() && it >= 0 && it <= 1000 } == true &&
            reps.toIntOrNull()?.let { it in 1..1000 } == true

        AdaptiveModalBottomSheet(
            onDismissRequest = { showRecord = false },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "記錄訓練組數",
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
                    Text(
                        text = "選擇訓練動作",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        plans.forEach { p ->
                            FilterChip(
                                selected = p.id == plan.id,
                                onClick = {
                                    planId = p.id
                                    number = "1"
                                },
                                label = { Text(p.exerciseName) }
                            )
                        }
                    }

                    val last = logs.firstOrNull { it.planId == plan.id && it.date < LocalDate.now().toString() }
                    if (last != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "上次表現（${last.date}）：${last.weightKg} kg × ${last.reps} 次",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("組別 (1–${plan.sets})") },
                            singleLine = true,
                            shape = fieldShape,
                            colors = softFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("重量 kg (徒手 0)") },
                            singleLine = true,
                            shape = fieldShape,
                            colors = softFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { reps = it },
                            label = { Text("次數") },
                            singleLine = true,
                            shape = fieldShape,
                            colors = softFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRecord = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        enabled = valid && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    viewModel.recordSet(plan, number.toInt(), weight.toFloat(), reps.toInt())
                                    showRecord = false
                                    totalRestSeconds = 60
                                    viewModel.startRest(60)
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
                            .weight(1.6f)
                            .height(48.dp)
                    ) {
                        Text("儲存並休息 60 秒", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
