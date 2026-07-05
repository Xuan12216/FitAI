package com.xuan.fitai.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.ModelType
import com.xuan.fitai.ui.components.ModelStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(
    viewModel: ModelSetupViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val models by viewModel.allModels.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val gemmaLoadState by viewModel.gemmaLoadState.collectAsState()
    val classifierLoadState by viewModel.classifierLoadState.collectAsState()
    val loadedModelName by viewModel.loadedModelName.collectAsState()

    val scrollState = rememberScrollState()
    var selectedModelIdForImport by remember { mutableStateOf<String?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Storage Access Framework file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedModelIdForImport != null) {
            viewModel.importLocalFile(selectedModelIdForImport!!, uri)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("本地 AI 模型管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "模型設定")
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
            // General Info Alert
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "本 App 使用完全本地化、免網路的 AI 模型。Gemma 與食物辨識皆執行於您的手機晶片，保障個人隱私安全。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Models Status Section
            Text(
                text = "本地端 AI 模型清單",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            models.forEach { model ->
                val downloadState = downloadStates[model.id] ?: ModelDownloadState.Idle
                val loadState = when (model.type) {
                    ModelType.LLM -> gemmaLoadState
                    ModelType.FOOD_CLASSIFIER -> classifierLoadState
                    else -> ModelLoadState.NotFound
                }

                ModelStatusCard(
                    model = model,
                    downloadState = downloadState,
                    loadState = loadState,
                    onDownloadClick = { viewModel.downloadModel(model.id) },
                    onLoadClick = { viewModel.loadModel(model) },
                    onImportClick = {
                        selectedModelIdForImport = model.id
                        filePickerLauncher.launch("*/*")
                    },
                    isActive = (model.type == ModelType.LLM && loadedModelName == model.name) ||
                            (model.type == ModelType.FOOD_CLASSIFIER && classifierLoadState is ModelLoadState.Loaded)
                )
            }
        }
    }

    // Config dialog modal
    if (showConfigDialog) {
        ModelConfigDialog(
            viewModel = viewModel,
            onDismiss = { showConfigDialog = false }
        )
    }
}

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
    var topK by remember(topKPref) { mutableStateOf(topKPref) }
    var topP by remember(topPPref) { mutableStateOf(topPPref) }
    var temp by remember(tempPref) { mutableStateOf(tempPref) }
    var useGpu by remember(useGpuPref) { mutableStateOf(useGpuPref) }
    var thinking by remember(thinkingPref) { mutableStateOf(thinkingPref) }
    var speculative by remember(speculativePref) { mutableStateOf(speculativePref) }
    var systemPrompt by remember(systemPromptPref) { mutableStateOf(systemPromptPref) }

    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurations (模型設定)",
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
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Model configs") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("System prompt") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Max tokens (2000-32000)
                    IntSliderInputRow(
                        label = "Max tokens (2000-32000)",
                        zhLabel = "最大 Token 數量",
                        description = "限制模型單次輸出的最大字數上限，防止生成內容過長。",
                        value = maxTokens,
                        valueRange = 2000..32000,
                        onValueChange = { maxTokens = it }
                    )

                    // TopK (5-100)
                    IntSliderInputRow(
                        label = "TopK (5-100)",
                        zhLabel = "TopK 候選詞數量限制",
                        description = "僅從機率最高的前 K 個詞中進行隨機篩選，數值越高輸出越隨機多變。",
                        value = topK,
                        valueRange = 5..100,
                        onValueChange = { topK = it }
                    )

                    // TopP (0.00-1.00)
                    SliderInputRow(
                        label = "TopP (0.00-1.00)",
                        zhLabel = "TopP 累積機率限制",
                        description = "篩選累積機率達到 P 的候選詞，控制答案的多樣性與發散度。",
                        value = topP,
                        valueRange = 0.0f..1.0f,
                        onValueChange = { topP = it }
                    )

                    // Temperature (0.00-2.00)
                    SliderInputRow(
                        label = "Temperature (0.00-2.00)",
                        zhLabel = "Temperature 隨機度 (溫度)",
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
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
    zhLabel: String,
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
    zhLabel: String,
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
