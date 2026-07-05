package com.xuan.fitai.data.local

import android.content.Context
import androidx.room.*
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
    version = 1,
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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
