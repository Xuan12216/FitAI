package com.xuan.fitai.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xuan.fitai.FitAIApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object AutoBackupEngine {
    internal val mutex = Mutex()
    fun localSnapshot(context: Context) = File(context.noBackupFilesDir, "latest-record-backup.json")

    fun hasAccess(context: Context, destination: String): Boolean {
        val uri = Uri.parse(destination)
        return uri.scheme == "content" && uri.authority == DRIVE_DOCUMENT_AUTHORITY &&
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    }

    suspend fun deliver(app: FitAIApplication, snapshot: AutoBackupSettings, manual: Boolean = false) = withContext(Dispatchers.IO) {
        if (!hasAccess(app, snapshot.destination)) throw SecurityException("Google Drive 檔案授權已失效，請重新選擇備份檔")
        val raw = RecordBackup.export(app.database)
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.size <= RecordBackup.MAX_BYTES) { "資料超過備份大小上限" }
        RecordBackup.parse(raw) // Never replace the chosen backup with an invalid document.
        currentCoroutineContext().ensureActive()
        val local = AtomicFile(localSnapshot(app))
        val localStream = local.startWrite()
        try { localStream.write(bytes); local.finishWrite(localStream) }
        catch (error: Exception) { local.failWrite(localStream); throw error }
        val current = app.autoBackupStore.settings.first()
        if ((!manual && !current.enabled) || current.destination != snapshot.destination || current.generation != snapshot.generation) return@withContext false
        currentCoroutineContext().ensureActive()
        app.contentResolver.openOutputStream(Uri.parse(snapshot.destination), "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("Google Drive 無法開啟備份檔")
        true // The provider accepted the file. Its cloud synchronization is managed by Drive.
    }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = AutoBackupEngine.mutex.withLock {
        val app = applicationContext as FitAIApplication
        val settings = app.autoBackupStore.settings.first()
        val manual = inputData.getBoolean(AutoBackupScheduler.IS_MANUAL, false)
        if (settings.destination.isBlank() || (!manual && !settings.enabled) ||
            settings.destination != inputData.getString(AutoBackupScheduler.DESTINATION) ||
            settings.generation != inputData.getLong(AutoBackupScheduler.GENERATION, -1)) return@withLock Result.success()
        try {
            app.autoBackupStore.record(settings, "running")
            if (AutoBackupEngine.deliver(app, settings, manual)) app.autoBackupStore.record(settings, "delivered", deliveredAt = System.currentTimeMillis())
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            app.autoBackupStore.record(settings, "needs_access", "Google Drive 授權已失效或檔案已移除，請重新選擇備份檔")
            Result.failure()
        } catch (error: Exception) {
            val retry = runAttemptCount < 3 && error !is IllegalArgumentException
            app.autoBackupStore.record(settings, if (retry) "retrying" else "failed",
                if (retry) "備份尚未交付 Google Drive，將在網路與排程允許時重試。可稍後按立即備份。" else "備份未完成，請確認 Drive 檔案與可用空間後重試。")
            if (retry) Result.retry() else Result.failure()
        }
    }
}
