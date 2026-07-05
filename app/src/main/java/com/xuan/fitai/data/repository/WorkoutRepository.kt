package com.xuan.fitai.data.repository

import com.xuan.fitai.data.local.WorkoutDao
import com.xuan.fitai.data.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    val allWorkoutPlans: Flow<List<WorkoutPlan>> = workoutDao.getAllWorkoutPlans()

    fun getWorkoutPlansForDay(dayOfWeek: String): Flow<List<WorkoutPlan>> {
        return workoutDao.getWorkoutPlansForDay(dayOfWeek)
    }

    suspend fun insertWorkoutPlan(plan: WorkoutPlan) {
        workoutDao.insertWorkoutPlan(plan)
    }

    suspend fun updateWorkoutPlan(plan: WorkoutPlan) {
        workoutDao.updateWorkoutPlan(plan)
    }

    suspend fun clearAllWorkoutPlans() {
        workoutDao.clearAllWorkoutPlans()
    }
}
