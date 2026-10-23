package com.xuan.fitai.data.local

import androidx.room.*
import com.xuan.fitai.data.model.Meal
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getMealsBetween(startTime: Long, endTime: Long): Flow<List<Meal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal)

    @Delete
    suspend fun deleteMeal(meal: Meal)

    @Query("SELECT * FROM meals ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<Meal>>

    @Query("SELECT * FROM meals ORDER BY timestamp DESC")
    suspend fun snapshotMeals(): List<Meal>

    @Query("DELETE FROM meals")
    suspend fun clearMeals()

    @Update
    suspend fun updateMeal(meal: Meal): Int
}
