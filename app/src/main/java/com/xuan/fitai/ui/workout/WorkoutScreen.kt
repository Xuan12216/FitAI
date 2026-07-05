package com.xuan.fitai.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.WorkoutPlan
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onNavigateBack: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val plans by viewModel.workoutPlans.collectAsState()

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
                    IconButton(onClick = { viewModel.regeneratePlan() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "重新產生計畫")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            item {
                HealthConnectPreviewCard()
            }

            item {
                TodayWorkoutCard(
                    today = today,
                    plans = todayPlans,
                    onToggle = viewModel::toggleWorkoutPlan
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
                        onToggle = { viewModel.toggleWorkoutPlan(item) }
                    )
                }
            }
        }
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
private fun HealthConnectPreviewCard() {
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
                    text = "Health Connect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = {}, label = { Text("運動紀錄") })
                SuggestionChip(onClick = {}, label = { Text("步數") })
                SuggestionChip(onClick = {}, label = { Text("心率") })
            }

            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("準備接入 Health Connect")
            }
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    today: String,
    plans: List<WorkoutPlan>,
    onToggle: (WorkoutPlan) -> Unit
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
                    WorkoutPlanRow(plan = plan, onToggle = { onToggle(plan) })
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
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = plan.exerciseName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Column {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${plan.sets} 組 ・ ${plan.reps} ・ ${plan.targetMuscleGroup}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        leadingContent = {
            Checkbox(
                checked = plan.isCompleted,
                onCheckedChange = { onToggle() }
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (plan.isCompleted) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
    )
    Divider(modifier = Modifier.padding(horizontal = 8.dp))
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
