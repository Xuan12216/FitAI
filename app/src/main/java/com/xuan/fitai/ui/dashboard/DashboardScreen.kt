package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.MealPeriod
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.ui.adaptive.AdaptivePanes
import com.xuan.fitai.ui.adaptive.LocalAdaptiveInfo
import com.xuan.fitai.ui.components.NutritionReferenceBadge
import com.xuan.fitai.ui.components.PastDatePickerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HISTORY_HEADER_KEY = "history-header"

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
    val adaptive = LocalAdaptiveInfo.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showManualAdd by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableIntStateOf(0) }
    var copyMeal by rememberSaveable { mutableStateOf(false) }
    var waterBusy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Meal?>(null) }
    var expandedGroups by rememberSaveable(mealDate.toString()) { mutableStateOf(arrayListOf<String>()) }
    var collapsedGroups by rememberSaveable(mealDate.toString()) { mutableStateOf(arrayListOf<String>()) }


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
        fun summaryItemCount(): Int {
            var count = 4 // today-header, record-actions, daily-advice, today-progress
            if (modelLoadState != ModelLoadState.Loaded) count++
            if (adviceUpdatedAt != null) count++
            return count
        }

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
                    Button(
                        onClick = { editorId = 0; copyMeal = false; showManualAdd = true },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("記錄一餐")
                    }
                    OutlinedButton(
                        onClick = onNavigateToScanner,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照辨識")
                    }
                }
            }
            item(key = "daily-advice") {
                DailyAdviceCard(
                    advice = advice.body,
                    generating = generating,
                    hasModel = modelName != null,
                    onRefresh = viewModel::refreshDailyAdvice,
                    onStop = viewModel::stopDailyAdvice,
                    onSetup = onNavigateToModelSetup,
                    isModelLoading = modelLoadState == ModelLoadState.Loading,
                    title = advice.title,
                    onAsk = {
                        onAskAdvice("根據這段今日建議提供具體替代食物：\n${advice.title}\n${advice.body}")
                    },
                    onRecord = { editorId = 0; copyMeal = false; showManualAdd = true }
                )
            }
            adviceUpdatedAt?.let { timestamp ->
                item(key = "advice-update") {
                    Text(
                        "建議更新：${Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "today-progress") {
                TodayProgressCard(
                    profile = profile,
                    meals = meals,
                    waterConsumed = water.waterConsumedMl,
                    waterTarget = water.waterTargetMl,
                    waterBusy = waterBusy,
                    onAddWater = { amount ->
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
                    },
                    onSettings = onNavigateToReminders,
                    servingMl = water.waterServingMl
                )
            }
        }

        fun LazyListScope.historyItems() {
            val friendlyMealDate = when (mealDate) {
                today -> "今天 (${mealDate.monthValue}/${mealDate.dayOfMonth})"
                today.minusDays(1) -> "昨天 (${mealDate.monthValue}/${mealDate.dayOfMonth})"
                else -> "${mealDate.monthValue}月${mealDate.dayOfMonth}日"
            }
            item(key = HISTORY_HEADER_KEY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (mealDate) {
                            today -> "今日餐點"
                            today.minusDays(1) -> "昨日餐點"
                            else -> "${mealDate.monthValue}/${mealDate.dayOfMonth} 餐點"
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${selectedMeals.size} 筆紀錄",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "meal-date") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(onClick = { viewModel.selectMealDate(mealDate.minusDays(1)) }) {
                            Icon(Icons.Default.ChevronLeft, "前一天")
                        }
                        TextButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(friendlyMealDate, fontWeight = FontWeight.SemiBold)
                        }
                        if (mealDate != today) {
                            TextButton(onClick = { viewModel.selectMealDate(today) }) {
                                Text("回到今天")
                            }
                        }
                        IconButton(
                            enabled = mealDate < today,
                            onClick = { viewModel.selectMealDate(mealDate.plusDays(1)) }
                        ) {
                            Icon(Icons.Default.ChevronRight, "後一天")
                        }
                    }
                }
            }
            if (selectedMeals.isEmpty()) {
                item {
                    OutlinedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (mealDate == today) "從第一餐開始" else "這天還沒有餐點紀錄",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (mealDate == today) "記錄餐點，就能看見今天的營養進度。" else "可補登當天的餐點與份量。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = { editorId = 0; copyMeal = false; showManualAdd = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("新增餐點")
                            }
                        }
                    }
                }
            } else {
                selectedMeals.groupBy { it.mealType }
                    .toList().sortedBy { (type, _) -> MealPeriod.ordered.indexOf(type).let { if (it < 0) Int.MAX_VALUE else it } }
                    .forEach { (type, records) ->
                        val expanded = mealGroupExpanded(records.size, type in expandedGroups, type in collapsedGroups)
                        mealGroupItems(
                            type = type, records = records, expanded = expanded,
                            onToggle = {
                                if (expanded) {
                                    expandedGroups = ArrayList(expandedGroups.filterNot { it == type })
                                    collapsedGroups = ArrayList((collapsedGroups + type).distinct())
                                } else {
                                    collapsedGroups = ArrayList(collapsedGroups.filterNot { it == type })
                                    expandedGroups = ArrayList((expandedGroups + type).distinct())
                                }
                            },
                            onDelete = { pendingDelete = it },
                            onEdit = { editorId = it.id; copyMeal = false; showManualAdd = true },
                            onRepeat = { editorId = it.id; copyMeal = true; showManualAdd = true }
                        )
                    }
            }

            item(key = "weight-trend") {
                val latestWeight = weights.lastOrNull()
                Card(
                    onClick = { showWeight = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MonitorWeight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("體重與目標追蹤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = latestWeight?.let {
                                    val targetSuffix = it.targetKg?.let { g -> " · 目標 ${g} kg" } ?: ""
                                    "最新 ${it.weightKg} kg (${it.date})$targetSuffix"
                                } ?: "點擊開始記錄每日體重與查看趨勢折線圖",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "開啟體重趨勢",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item(key = "weekly-overview") {
                WeeklyOverview(week, profile.targetCalories, mealDate) { date ->
                    viewModel.selectMealDate(date)
                    scope.launch {
                        if (adaptive.expanded || adaptive.folds.any { it.isSeparating }) {
                            historyScroll.animateScrollToItem(0)
                        } else {
                            val targetIndex = compactScroll.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == HISTORY_HEADER_KEY }?.index
                                ?: summaryItemCount()
                            compactScroll.animateScrollToItem(targetIndex)
                        }
                    }
                }
            }
        }

        val pagePadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = 24.dp + bottomContentPadding
        )
        AdaptivePanes(
            primary = {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = pagePadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { summaryItems() }
            },
            secondary = {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = historyScroll,
                    contentPadding = pagePadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { historyItems() }
            },
            compact = {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = compactScroll,
                    contentPadding = pagePadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    summaryItems()
                    historyItems()
                }
            }
        )
    }

    if (showDatePicker) {
        PastDatePickerDialog(mealDate, viewModel::selectMealDate) { showDatePicker = false }
    }
    if (showWeight) {
        WeightTrendDialog(profile, weights, { showWeight = false }, viewModel::saveWeight, viewModel::applyWeight)
    }
    val editingMeal = history.firstOrNull { it.id == editorId }
    if (showManualAdd && (editorId == 0 || editingMeal != null)) {
        key(editorId, copyMeal) {
            ManualMealDialog(viewModel, editingMeal, copyMeal, mealDate) {
                showManualAdd = false
            }
        }
    }
    pendingDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除這筆餐點？") },
            text = { Text(meal.name) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMeal(meal); pendingDelete = null }) {
                    Text("刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("保留")
                }
            }
        )
    }
}

