package com.xuan.fitai

import android.app.Application
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.local.AppDatabase
import com.xuan.fitai.data.repository.*

class FitAIApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var userPreferenceStore: UserPreferenceStore
    
    lateinit var userRepository: UserRepository
    lateinit var mealRepository: MealRepository
    lateinit var modelRepository: ModelRepository
    lateinit var workoutRepository: WorkoutRepository
    
    lateinit var gemmaHelper: GemmaLocalHelper
    lateinit var classifierHelper: FoodClassifierHelper
    lateinit var modelManager: ModelManager

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        userPreferenceStore = UserPreferenceStore(this)
        
        userRepository = UserRepository(userPreferenceStore)
        mealRepository = MealRepository(database.mealDao())
        modelRepository = ModelRepository(database.modelDao())
        workoutRepository = WorkoutRepository(database.workoutDao())
        
        gemmaHelper = GemmaLocalHelperImpl(this)
        classifierHelper = FoodClassifierHelperImpl(this)
        modelManager = ModelManager(this, modelRepository, userPreferenceStore, gemmaHelper, classifierHelper)
    }
}
