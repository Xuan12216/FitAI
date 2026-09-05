package com.xuan.fitai.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.model.MealReminder
import com.xuan.fitai.data.model.ReminderSettings
import com.xuan.fitai.data.model.ContextTokenLimits
import com.xuan.fitai.data.model.AppearanceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferenceStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        private val KEY_WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
        private val KEY_CUSTOM_HUE = intPreferencesKey("custom_theme_hue")
        val KEY_USER_GOAL = stringPreferencesKey("user_goal")
        val KEY_GENDER = stringPreferencesKey("gender")
        val KEY_AGE = intPreferencesKey("age")
        val KEY_HEIGHT = floatPreferencesKey("height")
        val KEY_WEIGHT = floatPreferencesKey("weight")
        val KEY_ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val KEY_DIET_PREFERENCE = stringPreferencesKey("diet_preference")
        val KEY_WORKOUT_EXPERIENCE = stringPreferencesKey("workout_experience")
        
        val KEY_SELECTED_LLM_PATH = stringPreferencesKey("selected_llm_path")
        val KEY_SELECTED_LLM_MODEL_ID = stringPreferencesKey("selected_llm_model_id")
        val KEY_SELECTED_CLASSIFIER_PATH = stringPreferencesKey("selected_classifier_path")
        val KEY_HF_TOKEN = stringPreferencesKey("hf_token")

        // Local AI model configuration keys
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_TOP_K = intPreferencesKey("top_k")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_USE_GPU = booleanPreferencesKey("use_gpu")
        val KEY_ENABLE_THINKING = booleanPreferencesKey("enable_thinking")
        val KEY_ENABLE_SPECULATIVE = booleanPreferencesKey("enable_speculative")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val KEY_WORKOUT_PLAN_THINKING = stringPreferencesKey("workout_plan_thinking")
        val KEY_WORKOUT_SUMMARY = stringPreferencesKey("workout_summary")

        val KEY_MEAL_REMINDERS_ENABLED = booleanPreferencesKey("meal_reminders_enabled")
        val KEY_MEAL_REMINDERS = stringPreferencesKey("meal_reminders")
        val KEY_WATER_REMINDERS_ENABLED = booleanPreferencesKey("water_reminders_enabled")
        val KEY_WATER_TARGET_ML = intPreferencesKey("water_target_ml")
        val KEY_WATER_INTERVAL_MINUTES = intPreferencesKey("water_interval_minutes")
        val KEY_WATER_START_HOUR = intPreferencesKey("water_start_hour")
        val KEY_WATER_END_HOUR = intPreferencesKey("water_end_hour")
        val KEY_WATER_CONSUMED_ML = intPreferencesKey("water_consumed_ml")
        val KEY_WATER_PROGRESS_DATE = stringPreferencesKey("water_progress_date")
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data.map { preferences ->
        UserProfile(
            goal = preferences[KEY_USER_GOAL] ?: "維持健康",
            gender = preferences[KEY_GENDER] ?: "男",
            age = preferences[KEY_AGE] ?: 25,
            height = preferences[KEY_HEIGHT] ?: 170f,
            weight = preferences[KEY_WEIGHT] ?: 60f,
            activityLevel = preferences[KEY_ACTIVITY_LEVEL] ?: "中度",
            dietPreference = preferences[KEY_DIET_PREFERENCE] ?: "無特殊偏好",
            workoutExperience = preferences[KEY_WORKOUT_EXPERIENCE] ?: "無經驗"
        )
    }

    val appearanceFlow: Flow<AppearanceSettings> = dataStore.data.map { preferences ->
        AppearanceSettings(
            useWallpaperColors = preferences[KEY_WALLPAPER_COLORS] ?: false,
            customHue = preferences[KEY_CUSTOM_HUE]?.coerceIn(0, 359),
        )
    }

    suspend fun saveAppearance(settings: AppearanceSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_WALLPAPER_COLORS] = settings.useWallpaperColors
            if (settings.customHue == null) preferences.remove(KEY_CUSTOM_HUE)
            else preferences[KEY_CUSTOM_HUE] = settings.customHue.coerceIn(0, 359)
        }
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_IS_ONBOARDING_COMPLETED] ?: false
    }

    val selectedLlmPathFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LLM_PATH]
    }

    val selectedLlmModelIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LLM_MODEL_ID]
    }

    val selectedClassifierPathFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_CLASSIFIER_PATH]
    }

    val hfTokenFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_HF_TOKEN]
    }

    // Config flows
    val maxTokensFlow: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[KEY_MAX_TOKENS] ?: ContextTokenLimits.DEFAULT).coerceIn(ContextTokenLimits.range)
    }
    val topKFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_TOP_K] ?: 64
    }
    val topPFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[KEY_TOP_P] ?: 0.95f
    }
    val temperatureFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[KEY_TEMPERATURE] ?: 1.0f
    }
    val useGpuFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_USE_GPU] ?: false
    }
    val enableThinkingFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ENABLE_THINKING] ?: false
    }
    val enableSpeculativeFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_ENABLE_SPECULATIVE] ?: false
    }
    val systemPromptFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_SYSTEM_PROMPT] ?: "你是一個專業的健康與營養顧問。請用繁體中文回答。"
    }

    val workoutPlanThinkingFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_WORKOUT_PLAN_THINKING]
    }

    val workoutSummaryFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_WORKOUT_SUMMARY]
    }

    val reminderSettingsFlow: Flow<ReminderSettings> = dataStore.data.map { preferences ->
        ReminderSettings(
            mealRemindersEnabled = preferences[KEY_MEAL_REMINDERS_ENABLED] ?: false,
            meals = decodeMeals(preferences[KEY_MEAL_REMINDERS]),
            waterRemindersEnabled = preferences[KEY_WATER_REMINDERS_ENABLED] ?: false,
            waterTargetMl = preferences[KEY_WATER_TARGET_ML] ?: 2000,
            waterIntervalMinutes = preferences[KEY_WATER_INTERVAL_MINUTES] ?: 60,
            waterStartHour = preferences[KEY_WATER_START_HOUR] ?: 8,
            waterEndHour = preferences[KEY_WATER_END_HOUR] ?: 22,
            waterConsumedMl = preferences[KEY_WATER_CONSUMED_ML] ?: 0,
            waterProgressDate = preferences[KEY_WATER_PROGRESS_DATE] ?: ""
        )
    }

    suspend fun saveWorkoutPlanThinking(thinking: String) {
        dataStore.edit { preferences ->
            preferences[KEY_WORKOUT_PLAN_THINKING] = thinking
        }
    }

    suspend fun saveWorkoutSummary(summary: String) {
        dataStore.edit { preferences ->
            preferences[KEY_WORKOUT_SUMMARY] = summary
        }
    }

    suspend fun saveReminderSettings(settings: ReminderSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_MEAL_REMINDERS_ENABLED] = settings.mealRemindersEnabled
            preferences[KEY_MEAL_REMINDERS] = settings.meals.joinToString(";") {
                "${it.label}|${it.hour}|${it.minute}|${it.enabled}"
            }
            preferences[KEY_WATER_REMINDERS_ENABLED] = settings.waterRemindersEnabled
            preferences[KEY_WATER_TARGET_ML] = settings.waterTargetMl
            preferences[KEY_WATER_INTERVAL_MINUTES] = settings.waterIntervalMinutes
            preferences[KEY_WATER_START_HOUR] = settings.waterStartHour
            preferences[KEY_WATER_END_HOUR] = settings.waterEndHour
            // Daily intake is updated separately by changeWater.
        }
    }

    // Read and write inside one transaction so rapid taps never overwrite each other.
    suspend fun changeWater(amountMl: Int, expectedDate: LocalDate? = null) {
        dataStore.edit { preferences ->
            val today = LocalDate.now()
            if (expectedDate != null && today != expectedDate) return@edit
            val consumed = if (preferences[KEY_WATER_PROGRESS_DATE] == today.toString()) {
                preferences[KEY_WATER_CONSUMED_ML] ?: 0
            } else 0
            preferences[KEY_WATER_CONSUMED_ML] = (consumed.toLong() + amountMl)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            preferences[KEY_WATER_PROGRESS_DATE] = today.toString()
        }
    }

    private fun decodeMeals(raw: String?): List<MealReminder> {
        if (raw.isNullOrBlank()) return ReminderSettings.defaultMeals()
        return raw.split(";").mapNotNull { item ->
            val parts = item.split("|")
            val hour = parts.getOrNull(1)?.toIntOrNull()
            val minute = parts.getOrNull(2)?.toIntOrNull()
            if (parts.size != 4 || hour == null || minute == null) null
            else MealReminder(parts[0], hour, minute, parts[3].toBoolean())
        }.ifEmpty { ReminderSettings.defaultMeals() }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_GOAL] = profile.goal
            preferences[KEY_GENDER] = profile.gender
            preferences[KEY_AGE] = profile.age
            preferences[KEY_HEIGHT] = profile.height
            preferences[KEY_WEIGHT] = profile.weight
            preferences[KEY_ACTIVITY_LEVEL] = profile.activityLevel
            preferences[KEY_DIET_PREFERENCE] = profile.dietPreference
            preferences[KEY_WORKOUT_EXPERIENCE] = profile.workoutExperience
            preferences[KEY_IS_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun saveSelectedLlmPath(path: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LLM_PATH] = path
        }
    }

    suspend fun saveSelectedLlmModelId(modelId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LLM_MODEL_ID] = modelId
        }
    }

    suspend fun saveSelectedClassifierPath(path: String) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_CLASSIFIER_PATH] = path
        }
    }

    suspend fun saveHfToken(token: String) {
        dataStore.edit { preferences ->
            preferences[KEY_HF_TOKEN] = token
        }
    }

    suspend fun clearHfToken() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_HF_TOKEN)
        }
    }

    suspend fun resetOnboarding() {
        dataStore.edit { preferences ->
            preferences[KEY_IS_ONBOARDING_COMPLETED] = false
        }
    }

    suspend fun saveModelConfig(
        maxTokens: Int,
        topK: Int,
        topP: Float,
        temperature: Float,
        useGpu: Boolean,
        enableThinking: Boolean,
        enableSpeculative: Boolean,
        systemPrompt: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MAX_TOKENS] = maxTokens.coerceIn(ContextTokenLimits.range)
            preferences[KEY_TOP_K] = topK
            preferences[KEY_TOP_P] = topP
            preferences[KEY_TEMPERATURE] = temperature
            preferences[KEY_USE_GPU] = useGpu
            preferences[KEY_ENABLE_THINKING] = enableThinking
            preferences[KEY_ENABLE_SPECULATIVE] = enableSpeculative
            preferences[KEY_SYSTEM_PROMPT] = systemPrompt
        }
    }
}
