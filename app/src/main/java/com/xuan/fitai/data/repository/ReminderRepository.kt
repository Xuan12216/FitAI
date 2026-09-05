package com.xuan.fitai.data.repository

import com.xuan.fitai.data.datastore.UserPreferenceStore
import com.xuan.fitai.data.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ReminderRepository(private val store: UserPreferenceStore) {
    val settings: Flow<ReminderSettings> = store.reminderSettingsFlow

    suspend fun save(settings: ReminderSettings) = store.saveReminderSettings(settings)

    suspend fun changeWater(amountMl: Int, expectedDate: LocalDate? = null) =
        store.changeWater(amountMl, expectedDate)
}
