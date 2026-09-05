package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuan.fitai.data.model.Meal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToModelSetup: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onResetOnboarding: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val meals by viewModel.todayMeals.collectAsStateWithLifecycle()
    val advice by viewModel.aiAdvice.collectAsStateWithLifecycle()
    val generating by viewModel.isAiAdviceGenerating.collectAsStateWithLifecycle()
    val modelName by viewModel.loadedModelName.collectAsStateWithLifecycle()
    val water by viewModel.waterSettings.collectAsStateWithLifecycle()
    val week by viewModel.weekSummary.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showManualAdd by rememberSaveable { mutableStateOf(false) }
    var showScannerSetup by rememberSaveable { mutableStateOf(false) }
    var waterBusy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Meal?>(null) }

    DisposableEffect(viewModel) {
        viewModel.setAdviceGenerationActive(true)
        onDispose { viewModel.setAdviceGenerationActive(false) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomContentPadding)) },
        topBar = {
            TopAppBar(
                title = { Text("FitAI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSetup) { Icon(Icons.Default.Tune, "外觀與 AI 設定") }
                    IconButton(onClick = onResetOnboarding) { Icon(Icons.Default.AccountCircle, "編輯個人資料") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(today.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.TRADITIONAL_CHINESE)),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("照顧自己，從今天開始", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("今日目標 · ${profile.goal}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { CalorieOverview(profile, meals.sumOf { it.calories.toDouble() }.toFloat()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showManualAdd = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("記錄一餐")
                    }
                    OutlinedButton(onClick = {
                        val file = java.io.File(context.filesDir, "models/food_classifier.tflite")
                        if (file.exists() && file.length() > 0) onNavigateToScanner() else showScannerSetup = true
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照辨識")
                    }
                }
            }
            item { NutritionOverview(profile, meals) }
            item {
                WaterOverview(water.waterConsumedMl, water.waterTargetMl, waterBusy, onAdd = { amount ->
                    scope.launch {
                        val date = today
                        waterBusy = true
                        try {
                            viewModel.changeWater(amount, date)
                            waterBusy = false
                            snackbar.currentSnackbarData?.dismiss()
                            if (snackbar.showSnackbar("已記錄 $amount ml", "撤銷", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                                viewModel.changeWater(-amount, date)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            snackbar.showSnackbar("飲水紀錄更新失敗，請再試一次。")
                        } finally {
                            waterBusy = false
                        }
                    }
                }, onSettings = onNavigateToReminders)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("今日餐點", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${meals.size} 筆紀錄", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (meals.isEmpty()) {
                item {
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("從第一餐開始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("記錄餐點，就能看見今天的營養進度。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showManualAdd = true }) { Text("新增餐點") }
                        }
                    }
                }
            } else {
                items(meals, key = { it.id }) { meal -> MealLogCard(meal, onDelete = { pendingDelete = meal }) }
            }
            item { WeeklyOverview(week) }
            item { DailyAdviceCard(advice, generating, modelName != null, viewModel::refreshDailyAdvice, onNavigateToModelSetup) }
        }
    }
    if (showManualAdd) ManualMealDialog(viewModel) { showManualAdd = false }
    if (showScannerSetup) {
        AlertDialog(
            onDismissRequest = { showScannerSetup = false },
            icon = { Icon(Icons.Default.PhotoCamera, null) },
            title = { Text("啟用拍照辨識") },
            text = { Text("首次使用需要下載食物辨識模型，也可以先手動記錄這一餐。") },
            confirmButton = { TextButton(onClick = { showScannerSetup = false; onNavigateToModelSetup() }) { Text("前往設定") } },
            dismissButton = { TextButton(onClick = { showScannerSetup = false; showManualAdd = true }) { Text("手動記錄") } }
        )
    }
    pendingDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除這筆餐點？") }, text = { Text(meal.name) },
            confirmButton = { TextButton(onClick = { viewModel.deleteMeal(meal); pendingDelete = null }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("保留") } }
        )
    }
}

@Composable
private fun MealLogCard(meal: Meal, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val time = Instant.ofEpochMilli(meal.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("$time · ${if (meal.isAiEstimated) "AI 估算" else "手動記錄"}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "刪除${meal.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${meal.calories.toInt()} kcal", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("蛋白質 ${meal.protein.toInt()}g · 碳水 ${meal.carbs.toInt()}g · 脂肪 ${meal.fat.toInt()}g",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!meal.aiAdvice.isNullOrBlank()) Text(meal.aiAdvice, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
