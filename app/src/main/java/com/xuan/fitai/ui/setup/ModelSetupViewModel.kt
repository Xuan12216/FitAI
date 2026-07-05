package com.xuan.fitai.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.ai.ModelManager
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.ModelType
import com.xuan.fitai.data.repository.ModelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ModelSetupViewModel(
    private val modelRepository: ModelRepository,
    private val userPreferenceStore: UserPreferenceStore,
    private val modelManager: ModelManager
) : ViewModel() {

    val allModels: StateFlow<List<LocalModelInfo>> = modelRepository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadStates = modelManager.downloadStates

    val hfToken: StateFlow<String> = userPreferenceStore.hfTokenFlow
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val gemmaLoadState = modelManager.gemmaHelper.loadState
    val classifierLoadState = modelManager.classifierHelper.loadState
    val loadedModelName = modelManager.gemmaHelper.loadedModelName

    // Configuration flows
    val maxTokens: StateFlow<Int> = userPreferenceStore.maxTokensFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4000)

    val topK: StateFlow<Int> = userPreferenceStore.topKFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 64)

    val topP: StateFlow<Float> = userPreferenceStore.topPFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.95f)

    val temperature: StateFlow<Float> = userPreferenceStore.temperatureFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val useGpu: StateFlow<Boolean> = userPreferenceStore.useGpuFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val enableThinking: StateFlow<Boolean> = userPreferenceStore.enableThinkingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val enableSpeculative: StateFlow<Boolean> = userPreferenceStore.enableSpeculativeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val systemPrompt: StateFlow<String> = userPreferenceStore.systemPromptFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "你是一個專業的健康與營養顧問。請用繁體中文回答。")

    init {
        viewModelScope.launch {
            modelManager.initializeDefaultModels()
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            userPreferenceStore.saveHfToken(token)
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            userPreferenceStore.clearHfToken()
        }
    }

    fun downloadModel(modelId: String) {
        val token = if (modelId == "gemma") hfToken.value else null
        viewModelScope.launch {
            modelManager.downloadModel(modelId, token) {
                // Once download completes, auto-load it
                viewModelScope.launch {
                    val model = modelRepository.getModelById(modelId)
                    if (model != null) {
                        loadModel(model)
                    }
                }
            }
        }
    }

    fun loadModel(model: LocalModelInfo) {
        viewModelScope.launch {
            rememberSelectedModel(model)
            modelManager.loadModel(model)
        }
    }

    fun importLocalFile(modelId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            modelManager.importLocalModelFile(modelId, uri)
            // Once imported, auto-load
            val model = modelRepository.getModelById(modelId)
            if (model != null) {
                rememberSelectedModel(model)
                modelManager.loadModel(model)
            }
        }
    }

    fun saveModelConfig(
        maxTokens: Int,
        topK: Int,
        topP: Float,
        temperature: Float,
        useGpu: Boolean,
        enableThinking: Boolean,
        enableSpeculative: Boolean,
        systemPrompt: String
    ) {
        viewModelScope.launch {
            userPreferenceStore.saveModelConfig(
                maxTokens, topK, topP, temperature, useGpu, enableThinking, enableSpeculative, systemPrompt
            )
            // Reload active LLM model to apply config immediately if loaded
            val selectedLlmModelId = userPreferenceStore.selectedLlmModelIdFlow.first()
            if (selectedLlmModelId != null) {
                val model = modelRepository.getModelById(selectedLlmModelId)
                if (model != null && model.isDownloaded) {
                    modelManager.loadModel(model)
                }
            }
        }
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

    class Factory(
        private val modelRepository: ModelRepository,
        private val userPreferenceStore: UserPreferenceStore,
        private val modelManager: ModelManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ModelSetupViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ModelSetupViewModel(modelRepository, userPreferenceStore, modelManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
