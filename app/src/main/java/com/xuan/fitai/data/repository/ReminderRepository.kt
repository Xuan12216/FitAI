package com.xuan.fitai.data.repository

import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.ReminderSettings
import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val store: UserPreferenceStore) {
    val settings: Flow<ReminderSettings> = store.reminderSettingsFlow

    suspend fun save(settings: ReminderSettings) = store.saveReminderSettings(settings)
}
