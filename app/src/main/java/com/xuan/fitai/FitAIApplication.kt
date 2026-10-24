package com.xuan.fitai

import android.app.Application
import com.xuan.fitai.backup.AutoBackupStore
import com.xuan.fitai.backup.AutoBackupScheduler
import kotlinx.coroutines.flow.distinctUntilChangedBy
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.local.AppDatabase
import com.xuan.fitai.data.repository.*
import com.xuan.fitai.util.HealthConnectHelper
import com.xuan.fitai.data.repository.ReminderRepository
import com.xuan.fitai.notification.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FitAIApplication : Application() {
    private val warmupStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var autoBackupStore: AutoBackupStore
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
    val googleAiTrafficMonitor = com.xuan.fitai.nutrition.GoogleAiTrafficMonitor()
    lateinit var foodAssessment: com.xuan.fitai.nutrition.FoodAssessment

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        userPreferenceStore = UserPreferenceStore(this)
        autoBackupStore = AutoBackupStore(this)
        autoBackupStore.settings
            .distinctUntilChangedBy { listOf(it.enabled, it.destination, it.generation, it.intervalMinutes, it.wifiOnly) }
            .onEach { AutoBackupScheduler.sync(this, it) }
            .launchIn(applicationScope)
        
        userRepository = UserRepository(userPreferenceStore)
        mealRepository = MealRepository(database.mealDao())
        modelRepository = ModelRepository(database.modelDao())
        workoutRepository = WorkoutRepository(database.workoutDao())
        reminderRepository = ReminderRepository(userPreferenceStore)
        reminderRepository.settings
            .onEach { ReminderScheduler.scheduleAll(this, it) }
            .launchIn(applicationScope)
        
        applicationScope.launch {
            userPreferenceStore.repairOnboardingIfDataExists()
        }
        
        gemmaHelper = GemmaLocalHelperImpl(this)
        foodAssessment = com.xuan.fitai.nutrition.FoodAssessment(gemmaHelper,
            com.xuan.fitai.nutrition.GoogleAiNutritionSource.create(this, googleAiTrafficMonitor), com.xuan.fitai.nutrition.TfdaNutritionRepository(this))
        classifierHelper = FoodClassifierHelperImpl(this)
        modelManager = ModelManager(this, modelRepository, userPreferenceStore, gemmaHelper, classifierHelper)
        healthConnectHelper = HealthConnectHelper(this)

    }

    // A background backup can create the process without a UI. Warm only when a screen opens.
    fun warmModelsForUi() {
        if (!warmupStarted.compareAndSet(false, true)) return
        applicationScope.launch {
            try {
                modelManager.initializeDefaultModels()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("FitAI", "Background model initialization failed", error)
            }
        }
    }
}
