package com.xuan.fitai.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
}
