package com.xuan.fitai.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.MealRepository
import com.xuan.fitai.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class ScannerUiState {
    data object CameraPreview : ScannerUiState()
    data object Classifying : ScannerUiState()
    data object GemmaVisionIdentifying : ScannerUiState()
    data class EditDetails(
        val detectedLabel: String,
        val confidence: Float,
        val candidates: List<FoodClassificationResult> = emptyList(),
        val gemmaIdentified: Boolean = false
    ) : ScannerUiState()
    data object GemmaAnalysing : ScannerUiState()
    data class GemmaAnalysisResult(
        val foodName: String,
        val portion: String,
        val analysis: GemmaFoodAnalysis
    ) : ScannerUiState()
    data object SavedSuccess : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

class FoodScannerViewModel(
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
    private val gemmaHelper: GemmaLocalHelper,
    private val classifierHelper: FoodClassifierHelper
) : ViewModel() {

    private companion object {
        const val GEMMA_VISION_TIMEOUT_MS = 120_000L
        const val GEMMA_LOAD_WAIT_TIMEOUT_MS = 120_000L
        const val LOW_CONFIDENCE_THRESHOLD = 0.60f
        const val MANUAL_FOOD_LABEL = "Manual input"
    }

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val classifierLoadState = classifierHelper.loadState
    val gemmaLoadState = gemmaHelper.loadState

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.CameraPreview)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun resetScanner() {
        _uiState.value = ScannerUiState.CameraPreview
    }

    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ScannerUiState.Classifying
            try {
                // Run classifier if model loaded, otherwise fall through to Gemma vision
                val results = if (classifierLoadState.value == ModelLoadState.Loaded) {
                    classifierHelper.classifyImage(bitmap)
                } else emptyList()

                val bestResult = results.firstOrNull()
                val confidence = bestResult?.confidence ?: 0f

                // If confidence < 60% or no result, try Gemma vision identification.
                // Gallery waits until the model instance is initialized before inference;
                // do the same when the app has just started and Gemma is still loading.
                if (confidence < LOW_CONFIDENCE_THRESHOLD && shouldTryGemmaVision()) {
                    _uiState.value = ScannerUiState.GemmaVisionIdentifying
                    val gemmaLabel = if (awaitGemmaVisionReady()) {
                        withTimeoutOrNull(GEMMA_VISION_TIMEOUT_MS) {
                            gemmaHelper.identifyFoodFromImage(bitmap)
                        }?.trim().orEmpty()
                    } else {
                        ""
                    }

                    if (gemmaLabel.isNotBlank() && gemmaLabel != MANUAL_FOOD_LABEL) {
                        _uiState.value = ScannerUiState.EditDetails(
                            detectedLabel = gemmaLabel,
                            confidence = 1.0f,
                            candidates = results,
                            gemmaIdentified = true
                        )
                    } else if (bestResult != null) {
                        _uiState.value = ScannerUiState.EditDetails(
                            detectedLabel = bestResult.label,
                            confidence = confidence,
                            candidates = results
                        )
                    } else {
                        _uiState.value = ScannerUiState.EditDetails(MANUAL_FOOD_LABEL, 0f, emptyList())
                    }
                } else if (bestResult != null) {
                    _uiState.value = ScannerUiState.EditDetails(
                        detectedLabel = bestResult.label,
                        confidence = confidence,
                        candidates = results
                    )
                } else {
                    // No model loaded at all — show blank for user to fill in
                    _uiState.value = ScannerUiState.EditDetails("未知食物", 0f, emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ScannerUiState.Error("影像辨識失敗: ${e.localizedMessage}")
            }
        }
    }

    private fun shouldTryGemmaVision(): Boolean {
        return when (gemmaLoadState.value) {
            ModelLoadState.Loaded -> gemmaHelper.visionReady.value
            ModelLoadState.Loading -> true
            else -> false
        }
    }

    private suspend fun awaitGemmaVisionReady(): Boolean {
        return when (gemmaLoadState.value) {
            ModelLoadState.Loaded -> gemmaHelper.visionReady.value
            ModelLoadState.Loading -> {
                withTimeoutOrNull(GEMMA_LOAD_WAIT_TIMEOUT_MS) {
                    gemmaLoadState.first { it != ModelLoadState.Loading }
                } == ModelLoadState.Loaded && gemmaHelper.visionReady.value
            }
            else -> false
        }
    }

    fun analyzeWithGemma(foodName: String, portion: String) {
        viewModelScope.launch {
            if (gemmaLoadState.value != ModelLoadState.Loaded) {
                _uiState.value = ScannerUiState.Error("Gemma AI 模型尚未載入，請先前往設定頁面載入模型")
                return@launch
            }
            _uiState.value = ScannerUiState.GemmaAnalysing
            try {
                val goal = userProfile.value.goal
                val analysis = gemmaHelper.analyzeFood(foodName, portion, goal)
                _uiState.value = ScannerUiState.GemmaAnalysisResult(foodName, portion, analysis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ScannerUiState.Error("Gemma 分析失敗: ${e.localizedMessage}")
            }
        }
    }

    fun saveMeal(foodName: String, portion: String, analysis: GemmaFoodAnalysis, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val meal = Meal(
                    name = "$foodName ($portion)",
                    calories = analysis.calories,
                    protein = analysis.protein,
                    carbs = analysis.carbs,
                    fat = analysis.fat,
                    portionSize = portion,
                    isAiEstimated = true,
                    aiAdvice = analysis.advice
                )
                mealRepository.insertMeal(meal)
                _uiState.value = ScannerUiState.SavedSuccess
                onComplete()
            } catch (e: Exception) {
                _uiState.value = ScannerUiState.Error("儲存失敗: ${e.localizedMessage}")
            }
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val mealRepository: MealRepository,
        private val gemmaHelper: GemmaLocalHelper,
        private val classifierHelper: FoodClassifierHelper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FoodScannerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FoodScannerViewModel(userRepository, mealRepository, gemmaHelper, classifierHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
