package com.xuan.fitai.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.data.local.ProgressDao
import com.xuan.fitai.data.model.WorkoutSetEntry
import java.time.LocalDate
import java.time.DayOfWeek
import kotlinx.coroutines.withContext
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.ai.PromptBuilder
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.model.WorkoutPlan
import com.xuan.fitai.data.repository.UserRepository
import com.xuan.fitai.data.repository.WorkoutRepository
import com.xuan.fitai.util.HealthConnectHelper
import com.xuan.fitai.util.HealthData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class WorkoutViewModel(
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val gemmaHelper: GemmaLocalHelper,
    private val healthConnectHelper: HealthConnectHelper,
    private val userPreferenceStore: com.xuan.fitai.data.datastore.UserPreferenceStore,
    private val progressDao: ProgressDao
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val setHistory = progressDao.workoutSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workoutPlans: StateFlow<List<WorkoutPlan>> = combine(workoutRepository.allWorkoutPlans, progressDao.workoutSets(), com.xuan.fitai.util.currentDateFlow()) { plans, logs, date ->
        plans.map { plan -> plan.copy(sets = plan.sets.coerceIn(1, 100), isCompleted = WorkoutProgress.completedSets(plan, logs, date) >= plan.sets.coerceIn(1, 100)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val restDeadline = MutableStateFlow<Long?>(null)
    fun startRest(seconds: Int) { restDeadline.value = android.os.SystemClock.elapsedRealtime() + seconds * 1000L }
    fun cancelRest() { restDeadline.value = null }
    suspend fun recordSet(plan: WorkoutPlan, number: Int, weight: Float, reps: Int) {
        require(number in 1..plan.sets && weight.isFinite() && weight >= 0 && weight <= 1000 && reps in 1..1000)
        progressDao.saveSet(WorkoutSetEntry(planId = plan.id, exerciseName = plan.exerciseName, date = LocalDate.now().toString(), setNumber = number, weightKg = weight, reps = reps))
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _generationThinking = MutableStateFlow<String?>(null)
    val generationThinking: StateFlow<String?> = _generationThinking.asStateFlow()

    private val _workoutSummary = MutableStateFlow<String?>(null)
    val workoutSummary: StateFlow<String?> = _workoutSummary.asStateFlow()

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData.asStateFlow()

    private val _hasHealthPermissions = MutableStateFlow(false)
    val hasHealthPermissions: StateFlow<Boolean> = _hasHealthPermissions.asStateFlow()

    private val _healthConnectSdkStatus = MutableStateFlow(healthConnectHelper.getSdkStatus())
    val healthConnectSdkStatus: StateFlow<Int> = _healthConnectSdkStatus.asStateFlow()

    init {
        // Auto-generate default plan if DB is empty
        viewModelScope.launch {
            val currentPlans = workoutRepository.allWorkoutPlans.first()
            if (currentPlans.isEmpty()) {
                generateDefaultWorkoutPlan()
            }
        }
        // Load persisted thinking process text from Datastore
        viewModelScope.launch {
            userPreferenceStore.workoutPlanThinkingFlow.collect { thinking ->
                _generationThinking.value = thinking
            }
        }
        // Load persisted AI summary
        viewModelScope.launch {
            userPreferenceStore.workoutSummaryFlow.collect { summary ->
                _workoutSummary.value = summary
            }
        }
        checkHealthConnectStatus()
        loadHealthData()
    }

    fun getHealthConnectHelper() = healthConnectHelper

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val status = healthConnectHelper.getSdkStatus()
            val available = healthConnectHelper.isSdkAvailable()
            val hasPerms = healthConnectHelper.hasAllPermissions()
            android.util.Log.d("FitAI_HealthConnect", "checkHealthConnectStatus: status=$status, available=$available, hasPerms=$hasPerms")
            
            _healthConnectSdkStatus.value = status
            _hasHealthPermissions.value = available && hasPerms
        }
    }

    fun loadHealthData() {
        viewModelScope.launch {
            if (healthConnectHelper.isSdkAvailable() && healthConnectHelper.hasAllPermissions()) {
                _healthData.value = healthConnectHelper.readTodayHealthData()
                _hasHealthPermissions.value = true
            } else {
                _healthData.value = null
                _hasHealthPermissions.value = false
            }
        }
    }

    private suspend fun generateDefaultWorkoutPlan() {
        val goal = userProfile.value.goal
        workoutRepository.clearAllWorkoutPlans()

        val plans = if (goal == "增肌") {
            listOf(
                WorkoutPlan(dayOfWeek = "星期一", exerciseName = "槓鈴臥推 (Bench Press)", sets = 4, reps = "8-12次", targetMuscleGroup = "胸肌"),
                WorkoutPlan(dayOfWeek = "星期一", exerciseName = "啞鈴飛鳥 (Dumbbell Flyes)", sets = 3, reps = "12次", targetMuscleGroup = "胸肌"),
                WorkoutPlan(dayOfWeek = "星期三", exerciseName = "深蹲 (Barbell Squat)", sets = 4, reps = "10次", targetMuscleGroup = "腿部"),
                WorkoutPlan(dayOfWeek = "星期三", exerciseName = "保加利亞單腿蹲 (Bulgarian Split Squat)", sets = 3, reps = "12次", targetMuscleGroup = "腿部"),
                WorkoutPlan(dayOfWeek = "星期五", exerciseName = "滑輪下拉 (Lat Pulldown)", sets = 4, reps = "10-12次", targetMuscleGroup = "背肌"),
                WorkoutPlan(dayOfWeek = "星期五", exerciseName = "單臂啞鈴划船 (Dumbbell Row)", sets = 3, reps = "12次", targetMuscleGroup = "背肌"),
                WorkoutPlan(dayOfWeek = "星期六", exerciseName = "槓鈴肩推 (Overhead Press)", sets = 4, reps = "8-10次", targetMuscleGroup = "肩膀")
            )
        } else if (goal == "減肥") {
            listOf(
                WorkoutPlan(dayOfWeek = "星期一", exerciseName = "波比跳 (Burpees)", sets = 3, reps = "15次", targetMuscleGroup = "全身"),
                WorkoutPlan(dayOfWeek = "星期一", exerciseName = "開合跳 (Jumping Jacks)", sets = 3, reps = "45秒", targetMuscleGroup = "有氧"),
                WorkoutPlan(dayOfWeek = "星期三", exerciseName = "徒手深蹲 (Bodyweight Squats)", sets = 4, reps = "20次", targetMuscleGroup = "腿部"),
                WorkoutPlan(dayOfWeek = "星期三", exerciseName = "登山者 (Mountain Climbers)", sets = 3, reps = "30秒", targetMuscleGroup = "核心"),
                WorkoutPlan(dayOfWeek = "星期五", exerciseName = "高抬腿 (High Knees)", sets = 3, reps = "45秒", targetMuscleGroup = "有氧"),
                WorkoutPlan(dayOfWeek = "星期五", exerciseName = "平板支撐 (Plank)", sets = 3, reps = "60秒", targetMuscleGroup = "核心"),
                WorkoutPlan(dayOfWeek = "星期日", exerciseName = "慢跑 (Jogging)", sets = 1, reps = "30分鐘", targetMuscleGroup = "心肺")
            )
        } else {
            listOf(
                WorkoutPlan(dayOfWeek = "星期二", exerciseName = "快走 (Brisk Walking)", sets = 1, reps = "30分鐘", targetMuscleGroup = "心肺"),
                WorkoutPlan(dayOfWeek = "星期四", exerciseName = "瑜伽伸展 (Yoga)", sets = 1, reps = "20分鐘", targetMuscleGroup = "柔軟度"),
                WorkoutPlan(dayOfWeek = "星期六", exerciseName = "徒手核心訓練 (Core Workout)", sets = 3, reps = "15下", targetMuscleGroup = "核心")
            )
        }

        for (plan in plans) {
            workoutRepository.insertWorkoutPlan(plan)
        }
    }

    val recordError = MutableStateFlow<String?>(null)
    private val updatingPlans = mutableSetOf<Int>()
    fun toggleWorkoutPlan(plan: WorkoutPlan) {
        if (!updatingPlans.add(plan.id)) return
        recordError.value = null
        viewModelScope.launch {
            try {
                val date = LocalDate.now()
                val start = date.with(DayOfWeek.MONDAY).toString()
                val end = date.with(DayOfWeek.SUNDAY).toString()
                if (plan.isCompleted) progressDao.clearWeek(plan.id, start, end)
                else progressDao.completeWeek(plan, date.toString(), start, end)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { recordError.value = "更新訓練紀錄失敗，請再試一次" }
            finally { updatingPlans.remove(plan.id) }
        }
    }

    fun addWorkoutPlan(dayOfWeek: String, exerciseName: String, sets: Int, reps: String, targetMuscleGroup: String) {
        viewModelScope.launch {
            workoutRepository.insertWorkoutPlan(
                WorkoutPlan(
                    dayOfWeek = dayOfWeek,
                    exerciseName = exerciseName,
                    sets = sets,
                    reps = reps,
                    targetMuscleGroup = targetMuscleGroup
                )
            )
        }
    }

    fun deleteWorkoutPlan(plan: WorkoutPlan) {
        viewModelScope.launch {
            workoutRepository.deleteWorkoutPlan(plan)
        }
    }

    fun updateWorkoutPlanDetails(plan: WorkoutPlan) {
        viewModelScope.launch {
            workoutRepository.updateWorkoutPlan(plan)
        }
    }

    fun regeneratePlan() {
        viewModelScope.launch {
            userPreferenceStore.saveWorkoutPlanThinking("")
            userPreferenceStore.saveWorkoutSummary("")
            _workoutSummary.value = null
            generateDefaultWorkoutPlan()
        }
    }

    fun generateAIWorkoutPlan(daysPerWeek: Int, preference: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _isSummarizing.value = true
            _generationThinking.value = null
            _workoutSummary.value = ""
            var currentText = ""
            try {
                val profile = userProfile.value
                val maxTokens = gemmaHelper.contextTokenLimit.first()
                val prompt = PromptBuilder.buildWorkoutPlanPrompt(
                    profile = profile,
                    daysPerWeek = daysPerWeek,
                    preference = preference,
                    maxTokens = maxTokens,
                )

                gemmaHelper.generateReplyFlow(prompt).collect { token ->
                    currentText += token
                    val thinking = GemmaOutputParser.extractThinking(currentText)
                    _generationThinking.value = thinking
                    val partialSummary = GemmaOutputParser.extractJsonStringValue(currentText, "summary")
                    _workoutSummary.value = if (!partialSummary.isNullOrBlank()) {
                        GemmaOutputParser.withThinkingContent(
                            thinkingText = thinking,
                            contentText = partialSummary
                        )
                    } else {
                        currentText
                    }
                }
                
                val thinking = GemmaOutputParser.extractThinking(currentText)
                _generationThinking.value = thinking
                userPreferenceStore.saveWorkoutPlanThinking(thinking ?: "")

                val jsonObjectStr = GemmaOutputParser.extractJson(currentText)
                val jsonArrayStr = GemmaOutputParser.extractJsonArray(currentText)
                val rootObject = try {
                    if (jsonObjectStr.isNotBlank()) JSONObject(jsonObjectStr) else null
                } catch (_: Exception) {
                    try {
                        val repaired = GemmaOutputParser.repairJson(jsonObjectStr)
                        if (repaired.isNotBlank()) JSONObject(repaired) else null
                    } catch (_: Exception) {
                        null
                    }
                }
                val summary = rootObject?.optString("summary")?.trim().orEmpty().ifBlank {
                    GemmaOutputParser.extractJsonStringValue(currentText, "summary")?.trim().orEmpty()
                }
                val jsonArray = rootObject?.optJSONArray("weeklyPlan")
                    ?: try {
                        if (jsonArrayStr.isNotBlank()) JSONArray(jsonArrayStr) else null
                    } catch (_: Exception) {
                        try {
                            val repairedArray = GemmaOutputParser.repairJson(jsonArrayStr)
                            if (repairedArray.isNotBlank()) JSONArray(repairedArray) else null
                        } catch (_: Exception) {
                            null
                        }
                    }

                val plansToInsert = mutableListOf<WorkoutPlan>()
                if (jsonArray != null && jsonArray.length() > 0) {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        plansToInsert.add(
                            WorkoutPlan(
                                dayOfWeek = obj.optString("dayOfWeek", "星期一"),
                                exerciseName = obj.optString("exerciseName", "未知運動"),
                                sets = obj.optInt("sets", 3).coerceIn(1, 100),
                                reps = obj.optString("reps", "10下"),
                                targetMuscleGroup = obj.optString("targetMuscleGroup", "全身")
                            )
                        )
                    }
                }

                if (plansToInsert.isEmpty()) {
                    plansToInsert.addAll(GemmaOutputParser.extractWorkoutPlansFromText(currentText))
                }

                if (plansToInsert.isNotEmpty()) {
                    workoutRepository.clearAllWorkoutPlans()
                    for (plan in plansToInsert) {
                        workoutRepository.insertWorkoutPlan(plan)
                    }
                    val effectiveSummary = summary.ifBlank { "已為您客製化規劃每週 ${daysPerWeek} 天訓練計畫。" }
                    val summaryWithThinking = GemmaOutputParser.withThinkingContent(
                        thinkingText = thinking,
                        contentText = effectiveSummary
                    )
                    _workoutSummary.value = summaryWithThinking
                    userPreferenceStore.saveWorkoutSummary(summaryWithThinking)
                } else {
                    userPreferenceStore.saveWorkoutPlanThinking("")
                    userPreferenceStore.saveWorkoutSummary("")
                    _workoutSummary.value = null
                    generateDefaultWorkoutPlan()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Workout", "AI generation exception, checking salvageable exercises", e)
                val fallbackPlans = GemmaOutputParser.extractWorkoutPlansFromText(currentText)
                if (fallbackPlans.isNotEmpty()) {
                    workoutRepository.clearAllWorkoutPlans()
                    for (plan in fallbackPlans) {
                        workoutRepository.insertWorkoutPlan(plan)
                    }
                    val partialSummary = GemmaOutputParser.extractJsonStringValue(currentText, "summary")
                        ?: "已為您安排訓練項目。"
                    _workoutSummary.value = partialSummary
                    userPreferenceStore.saveWorkoutSummary(partialSummary)
                } else {
                    userPreferenceStore.saveWorkoutPlanThinking("")
                    userPreferenceStore.saveWorkoutSummary("")
                    generateDefaultWorkoutPlan()
                }
            } finally {
                _isGenerating.value = false
                _isSummarizing.value = false
            }
        }
    }

    private fun generateWorkoutSummary(planJson: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _workoutSummary.value = ""
            try {
                val profile = userProfile.value
                val maxTokens = gemmaHelper.contextTokenLimit.first()
                val summaryPrompt = PromptBuilder.buildWorkoutSummaryPrompt(
                    goal = profile.goal,
                    planJson = planJson,
                    maxTokens = maxTokens,
                )

                var rawText = ""
                gemmaHelper.generateReplyFlow(summaryPrompt).collect { token ->
                    rawText += token
                    // Store raw text directly — identical to DashboardViewModel:
                    //   currentText += token; _aiAdvice.value = currentText
                    // ThinkingContent handles thinking/content split itself,
                    // so there's no flash of thinking text in the content area
                    _workoutSummary.value = rawText
                }

                // Persist raw text so ThinkingContent re-parses correctly on next visit
                _workoutSummary.value = rawText
                userPreferenceStore.saveWorkoutSummary(rawText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Workout", "Summary generation failed", e)
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val workoutRepository: WorkoutRepository,
        private val gemmaHelper: GemmaLocalHelper,
        private val healthConnectHelper: HealthConnectHelper,
        private val userPreferenceStore: com.xuan.fitai.data.datastore.UserPreferenceStore,
        private val progressDao: ProgressDao
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(userRepository, workoutRepository, gemmaHelper, healthConnectHelper, userPreferenceStore, progressDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
