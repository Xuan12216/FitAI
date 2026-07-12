package com.xuan.fitai

import android.app.Application
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.local.AppDatabase
import com.xuan.fitai.data.repository.*
import com.xuan.fitai.util.HealthConnectHelper
import com.xuan.fitai.data.repository.ReminderRepository
import com.xuan.fitai.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FitAIApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: AppDatabase
    lateinit var userPreferenceStore: UserPreferenceStore
    
    lateinit var userRepository: UserRepository
    lateinit var mealRepository: MealRepository
    lateinit var modelRepository: ModelRepository
    lateinit var workoutRepository: WorkoutRepository
    lateinit var reminderRepository: ReminderRepository
    
    lateinit var gemmaHelper: GemmaLocalHelper
    lateinit var classifierHelper: FoodClassifierHelper
    lateinit var modelManager: ModelManager
    lateinit var healthConnectHelper: HealthConnectHelper

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        userPreferenceStore = UserPreferenceStore(this)
        
        userRepository = UserRepository(userPreferenceStore)
        mealRepository = MealRepository(database.mealDao())
        modelRepository = ModelRepository(database.modelDao())
        workoutRepository = WorkoutRepository(database.workoutDao())
        reminderRepository = ReminderRepository(userPreferenceStore)
        reminderRepository.settings
            .onEach { ReminderScheduler.scheduleAll(this, it) }
            .launchIn(applicationScope)
        
        gemmaHelper = GemmaLocalHelperImpl(this)
        classifierHelper = FoodClassifierHelperImpl(this)
        modelManager = ModelManager(this, modelRepository, userPreferenceStore, gemmaHelper, classifierHelper)
        healthConnectHelper = HealthConnectHelper(this)
    }
}
