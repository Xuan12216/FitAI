package com.xuan.fitai.ui.dashboard

import com.xuan.fitai.data.model.Meal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailySummary(val date: LocalDate, val calories: Float, val mealCount: Int)

fun summarizeWeek(meals: List<Meal>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DailySummary> {
    val byDay = meals.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    return (6L downTo 0L).map { offset ->
        val date = today.minusDays(offset)
        val entries = byDay[date].orEmpty()
        DailySummary(date, entries.sumOf { it.calories.toDouble() }.toFloat(), entries.size)
    }
}
