package com.xuan.fitai.data.local

import androidx.room.*
import com.xuan.fitai.data.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_plans ORDER BY id ASC")
    fun getAllWorkoutPlans(): Flow<List<WorkoutPlan>>

    @Query("SELECT * FROM workout_plans WHERE dayOfWeek = :dayOfWeek ORDER BY id ASC")
    fun getWorkoutPlansForDay(dayOfWeek: String): Flow<List<WorkoutPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlan(plan: WorkoutPlan)

    @Update
    suspend fun updateWorkoutPlan(plan: WorkoutPlan)

    @Query("DELETE FROM workout_plans")
    suspend fun clearAllWorkoutPlans()
}
