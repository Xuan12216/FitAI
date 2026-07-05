package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.ui.components.NutritionProgressCard
import com.xuan.fitai.ui.components.ThinkingContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val meals by viewModel.todayMeals.collectAsState()
    val advice by viewModel.aiAdvice.collectAsState()
    val isAiAdviceGenerating by viewModel.isAiAdviceGenerating.collectAsState()
    val loadedModelName by viewModel.loadedModelName.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showManualAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.setAdviceGenerationActive(true)
        onDispose {
            viewModel.setAdviceGenerationActive(false)
        }
    }

    val consumedCalories = meals.sumOf { it.calories.toDouble() }.toFloat()
    val remainingCalories = (profile.targetCalories - consumedCalories).coerceAtLeast(0f)
    
    val consumedProtein = meals.sumOf { it.protein.toDouble() }.toFloat()
    val consumedCarbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
    val consumedFat = meals.sumOf { it.fat.toDouble() }.toFloat()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FitAI 健康儀表板", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onResetOnboarding) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "重設基本資料")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Calorie Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "今日剩餘熱量目標",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${remainingCalories.toInt()} kcal",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("已攝取", style = MaterialTheme.typography.bodySmall)
                            Text("${consumedCalories.toInt()} kcal", fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("每日目標", style = MaterialTheme.typography.bodySmall)
                            Text("${profile.targetCalories.toInt()} kcal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Actions Row
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val file = java.io.File(context.filesDir, "models/food_classifier.tflite")
                        if (file.exists() && file.length() > 0) {
                            onNavigateToScanner()
                        } else {
                            android.widget.Toast.makeText(context, "⚠️ 尚未下載食物辨識模型！請先至「模型設定」頁面下載或匯入。", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("相機掃描")
                }
                
                OutlinedButton(
                    onClick = { showManualAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("手動記錄")
                }
            }

            // Features Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val e2b = java.io.File(context.filesDir, "models/gemma-4-E2B-it.litertlm")
                        val e4b = java.io.File(context.filesDir, "models/gemma-4-E4B-it.litertlm")
                        val isGemmaReady = (e2b.exists() && e2b.length() > 0) || (e4b.exists() && e4b.length() > 0)
                        if (isGemmaReady) {
                            onNavigateToChat()
                        } else {
                            android.widget.Toast.makeText(context, "⚠️ 尚未下載 Gemma 4 模型！請先至「模型設定」頁面下載或匯入。", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("問 AI 助理", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("諮詢飲食運動", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToWorkout
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("運動計畫", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("個人運動菜單", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            // Model Settings Entry Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSetup,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("模型管理設定", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("管理本地 Gemma 與辨識模型", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
                }
            }

            // AI Daily Advice Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("今日本地 AI 營養建議", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (loadedModelName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("使用模型: $loadedModelName", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isAiAdviceGenerating) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "AI 營養建議生成中，請稍候...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        ThinkingContent(rawText = advice)
                    }
                }
            }

            // Macronutrient Progress Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("三大營養素進度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                NutritionProgressCard(
                    title = "蛋白質",
                    current = consumedProtein,
                    target = profile.targetProteinGrams,
                    color = Color(0xFFFF9800)
                )
                NutritionProgressCard(
                    title = "碳水化合物",
                    current = consumedCarbs,
                    target = profile.targetCarbsGrams,
                    color = Color(0xFF2196F3)
                )
                NutritionProgressCard(
                    title = "脂肪",
                    current = consumedFat,
                    target = profile.targetFatGrams,
                    color = Color(0xFFE91E63)
                )
            }

            // Today's Meal Logs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日飲食記錄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                if (meals.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("今天還沒有記錄餐點，開始掃描你的第一餐吧！", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    meals.forEach { meal ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(meal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        if (meal.isAiEstimated) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("AI 估算", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    Text(
                                        "蛋白質 ${meal.protein.toInt()}g | 碳水 ${meal.carbs.toInt()}g | 脂肪 ${meal.fat.toInt()}g",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!meal.aiAdvice.isNullOrBlank()) {
                                        Text(
                                            text = meal.aiAdvice,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${meal.calories.toInt()} kcal",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    IconButton(onClick = { viewModel.deleteMeal(meal) }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Add Dialog
    if (showManualAddDialog) {
        var mealName by remember { mutableStateOf("") }
        var caloriesVal by remember { mutableStateOf("") }
        var proteinVal by remember { mutableStateOf("") }
        var carbsVal by remember { mutableStateOf("") }
        var fatVal by remember { mutableStateOf("") }
        var isAiAnalyzing by remember { mutableStateOf(false) }
        var aiErrorMsg by remember { mutableStateOf<String?>(null) }
        var aiReasoning by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("手動新增餐點") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("餐點名稱") },
                        placeholder = { Text("例如：貢丸湯 一碗") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (mealName.isNotBlank()) {
                                isAiAnalyzing = true
                                aiErrorMsg = null
                                aiReasoning = ""
                                coroutineScope.launch {
                                    val result = viewModel.askAiForMealSuggestion(mealName)
                                    isAiAnalyzing = false
                                    if (result != null) {
                                        caloriesVal = result.calories.toInt().toString()
                                        proteinVal = result.protein.toInt().toString()
                                        carbsVal = result.carbs.toInt().toString()
                                        fatVal = result.fat.toInt().toString()
                                        aiReasoning = GemmaOutputParser.withThinkingContent(
                                            thinkingText = result.thinking,
                                            contentText = result.reasoning
                                        )
                                    } else {
                                        aiErrorMsg = "⚠️ 估算失敗！請確認已在「模型設定」頁面載入 Gemma 模型。"
                                    }
                                }
                            } else {
                                aiErrorMsg = "⚠️ 請先輸入餐點名稱"
                            }
                        },
                        enabled = !isAiAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        if (isAiAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("本地 AI 估算中...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✨ 使用本地 AI 估算熱量與營養素")
                        }
                    }

                    if (aiErrorMsg != null) {
                        Text(
                            text = aiErrorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (aiReasoning.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 AI 估算依據：",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ThinkingContent(
                                    rawText = aiReasoning,
                                    contentTextStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))


                    OutlinedTextField(
                        value = caloriesVal,
                        onValueChange = { caloriesVal = it },
                        label = { Text("熱量 (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = proteinVal,
                        onValueChange = { proteinVal = it },
                        label = { Text("蛋白質 (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = carbsVal,
                        onValueChange = { carbsVal = it },
                        label = { Text("碳水化合物 (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fatVal,
                        onValueChange = { fatVal = it },
                        label = { Text("脂肪 (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cal = caloriesVal.toFloatOrNull() ?: 0f
                        val p = proteinVal.toFloatOrNull() ?: 0f
                        val c = carbsVal.toFloatOrNull() ?: 0f
                        val f = fatVal.toFloatOrNull() ?: 0f
                        
                        if (mealName.isNotBlank()) {
                            viewModel.addManualMeal(mealName, cal, p, c, f)
                            showManualAddDialog = false
                        }
                    }
                ) {
                    Text("新增")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
