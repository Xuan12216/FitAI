package com.xuan.fitai.ai

import android.content.Context
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelType
import com.xuan.fitai.data.repository.ModelRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

class ModelManager(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val userPreferenceStore: UserPreferenceStore,
    val gemmaHelper: GemmaLocalHelper,
    val classifierHelper: FoodClassifierHelper
) {
    private val downloader = ModelDownloader()

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    fun getModelFile(fileName: String): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }

    suspend fun initializeDefaultModels() {
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
            gemmaHelper.loadModelSync(preferredLlm.localPath, preferredLlm.name)
        }
    }

    suspend fun downloadModel(modelId: String, token: String?, onComplete: suspend () -> Unit = {}) {
        val model = modelRepository.getModelById(modelId) ?: return
        val destinationFile = File(model.localPath)

        _downloadStates.value = _downloadStates.value + (modelId to ModelDownloadState.Idle)

        downloader.downloadModel(
            url = model.sourceUrl ?: "",
            token = token,
            destinationFile = destinationFile,
            onProgress = { state ->
                _downloadStates.value = _downloadStates.value + (modelId to state)
                if (state is ModelDownloadState.Completed) {
                    val updatedModel = model.copy(isDownloaded = true)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        modelRepository.updateModel(updatedModel)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onComplete()
                        }
                    }
                }
            }
        )
    }

    fun loadModel(model: LocalModelInfo) {
        android.util.Log.d("FitAI_Diag", "ModelManager.loadModel called for: ${model.id}, path: ${model.localPath}")
        val file = File(model.localPath)
        android.util.Log.d("FitAI_Diag", "File exists: ${file.exists()}, length: ${file.length()}")
        if (!file.exists()) return

        if (model.fileSizeBytes > 0 && file.length() != model.fileSizeBytes) {
            android.util.Log.w("FitAI_Diag", "Model file size mismatch! Expected ${model.fileSizeBytes}, got ${file.length()}. Deleting corrupted file...")
            file.delete()
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
}
