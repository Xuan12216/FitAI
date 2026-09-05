package com.xuan.fitai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.xuan.fitai.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingCompletionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun freshInstallReturnsFalse() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("fresh.preferences_pb") })
        assertFalse(store.isOnboardingCompletedFlow.first())
    }

    @Test fun savedUserProfileReturnsTrue() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("saved.preferences_pb") })
        store.saveUserProfile(UserProfile(goal = "增肌", weight = 70f))
        assertTrue(store.isOnboardingCompletedFlow.first())
    }

    @Test fun existingProfileDataReturnsTrueEvenIfCompletionWasFalse() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("repaired.preferences_pb") })
        store.saveUserProfile(UserProfile(goal = "增肌", weight = 70f))
        
        // Simulate previous bug where resetOnboarding was called
        store.resetOnboarding()
        
        // Flow still recognizes existing profile data
        assertTrue(store.isOnboardingCompletedFlow.first())
        
        // Repair restores explicit flag in DataStore
        store.repairOnboardingIfDataExists()
        assertTrue(store.isOnboardingCompletedFlow.first())
    }
}
