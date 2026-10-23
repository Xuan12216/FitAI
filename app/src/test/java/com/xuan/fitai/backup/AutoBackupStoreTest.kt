package com.xuan.fitai.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoBackupStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun reconnectRejectsOldDeliveryAndDisconnectStopsSchedule() = runTest {
        val store = AutoBackupStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("backup.preferences_pb") })
        assertFalse(store.settings.first().enabled)
        assertTrue(runCatching { store.setEnabled(true) }.isFailure)
        store.connect("content://drive/first", "first.json")
        val old = store.settings.first()
        store.record(old, "delivered", deliveredAt = 1234)
        assertEquals(1234L, store.settings.first().lastDeliveredAt)
        store.connect("content://drive/second", "second.json")
        store.record(old, "delivered", deliveredAt = 9999)
        val second = store.settings.first()
        assertEquals(0L, second.lastDeliveredAt)
        assertTrue(second.generation > old.generation)
        store.disconnect()
        store.record(second, "needs_access", "old error")
        val disconnected = store.settings.first()
        assertFalse(disconnected.enabled)
        assertEquals("", disconnected.destination)
        assertEquals("", disconnected.error)
    }
    @Test fun invalidIntervalsNeverReplaceSavedScheduleAndLostAccessDisablesAuto() = runTest {
        val store = AutoBackupStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("interval.preferences_pb") })
        store.connect("content://drive/backup", "backup.json")
        store.setInterval(360)
        store.setWifiOnly(false)
        for (invalid in listOf(-1L, 0L, 14L, 43201L, Long.MAX_VALUE)) assertTrue(runCatching { store.setInterval(invalid) }.isFailure)
        val saved = store.settings.first()
        assertEquals(360L, saved.intervalMinutes)
        assertFalse(saved.wifiOnly)
        store.record(saved, "retrying", "network")
        assertTrue(store.settings.first().enabled)
        assertEquals(0L, store.settings.first().lastDeliveredAt)
        store.record(saved, "needs_access", "permission")
        assertFalse(store.settings.first().enabled)
        assertEquals("permission", store.settings.first().error)
    }
}
