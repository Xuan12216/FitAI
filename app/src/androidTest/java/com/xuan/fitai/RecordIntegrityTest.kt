package com.xuan.fitai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.xuan.fitai.backup.*
import com.xuan.fitai.data.local.AppDatabase
import com.xuan.fitai.data.model.*
import com.xuan.fitai.nutrition.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordIntegrityTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase
    @Before fun create() { database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
    @After fun close() { database.close() }

    @Test fun editAndCopyPreserveIdentityAndTimestamp() = runBlocking {
        val old = Meal(id = 9, name = "早餐", calories = 300f, protein = 10f, carbs = 40f, fat = 8f, timestamp = 1000, portionSize = "一碗", mealType = "早餐", isFavorite = true)
        database.mealDao().insertMeal(old)
        assertEquals(1, database.mealDao().updateMeal(old.copy(calories = 350f)))
        database.mealDao().insertMeal(old.copy(id = 0, timestamp = 2000))
        val rows = database.mealDao().snapshotMeals()
        assertEquals(2, rows.size)
        assertEquals(350f, rows.first { it.id == 9 }.calories)
        assertEquals(1000L, rows.first { it.id == 9 }.timestamp)
        assertEquals(300f, rows.first { it.id != 9 }.calories)
        assertEquals(1, database.mealDao().getMealsBetween(0, 1500).first().size)
    }

    @Test fun roundTripPreservesAllRecordTypesAndFavorite() = runBlocking {
        val source = NutritionReferenceJson.encode(NutritionReference(1000, false, listOf(NutritionFood("A", "白飯", "", "熟", 183f, 3.1f, 41f, .3f))))
        database.mealDao().insertMeal(Meal(id = 1, name = "測試餐", calories = 100f, protein = 5f, carbs = 10f, fat = 2f, isFavorite = true, mealType = "午餐", nutritionReferenceJson = source))
        database.workoutDao().insertWorkoutPlan(WorkoutPlan(id = 1, dayOfWeek = "星期一", exerciseName = "深蹲", sets = 3, reps = "10", targetMuscleGroup = "腿"))
        database.progressDao().saveWeight(WeightEntry("2026-10-20", 70f, 68f))
        database.progressDao().saveSet(WorkoutSetEntry(planId = 1, exerciseName = "深蹲", date = "2026-10-20", setNumber = 1, weightKg = 20f, reps = 10))
        val raw = RecordBackup.export(database)
        val parsed = RecordBackup.parse(raw)
        RecordBackup.restore(database, parsed)
        assertEquals(1, database.mealDao().snapshotMeals().size)
        assertTrue(database.mealDao().snapshotMeals().single().isFavorite)
        assertEquals(source, database.mealDao().snapshotMeals().single().nutritionReferenceJson)
        assertEquals(68f, database.progressDao().snapshotWeights().single().targetKg)
        assertEquals(20f, database.progressDao().snapshotSets().single().weightKg)
        assertEquals(parsed, RecordBackup.parse(RecordBackup.export(database)))
        val old = org.json.JSONObject(raw).put("version", 1)
        old.getJSONArray("meals").getJSONObject(0).remove("nutritionReference")
        assertNull(RecordBackup.parse(old.toString()).meals.single().nutritionReferenceJson)
        val bad = org.json.JSONObject(raw)
        bad.getJSONArray("meals").getJSONObject(0).put("nutritionReference", "{}")
        assertTrue(runCatching { RecordBackup.parse(bad.toString()) }.isFailure)
    }

    @Test fun sameSetUpdatesAndWeeksRemainIndependent() = runBlocking {
        val entry = WorkoutSetEntry(planId = 1, exerciseName = "臥推", date = "2026-10-12", setNumber = 1, weightKg = 20f, reps = 8)
        database.progressDao().saveSet(entry)
        database.progressDao().saveSet(entry.copy(weightKg = 25f))
        database.progressDao().saveSet(entry.copy(date = "2026-10-19", weightKg = 30f))
        assertEquals(2, database.progressDao().snapshotSets().size)
        database.progressDao().clearWeek(1, "2026-10-19", "2026-10-25")
        assertEquals(25f, database.progressDao().snapshotSets().single().weightKg)
    }

    @Test fun invalidBackupNeverReplacesExistingMeals() = runBlocking {
        database.mealDao().insertMeal(Meal(name = "原資料", calories = 100f, protein = 1f, carbs = 1f, fat = 1f))
        val raw = RecordBackup.export(database)
        val corrupt = raw.replace("\"calories\": 100", "\"calories\": -100")
        assertTrue(runCatching { RecordBackup.parse(corrupt) }.isFailure)
        assertEquals("原資料", database.mealDao().snapshotMeals().single().name)
        assertTrue(runCatching { RecordBackup.parse("{\"format\":\"other\",\"version\":1}") }.isFailure)
    }

    @Test fun restoreFailureRollsBackDeletion() = runBlocking {
        database.mealDao().insertMeal(Meal(name = "保留原餐點", calories = 100f, protein = 1f, carbs = 1f, fat = 1f))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_weight BEFORE INSERT ON weight_entries BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val records = BackupRecords(emptyList(), listOf(WeightEntry("2026-10-20", 70f)), emptyList(), emptyList())
        assertTrue(runCatching { RecordBackup.restore(database, records) }.isFailure)
        assertEquals("保留原餐點", database.mealDao().snapshotMeals().single().name)
    }

    @Test fun migrationPreservesExistingMealAndCreatesNewStorage() {
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE meals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, calories REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL, timestamp INTEGER NOT NULL, isAiEstimated INTEGER NOT NULL, portionSize TEXT NOT NULL, aiAdvice TEXT)")
                db.execSQL("INSERT INTO meals VALUES (1, '舊版餐點', 123, 1, 2, 3, 456, 0, '1份', NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_3_4.migrate(db)
            AppDatabase.MIGRATION_4_5.migrate(db)
            db.query("SELECT name, calories, mealType, isFavorite FROM meals WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("舊版餐點", it.getString(0)); assertEquals(123, it.getInt(1)); assertEquals("未分類", it.getString(2)); assertEquals(0, it.getInt(3))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('weight_entries','workout_sets','advice_cache')").use { assertEquals(3, it.count) }
            db.query("SELECT nutritionReferenceJson FROM meals WHERE id=1").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
