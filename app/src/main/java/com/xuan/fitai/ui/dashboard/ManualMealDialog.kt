package com.xuan.fitai.ui.dashboard

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.xuan.fitai.ui.adaptive.AdaptiveModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.nutrition.FoodAssessmentEvent
import com.xuan.fitai.nutrition.NutritionPortionCalculator
import com.xuan.fitai.nutrition.NutritionReferenceJson
import com.xuan.fitai.ui.components.AppLoadingIndicator
import com.xuan.fitai.ui.components.NutritionReferenceBadge
import com.xuan.fitai.ui.components.NutritionReferenceToggle
import com.xuan.fitai.ui.components.PastDatePickerDialog
import com.xuan.fitai.ui.components.ThinkingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ManualMealDialog(
    viewModel: DashboardViewModel,
    initial: Meal? = null,
    copy: Boolean = false,
    initialDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mealName by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var caloriesVal by rememberSaveable { mutableStateOf(initial?.calories?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "") }
    var proteinVal by rememberSaveable { mutableStateOf(initial?.protein?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "") }
    var carbsVal by rememberSaveable { mutableStateOf(initial?.carbs?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "") }
    var fatVal by rememberSaveable { mutableStateOf(initial?.fat?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "") }
    var portion by rememberSaveable { mutableStateOf(initial?.portionSize ?: "1份") }
    var favorite by rememberSaveable { mutableStateOf(initial?.isFavorite ?: false) }

    val seed = if (initial != null && !copy) {
        Instant.ofEpochMilli(initial.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } else {
        initialDate.atTime(LocalTime.now())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(seed.toLocalDate().toString()) }
    var hour by rememberSaveable { mutableIntStateOf(seed.hour) }
    var minute by rememberSaveable { mutableIntStateOf(seed.minute) }
    var automaticType by rememberSaveable { mutableStateOf(initial == null || copy) }
    var type by rememberSaveable { mutableStateOf(
        if (initial != null && !copy) initial.mealType else com.xuan.fitai.data.model.MealPeriod.forHour(seed.hour)
    ) }
    var saving by remember { mutableStateOf(false) }
    var templateMenu by rememberSaveable { mutableStateOf(false) }

    val history by viewModel.mealHistory.collectAsState()
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiErrorMsg by remember { mutableStateOf<String?>(null) }
    var aiReasoning by remember { mutableStateOf(initial?.aiAdvice.orEmpty()) }
    var useReference by rememberSaveable { mutableStateOf(true) }
    var referenceJson by rememberSaveable { mutableStateOf(initial?.nutritionReferenceJson) }
    var aiEstimated by rememberSaveable { mutableStateOf(initial?.isAiEstimated ?: false) }
    var savedAdvice by rememberSaveable { mutableStateOf(initial?.aiAdvice) }
    var analysisStatus by remember { mutableStateOf("") }
    var lookupMessage by remember { mutableStateOf("") }

    var canRetryGoogle by remember(mealName, portion, useReference) { mutableStateOf(false) }

    val isCaloriesInvalid = caloriesVal.isNotBlank() &&
        caloriesVal.toFloatOrNull()?.let { it.isFinite() && it >= 0 } != true
    val canSave = !saving && !isAiAnalyzing &&
        portion.isNotBlank() &&
        mealName.isNotBlank() &&
        caloriesVal.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true &&
        listOf(proteinVal, carbsVal, fatVal).all { value ->
            value.isBlank() || value.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true
        }

    fun startAiEstimate(googleRetry: Boolean = false) {
        if (isAiAnalyzing) return
        if (mealName.isNotBlank()) {
            isAiAnalyzing = true
            aiErrorMsg = null
            if (!googleRetry) aiReasoning = ""
            lookupMessage = ""
            var pendingReference: String? = null
            coroutineScope.launch {
                val rawText = StringBuilder()
                try {
                    viewModel.askAiForMealSuggestion(mealName.trim(), portion, useReference, googleRetry).collect { event ->
                        when (event) {
                            is FoodAssessmentEvent.GoogleFinished -> canRetryGoogle = event.canRetry
                            is FoodAssessmentEvent.Status -> analysisStatus = event.text
                            is FoodAssessmentEvent.Lookup -> {
                                pendingReference = event.result.reference?.let(NutritionReferenceJson::encode)
                                lookupMessage = event.result.message
                            }
                            is FoodAssessmentEvent.Token -> {
                                rawText.append(event.text)
                                aiReasoning = rawText.toString()
                            }
                        }
                    }
                    val result = NutritionPortionCalculator.apply(
                        GemmaOutputParser.parseFoodAnalysis(rawText.toString()),
                        NutritionReferenceJson.decodeOrNull(pendingReference),
                        portion
                    )
                    caloriesVal = result.calories.toInt().toString()
                    proteinVal = String.format(Locale.ROOT, "%.1f", result.protein)
                    carbsVal = String.format(Locale.ROOT, "%.1f", result.carbs)
                    fatVal = String.format(Locale.ROOT, "%.1f", result.fat)
                    referenceJson = NutritionReferenceJson.withSelection(pendingReference, result.referenceFoodId)
                    aiEstimated = true
                    savedAdvice = result.advice
                    aiReasoning = GemmaOutputParser.withThinkingContent(
                        thinkingText = result.thinking,
                        contentText = result.reasoning
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aiErrorMsg = "估算未完成：${e.localizedMessage ?: "請稍後再試"}"
                } finally {
                    isAiAnalyzing = false
                }
            }
        } else {
            aiErrorMsg = "⚠️ 請先輸入餐點名稱"
        }
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sticky Header + Favorite Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        initial != null && !copy -> "編輯餐點"
                        copy -> "再記一次"
                        else -> "新增／補登餐點"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                FilterChip(
                    selected = favorite,
                    onClick = { favorite = !favorite },
                    leadingIcon = {
                        Icon(
                            imageVector = if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("常用餐點") }
                )
            }

            // Scrollable Body
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
            ) {

            // Quick Template Picker
            val templates = remember(history) {
                history.distinctBy { it.name to it.portionSize }
                    .sortedByDescending { it.isFavorite }
                    .take(10)
            }
            if (templates.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "從常用／最近餐點帶入",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                enabled = !isAiAnalyzing,
                                onClick = { templateMenu = !templateMenu }
                            ) {
                                Text(if (templateMenu) "收起" else "展開 (${templates.size})")
                            }
                        }
                        AnimatedVisibility(visible = templateMenu) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                templates.forEach { meal ->
                                    SuggestionChip(
                                        enabled = !isAiAnalyzing,
                                        onClick = {
                                            mealName = meal.name
                                            portion = meal.portionSize
                                            caloriesVal = meal.calories.toInt().toString()
                                            proteinVal = meal.protein.toString()
                                            carbsVal = meal.carbs.toString()
                                            fatVal = meal.fat.toString()
                                            favorite = meal.isFavorite
                                            templateMenu = false
                                            referenceJson = meal.nutritionReferenceJson
                                            aiEstimated = meal.isAiEstimated
                                            savedAdvice = meal.aiAdvice
                                            lookupMessage = ""
                                            aiReasoning = meal.aiAdvice.orEmpty()
                                        },
                                        label = {
                                            Text("${if (meal.isFavorite) "★ " else ""}${meal.name} (${meal.portionSize})")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Date, Time & Meal Type Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(date)
                }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            hour = h; minute = m
                            if (automaticType) type = com.xuan.fitai.data.model.MealPeriod.forHour(h)
                        }, hour, minute, true).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                com.xuan.fitai.data.model.MealPeriod.ordered.forEach { label ->
                    FilterChip(
                        selected = type == label,
                        onClick = { type = label; automaticType = false },
                        label = { Text(label) }
                    )
                }
            }

            // Meal Name & Portion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = mealName,
                    onValueChange = {
                        mealName = it
                        referenceJson = null
                        aiEstimated = false
                        savedAdvice = null
                        lookupMessage = ""
                        aiReasoning = ""
                    },
                    enabled = !isAiAnalyzing,
                    label = { Text("餐點名稱") },
                    placeholder = { Text("例如：雞胸肉便當") },
                    singleLine = true,
                    modifier = Modifier.weight(1.8f)
                )
                OutlinedTextField(
                    value = portion,
                    onValueChange = {
                        portion = it
                        referenceJson = null
                        aiEstimated = false
                        savedAdvice = null
                        lookupMessage = ""
                        aiReasoning = ""
                    },
                    enabled = !isAiAnalyzing,
                    label = { Text("份量") },
                    placeholder = { Text("1份 / 200g") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // AI Estimation & TFDA Reference Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NutritionReferenceToggle(useReference, { useReference = it }, !isAiAnalyzing)

                    Button(
                        onClick = { startAiEstimate() },
                        enabled = !isAiAnalyzing && portion.isNotBlank() && mealName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        if (isAiAnalyzing) {
                            AppLoadingIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(analysisStatus.ifBlank { "本地 AI 估算中…" })
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 估算熱量與營養素")
                        }
                    }

                    if (lookupMessage.isNotBlank() && (canRetryGoogle || referenceJson == null)) {
                        Text(
                            text = lookupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (useReference && canRetryGoogle) {
                        OutlinedButton(onClick = { startAiEstimate(googleRetry = true) },
                            enabled = !isAiAnalyzing, modifier = Modifier.fillMaxWidth()) {
                            Text("再查一次網路 AI")
                        }
                    }
                    if (aiEstimated && referenceJson == null) {
                        Text("估算 AI：${com.xuan.fitai.nutrition.nutritionEstimatorLabel(referenceJson, true)}", style = MaterialTheme.typography.labelMedium)
                    }
                    NutritionReferenceBadge(referenceJson)

                    if (aiErrorMsg != null) {
                        Text(
                            text = aiErrorMsg!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isAiAnalyzing || aiReasoning.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 AI 估算依據",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ThinkingContent(
                                    rawText = aiReasoning.ifBlank { "AI 分析中..." },
                                    contentTextStyle = MaterialTheme.typography.bodySmall,
                                    isGenerating = isAiAnalyzing
                                )
                            }
                        }
                    }
                }
            }

            // 2x2 Grid for Nutrition Values
            Text(
                text = "營養數值（可手動微調）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = caloriesVal,
                    onValueChange = { caloriesVal = it },
                    label = { Text("熱量 (kcal) *") },
                    isError = isCaloriesInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = proteinVal,
                    onValueChange = { proteinVal = it },
                    label = { Text("蛋白質 (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = carbsVal,
                    onValueChange = { carbsVal = it },
                    label = { Text("碳水化合物 (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fatVal,
                    onValueChange = { fatVal = it },
                    label = { Text("脂肪 (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            }

            // Sticky Bottom Action Row
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("取消")
                }
                Button(
                    enabled = canSave,
                    onClick = {
                        val cal = caloriesVal.toFloatOrNull() ?: 0f
                        val p = proteinVal.toFloatOrNull() ?: 0f
                        val c = carbsVal.toFloatOrNull() ?: 0f
                        val f = fatVal.toFloatOrNull() ?: 0f

                        saving = true
                        coroutineScope.launch {
                            try {
                                val parsedDate = runCatching { LocalDate.parse(date) }.getOrDefault(initialDate)
                                val timestamp = parsedDate
                                    .atTime(hour, minute)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                                viewModel.saveMealRecord(
                                    initial, copy, mealName.trim(), portion.trim(), type, favorite,
                                    timestamp, cal, p, c, f, aiEstimated, savedAdvice, referenceJson
                                )
                                viewModel.selectMealDate(parsedDate)
                                onDismiss()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                aiErrorMsg = "儲存失敗，請再試一次"
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                ) {
                    Text(if (saving) "儲存中…" else "儲存餐點", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDatePicker) {
        PastDatePickerDialog(
            selectedDate = runCatching { LocalDate.parse(date) }.getOrDefault(initialDate),
            onDateSelected = { date = it.toString() },
            onDismiss = { showDatePicker = false }
        )
    }
}
