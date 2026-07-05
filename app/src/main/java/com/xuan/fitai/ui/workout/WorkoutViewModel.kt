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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

class WorkoutViewModel(
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val gemmaHelper: GemmaLocalHelper,
    private val healthConnectHelper: HealthConnectHelper
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val workoutPlans: StateFlow<List<WorkoutPlan>> = workoutRepository.allWorkoutPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationThinking = MutableStateFlow<String?>(null)
    val generationThinking: StateFlow<String?> = _generationThinking.asStateFlow()

    private val _healthData = MutableStateFlow<HealthData?>(null)
    val healthData: StateFlow<HealthData?> = _healthData.asStateFlow()

    private val _hasHealthPermissions = MutableStateFlow(false)
    val hasHealthPermissions: StateFlow<Boolean> = _hasHealthPermissions.asStateFlow()

    private val _healthConnectSdkStatus = MutableStateFlow(healthConnectHelper.getSdkStatus())
    val healthConnectSdkStatus: StateFlow<Int> = _healthConnectSdkStatus.asStateFlow()

    init {
        // Auto-generate default plan if DB is empty
        viewModelScope.launch {
            workoutPlans.collect { list ->
                if (list.isEmpty()) {
                    generateDefaultWorkoutPlan()
                }
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
            generateDefaultWorkoutPlan()
        }
    }

    fun generateAIWorkoutPlan(daysPerWeek: Int, preference: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationThinking.value = null
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
                    Provide your response in JSON format matching this array structure:
                    [
                      {"dayOfWeek": "星期一", "exerciseName": "槓鈴臥推 (Bench Press)", "sets": 4, "reps": "8-12次", "targetMuscleGroup": "胸肌"},
                      {"dayOfWeek": "星期三", "exerciseName": "深蹲 (Barbell Squat)", "sets": 4, "reps": "10次", "targetMuscleGroup": "腿部"}
                    ]
                    Only include the days they should train based on the selected $daysPerWeek days. Use traditional Chinese for the days (e.g. 星期一, 星期三) and targetMuscleGroup.
                    Ensure you output ONLY the raw JSON array string.
                """.trimIndent()

                var currentText = ""
                gemmaHelper.generateReplyFlow(prompt).collect { token ->
                    currentText += token
                    val thinking = GemmaOutputParser.extractThinking(currentText)
                    _generationThinking.value = thinking
                }
                
                val thinking = GemmaOutputParser.extractThinking(currentText)
                _generationThinking.value = thinking

                val jsonArrayStr = GemmaOutputParser.extractJsonArray(currentText)
                if (jsonArrayStr.isNotBlank()) {
                    val jsonArray = JSONArray(jsonArrayStr)
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
                    }
                } else {
                    // Fallback to default if JSON extraction fails
                    generateDefaultWorkoutPlan()
                }
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Workout", "AI generation failed", e)
                generateDefaultWorkoutPlan()
            } finally {
                _isGenerating.value = false
            }
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val workoutRepository: WorkoutRepository,
        private val gemmaHelper: GemmaLocalHelper,
        private val healthConnectHelper: HealthConnectHelper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(userRepository, workoutRepository, gemmaHelper, healthConnectHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
