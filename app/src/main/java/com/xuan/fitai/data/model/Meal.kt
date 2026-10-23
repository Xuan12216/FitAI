package com.xuan.fitai.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val isAiEstimated: Boolean = false,
    val portionSize: String = "1份",
    val aiAdvice: String? = null,
    @ColumnInfo(defaultValue = "'未分類'") val mealType: String = "未分類",
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val nutritionReferenceJson: String? = null
)
