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
        WorkoutPlan::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun workoutDao(): WorkoutDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
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
