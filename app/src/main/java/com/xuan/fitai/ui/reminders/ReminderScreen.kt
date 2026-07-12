package com.xuan.fitai.ui.reminders

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xuan.fitai.data.model.MealReminder
import com.xuan.fitai.notification.ReminderScheduler
import java.time.LocalDate
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.app.AlarmManager
import android.provider.Settings
import android.net.Uri
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(viewModel: ReminderViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var isBatteryOptimized by remember { mutableStateOf(false) }
    var isExactAlarmPermissionMissing by remember { mutableStateOf(false) }

    fun checkPermissions() {
        isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        isExactAlarmPermissionMissing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            !alarmManager.canScheduleExactAlarms()
        } else {
            false
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun save(transform: (com.xuan.fitai.data.model.ReminderSettings) -> com.xuan.fitai.data.model.ReminderSettings) {
        viewModel.update(transform)
        // Schedule on the next composition after the settings flow reflects the update.
    }

    androidx.compose.runtime.LaunchedEffect(settings) { ReminderScheduler.scheduleAll(context, settings) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("提醒與習慣", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isBatteryOptimized) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("忽略電池最佳化", fontWeight = FontWeight.Bold)
                            Text("建議開啟「忽略電池最佳化」以確保提醒能準時送達。", style = MaterialTheme.typography.bodySmall)
                        }
                        FilterChip(
                            selected = false,
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            label = { Text("前往設定") }
                        )
                    }
                }
            }

            if (isExactAlarmPermissionMissing) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("需要精準鬧鐘權限", fontWeight = FontWeight.Bold)
                            Text("開啟精準鬧鐘以確保提醒時間不會被系統延遲。", style = MaterialTheme.typography.bodySmall)
                        }
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            label = { Text("允許") }
                        )
                    }
                }
            }

            if (!notificationPermissionGranted) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("尚未允許通知", fontWeight = FontWeight.Bold)
                            Text("開啟後才能收到用餐與喝水提醒。", style = MaterialTheme.typography.bodySmall)
                        }
                        FilterChip(selected = false, onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, label = { Text("允許") })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(Icons.Default.Notifications, "用餐提醒", settings.mealRemindersEnabled) {
                        save { it.copy(mealRemindersEnabled = !it.mealRemindersEnabled) }
                    }
                    Text("每天吃幾餐", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(2, 3, 4, 5), key = { it }) { count ->
                            FilterChip(
                                selected = settings.meals.size == count,
                                onClick = { viewModel.setMealCount(count) },
                                label = { Text("$count 餐") },
                                colors = reminderSelectionColors()
                            )
                        }
                    }
                    HorizontalDivider()
                    settings.meals.forEachIndexed { index, meal ->
                        MealRow(meal = meal, onChanged = { updated ->
                            save { current -> current.copy(meals = current.meals.toMutableList().also { it[index] = updated }) }
                        })
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(Icons.Default.Notifications, "喝水提醒", settings.waterRemindersEnabled) {
                        save { it.copy(waterRemindersEnabled = !it.waterRemindersEnabled) }
                    }
                    val todayConsumed = if (settings.waterProgressDate == LocalDate.now().toString()) settings.waterConsumedMl else 0
                    val progress = (todayConsumed.toFloat() / settings.waterTargetMl.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Text("今日喝水  $todayConsumed / ${settings.waterTargetMl} ml", fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = false, onClick = { viewModel.addWater(250) }, label = { Text("+250 ml") })
                        FilterChip(selected = false, onClick = { ReminderScheduler.sendTestWaterReminder(context) }, label = { Text("測試提醒") })
                    }
                    NumberRow("每日目標", settings.waterTargetMl, "ml", listOf(1500, 2000, 2500, 3000)) { value -> save { it.copy(waterTargetMl = value) } }
                    NumberRow("提醒頻率", settings.waterIntervalMinutes, "分鐘", listOf(30, 60, 90, 120)) { value -> save { it.copy(waterIntervalMinutes = value) } }
                    Text("提醒時段", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HourPicker("開始時間", settings.waterStartHour, Modifier.weight(1f)) { value -> save { it.copy(waterStartHour = value) } }
                        HourPicker("結束時間", settings.waterEndHour, Modifier.weight(1f)) { value -> save { it.copy(waterEndHour = value) } }
                    }
                    Text("在這段時間外不會提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun MealRow(meal: MealReminder, onChanged: (MealReminder) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(meal.label, fontWeight = FontWeight.SemiBold)
            Text("%02d:%02d".format(meal.hour, meal.minute), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            TimePickerDialog(context, { _, hour, minute -> onChanged(meal.copy(hour = hour, minute = minute)) }, meal.hour, meal.minute, true).show()
        }) { Text("%02d:%02d".format(meal.hour, meal.minute)) }
        Spacer(Modifier.width(8.dp))
        Switch(checked = meal.enabled, onCheckedChange = { onChanged(meal.copy(enabled = it)) })
    }
}

@Composable
private fun HourPicker(label: String, hour: Int, modifier: Modifier, onSelected: (Int) -> Unit) {
    val context = LocalContext.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = {
            TimePickerDialog(context, { _, selectedHour, _ -> onSelected(selectedHour) }, hour, 0, true).show()
        }) { Text("%02d:00".format(hour)) }
    }
}

@Composable
private fun NumberRow(label: String, current: Int, unit: String, options: List<Int>, onSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it }) { option ->
                FilterChip(
                    selected = current == option,
                    onClick = { onSelected(option) },
                    label = { Text("$option $unit") },
                    colors = reminderSelectionColors()
                )
            }
        }
    }
}

@Composable
private fun reminderSelectionColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)