@Composable
private fun MealLogRow(
    meal: Meal,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Row: Name + Time/Portion + Quick Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (meal.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "常用餐點",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val time = Instant.ofEpochMilli(meal.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    text = "$time  ·  ${meal.portionSize}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = com.xuan.fitai.nutrition.nutritionEstimatorLabel(meal.nutritionReferenceJson, meal.isAiEstimated)
                        ?.let { "$it 估算" } ?: "手動記錄",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact Action Pill & Delete Button
            FilledTonalButton(
                onClick = onRepeat,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("再記一次", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "刪除${meal.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Middle Row: Calories + Macro Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${meal.calories.toInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroBadge("蛋白 ${meal.protein.toInt()}g")
                MacroBadge("碳水 ${meal.carbs.toInt()}g")
                MacroBadge("脂肪 ${meal.fat.toInt()}g")
            }
        }

        NutritionReferenceBadge(meal.nutritionReferenceJson)

        if (!meal.aiAdvice.isNullOrBlank()) {
            val cleanAdvice = remember(meal.aiAdvice) {
                com.xuan.fitai.ui.components.parseMarkdownToAnnotatedString(
                    com.xuan.fitai.ui.components.extractAnswerWithoutThinking(meal.aiAdvice)
                )
            }
            Text(
                text = cleanAdvice,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MacroBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

internal fun mealGroupExpanded(count: Int, explicitlyExpanded: Boolean, explicitlyCollapsed: Boolean): Boolean =
    explicitlyExpanded || (count <= 3 && !explicitlyCollapsed)

internal fun LazyListScope.mealGroupItems(
    type: String,
    records: List<Meal>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (Meal) -> Unit,
    onEdit: (Meal) -> Unit,
    onRepeat: (Meal) -> Unit,
) {
    item(key = "type-$type") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Surface(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().semantics {
                    heading()
                    stateDescription = if (expanded) "已展開" else "已收合"
                },
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                when (type) {
                                    "早餐" -> Icons.Default.BreakfastDining
                                    "午餐" -> Icons.Default.LunchDining
                                    "晚餐" -> Icons.Default.DinnerDining
                                    "宵夜" -> Icons.Default.Bedtime
                                    else -> Icons.Default.Restaurant
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${records.size} 筆餐點", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${records.sumOf { it.calories.toDouble() }.toInt()} kcal",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            if (expanded) "收合$type" else "展開$type")
                    }
                    Text(
                        String.format(Locale.ROOT, "蛋白質 %.1fg · 碳水 %.1fg · 脂肪 %.1fg",
                            records.sumOf { it.protein.toDouble() }, records.sumOf { it.carbs.toDouble() }, records.sumOf { it.fat.toDouble() }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!expanded) Text(
                        records.take(3).joinToString("、") { it.name } + if (records.size > 3) " 等 ${records.size} 筆，點擊展開" else "，點擊展開",
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                records.forEachIndexed { index, meal ->
                    key(meal.id) {
                        MealLogRow(meal, onDelete = { onDelete(meal) }, onEdit = { onEdit(meal) }, onRepeat = { onRepeat(meal) })
                        if (index < records.lastIndex) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
