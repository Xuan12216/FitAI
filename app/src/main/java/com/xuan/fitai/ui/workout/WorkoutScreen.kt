package com.xuan.fitai.ui.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.xuan.fitai.data.model.WorkoutPlan
import com.xuan.fitai.ui.components.ThinkingContent
import com.xuan.fitai.util.HealthData
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onNavigateBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val plans by viewModel.workoutPlans.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val generationThinking by viewModel.generationThinking.collectAsState()
    val workoutSummary by viewModel.workoutSummary.collectAsState()
    val healthData by viewModel.healthData.collectAsState()
    val hasHealthPermissions by viewModel.hasHealthPermissions.collectAsState()

    var showAiDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedPlanForEdit by remember { mutableStateOf<WorkoutPlan?>(null) }

    // Health Connect Permission Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkHealthConnectStatus()
        viewModel.loadHealthData()
    }

    // Refresh Health Connect data when entering screen or returning from Play Store/Settings
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkHealthConnectStatus()
                viewModel.loadHealthData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val dayOrder = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val today = currentDayOfWeek()
    val groupedPlans = plans.groupBy { it.dayOfWeek }
    val sortedDays = dayOrder.filter { groupedPlans.containsKey(it) }
    val todayPlans = groupedPlans[today].orEmpty()
    val completedCount = plans.count { it.isCompleted }
    val completionRatio = if (plans.isEmpty()) 0f else completedCount.toFloat() / plans.size
    val weeklySets = plans.sumOf { it.sets }
    val trainingDayCount = groupedPlans.keys.size
    val topMuscleGroups = plans
        .groupBy { it.targetMuscleGroup }
        .mapValues { (_, items) -> items.sumOf { it.sets } }
        .entries
        .sortedByDescending { it.value }
        .take(4)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("運動計畫", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiDialog = true }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "AI 重新設計計畫")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedPlanForEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增運動")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isGenerating && generationThinking.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "✨ 本地 Gemma 4 正在為您排程運動計畫...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "可能需要幾秒鐘，請稍候",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isGenerating) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "AI 運動計畫排程中，請稍候...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    item {
                        WorkoutPlanHeader(
                            goal = profile.goal,
                            experience = profile.workoutExperience,
                            weeklySets = weeklySets,
                            trainingDayCount = trainingDayCount,
                            completionRatio = completionRatio,
                            completedCount = completedCount,
                            totalCount = plans.size
                        )
                    }



                    // Show the card while the combined plan + summary request is running.
                    if (!workoutSummary.isNullOrBlank() || isSummarizing) {
                        item {
                            AISummaryCard(
                                summary = workoutSummary,
                                isSummarizing = isSummarizing
                            )
                        }
                    }

                    item {
                        val context = LocalContext.current
                        val sdkStatus by viewModel.healthConnectSdkStatus.collectAsState()
                        HealthConnectPreviewCard(
                            sdkStatus = sdkStatus,
                            hasPermissions = hasHealthPermissions,
                            healthData = healthData,
                            onRequestPermissions = {
                                if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                                    permissionsLauncher.launch(viewModel.getHealthConnectHelper().permissions)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                                        setPackage("com.android.vending")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
                                        context.startActivity(webIntent)
                                    }
                                }
                            }
                        )
                    }

                    item {
                        TodayWorkoutCard(
                            today = today,
                            plans = todayPlans,
                            onToggle = viewModel::toggleWorkoutPlan,
                            onEdit = {
                                selectedPlanForEdit = it
                                showAddEditDialog = true
                            },
                            onDelete = viewModel::deleteWorkoutPlan
                        )
                    }

                    item {
                        MuscleBalanceCard(topMuscleGroups = topMuscleGroups)
                    }

                    sortedDays.forEach { day ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = day,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        items(groupedPlans[day].orEmpty(), key = { it.id }) { item ->
                            WorkoutPlanRow(
                                plan = item,
                                onToggle = { viewModel.toggleWorkoutPlan(item) },
                                onEdit = {
                                    selectedPlanForEdit = item
                                    showAddEditDialog = true
                                },
                                onDelete = { viewModel.deleteWorkoutPlan(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    // AI Generation Configuration Dialog
    if (showAiDialog) {
        var daysPerWeek by remember { mutableStateOf(3) }
        var preference by remember { mutableStateOf("阻力訓練") }

        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI 智能設計運動計畫", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "本地 Gemma 4 將根據您的目標「${profile.goal}」與身型資料，客製化設計一週運動計畫。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column {
                        Text("每週訓練天數：$daysPerWeek 天", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = daysPerWeek.toFloat(),
                            onValueChange = { daysPerWeek = it.toInt() },
                            valueRange = 2f..6f,
                            steps = 3
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("訓練偏好：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("阻力訓練", "有氧減脂", "徒手健體", "混合訓練").forEach { pref ->
                                FilterChip(
                                    selected = preference == pref,
                                    onClick = { preference = pref },
                                    label = { Text(pref) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiDialog = false
                        viewModel.generateAIWorkoutPlan(daysPerWeek, preference)
                    }
                ) {
                    Text("智能生成")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Add / Edit Manual Workout Dialog
    if (showAddEditDialog) {
        var dayOfWeek by remember { mutableStateOf(selectedPlanForEdit?.dayOfWeek ?: "星期一") }
        var exerciseName by remember { mutableStateOf(selectedPlanForEdit?.exerciseName ?: "") }
        var setsString by remember { mutableStateOf(selectedPlanForEdit?.sets?.toString() ?: "3") }
        var reps by remember { mutableStateOf(selectedPlanForEdit?.reps ?: "10次") }
        var targetMuscleGroup by remember { mutableStateOf(selectedPlanForEdit?.targetMuscleGroup ?: "全身") }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (selectedPlanForEdit == null) "新增運動項目" else "編輯運動項目",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Day of week selector
                    var showDayDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showDayDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("選擇天數：$dayOfWeek")
                        }
                        DropdownMenu(
                            expanded = showDayDropdown,
                            onDismissRequest = { showDayDropdown = false }
                        ) {
                            dayOrder.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day) },
                                    onClick = {
                                        dayOfWeek = day
                                        showDayDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = exerciseName,
                        onValueChange = { exerciseName = it },
                        label = { Text("動作名稱 (例如: 深蹲, 跑步)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = setsString,
                        onValueChange = { setsString = it },
                        label = { Text("組數") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = { Text("次數 / 時間 (例如: 10次, 45秒)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetMuscleGroup,
                        onValueChange = { targetMuscleGroup = it },
                        label = { Text("目標肌群 (例如: 胸肌, 腿部, 核心)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sets = setsString.toIntOrNull() ?: 3
                        if (exerciseName.isNotBlank()) {
                            if (selectedPlanForEdit == null) {
                                viewModel.addWorkoutPlan(dayOfWeek, exerciseName, sets, reps, targetMuscleGroup)
                            } else {
                                viewModel.updateWorkoutPlanDetails(
                                    selectedPlanForEdit!!.copy(
                                        dayOfWeek = dayOfWeek,
                                        exerciseName = exerciseName,
                                        sets = sets,
                                        reps = reps,
                                        targetMuscleGroup = targetMuscleGroup
                                    )
                                )
                            }
                            showAddEditDialog = false
                        }
                    }
                ) {
                    Text("儲存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun WorkoutPlanHeader(
    goal: String,
    experience: String,
    weeklySets: Int,
    trainingDayCount: Int,
    completionRatio: Float,
    completedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$goal 訓練週計畫",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$experience ・ 每週 $trainingDayCount 天 ・ $weeklySets 組",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LinearProgressIndicator(
                progress = { completionRatio },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "本週完成 $completedCount / $totalCount 個項目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HealthConnectPreviewCard(
    sdkStatus: Int,
    hasPermissions: Boolean,
    healthData: HealthData?,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Health Connect 健身數據",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                Text(
                    text = "您的裝置尚未安裝或需要更新 Google Health Connect。請點擊下方按鈕前往 Google Play 商店下載安裝，方可接入您的今日健身數據！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("安裝 Google Health Connect")
                }
            } else if (!hasPermissions) {
                Text(
                    text = "串接 Health Connect 可以直接讀取您的今日步數、平均心率與消耗熱量！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("授權串接健康數據")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日步數", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${healthData?.steps ?: 0} 步",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("平均心率", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${healthData?.heartRate ?: 0} bpm",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("活動消耗", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${healthData?.calories ?: 0} kcal",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    today: String,
    plans: List<WorkoutPlan>,
    onToggle: (WorkoutPlan) -> Unit,
    onEdit: (WorkoutPlan) -> Unit,
    onDelete: (WorkoutPlan) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "今日訓練 ・ $today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (plans.isEmpty()) {
                Text(
                    text = "今天安排恢復或輕活動",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                plans.forEach { plan ->
                    WorkoutPlanRow(
                        plan = plan,
                        onToggle = { onToggle(plan) },
                        onEdit = { onEdit(plan) },
                        onDelete = { onDelete(plan) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleBalanceCard(topMuscleGroups: List<Map.Entry<String, Int>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Face, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "肌群分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            topMuscleGroups.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = entry.key, style = MaterialTheme.typography.bodyMedium)
                    AssistChip(onClick = {}, label = { Text("${entry.value} 組") })
                }
            }
        }
    }
}

@Composable
private fun WorkoutPlanRow(
    plan: WorkoutPlan,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (plan.isCompleted) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                } else {
                    Color.Transparent
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = plan.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.exerciseName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${plan.sets} 組 ・ ${plan.reps} ・ ${plan.targetMuscleGroup}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "編輯",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "刪除",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}

@Composable
private fun AISummaryCard(
    summary: String?,
    isSummarizing: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI 計畫摘要",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Identical to DashboardScreen:
            //   ThinkingContent(rawText = advice, isGenerating = isAiAdviceGenerating)
            // ThinkingContent handles thinking/content split — no flash issue
            if (!summary.isNullOrBlank()) {
                ThinkingContent(
                    rawText = summary,
                    isGenerating = isSummarizing
                )
            } else if (isSummarizing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "正在產生運動計畫與摘要，請稍候...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun currentDayOfWeek(): String {
    return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "星期一"
        Calendar.TUESDAY -> "星期二"
        Calendar.WEDNESDAY -> "星期三"
        Calendar.THURSDAY -> "星期四"
        Calendar.FRIDAY -> "星期五"
        Calendar.SATURDAY -> "星期六"
        else -> "星期日"
    }
}
