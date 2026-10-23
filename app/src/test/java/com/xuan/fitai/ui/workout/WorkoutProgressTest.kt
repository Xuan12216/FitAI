package com.xuan.fitai.ui.workout
import com.xuan.fitai.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class WorkoutProgressTest {
    private val plan = WorkoutPlan(1, "星期一", "深蹲", 2, "10", "腿")
    @Test fun newWeekDoesNotReuseOldCompletionAndHistoryIsUnchanged() {
        val logs = listOf(WorkoutSetEntry(planId = 1, exerciseName = "深蹲", date = "2026-10-18", setNumber = 1, weightKg = 20f, reps = 10))
        assertEquals(1, WorkoutProgress.completedSets(plan, logs, LocalDate.parse("2026-10-18")))
        assertEquals(0, WorkoutProgress.completedSets(plan, logs, LocalDate.parse("2026-10-19")))
        assertEquals(1, logs.size)
    }
    @Test fun renamedPlansAndOutOfRangeSetsCannotCompleteCurrentPlan() {
        val logs = listOf(WorkoutSetEntry(planId = 1, exerciseName = "舊動作", date = "2026-10-20", setNumber = 1, weightKg = 20f, reps = 10),
            WorkoutSetEntry(planId = 1, exerciseName = "深蹲", date = "2026-10-20", setNumber = 5, weightKg = 20f, reps = 10))
        assertEquals(0, WorkoutProgress.completedSets(plan, logs, LocalDate.parse("2026-10-20")))
    }
}
