package com.xuan.fitai.ui.dashboard

import com.xuan.fitai.data.model.Meal
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailySummaryTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 9, 5)

    @Test fun includesSevenDaysWithEmptyDaysAndIgnoresOlderMeals() {
        val meals = listOf(meal(today, 300f), meal(today, 450f), meal(today.minusDays(6), 100f), meal(today.minusDays(7), 900f))
        val result = summarizeWeek(meals, today, zone)
        assertEquals(7, result.size)
        assertEquals(today.minusDays(6), result.first().date)
        assertEquals(100f, result.first().calories)
        assertEquals(0, result[1].mealCount)
        assertEquals(750f, result.last().calories)
        assertEquals(2, result.last().mealCount)
    }

    @Test fun groupsUsingLocalMidnightInsteadOfUtcDay() {
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val result = summarizeWeek(listOf(meal(today, 300f), meal(today, 100f).copy(timestamp = midnight - 1)), today, zone)
        assertEquals(300f, result.last().calories)
        assertEquals(100f, result[result.lastIndex - 1].calories)
    }

    private fun meal(date: LocalDate, calories: Float) = Meal(
        name = "餐點", calories = calories, protein = 0f, carbs = 0f, fat = 0f,
        timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
    )
}
