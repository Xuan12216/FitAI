package com.xuan.fitai.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.ModelType
import com.xuan.fitai.ui.components.LoadingDialog
import com.xuan.fitai.ui.components.ModelStatusCard
import com.xuan.fitai.ui.components.SettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ModelSetupViewModel, onNavigateBack: () -> Unit, initialTab: Int = 0) {
    val models by viewModel.allModels.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val gemmaLoadState by viewModel.gemmaLoadState.collectAsStateWithLifecycle()
    val classifierLoadState by viewModel.classifierLoadState.collectAsStateWithLifecycle()
    val loadedModelName by viewModel.loadedModelName.collectAsStateWithLifecycle()
    val applying by viewModel.isApplyingModelConfig.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val contextTokens by viewModel.maxTokens.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var selectedImportId by rememberSaveable { mutableStateOf<String?>(null) }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    val appearanceScroll = rememberScrollState()
    val modelsScroll = rememberScrollState()
    val backupScroll = rememberScrollState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val modelId = selectedImportId
        if (uri != null && modelId != null) viewModel.importLocalFile(modelId, uri)
    }

    Scaffold(topBar = {
        Column {
            CenterAlignedTopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }
            )
            SecondaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("外觀" to Icons.Default.Palette, "AI 模型" to Icons.Default.SmartToy, "備份" to Icons.Default.CloudUpload).forEachIndexed { index, (label, icon) ->
                    val compact = com.xuan.fitai.ui.adaptive.LocalAdaptiveInfo.current.width < 600.dp
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                        icon = if (compact) ({ Icon(icon, null, Modifier.size(20.dp)) }) else null,
                        text = {
                            if (compact) Text(label)
                            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(icon, null, Modifier.size(20.dp)); Text(label)
                            }
                        })
                }
            }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(when (selectedTab) { 0 -> appearanceScroll; 1 -> modelsScroll; else -> backupScroll })
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsPageHeading(
                title = when (selectedTab) { 0 -> "打造你的 FitAI"; 1 -> "管理本地 AI"; else -> "備份與還原專區" },
                description = when (selectedTab) { 0 -> "選擇喜歡的色彩，讓每一天更有自己的風格。"; 1 -> "調整對話容量，選擇適合裝置的模型。"; else -> "管理 Google Drive 自動備份與紀錄還原。" }
            )
            if (selectedTab == 2) {
                BackupSettingsCard()
            } else if (selectedTab == 0) {
                appearance?.let {
                    FontSizeSettingsCard(it, viewModel::saveAppearance)
                    AppearanceSettingsCard(it, viewModel::saveAppearance)
                }
            } else {
                SettingsCard {
                    Text("AI 對話記憶與回應", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("對話記憶長度（上下文容量）", style = MaterialTheme.typography.bodyMedium)
                        Text("$contextTokens tokens", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("一般日常記錄使用預設值即可；喜歡長篇對話可調高容量。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showConfig = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("進階微調參數")
                    }
                }
                val failure = (gemmaLoadState as? ModelLoadState.Failed)?.message
                    ?: (classifierLoadState as? ModelLoadState.Failed)?.message
                if (failure != null) {
                    SettingsCard {
                        Text("模型未能載入", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("可用模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("下載完成後即可在裝置上離線使用。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                models.forEach { model ->
                    ModelStatusCard(
                        model = model,
                        downloadState = downloadStates[model.id] ?: ModelDownloadState.Idle,
                        loadState = when (model.type) {
                            ModelType.LLM -> gemmaLoadState
                            ModelType.FOOD_CLASSIFIER -> classifierLoadState
                            else -> ModelLoadState.NotFound
                        },
                        onDownloadClick = { viewModel.downloadModel(model.id) },
                        onLoadClick = { viewModel.loadModel(model) },
                        onImportClick = { selectedImportId = model.id; filePicker.launch("*/*") },
                        isActive = (model.type == ModelType.LLM && loadedModelName == model.name) ||
                            (model.type == ModelType.FOOD_CLASSIFIER && classifierLoadState is ModelLoadState.Loaded)
                    )
                }
            }
        }
    }
    if (showConfig) ModelConfigDialog(viewModel) { showConfig = false }
    if (applying) LoadingDialog(
        title = "套用模型設定",
        message = "正在以新的參數重新載入模型…"
    )
}

@Composable
private fun SettingsPageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
