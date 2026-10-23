package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import com.xuan.fitai.ui.adaptive.AdaptivePanes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuan.fitai.data.model.ModelLoadState
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
    onNavigateToProfile: () -> Unit,
    onAskAdvice: (String) -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val meals by viewModel.todayMeals.collectAsStateWithLifecycle()
    val history by viewModel.mealHistory.collectAsStateWithLifecycle()
    val selectedMeals by viewModel.selectedMeals.collectAsStateWithLifecycle()
    val mealDate by viewModel.mealDate.collectAsStateWithLifecycle()
    val advice by viewModel.dailyAdvice.collectAsStateWithLifecycle()
    val adviceUpdatedAt by viewModel.adviceUpdatedAt.collectAsStateWithLifecycle()
    val generating by viewModel.isAiAdviceGenerating.collectAsStateWithLifecycle()
    val modelName by viewModel.loadedModelName.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val water by viewModel.waterSettings.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showWeight by rememberSaveable { mutableStateOf(false) }
    val week by viewModel.weekSummary.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val compactScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val adaptive = com.xuan.fitai.ui.adaptive.LocalAdaptiveInfo.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showManualAdd by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableIntStateOf(0) }
    var copyMeal by rememberSaveable { mutableStateOf(false) }
    var waterBusy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Meal?>(null) }

    DisposableEffect(viewModel, showManualAdd) {
        viewModel.setAdviceGenerationActive(!showManualAdd)
        onDispose { viewModel.setAdviceGenerationActive(false) }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomContentPadding)) },
        topBar = {
            key(statusBarHeight) {
                TopAppBar(
                    title = { Text("FitAI", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToSetup) { Icon(Icons.Default.Tune, "外觀與 AI 設定") }
                        IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.AccountCircle, "編輯個人資料") }
                    },
                    expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    contentPadding = PaddingValues(top = statusBarHeight),
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        fun LazyListScope.summaryItems() {
            if (modelLoadState != ModelLoadState.Loaded) {
                item(key = "model-status") {
                    ModelStatusBanner(modelLoadState, modelName, onNavigateToModelSetup)
                }
            }
            item(key = "today-header") {
                Text(
                    "${today.format(DateTimeFormatter.ofPattern("M/d EEEE", Locale.TRADITIONAL_CHINESE))}  ·  目標：${profile.goal}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "record-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { editorId = 0; copyMeal = false; showManualAdd = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("記錄一餐")
                    }
                    OutlinedButton(onClick = {
                        onNavigateToScanner()
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照辨識")
                    }
                }
            }
            item(key = "daily-advice") { DailyAdviceCard(advice.body, generating, modelName != null, viewModel::refreshDailyAdvice, viewModel::stopDailyAdvice, onNavigateToModelSetup, isModelLoading = modelLoadState == ModelLoadState.Loading, title = advice.title, onAsk = { onAskAdvice("根據這段今日建議提供具體替代食物：\n${advice.title}\n${advice.body}") }, onRecord = { editorId = 0; copyMeal = false; showManualAdd = true }) }
            adviceUpdatedAt?.let { timestamp -> item(key = "advice-update") {
                Text("建議更新：${Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item(key = "today-progress") {
                TodayProgressCard(profile, meals, water.waterConsumedMl, water.waterTargetMl, waterBusy, onAddWater = { amount ->
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
                }, onSettings = onNavigateToReminders, servingMl = water.waterServingMl)
            }
        }
        fun LazyListScope.historyItems() {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mealDate == today) "今日餐點" else "$mealDate 餐點", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${selectedMeals.size} 筆紀錄", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "meal-date") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectMealDate(mealDate.minusDays(1)) }) { Icon(Icons.Default.ChevronLeft, "前一天") }
                    TextButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) { Text(mealDate.toString()) }
                    TextButton(onClick = { viewModel.selectMealDate(today) }) { Text("今天") }
                    IconButton(enabled = mealDate < today, onClick = { viewModel.selectMealDate(mealDate.plusDays(1)) }) { Icon(Icons.Default.ChevronRight, "後一天") }
                }
            }
            if (selectedMeals.isEmpty()) {
                item {
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (mealDate == today) "從第一餐開始" else "這天還沒有餐點紀錄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (mealDate == today) "記錄餐點，就能看見今天的營養進度。" else "可補登當天的餐點與份量。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { editorId = 0; copyMeal = false; showManualAdd = true }) { Text("新增餐點") }
                        }
                    }
                }
            } else {
                selectedMeals.groupBy { it.mealType }.forEach { (type, records) ->
                    item(key = "type-$type") { Text(type, style = MaterialTheme.typography.titleSmall) }
                    items(records, key = { "meal-${it.id}" }) { meal ->
                        MealLogCard(meal, onDelete = { pendingDelete = meal }, onEdit = {
                            editorId = meal.id; copyMeal = false; showManualAdd = true
                        }, onRepeat = { editorId = meal.id; copyMeal = true; showManualAdd = true })
                    }
                }
            }
            item(key = "weight-trend") {
                OutlinedCard(onClick = { showWeight = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("體重與目標", style = MaterialTheme.typography.titleMedium)
                            Text(weights.lastOrNull()?.let { "${it.weightKg} kg · ${it.date}" } ?: "開始記錄體重趨勢")
                        }
                        Icon(Icons.Default.ShowChart, "開啟體重趨勢")
                    }
                }
            }
            item(key = "weekly-overview") { WeeklyOverview(week, profile.targetCalories, mealDate) { date ->
                viewModel.selectMealDate(date)
                scope.launch {
                    if (adaptive.expanded || adaptive.folds.any { it.isSeparating }) historyScroll.animateScrollToItem(0)
                    else compactScroll.animateScrollToItem(4 + (if (modelLoadState == ModelLoadState.Loaded) 0 else 1) + (if (adviceUpdatedAt != null) 1 else 0))
                }
            } }
        }
        val pagePadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp + bottomContentPadding)
        AdaptivePanes(
            primary = { LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) { summaryItems() } },
            secondary = { LazyColumn(Modifier.fillMaxSize(), state = historyScroll, contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) { historyItems() } },
            compact = { LazyColumn(Modifier.fillMaxSize(), state = compactScroll, contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) { summaryItems(); historyItems() } }
        )
    }
    if (showDatePicker) com.xuan.fitai.ui.components.PastDatePickerDialog(mealDate, viewModel::selectMealDate) { showDatePicker = false }
    if (showWeight) WeightTrendDialog(profile, weights, { showWeight = false }, viewModel::saveWeight, viewModel::applyWeight)
    if (showManualAdd) key(editorId, copyMeal) {
        ManualMealDialog(viewModel, history.firstOrNull { it.id == editorId }, copyMeal, mealDate) { showManualAdd = false }
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
private fun MealLogCard(meal: Meal, onDelete: () -> Unit, onEdit: () -> Unit, onRepeat: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${if (meal.isFavorite) "★ " else ""}${meal.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val time = Instant.ofEpochMilli(meal.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("$time · ${if (meal.isAiEstimated) "AI 估算" else "手動記錄"}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "刪除${meal.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${meal.portionSize} · ${meal.mealType}", style = MaterialTheme.typography.bodySmall)
            com.xuan.fitai.ui.components.NutritionReferenceBadge(meal.nutritionReferenceJson)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("編輯") }
                TextButton(onClick = onRepeat) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("再記一次") }
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
