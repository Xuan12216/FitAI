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
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.ui.components.AppLoadingIndicator
import com.xuan.fitai.ui.components.ThinkingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ManualMealDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var mealName by rememberSaveable { mutableStateOf("") }
    var caloriesVal by rememberSaveable { mutableStateOf("") }
    var proteinVal by rememberSaveable { mutableStateOf("") }
    var carbsVal by rememberSaveable { mutableStateOf("") }
    var fatVal by rememberSaveable { mutableStateOf("") }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiErrorMsg by remember { mutableStateOf<String?>(null) }
    var aiReasoning by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                                val rawText = StringBuilder()
                                try {
                                    viewModel.askAiForMealSuggestion(mealName.trim()).collect { token ->
                                        rawText.append(token)
                                        aiReasoning = rawText.toString()
                                    }
                                    val result = GemmaOutputParser.parseFoodAnalysis(rawText.toString())
                                    caloriesVal = result.calories.toInt().toString()
                                    proteinVal = result.protein.toInt().toString()
                                    carbsVal = result.carbs.toInt().toString()
                                    fatVal = result.fat.toInt().toString()
                                    aiReasoning = GemmaOutputParser.withThinkingContent(
                                        thinkingText = result.thinking,
                                        contentText = result.reasoning
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    aiErrorMsg = "⚠️ 估算失敗！請確認已在「模型設定」頁面載入 Gemma 模型。"
                                } finally {
                                    isAiAnalyzing = false
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
                        AppLoadingIndicator(
                            modifier = Modifier.size(18.dp),
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
                enabled = !isAiAnalyzing && mealName.isNotBlank() &&
                    caloriesVal.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true &&
                    listOf(proteinVal, carbsVal, fatVal).all { value ->
                        value.isBlank() || value.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true
                    },
                onClick = {
                    val cal = caloriesVal.toFloatOrNull() ?: 0f
                    val p = proteinVal.toFloatOrNull() ?: 0f
                    val c = carbsVal.toFloatOrNull() ?: 0f
                    val f = fatVal.toFloatOrNull() ?: 0f
                    
                    if (mealName.isNotBlank()) {
                        viewModel.addManualMeal(mealName, cal, p, c, f)
                        onDismiss()
                    }
                }
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("取消")
            }
        }
    )
}
