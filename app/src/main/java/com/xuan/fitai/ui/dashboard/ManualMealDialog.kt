package com.xuan.fitai.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.Meal
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.nutrition.FoodAssessmentEvent
import com.xuan.fitai.nutrition.NutritionReferenceJson
import com.xuan.fitai.ui.components.NutritionReferenceBadge
import com.xuan.fitai.ui.components.NutritionReferenceToggle
import com.xuan.fitai.ui.components.AppLoadingIndicator
import com.xuan.fitai.ui.components.ThinkingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ManualMealDialog(viewModel: DashboardViewModel, initial: Meal? = null, copy: Boolean = false, initialDate: LocalDate = LocalDate.now(), onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var mealName by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var caloriesVal by rememberSaveable { mutableStateOf(initial?.calories?.toString() ?: "") }
    var proteinVal by rememberSaveable { mutableStateOf(initial?.protein?.toString() ?: "") }
    var carbsVal by rememberSaveable { mutableStateOf(initial?.carbs?.toString() ?: "") }
    var fatVal by rememberSaveable { mutableStateOf(initial?.fat?.toString() ?: "") }
    val context = LocalContext.current
    var portion by rememberSaveable { mutableStateOf(initial?.portionSize ?: "1份") }
    var type by rememberSaveable { mutableStateOf(initial?.mealType ?: "未分類") }
    var favorite by rememberSaveable { mutableStateOf(initial?.isFavorite ?: false) }
    val seed = if (initial != null && !copy) Instant.ofEpochMilli(initial.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime() else initialDate.atTime(LocalTime.now())
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(seed.toLocalDate().toString()) }
    var hour by rememberSaveable { mutableIntStateOf(seed.hour) }
    var minute by rememberSaveable { mutableIntStateOf(seed.minute) }
    var saving by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    val history by viewModel.mealHistory.collectAsState()
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiErrorMsg by remember { mutableStateOf<String?>(null) }
    var aiReasoning by remember { mutableStateOf("") }
    var useReference by rememberSaveable { mutableStateOf(true) }
    var referenceJson by rememberSaveable { mutableStateOf(initial?.nutritionReferenceJson) }
    var aiEstimated by rememberSaveable { mutableStateOf(initial?.isAiEstimated ?: false) }
    var savedAdvice by rememberSaveable { mutableStateOf(initial?.aiAdvice) }
    var analysisStatus by remember { mutableStateOf("") }
    var lookupMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null && !copy) "編輯餐點" else if (copy) "再記一次" else "新增／補登餐點") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TextButton(enabled = !isAiAnalyzing, onClick = { templateMenu = !templateMenu }) { Text("從常用／最近餐點帶入") }
                if (templateMenu) {
                    history.distinctBy { it.name to it.portionSize }.sortedByDescending { it.isFavorite }.take(10).forEach { meal ->
                        TextButton(onClick = {
                            mealName = meal.name; portion = meal.portionSize; type = meal.mealType
                            caloriesVal = meal.calories.toString(); proteinVal = meal.protein.toString()
                            carbsVal = meal.carbs.toString(); fatVal = meal.fat.toString(); templateMenu = false
                            referenceJson = meal.nutritionReferenceJson; aiEstimated = meal.isAiEstimated; savedAdvice = meal.aiAdvice
                            lookupMessage = ""; aiReasoning = ""
                        }, enabled = !isAiAnalyzing) { Text("${if (meal.isFavorite) "★ " else ""}${meal.name} · ${meal.portionSize}") }
                    }
                    if (history.isEmpty()) Text("記錄第一餐後，就能在這裡快速帶入。")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) { Text(date) }
                    OutlinedButton(onClick = { android.app.TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() }) {
                        Text(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("早餐", "午餐", "晚餐", "點心", "未分類").forEach { label ->
                        FilterChip(selected = type == label, onClick = { type = label }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = portion, onValueChange = {
                    portion = it; referenceJson = null; aiEstimated = false; savedAdvice = null; lookupMessage = ""; aiReasoning = ""
                }, enabled = !isAiAnalyzing, label = { Text("份量") }, modifier = Modifier.fillMaxWidth())
                Text("請依份量確認下方營養數值，或使用 AI 重新估算。", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("設為常用餐點")
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it; referenceJson = null; aiEstimated = false; savedAdvice = null; lookupMessage = ""; aiReasoning = "" },
                    enabled = !isAiAnalyzing,
                    label = { Text("餐點名稱") },
                    placeholder = { Text("例如：貢丸湯 一碗") },
                    modifier = Modifier.fillMaxWidth()
                )

                NutritionReferenceToggle(useReference, { useReference = it }, !isAiAnalyzing)
                Button(
                    onClick = {
                        if (mealName.isNotBlank()) {
                            isAiAnalyzing = true
                            aiErrorMsg = null
                            aiReasoning = ""
                            lookupMessage = ""
                            var pendingReference: String? = null
                            coroutineScope.launch {
                                val rawText = StringBuilder()
                                try {
                                    viewModel.askAiForMealSuggestion(mealName.trim(), portion, useReference).collect { event ->
                                        when (event) {
                                            is FoodAssessmentEvent.Status -> analysisStatus = event.text
                                            is FoodAssessmentEvent.Lookup -> {
                                                pendingReference = event.result.reference?.let(NutritionReferenceJson::encode)
                                                lookupMessage = event.result.message
                                            }
                                            is FoodAssessmentEvent.Token -> { rawText.append(event.text); aiReasoning = rawText.toString() }
                                        }
                                    }
                                    val result = com.xuan.fitai.nutrition.NutritionPortionCalculator.apply(
                                        GemmaOutputParser.parseFoodAnalysis(rawText.toString()), NutritionReferenceJson.decodeOrNull(pendingReference), portion)
                                    caloriesVal = result.calories.toInt().toString()
                                    proteinVal = String.format(java.util.Locale.ROOT, "%.1f", result.protein)
                                    carbsVal = String.format(java.util.Locale.ROOT, "%.1f", result.carbs)
                                    fatVal = String.format(java.util.Locale.ROOT, "%.1f", result.fat)
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
                    },
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
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 估算熱量與營養素")
                    }
                }

                if (lookupMessage.isNotBlank()) Text(lookupMessage, style = MaterialTheme.typography.bodySmall)
                NutritionReferenceBadge(referenceJson)
                if (aiErrorMsg != null) {
                    Text(
                        text = aiErrorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (isAiAnalyzing || aiReasoning.isNotBlank()) {
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
                                rawText = aiReasoning.ifBlank { "Gemma 分析中..." },
                                contentTextStyle = MaterialTheme.typography.bodySmall,
                                isGenerating = isAiAnalyzing
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))


                OutlinedTextField(
                    value = caloriesVal,
                    onValueChange = { caloriesVal = it },
                    label = { Text("熱量 (kcal)") },
                    supportingText = { Text("必填，請輸入 0 以上的數值") },
                    isError = caloriesVal.isNotBlank() && caloriesVal.toFloatOrNull()?.let { it.isFinite() && it >= 0 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = proteinVal,
                    onValueChange = { proteinVal = it },
                    label = { Text("蛋白質 (g，選填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carbsVal,
                    onValueChange = { carbsVal = it },
                    label = { Text("碳水化合物 (g，選填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fatVal,
                    onValueChange = { fatVal = it },
                    label = { Text("脂肪 (g，選填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && !isAiAnalyzing && portion.isNotBlank() && mealName.isNotBlank() &&
                    caloriesVal.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true &&
                    listOf(proteinVal, carbsVal, fatVal).all { value ->
                        value.isBlank() || value.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true
                    },
                onClick = {
                    val cal = caloriesVal.toFloatOrNull() ?: 0f
                    val p = proteinVal.toFloatOrNull() ?: 0f
                    val c = carbsVal.toFloatOrNull() ?: 0f
                    val f = fatVal.toFloatOrNull() ?: 0f
                    
                    saving = true
                    coroutineScope.launch {
                        try {
                            val timestamp = LocalDate.parse(date).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            viewModel.saveMealRecord(initial, copy, mealName, portion, type, favorite, timestamp, cal, p, c, f,
                                aiEstimated, savedAdvice, referenceJson)
                            viewModel.selectMealDate(LocalDate.parse(date))
                            onDismiss()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { aiErrorMsg = "儲存失敗，請再試一次" }
                        finally { saving = false }
                    }
                }
            ) {
                Text(if (saving) "儲存中…" else "儲存餐點")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("取消")
            }
        }
    )
    if (showDatePicker) com.xuan.fitai.ui.components.PastDatePickerDialog(LocalDate.parse(date), { date = it.toString() }) { showDatePicker = false }

}
