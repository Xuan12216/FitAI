package com.xuan.fitai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.xuan.fitai.backup.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class AutoBackupSchedulingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
    }
    @After fun close() {
        WorkManager.getInstance(context).cancelAllWork().result.get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }
    @Test fun scheduleUpdatesSameJobAndPauseCancelsIt() {
        val settings = AutoBackupSettings(enabled = true, destination = "content://com.google.android.apps.docs.storage/document/test", generation = 1)
        val manager = WorkManager.getInstance(context)
        AutoBackupScheduler.sync(context, settings)
        val first = manager.getWorkInfosForUniqueWork(AutoBackupScheduler.PERIODIC).get(10, TimeUnit.SECONDS).single()
        assertEquals(NetworkType.UNMETERED, first.constraints.requiredNetworkType)
        AutoBackupScheduler.sync(context, settings.copy(intervalMinutes = 360, wifiOnly = false))
        val updated = manager.getWorkInfosForUniqueWork(AutoBackupScheduler.PERIODIC).get(10, TimeUnit.SECONDS).single()
        assertEquals(first.id, updated.id)
        assertEquals(NetworkType.CONNECTED, updated.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(6), updated.periodicityInfo!!.repeatIntervalMillis)
        AutoBackupScheduler.sync(context, settings.copy(enabled = false))
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(first.id).get(10, TimeUnit.SECONDS)!!.state)
    }
    @Test fun repeatedImmediateRequestsKeepOnlyOnePendingJobAndDisconnectCancelsIt() {
        val settings = AutoBackupSettings(destination = "content://com.google.android.apps.docs.storage/document/test", generation = 1)
        val manager = WorkManager.getInstance(context)
        AutoBackupScheduler.now(context, settings)
        AutoBackupScheduler.now(context, settings)
        assertEquals(1, manager.getWorkInfosForUniqueWork(AutoBackupScheduler.MANUAL).get(10, TimeUnit.SECONDS).count { !it.state.isFinished })
        AutoBackupScheduler.sync(context, AutoBackupSettings())
        assertTrue(manager.getWorkInfosForUniqueWork(AutoBackupScheduler.MANUAL).get(10, TimeUnit.SECONDS).all { it.state.isFinished })
    }
}
