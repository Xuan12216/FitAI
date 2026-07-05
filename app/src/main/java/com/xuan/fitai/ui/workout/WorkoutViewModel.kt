package com.xuan.fitai.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.model.WorkoutPlan
import com.xuan.fitai.data.repository.UserRepository
import com.xuan.fitai.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WorkoutViewModel(
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val workoutPlans: StateFlow<List<WorkoutPlan>> = workoutRepository.allWorkoutPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Auto-generate default plan if DB is empty
        viewModelScope.launch {
            workoutPlans.collect { list ->
                if (list.isEmpty()) {
                    generateDefaultWorkoutPlan()
                }
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

    fun regeneratePlan() {
        viewModelScope.launch {
            generateDefaultWorkoutPlan()
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val workoutRepository: WorkoutRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(userRepository, workoutRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
