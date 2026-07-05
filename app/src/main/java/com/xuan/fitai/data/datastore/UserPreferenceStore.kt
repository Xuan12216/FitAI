package com.xuan.fitai.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.xuan.fitai.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferenceStore(private val context: Context) {

    companion object {
        val KEY_IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
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
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { preferences ->
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

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_ONBOARDING_COMPLETED] ?: false
    }

    val selectedLlmPathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LLM_PATH]
    }

    val selectedLlmModelIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_LLM_MODEL_ID]
    }

    val selectedClassifierPathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SELECTED_CLASSIFIER_PATH]
    }

    val hfTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_HF_TOKEN]
    }

    // Config flows
    val maxTokensFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MAX_TOKENS] ?: 4000
    }
    val topKFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOP_K] ?: 64
    }
    val topPFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOP_P] ?: 0.95f
    }
    val temperatureFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_TEMPERATURE] ?: 1.0f
    }
    val useGpuFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_GPU] ?: false
    }
    val enableThinkingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLE_THINKING] ?: false
    }
    val enableSpeculativeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLE_SPECULATIVE] ?: false
    }
    val systemPromptFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SYSTEM_PROMPT] ?: "你是一個專業的健康與營養顧問。請用繁體中文回答。"
    }

    val workoutPlanThinkingFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_WORKOUT_PLAN_THINKING]
    }

    val workoutSummaryFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_WORKOUT_SUMMARY]
    }

    suspend fun saveWorkoutPlanThinking(thinking: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WORKOUT_PLAN_THINKING] = thinking
        }
    }

    suspend fun saveWorkoutSummary(summary: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WORKOUT_SUMMARY] = summary
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.dataStore.edit { preferences ->
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
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LLM_PATH] = path
        }
    }

    suspend fun saveSelectedLlmModelId(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LLM_MODEL_ID] = modelId
        }
    }

    suspend fun saveSelectedClassifierPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_CLASSIFIER_PATH] = path
        }
    }

    suspend fun saveHfToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HF_TOKEN] = token
        }
    }

    suspend fun clearHfToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_HF_TOKEN)
        }
    }

    suspend fun resetOnboarding() {
        context.dataStore.edit { preferences ->
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
        context.dataStore.edit { preferences ->
            preferences[KEY_MAX_TOKENS] = maxTokens
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
