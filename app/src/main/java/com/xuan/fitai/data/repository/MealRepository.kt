package com.xuan.fitai.data.repository

import com.xuan.fitai.data.local.MealDao
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.util.DateTimeUtil
import kotlinx.coroutines.flow.Flow

class MealRepository(private val mealDao: MealDao) {
    fun getMealsForDay(timestampMillis: Long): Flow<List<Meal>> {
        val start = DateTimeUtil.getStartOfDay(timestampMillis)
        val end = DateTimeUtil.getEndOfDay(timestampMillis)
        return mealDao.getMealsBetween(start, end)
    }

    suspend fun insertMeal(meal: Meal) {
        mealDao.insertMeal(meal)
    }

    suspend fun deleteMeal(meal: Meal) {
        mealDao.deleteMeal(meal)
    }

    suspend fun updateMeal(meal: Meal) {
        mealDao.updateMeal(meal)
    }
}
