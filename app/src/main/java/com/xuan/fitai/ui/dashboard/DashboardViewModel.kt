package com.xuan.fitai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.data.model.AdviceCache
import com.xuan.fitai.data.local.ProgressDao
import com.xuan.fitai.ai.DashboardAdviceContent
import com.xuan.fitai.ai.DashboardAdviceParser
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.MealRepository
import com.xuan.fitai.data.repository.UserRepository
import com.xuan.fitai.data.repository.ReminderRepository
import com.xuan.fitai.data.model.ReminderSettings
import com.xuan.fitai.util.currentDateFlow
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
    private val gemmaHelper: GemmaLocalHelper,
    private val reminderRepository: ReminderRepository,
    private val progressDao: ProgressDao,
    private val foodAssessment: com.xuan.fitai.nutrition.FoodAssessment
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val mealHistory = mealRepository.allMeals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var followToday = true
    val mealDate = selectedDate.asStateFlow()
    val selectedMeals = combine(mealHistory, selectedDate) { meals, date ->
        meals.filter { java.time.Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun selectMealDate(date: LocalDate) { selectedDate.value = date; followToday = date == LocalDate.now() }
    val weights = progressDao.weights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    suspend fun saveWeight(entry: com.xuan.fitai.data.model.WeightEntry) = progressDao.saveWeight(entry)
    suspend fun deleteWeight(date: String) = progressDao.deleteWeight(date)
    suspend fun applyWeight(weight: Float) { userRepository.saveUserProfile(userRepository.userProfile.first().copy(weight = weight)) }
    suspend fun saveMealRecord(original: Meal?, copy: Boolean, name: String, portion: String, type: String,
        favorite: Boolean, timestamp: Long, cal: Float, protein: Float, carbs: Float, fat: Float,
        aiEstimated: Boolean, aiAdvice: String?, referenceJson: String?) {
        require(name.isNotBlank() && portion.isNotBlank())
        require(listOf(cal, protein, carbs, fat).all { it.isFinite() && it >= 0 })
        val record = Meal(id = if (copy) 0 else original?.id ?: 0, name = name.trim(), portionSize = portion.trim(),
            mealType = type, isFavorite = favorite, timestamp = timestamp, calories = cal, protein = protein, carbs = carbs, fat = fat,
            isAiEstimated = aiEstimated, aiAdvice = aiAdvice, nutritionReferenceJson = referenceJson)
        if (record.id == 0) mealRepository.insertMeal(record) else check(mealRepository.updateMeal(record) == 1) { "這筆餐點已不存在，請重新新增" }
    }

    val today = currentDateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    private val weekMeals = today.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        mealRepository.getMealsBetween(
            date.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayMeals: StateFlow<List<Meal>> = combine(today, weekMeals) { date, meals ->
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        meals.filter { it.timestamp >= start && it.timestamp < end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekSummary = combine(today, weekMeals) { date, meals -> summarizeWeek(meals, date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), summarizeWeek(emptyList(), LocalDate.now()))

    val waterSettings = combine(today, reminderRepository.settings) { date, settings ->
        settings.copy(waterConsumedMl = if (settings.waterProgressDate == date.toString()) settings.waterConsumedMl else 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettings())

    suspend fun changeWater(amountMl: Int, date: LocalDate) = reminderRepository.changeWater(amountMl, date)

    private val _aiAdvice = MutableStateFlow("正在為您產生今日本地 AI 健康建議...")
    val dailyAdvice: StateFlow<DashboardAdviceContent> = _aiAdvice
        .map(DashboardAdviceParser::parse)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardAdviceContent())

    private val _adviceUpdatedAt = MutableStateFlow<Long?>(null)
    val adviceUpdatedAt = _adviceUpdatedAt.asStateFlow()

    private val _isAiAdviceGenerating = MutableStateFlow(false)
    val isAiAdviceGenerating: StateFlow<Boolean> = _isAiAdviceGenerating.asStateFlow()

    private val dashboardActive = MutableStateFlow(false)
    private var lastGeneratedAdviceSignature: String? = null
    private val refreshVersion = MutableStateFlow(0)
    @Volatile
    private var bypassAdviceCacheOnce = false

    val modelLoadState = gemmaHelper.loadState
    val loadedModelName: StateFlow<String?> = gemmaHelper.loadedModelName

    private data class DashboardAdviceInput(
        val profile: UserProfile,
        val meals: List<Meal>,
        val loadState: ModelLoadState,
        val isActive: Boolean,
        val date: LocalDate
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
        viewModelScope.launch { currentDateFlow().collect { if (followToday) selectedDate.value = it } }
        // Automatically generate advice when profile, meals, or AI load state updates
        viewModelScope.launch {
            combine(userProfile, combine(todayMeals, today) { meals, date -> meals to date }, gemmaHelper.loadState, dashboardActive, refreshVersion) { profile, datedMeals, loadState, isActive, version ->
                val meals = datedMeals.first
                val input = DashboardAdviceInput(profile, meals, loadState, isActive, datedMeals.second)
                val request = DashboardAdviceRequest(
                    profile = profile,
                    meals = meals,
                    loadState = loadState,
                    isActive = isActive,
                    eventSignature = "${input.eventSignature()}|$version",
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
            date,
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

    fun stopDailyAdvice() {
        dashboardActive.value = false
    }

    fun refreshDailyAdvice() {
        dashboardActive.value = true
        lastGeneratedAdviceSignature = null
        bypassAdviceCacheOnce = true
        refreshVersion.update { it + 1 }
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
        val maxTokens = gemmaHelper.contextTokenLimit.first()

        val prompt = com.xuan.fitai.ai.PromptBuilder.buildDashboardAdvicePrompt(
            profile = profile,
            todayCalories = totalCalories,
            todayProtein = totalProtein,
            todayCarbs = totalCarbs,
            todayFat = totalFat,
            maxTokens = maxTokens,
        )

        try {
            val bypassCache = bypassAdviceCacheOnce
            bypassAdviceCacheOnce = false
            val cacheKey = "${today.value}|$contentSignature"
            val cached = if (!bypassCache) progressDao.advice(cacheKey) else null
            if (cached != null) {
                _aiAdvice.value = cached.rawText
                _adviceUpdatedAt.value = cached.updatedAt
                lastGeneratedAdviceSignature = contentSignature
                return
            }
            _isAiAdviceGenerating.value = true
            _aiAdvice.value = ""
            _adviceUpdatedAt.value = null
            android.util.Log.d("FitAI_VM", "generateDailyAdvice: calling generateReplyFlow")
            collectAdviceWithOneRetry(prompt)
            if (_aiAdvice.value.isNotBlank()) {
                val updatedAt = System.currentTimeMillis()
                progressDao.saveAdvice(AdviceCache(cacheKey, _aiAdvice.value, updatedAt))
                _adviceUpdatedAt.value = updatedAt
            }
            lastGeneratedAdviceSignature = contentSignature
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_VM", "generateDailyAdvice cancelled")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_VM", "generateDailyAdvice failed", e)
            _aiAdvice.value = "今日健康建議產生失敗: ${e.localizedMessage}"
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
                    if (GemmaOutputParser.detectRepetitionLoop(currentText) != null) {
                        throw RepetitionLoopException()
                    }
                }
                _aiAdvice.value = GemmaOutputParser.pruneRepetitionLoop(currentText).trim()
                return
            } catch (e: RepetitionLoopException) {
                android.util.Log.w("FitAI_VM", "Repetition loop detected in daily advice; pruning and finishing early.")
                val pruned = GemmaOutputParser.pruneRepetitionLoop(_aiAdvice.value).trim()
                _aiAdvice.value = pruned
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

    fun askAiForMealSuggestion(foodName: String, portion: String, useReference: Boolean, googleRetry: Boolean = false) =
        foodAssessment.analyze(foodName, portion, userProfile.value.goal, useReference, googleRetry)

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
        private val gemmaHelper: GemmaLocalHelper,
        private val reminderRepository: ReminderRepository,
        private val progressDao: ProgressDao,
        private val foodAssessment: com.xuan.fitai.nutrition.FoodAssessment
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(userRepository, mealRepository, gemmaHelper, reminderRepository, progressDao, foodAssessment) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private class RepetitionLoopException : CancellationException("Repetition loop detected")
}
