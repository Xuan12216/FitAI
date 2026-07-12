package com.xuan.fitai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.MealRepository
import com.xuan.fitai.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class DashboardViewModel(
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
    private val gemmaHelper: GemmaLocalHelper
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val todayMeals: StateFlow<List<Meal>> = mealRepository.getMealsForDay(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiAdvice = MutableStateFlow("正在為您產生今日本地 AI 健康建議...")
    val aiAdvice: StateFlow<String> = _aiAdvice.asStateFlow()

    private val _isAiAdviceGenerating = MutableStateFlow(false)
    val isAiAdviceGenerating: StateFlow<Boolean> = _isAiAdviceGenerating.asStateFlow()

    private val dashboardActive = MutableStateFlow(false)
    private var lastGeneratedAdviceSignature: String? = null
    private var lastRequestedAdviceSignature: String? = null

    val loadedModelName: StateFlow<String?> = gemmaHelper.loadedModelName

    private data class DashboardAdviceInput(
        val profile: UserProfile,
        val meals: List<Meal>,
        val loadState: ModelLoadState,
        val isActive: Boolean
    )

    private data class DashboardAdviceRequest(
        val profile: UserProfile,
        val meals: List<Meal>,
        val loadState: ModelLoadState,
        val isActive: Boolean,
        val eventSignature: String,
        val contentSignature: String
    )

    init {
        // Automatically generate advice when profile, meals, or AI load state updates
        viewModelScope.launch {
            combine(userProfile, todayMeals, gemmaHelper.loadState, dashboardActive) { profile, meals, loadState, isActive ->
                val input = DashboardAdviceInput(profile, meals, loadState, isActive)
                val request = DashboardAdviceRequest(
                    profile = profile,
                    meals = meals,
                    loadState = loadState,
                    isActive = isActive,
                    eventSignature = input.eventSignature(),
                    contentSignature = input.contentSignature()
                )
                android.util.Log.d(
                    "FitAI_VM",
                    "DashboardViewModel init flow combined: loadState=$loadState, active=$isActive, eventSignature=${request.eventSignature}, contentSignature=${request.contentSignature}"
                )
                request
            }
                .debounce(500.milliseconds)
                .distinctUntilChangedBy { it.eventSignature }
                .collectLatest { input ->
                if (!input.isActive) {
                    _isAiAdviceGenerating.value = false
                    android.util.Log.d("FitAI_VM", "DashboardViewModel skipped advice: screen inactive")
                    return@collectLatest
                }
                if (input.loadState == ModelLoadState.Loaded && lastGeneratedAdviceSignature == input.contentSignature) {
                    android.util.Log.d("FitAI_VM", "DashboardViewModel skipped advice: duplicate contentSignature=${input.contentSignature}")
                    return@collectLatest
                }
                if (input.loadState == ModelLoadState.Loaded && lastRequestedAdviceSignature == input.contentSignature) {
                    android.util.Log.d("FitAI_VM", "DashboardViewModel skipped advice: duplicate in-flight/requested contentSignature=${input.contentSignature}")
                    return@collectLatest
                }
                android.util.Log.d("FitAI_VM", "DashboardViewModel collecting flow: calling generateDailyAdvice")
                generateDailyAdvice(input.profile, input.meals, input.loadState, input.contentSignature)
            }
        }
    }

    private fun DashboardAdviceInput.eventSignature(): String {
        return listOf(isActive, loadState, contentSignature()).joinToString(separator = "|")
    }

    private fun DashboardAdviceInput.contentSignature(): String {
        val mealsKey = meals.joinToString(separator = ";") {
            "${it.id}:${it.name}:${it.calories}:${it.protein}:${it.carbs}:${it.fat}:${it.timestamp}"
        }
        return listOf(
            profile.goal,
            profile.targetCalories,
            profile.targetProteinGrams,
            profile.targetCarbsGrams,
            profile.targetFatGrams,
            mealsKey
        ).joinToString(separator = "|")
    }

    fun setAdviceGenerationActive(active: Boolean) {
        dashboardActive.value = active
    }

    fun refreshDailyAdvice() {
        viewModelScope.launch {
            lastGeneratedAdviceSignature = null
            lastRequestedAdviceSignature = null
            generateDailyAdvice(
                profile = userProfile.value,
                meals = todayMeals.value,
                loadState = gemmaHelper.loadState.value,
                contentSignature = DashboardAdviceInput(
                    profile = userProfile.value,
                    meals = todayMeals.value,
                    loadState = gemmaHelper.loadState.value,
                    isActive = true
                ).contentSignature()
            )
        }
    }

    private suspend fun generateDailyAdvice(
        profile: UserProfile,
        meals: List<Meal>,
        loadState: ModelLoadState,
        contentSignature: String
    ) {
        android.util.Log.d("FitAI_VM", "generateDailyAdvice started: loadState=$loadState")
        if (loadState == ModelLoadState.Loading) {
            _isAiAdviceGenerating.value = true
            android.util.Log.d("FitAI_VM", "generateDailyAdvice skipped: model loading")
            return
        }

        if (loadState != ModelLoadState.Loaded) {
            _isAiAdviceGenerating.value = false
            _aiAdvice.value = "⚠️ 本地 Gemma AI 模型尚未載入。請先至「模型設定」頁面下載或載入模型，以取得精準健康建議。"
            android.util.Log.d("FitAI_VM", "generateDailyAdvice returned early: model not loaded")
            return
        }

        val totalCalories = meals.sumOf { it.calories.toDouble() }.toFloat()
        val totalProtein = meals.sumOf { it.protein.toDouble() }.toFloat()
        val totalCarbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
        val totalFat = meals.sumOf { it.fat.toDouble() }.toFloat()

        val prompt = """
            你是一位專業健康顧問。使用者目標是「${profile.goal}」。
            今日熱量目標為 ${profile.targetCalories.toInt()} kcal，已攝取 $totalCalories kcal。
            今日三大營養素已攝取：蛋白質 ${totalProtein.toInt()}g，碳水 ${totalCarbs.toInt()}g，脂肪 ${totalFat.toInt()}g。
            請根據以上數據，只輸出一段 150 個中文字以內的繁體中文實用建議。
        """.trimIndent()

        try {
            _isAiAdviceGenerating.value = true
            lastRequestedAdviceSignature = contentSignature
            android.util.Log.d("FitAI_VM", "generateDailyAdvice: calling generateReplyFlow")
            collectAdviceWithOneRetry(prompt)
            lastGeneratedAdviceSignature = contentSignature
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_VM", "generateDailyAdvice cancelled")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_VM", "generateDailyAdvice failed", e)
            _aiAdvice.value = "今日健康建議產生失敗: ${e.localizedMessage}"
            lastRequestedAdviceSignature = null
        } finally {
            _isAiAdviceGenerating.value = false
            android.util.Log.d("FitAI_VM", "generateDailyAdvice finished")
        }
    }

    private suspend fun collectAdviceWithOneRetry(prompt: String) {
        var attempt = 0
        while (true) {
            try {
                var currentText = ""
                gemmaHelper.generateReplyFlow(prompt).collect { token ->
                    currentText += token
                    _aiAdvice.value = currentText
                }
                return
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!e.isRecoverableGemmaSessionError() || attempt >= 1) {
                    throw e
                }
                attempt += 1
                android.util.Log.w("FitAI_VM", "generateDailyAdvice retrying after recoverable Gemma session error", e)
                _aiAdvice.value = ""
            }
        }
    }

    private fun Throwable.isRecoverableGemmaSessionError(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current.message.orEmpty().contains("Session is not prefilled yet", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            mealRepository.deleteMeal(meal)
        }
    }

    fun askAiForMealSuggestion(foodName: String): Flow<String> {
        if (gemmaHelper.loadState.value != ModelLoadState.Loaded) {
            return flow { throw IllegalStateException("Gemma model is not loaded") }
        }
        val goal = userProfile.value.goal
        return gemmaHelper.analyzeFoodFlow(foodName, "1份", goal)
    }

    fun addManualMeal(name: String, cal: Float, p: Float, c: Float, f: Float) {
        viewModelScope.launch {
            mealRepository.insertMeal(
                Meal(
                    name = name,
                    calories = cal,
                    protein = p,
                    carbs = c,
                    fat = f,
                    isAiEstimated = false
                )
            )
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val mealRepository: MealRepository,
        private val gemmaHelper: GemmaLocalHelper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(userRepository, mealRepository, gemmaHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
