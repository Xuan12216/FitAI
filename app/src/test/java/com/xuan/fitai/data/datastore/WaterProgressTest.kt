package com.xuan.fitai.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.xuan.fitai.data.model.ReminderSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class WaterProgressTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun concurrentAddsPersistAndCanExceedTarget() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("concurrent.preferences_pb")
        }
        val store = UserPreferenceStore(dataStore)
        coroutineScope { repeat(12) { launch { store.changeWater(250) } } }
        assertEquals(3000, store.reminderSettingsFlow.first().waterConsumedMl)
        store.saveReminderSettings(ReminderSettings(waterTargetMl = 2500))
        assertEquals(3000, store.reminderSettingsFlow.first().waterConsumedMl)
        store.changeWater(-500, LocalDate.now())
        assertEquals(2500, store.reminderSettingsFlow.first().waterConsumedMl)
    }

    @Test fun resetsPreviousDayAndDoesNotUndoAcrossMidnight() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.newFile("rollover.preferences_pb")
        }
        dataStore.edit {
            it[UserPreferenceStore.KEY_WATER_PROGRESS_DATE] = LocalDate.now().minusDays(1).toString()
            it[UserPreferenceStore.KEY_WATER_CONSUMED_ML] = 1500
        }
        val store = UserPreferenceStore(dataStore)
        store.changeWater(250)
        assertEquals(250, store.reminderSettingsFlow.first().waterConsumedMl)
        store.changeWater(-250, LocalDate.now().minusDays(1))
        assertEquals(250, store.reminderSettingsFlow.first().waterConsumedMl)
        store.changeWater(-500, LocalDate.now())
        assertEquals(0, store.reminderSettingsFlow.first().waterConsumedMl)
    }

    @Test fun survivesStoreReopen() = runTest {
        val file = folder.newFile("persisted.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file })
        firstStore.changeWater(500)
        firstJob.cancelAndJoin()
        val reopened = UserPreferenceStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        assertEquals(500, reopened.reminderSettingsFlow.first().waterConsumedMl)
        assertEquals(LocalDate.now().toString(), reopened.reminderSettingsFlow.first().waterProgressDate)
    }
}
