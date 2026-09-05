package com.xuan.fitai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContextTokensTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultsTo1024AndPreservesExistingPreference() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("defaults.preferences_pb") }
        val store = UserPreferenceStore(data)
        assertEquals(1024, store.maxTokensFlow.first())
        data.edit { it[UserPreferenceStore.KEY_MAX_TOKENS] = 4000 }
        assertEquals(4000, store.maxTokensFlow.first())
    }

    @Test fun savesLowTokenValuesAndBoundsInvalidValues() = runTest {
        val store = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("low-tokens.preferences_pb") })
        for ((requested, expected) in listOf(256 to 256, 512 to 512, 1024 to 1024, 1 to 256, 40000 to 32000)) {
            store.saveModelConfig(requested, 64, 0.95f, 1f, false, false, false, "Test prompt")
            assertEquals(expected, store.maxTokensFlow.first())
        }
    }
}
