package com.xuan.fitai.backup

import androidx.room.withTransaction
import com.xuan.fitai.data.local.AppDatabase
import com.xuan.fitai.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import com.xuan.fitai.nutrition.NutritionReferenceJson
import java.time.LocalDate

data class BackupRecords(val meals: List<Meal>, val weights: List<WeightEntry>, val plans: List<WorkoutPlan>, val sets: List<WorkoutSetEntry>)

/** Versioned record backup. Validate the entire document before any write. */
object RecordBackup {
    const val MAX_BYTES = 16 * 1024 * 1024
    private fun array(items: List<JSONObject>) = JSONArray(items)
    private fun number(o: JSONObject, key: String): Float = o.getDouble(key).toFloat().also { require(it.isFinite() && it >= 0) { "$key 數值不正確" } }
    private fun text(o: JSONObject, key: String): String = o.getString(key).also { require(it.isNotBlank() && it.length <= 2000) { "$key 內容不正確" } }
    private fun rows(root: JSONObject, key: String): List<JSONObject> {
        val a = root.getJSONArray(key); require(a.length() <= 10000) { "資料筆數過多" }
        return (0 until a.length()).map { a.getJSONObject(it) }
    }
    suspend fun export(database: AppDatabase): String = database.withTransaction {
        val meals = database.mealDao().snapshotMeals()
        val weights = database.progressDao().snapshotWeights()
        val plans = database.workoutDao().snapshotPlans()
        val sets = database.progressDao().snapshotSets()
        require(listOf(meals.size, weights.size, plans.size, sets.size).all { it <= 10000 }) { "資料超過單次備份筆數上限" }
        val estimated = meals.sumOf { (it.name.length + it.portionSize.length + (it.aiAdvice?.length ?: 0) + (it.nutritionReferenceJson?.length ?: 0)).toLong() * 4 + 400 } + sets.size * 300L + weights.size * 100L + plans.size * 500L
        require(estimated <= MAX_BYTES) { "資料超過單次備份大小上限" }
        JSONObject().put("format", "fitai-records").put("version", 2).put("createdAt", System.currentTimeMillis())
            .put("meals", array(meals.map { JSONObject().put("id", it.id).put("name", it.name).put("calories", it.calories).put("protein", it.protein).put("carbs", it.carbs).put("fat", it.fat).put("timestamp", it.timestamp).put("portion", it.portionSize).put("type", it.mealType).put("favorite", it.isFavorite).put("ai", it.isAiEstimated).put("advice", it.aiAdvice ?: JSONObject.NULL).put("nutritionReference", it.nutritionReferenceJson ?: JSONObject.NULL) }))
            .put("weights", array(weights.map { JSONObject().put("date", it.date).put("weight", it.weightKg).put("target", it.targetKg ?: JSONObject.NULL) }))
            .put("plans", array(plans.map { JSONObject().put("id", it.id).put("day", it.dayOfWeek).put("name", it.exerciseName).put("sets", it.sets).put("reps", it.reps).put("muscle", it.targetMuscleGroup).put("timestamp", it.timestamp) }))
            .put("sets", array(sets.map { JSONObject().put("id", it.id).put("planId", it.planId).put("name", it.exerciseName).put("date", it.date).put("number", it.setNumber).put("weight", it.weightKg).put("reps", it.reps) })).toString(2)
    }
    fun parse(raw: String): BackupRecords {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "備份超過 16 MB" }
        val root = JSONObject(raw)
        require(root.getString("format") == "fitai-records" && root.getInt("version") in 1..2) { "不支援的備份格式或版本" }
        val meals = rows(root, "meals").map { o ->
            val id = o.getInt("id"); require(id > 0)
            val time = o.getLong("timestamp"); require(time >= 0)
            Meal(id, text(o, "name"), number(o, "calories"), number(o, "protein"), number(o, "carbs"), number(o, "fat"), time,
                o.optBoolean("ai"), text(o, "portion"), if (o.isNull("advice")) null else o.getString("advice").also { require(it.length <= 20000) }, text(o, "type"), o.optBoolean("favorite"),
                if (!o.has("nutritionReference") || o.isNull("nutritionReference")) null else o.getString("nutritionReference").also { NutritionReferenceJson.decode(it) })
        }
        val weights = rows(root, "weights").map { o ->
            val date = text(o, "date"); LocalDate.parse(date)
            val weight = number(o, "weight"); require(weight > 0 && weight <= 1000)
            val target = if (o.isNull("target")) null else number(o, "target").also { require(it > 0 && it <= 1000) }
            WeightEntry(date, weight, target)
        }
        val plans = rows(root, "plans").map { o ->
            val id = o.getInt("id"); val sets = o.getInt("sets"); require(id > 0 && sets in 1..100)
            val day = text(o, "day"); require(day in listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"))
            require(o.getLong("timestamp") >= 0)
            WorkoutPlan(id, day, text(o, "name"), sets, text(o, "reps"), text(o, "muscle"), false, o.getLong("timestamp"))
        }
        val sets = rows(root, "sets").map { o ->
            val id = o.getInt("id"); val planId = o.getInt("planId"); val setNumber = o.getInt("number"); val reps = o.getInt("reps")
            val date = text(o, "date"); LocalDate.parse(date)
            require(id > 0 && planId > 0 && setNumber in 1..100 && reps in 1..1000)
            val weight = number(o, "weight"); require(weight <= 1000)
            WorkoutSetEntry(id, planId, text(o, "name"), date, setNumber, weight, reps)
        }
        require(meals.map { it.id }.distinct().size == meals.size && weights.map { it.date }.distinct().size == weights.size && plans.map { it.id }.distinct().size == plans.size && sets.map { it.id }.distinct().size == sets.size) { "備份含重複資料" }
        require(sets.map { Triple(it.planId, it.date, it.setNumber) }.distinct().size == sets.size) { "備份含重複組數" }
        return BackupRecords(meals, weights, plans, sets)
    }
    suspend fun restore(database: AppDatabase, records: BackupRecords) = database.withTransaction {
        database.mealDao().clearMeals(); database.progressDao().clearWeights(); database.progressDao().clearSets(); database.workoutDao().clearAllWorkoutPlans(); database.progressDao().clearAdvice()
        records.meals.forEach { database.mealDao().insertMeal(it) }
        records.weights.forEach { database.progressDao().saveWeight(it) }
        records.plans.forEach { database.workoutDao().insertWorkoutPlan(it) }
        records.sets.forEach { database.progressDao().saveSet(it) }
    }
}
