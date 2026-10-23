package com.xuan.fitai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "weight_entries")
data class WeightEntry(@PrimaryKey val date: String, val weightKg: Float, val targetKg: Float? = null)

@Entity(tableName = "workout_sets", indices = [Index(value = ["planId", "date", "setNumber"], unique = true)])
data class WorkoutSetEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val planId: Int,
    val exerciseName: String,
    val date: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int
)

@Entity(tableName = "advice_cache")
data class AdviceCache(@PrimaryKey val signature: String, val rawText: String, val updatedAt: Long)
