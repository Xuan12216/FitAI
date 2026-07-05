package com.xuan.fitai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_plans")
data class WorkoutPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String, // 星期一 到 星期日
    val exerciseName: String,
    val sets: Int,
    val reps: String,
    val targetMuscleGroup: String,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
