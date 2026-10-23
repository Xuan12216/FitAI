package com.xuan.fitai.data.local

import androidx.room.*
import com.xuan.fitai.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM weight_entries ORDER BY date ASC") fun weights(): Flow<List<WeightEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveWeight(entry: WeightEntry)
    @Query("DELETE FROM weight_entries WHERE date = :date") suspend fun deleteWeight(date: String)
    @Query("SELECT * FROM workout_sets ORDER BY date DESC, setNumber ASC") fun workoutSets(): Flow<List<WorkoutSetEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSet(entry: WorkoutSetEntry)
    @Query("DELETE FROM workout_sets WHERE planId = :planId AND date = :date AND setNumber = :number") suspend fun deleteSet(planId: Int, date: String, number: Int)
    @Query("DELETE FROM workout_sets WHERE planId = :planId AND date >= :start AND date <= :end") suspend fun clearWeek(planId: Int, start: String, end: String)
    @Query("SELECT * FROM advice_cache WHERE signature = :signature") suspend fun advice(signature: String): AdviceCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAdvice(entry: AdviceCache)
    @Query("SELECT * FROM weight_entries ORDER BY date ASC") suspend fun snapshotWeights(): List<WeightEntry>
    @Query("SELECT * FROM workout_sets ORDER BY date DESC, setNumber ASC") suspend fun snapshotSets(): List<WorkoutSetEntry>
    @Transaction
    suspend fun completeWeek(plan: WorkoutPlan, date: String, start: String, end: String) {
        val existing = snapshotSets().filter { it.planId == plan.id && it.exerciseName == plan.exerciseName && it.date >= start && it.date <= end }.map { it.setNumber }.toSet()
        for (number in 1..plan.sets.coerceIn(1, 100)) {
            if (number !in existing) saveSet(WorkoutSetEntry(planId = plan.id, exerciseName = plan.exerciseName, date = date,
                setNumber = number, weightKg = 0f, reps = Regex("\\d+").find(plan.reps)?.value?.toIntOrNull()?.coerceIn(1, 1000) ?: 1))
        }
    }
    @Query("DELETE FROM advice_cache") suspend fun clearAdvice()
    @Query("DELETE FROM weight_entries") suspend fun clearWeights()
    @Query("DELETE FROM workout_sets") suspend fun clearSets()
}
