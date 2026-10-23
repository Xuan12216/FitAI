package com.xuan.fitai.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xuan.fitai.data.model.*

class Converters {
    @TypeConverter
    fun toModelType(value: String) = enumValueOf<ModelType>(value)

    @TypeConverter
    fun fromModelType(value: ModelType) = value.name
}

@Database(
    entities = [
        Meal::class,
        ChatMessage::class,
        LocalModelInfo::class,
        WorkoutPlan::class,
        WeightEntry::class, WorkoutSetEntry::class, AdviceCache::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitai_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE meals ADD COLUMN nutritionReferenceJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE meals ADD COLUMN mealType TEXT NOT NULL DEFAULT '未分類'")
                database.execSQL("ALTER TABLE meals ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE TABLE IF NOT EXISTS weight_entries (date TEXT NOT NULL PRIMARY KEY, weightKg REAL NOT NULL, targetKg REAL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS workout_sets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, planId INTEGER NOT NULL, exerciseName TEXT NOT NULL, date TEXT NOT NULL, setNumber INTEGER NOT NULL, weightKg REAL NOT NULL, reps INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_sets_planId_date_setNumber ON workout_sets(planId, date, setNumber)")
                database.execSQL("CREATE TABLE IF NOT EXISTS advice_cache (signature TEXT NOT NULL PRIMARY KEY, rawText TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN audioBytes BLOB")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN imageBytes BLOB")
            }
        }
    }
}
