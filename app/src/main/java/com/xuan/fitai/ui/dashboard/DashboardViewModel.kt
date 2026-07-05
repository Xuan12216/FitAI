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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    val loadedModelName: StateFlow<String?> = gemmaHelper.loadedModelName

    init {
        // Automatically generate advice when profile, meals, or AI load state updates
        viewModelScope.launch {
            combine(userProfile, todayMeals, gemmaHelper.loadState) { profile, meals, loadState ->
                android.util.Log.d("FitAI_VM", "DashboardViewModel init flow combined: loadState=$loadState")
                Pair(profile, meals)
            }.collect { (profile, meals) ->
                android.util.Log.d("FitAI_VM", "DashboardViewModel collecting flow: calling generateDailyAdvice")
                generateDailyAdvice(profile, meals)
            }
        }
    }

    private suspend fun generateDailyAdvice(profile: UserProfile, meals: List<Meal>) {
        android.util.Log.d("FitAI_VM", "generateDailyAdvice started: loadState=${gemmaHelper.loadState.value}")
        if (gemmaHelper.loadState.value != ModelLoadState.Loaded) {
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
            請根據以上數據，給予使用者一小段（大約 50 字以內）今日飲食的繁體中文簡短實用建議。
        """.trimIndent()

        try {
            _isAiAdviceGenerating.value = true
            android.util.Log.d("FitAI_VM", "generateDailyAdvice: calling generateReply")
            val reply = gemmaHelper.generateReply(prompt)
            android.util.Log.d("FitAI_VM", "generateDailyAdvice: generateReply returned, length=${reply.length}")
            _aiAdvice.value = reply
        } catch (e: Exception) {
            android.util.Log.e("FitAI_VM", "generateDailyAdvice failed", e)
            _aiAdvice.value = "今日健康建議產生失敗: ${e.localizedMessage}"
        } finally {
            _isAiAdviceGenerating.value = false
            android.util.Log.d("FitAI_VM", "generateDailyAdvice finished")
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            mealRepository.deleteMeal(meal)
        }
    }

    suspend fun askAiForMealSuggestion(foodName: String): com.xuan.fitai.ai.GemmaFoodAnalysis? {
        if (gemmaHelper.loadState.value != ModelLoadState.Loaded) {
            return null
        }
        val goal = userProfile.value.goal
        return try {
            gemmaHelper.analyzeFood(foodName, "1份", goal)
        } catch (e: Exception) {
            null
        }
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
