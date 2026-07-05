package com.xuan.fitai.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.GemmaOutputParser
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
    private val userPreferenceStore: com.xuan.fitai.data.datastore.UserPreferenceStore
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val workoutPlans: StateFlow<List<WorkoutPlan>> = workoutRepository.allWorkoutPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun toggleWorkoutPlan(plan: WorkoutPlan) {
        viewModelScope.launch {
            workoutRepository.updateWorkoutPlan(plan.copy(isCompleted = !plan.isCompleted))
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
            try {
                val profile = userProfile.value
                val prompt = """
                    You are a professional fitness coach. The user's profile is:
                    - Goal: ${profile.goal}
                    - Age: ${profile.age}, Gender: ${profile.gender}
                    - Height: ${profile.height}cm, Weight: ${profile.weight}kg
                    - Experience level: ${profile.workoutExperience}
                    - Weekly training days: $daysPerWeek days
                    - Preferences: $preference

                    Generate a customized weekly workout plan for them.
                    Output ONLY one raw JSON object. Do not include markdown, code fences, or extra prose.
                    The JSON object must match this schema exactly:
                    {
                      "summary": "3-5 short Traditional Chinese sentences. Mention weekly frequency, main training focus, and a motivational closing.",
                      "weeklyPlan": [
                        {
                          "dayOfWeek": "Traditional Chinese weekday name, for example 星期一",
                          "exerciseName": "Traditional Chinese exercise name with optional English in parentheses",
                          "sets": 4,
                          "reps": "8-12次",
                          "targetMuscleGroup": "Traditional Chinese target muscle group"
                        }
                      ]
                    }
                    Only include the days they should train based on the selected $daysPerWeek days.
                    Make weeklyPlan practical and balanced for the user's goal and experience level.
                """.trimIndent()

                var currentText = ""
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
                val rootObject = if (jsonObjectStr.isNotBlank()) JSONObject(jsonObjectStr) else null
                val summary = rootObject?.optString("summary")?.trim().orEmpty()
                val jsonArray = rootObject?.optJSONArray("weeklyPlan")
                    ?: if (jsonArrayStr.isNotBlank()) JSONArray(jsonArrayStr) else null
                if (jsonArray != null) {
                    if (jsonArray.length() > 0) {
                        workoutRepository.clearAllWorkoutPlans()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            workoutRepository.insertWorkoutPlan(
                                WorkoutPlan(
                                    dayOfWeek = obj.optString("dayOfWeek", "星期一"),
                                    exerciseName = obj.optString("exerciseName", "未知運動"),
                                    sets = obj.optInt("sets", 3),
                                    reps = obj.optString("reps", "10下"),
                                    targetMuscleGroup = obj.optString("targetMuscleGroup", "全身")
                                )
                            )
                        }
                        val summaryWithThinking = GemmaOutputParser.withThinkingContent(
                            thinkingText = thinking,
                            contentText = summary
                        )
                        _workoutSummary.value = summaryWithThinking
                        userPreferenceStore.saveWorkoutSummary(summaryWithThinking)
                    }
                } else {
                    // Fallback to default if JSON extraction fails
                    userPreferenceStore.saveWorkoutPlanThinking("")
                    userPreferenceStore.saveWorkoutSummary("")
                    _workoutSummary.value = null
                    generateDefaultWorkoutPlan()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Workout", "AI generation failed", e)
                userPreferenceStore.saveWorkoutPlanThinking("")
                userPreferenceStore.saveWorkoutSummary("")
                generateDefaultWorkoutPlan()
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
                val summaryPrompt = """
                    Based on the following workout plan JSON, write a concise and motivating summary in Traditional Chinese (繁體中文) for the user.
                    The summary should:
                    - Be 3-5 sentences
                    - Highlight the key training focus and muscle groups targeted
                    - Mention the training frequency and volume
                    - Include a motivational closing statement
                    - Use a friendly, encouraging tone
                    - Do NOT include any JSON or code, only plain prose text

                    User Goal: ${profile.goal}
                    Workout Plan JSON:
                    $planJson

                    Write the summary now:
                """.trimIndent()

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
        private val userPreferenceStore: com.xuan.fitai.data.datastore.UserPreferenceStore
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(userRepository, workoutRepository, gemmaHelper, healthConnectHelper, userPreferenceStore) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
