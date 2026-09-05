package com.xuan.fitai.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.ContextTokenLimits

@Composable
fun ModelConfigDialog(
    viewModel: ModelSetupViewModel,
    onDismiss: () -> Unit
) {
    val maxTokensPref by viewModel.maxTokens.collectAsState()
    val topKPref by viewModel.topK.collectAsState()
    val topPPref by viewModel.topP.collectAsState()
    val tempPref by viewModel.temperature.collectAsState()
    val useGpuPref by viewModel.useGpu.collectAsState()
    val thinkingPref by viewModel.enableThinking.collectAsState()
    val speculativePref by viewModel.enableSpeculative.collectAsState()
    val systemPromptPref by viewModel.systemPrompt.collectAsState()

    var maxTokens by remember(maxTokensPref) { mutableStateOf(maxTokensPref) }
    var topK by remember { mutableStateOf(topKPref) }
    var topP by remember { mutableStateOf(topPPref) }
    var temp by remember { mutableStateOf(tempPref) }
    var useGpu by remember { mutableStateOf(useGpuPref) }
    var thinking by remember { mutableStateOf(thinkingPref) }
    var speculative by remember { mutableStateOf(speculativePref) }
    var systemPrompt by remember { mutableStateOf(systemPromptPref) }

    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "AI 模型參數",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tab Header
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("模型參數") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("系統提示詞") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    IntSliderInputRow(
                        label = "上下文容量（256–32000 tokens）",
                        description = "模型處理對話的 token 容量，並非中文字數。較小容量可減少記憶體需求，但可容納的對話也較短。",
                        value = maxTokens,
                        valueRange = ContextTokenLimits.range,
                        onValueChange = { maxTokens = it }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContextTokenLimits.presets.forEach { tokens ->
                            FilterChip(
                                selected = maxTokens == tokens,
                                onClick = { maxTokens = tokens },
                                label = { Text(tokens.toString()) }
                            )
                        }
                    }
                    Text(
                        "可先試 1024；256／512 適合短文字。長對話、圖片或語音可能需要更大容量。儲存後會重新載入模型，速度仍取決於模型與裝置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // TopK (5-100)
                    IntSliderInputRow(
                        label = "TopK (5-100)",
                        description = "僅從機率最高的前 K 個詞中進行隨機篩選，數值越高輸出越隨機多變。",
                        value = topK,
                        valueRange = 5..100,
                        onValueChange = { topK = it }
                    )

                    // TopP (0.00-1.00)
                    SliderInputRow(
                        label = "TopP (0.00-1.00)",
                        description = "篩選累積機率達到 P 的候選詞，控制答案的多樣性與發散度。",
                        value = topP,
                        valueRange = 0.0f..1.0f,
                        onValueChange = { topP = it }
                    )

                    // Temperature (0.00-2.00)
                    SliderInputRow(
                        label = "Temperature (0.00-2.00)",
                        description = "控制生成內容的創意與自由度。數值越高越有創意，越低越精確嚴謹。",
                        value = temp,
                        valueRange = 0.0f..2.0f,
                        onValueChange = { temp = it },
                        formatStr = "%.2f"
                    )

                    // Accelerator CPU / GPU Toggle
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Accelerator (硬體加速器)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(text = "選擇使用 CPU 或 GPU 運行模型。啟用 GPU 可大幅加快生成速度（若裝置支援）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        AcceleratorToggle(useGpu = useGpu, onToggle = { useGpu = it })
                    }

                    // Enable thinking Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable thinking (啟用深度思考)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(text = "讓具備思考鏈（CoT）的模型在回答複雜問題前先進行深度推理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = thinking, onCheckedChange = { thinking = it })
                    }

                    // Enable speculative decoding Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable speculative decoding (啟用投機解碼)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(text = "利用小模型預測大模型輸出以加速推理生成速度（若適用）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = speculative, onCheckedChange = { speculative = it })
                    }

                } else {
                    // System prompt text input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "System Prompt (系統提示詞)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(text = "設定本地 AI 助理的角色扮演規則與答題指令規範。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            placeholder = { Text("例如：你是一個熱心且專業的健身與飲食小助手，請以繁體中文回答。") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            maxLines = 8
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveModelConfig(
                        maxTokens = maxTokens,
                        topK = topK,
                        topP = topP,
                        temperature = temp,
                        useGpu = useGpu,
                        enableThinking = thinking,
                        enableSpeculative = speculative,
                        systemPrompt = systemPrompt
                    )
                    onDismiss()
                }
            ) {
                Text("儲存並套用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun AcceleratorToggle(
    useGpu: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val selectedColor = MaterialTheme.colorScheme.primaryContainer
        val unselectedColor = Color.Transparent
        val selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        val unselectedTextColor = MaterialTheme.colorScheme.onSurface

        Surface(
            onClick = { onToggle(true) },
            shape = RoundedCornerShape(50),
            color = if (useGpu) selectedColor else unselectedColor,
            modifier = Modifier
                .width(80.dp)
                .height(36.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (useGpu) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("GPU", style = MaterialTheme.typography.bodyMedium, color = if (useGpu) selectedTextColor else unselectedTextColor)
            }
        }

        Surface(
            onClick = { onToggle(false) },
            shape = RoundedCornerShape(50),
            color = if (!useGpu) selectedColor else unselectedColor,
            modifier = Modifier
                .width(80.dp)
                .height(36.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!useGpu) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("CPU", style = MaterialTheme.typography.bodyMedium, color = if (!useGpu) selectedTextColor else unselectedTextColor)
            }
        }
    }
}

@Composable
fun SliderInputRow(
    label: String,
    zhLabel: String = "",
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    formatStr: String = "%.2f"
) {
    var textVal by remember(value) { mutableStateOf(String.format(formatStr, value)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = zhLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = textVal,
                onValueChange = {
                    textVal = it
                    val parsed = it.toFloatOrNull()
                    if (parsed != null && parsed in valueRange) {
                        onValueChange(parsed)
                    }
                },
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun IntSliderInputRow(
    label: String,
    zhLabel: String = "",
    description: String,
    value: Int,
    valueRange: ClosedRange<Int>,
    onValueChange: (Int) -> Unit
) {
    var textVal by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = zhLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange.start.toFloat()..valueRange.endInclusive.toFloat(),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = textVal,
                onValueChange = {
                    textVal = it
                    val parsed = it.toIntOrNull()
                    if (parsed != null && parsed in valueRange) {
                        onValueChange(parsed)
                    }
                },
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
