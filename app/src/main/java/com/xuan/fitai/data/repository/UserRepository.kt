package com.xuan.fitai.data.repository

import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userPreferenceStore: UserPreferenceStore) {
    val userProfile: Flow<UserProfile> = userPreferenceStore.userProfileFlow
    val isOnboardingCompleted: Flow<Boolean> = userPreferenceStore.isOnboardingCompletedFlow

    suspend fun saveUserProfile(profile: UserProfile) {
        userPreferenceStore.saveUserProfile(profile)
    }

    suspend fun resetOnboarding() {
        userPreferenceStore.resetOnboarding()
    }
}
