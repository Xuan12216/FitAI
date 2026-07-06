package com.xuan.fitai.ai

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelType
import com.xuan.fitai.data.repository.ModelRepository
import com.xuan.fitai.service.ModelDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File

class ModelManager(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val userPreferenceStore: UserPreferenceStore,
    val gemmaHelper: GemmaLocalHelper,
    val classifierHelper: FoodClassifierHelper
) {
    private val downloader = ModelDownloader()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloadJobs = mutableMapOf<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    fun getModelFile(fileName: String): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }

    suspend fun initializeDefaultModels() = withContext(Dispatchers.IO) {
        // Clean up legacy model
        modelRepository.deleteModelById("gemma")

        val models = listOf(
            LocalModelInfo(
                id = "gemma_4_e4b",
                name = "Gemma 4 E4B Local LLM (4B)",
                type = ModelType.LLM,
                fileName = "gemma-4-E4B-it.litertlm",
                localPath = getModelFile("gemma-4-E4B-it.litertlm").absolutePath,
                sourceUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                requiresToken = false,
                fileSizeBytes = 3659530240L
            ),
            LocalModelInfo(
                id = "gemma_4_e2b",
                name = "Gemma 4 E2B Local LLM (2B)",
                type = ModelType.LLM,
                fileName = "gemma-4-E2B-it.litertlm",
                localPath = getModelFile("gemma-4-E2B-it.litertlm").absolutePath,
                sourceUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                requiresToken = false,
                fileSizeBytes = 2588147712L
            ),
            LocalModelInfo(
                id = "food_classifier",
                name = "Food Classifier MobileNet",
                type = ModelType.FOOD_CLASSIFIER,
                fileName = "food_classifier.tflite",
                localPath = getModelFile("food_classifier.tflite").absolutePath,
                sourceUrl = "https://github.com/second-state/wasmedge-quickjs/raw/main/example_js/tensorflow_lite_demo/lite-model_aiy_vision_classifier_food_V1_1.tflite",
                requiresToken = false,
                fileSizeBytes = 21151551L
            )
        )

        val downloadedLlms = mutableListOf<LocalModelInfo>()
        for (model in models) {
            val file = File(model.localPath)
            if (file.exists()) {
                if (file.length() < 1024 || (model.fileSizeBytes > 0 && file.length() != model.fileSizeBytes)) {
                    android.util.Log.w("FitAI_Diag", "Model ${model.id} is corrupted! Expected ${model.fileSizeBytes} bytes, but got ${file.length()} bytes. Deleting...")
                    file.delete()
                }
            }
            val fileExists = file.exists()

            val existing = modelRepository.getModelById(model.id)
            if (existing == null) {
                modelRepository.insertModel(model.copy(isDownloaded = fileExists))
            } else {
                modelRepository.updateModel(existing.copy(
                    isDownloaded = fileExists,
                    sourceUrl = model.sourceUrl,
                    requiresToken = model.requiresToken,
                    fileSizeBytes = model.fileSizeBytes
                ))
            }
            if (fileExists) {
                if (model.type == ModelType.LLM) {
                    downloadedLlms += model
                } else {
                    loadModel(model)
                }
            }
        }

        val selectedLlmModelId = userPreferenceStore.selectedLlmModelIdFlow.first()
        val selectedLlmPath = userPreferenceStore.selectedLlmPathFlow.first()
        val preferredLlm = downloadedLlms.firstOrNull { it.id == selectedLlmModelId }
            ?: downloadedLlms.firstOrNull { it.localPath == selectedLlmPath }
            ?: downloadedLlms.firstOrNull()
        if (preferredLlm != null) {
            rememberSelectedModel(preferredLlm)
            gemmaHelper.loadModelSync(preferredLlm.localPath, preferredLlm.name)
        }
    }

    fun downloadModel(modelId: String, token: String?) {
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
            putExtra(ModelDownloadService.EXTRA_TOKEN, token)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun downloadModelInBackground(modelId: String, token: String?) {
        synchronized(activeDownloadJobs) {
            if (activeDownloadJobs[modelId]?.isActive == true) return
        }

        _downloadStates.update { it + (modelId to ModelDownloadState.Idle) }

        val job = managerScope.launch(start = CoroutineStart.LAZY) {
            val model = modelRepository.getModelById(modelId) ?: run {
                _downloadStates.update { it + (modelId to ModelDownloadState.Failed("Model metadata not found")) }
                return@launch
            }
            val destinationFile = File(model.localPath)
            var finalState: ModelDownloadState = ModelDownloadState.Idle

            try {
                downloader.downloadModel(
                    url = model.sourceUrl ?: "",
                    token = token,
                    destinationFile = destinationFile,
                    onProgress = { state ->
                        finalState = state
                        _downloadStates.update { it + (modelId to state) }
                    }
                )

                when (finalState) {
                    is ModelDownloadState.Completed -> {
                        val updatedModel = model.copy(isDownloaded = true)
                        modelRepository.updateModel(updatedModel)
                    }
                    is ModelDownloadState.Failed,
                    ModelDownloadState.Cancelled -> {
                        destinationFile.delete()
                        modelRepository.updateModel(model.copy(isDownloaded = false))
                    }
                    else -> Unit
                }
            } finally {
                synchronized(activeDownloadJobs) {
                    activeDownloadJobs.remove(modelId)
                }
            }
        }

        synchronized(activeDownloadJobs) {
            activeDownloadJobs[modelId] = job
        }
        job.start()
    }

    fun loadModel(model: LocalModelInfo) {
        android.util.Log.d("FitAI_Diag", "ModelManager.loadModel called for: ${model.id}, path: ${model.localPath}")
        val file = File(model.localPath)
        android.util.Log.d("FitAI_Diag", "File exists: ${file.exists()}, length: ${file.length()}")
        if (!file.exists()) return

        if (model.fileSizeBytes > 0 && file.length() != model.fileSizeBytes) {
            android.util.Log.w("FitAI_Diag", "Model file size mismatch! Expected ${model.fileSizeBytes}, got ${file.length()}. Deleting corrupted file...")
            file.delete()
            managerScope.launch {
                modelRepository.updateModel(model.copy(isDownloaded = false))
            }
            return
        }

        when (model.type) {
            ModelType.LLM -> {
                android.util.Log.d("FitAI_Diag", "Loading LLM: ${model.name}")
                gemmaHelper.loadModel(model.localPath, model.name)
            }
            ModelType.FOOD_CLASSIFIER -> {
                android.util.Log.d("FitAI_Diag", "Loading Food Classifier: ${model.name}")
                classifierHelper.loadModel(model.localPath)
            }
            else -> {}
        }
    }

    suspend fun importLocalModelFile(modelId: String, uri: android.net.Uri) = withContext(Dispatchers.IO) {
        val model = modelRepository.getModelById(modelId) ?: return@withContext
        val destFile = File(model.localPath)
        destFile.parentFile?.mkdirs()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            java.io.FileOutputStream(destFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
        
        val updated = model.copy(isDownloaded = true)
        modelRepository.updateModel(updated)
    }

    private suspend fun rememberSelectedModel(model: LocalModelInfo) {
        when (model.type) {
            ModelType.LLM -> {
                userPreferenceStore.saveSelectedLlmModelId(model.id)
                userPreferenceStore.saveSelectedLlmPath(model.localPath)
            }
            ModelType.FOOD_CLASSIFIER -> userPreferenceStore.saveSelectedClassifierPath(model.localPath)
            else -> Unit
        }
    }
}
