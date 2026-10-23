package com.xuan.fitai.ui.workout
import com.xuan.fitai.data.model.WorkoutPlan
import com.xuan.fitai.data.model.WorkoutSetEntry
import java.time.DayOfWeek
import java.time.LocalDate

object WorkoutProgress {
    fun completedSets(plan: WorkoutPlan, logs: List<WorkoutSetEntry>, date: LocalDate): Int {
        val start = date.with(DayOfWeek.MONDAY).toString(); val end = date.with(DayOfWeek.SUNDAY).toString()
        return logs.filter { it.planId == plan.id && it.exerciseName == plan.exerciseName && it.date >= start && it.date <= end && it.setNumber in 1..plan.sets.coerceIn(1, 100) }.map { it.setNumber }.distinct().size
    }
}
