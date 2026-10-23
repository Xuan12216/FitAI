package com.xuan.fitai.backup

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {
    const val PERIODIC = "fitai-periodic-drive-backup"
    const val MANUAL = "fitai-manual-drive-backup"
    const val DESTINATION = "destination"
    const val GENERATION = "generation"
    const val IS_MANUAL = "manual"

    private fun constraints(settings: AutoBackupSettings) = Constraints.Builder()
        .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true).build()
    private fun input(settings: AutoBackupSettings, manual: Boolean) = workDataOf(
        DESTINATION to settings.destination, GENERATION to settings.generation, IS_MANUAL to manual)

    fun sync(context: Context, settings: AutoBackupSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.enabled || settings.destination.isBlank()) {
            manager.cancelUniqueWork(PERIODIC)
            if (settings.destination.isBlank()) manager.cancelUniqueWork(MANUAL)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(settings.intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints(settings)).setInputData(input(settings, false))
            .setInitialDelay(settings.intervalMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun now(context: Context, settings: AutoBackupSettings) {
        require(settings.destination.isNotBlank())
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(input(settings, true))
            .setConstraints(constraints(settings)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL)
    }
}
