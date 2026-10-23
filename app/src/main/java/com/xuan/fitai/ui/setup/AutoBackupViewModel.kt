package com.xuan.fitai.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutoBackupViewModel(private val app: FitAIApplication) : ViewModel() {
    val settings = app.autoBackupStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoBackupSettings())
    val message = MutableStateFlow<String?>(null)
    val changing = MutableStateFlow(false)
    private val manager = WorkManager.getInstance(app)
    val activity = combine(manager.getWorkInfosForUniqueWorkFlow(AutoBackupScheduler.PERIODIC), manager.getWorkInfosForUniqueWorkFlow(AutoBackupScheduler.MANUAL)) { scheduled, manual ->
        when {
            (scheduled + manual).any { it.state == WorkInfo.State.RUNNING } -> "running"
            manual.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> "waiting"
            else -> "idle"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "idle")
    private fun change(block: suspend () -> Unit) {
        if (changing.value) return
        changing.value = true; message.value = null
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message.value = error.message ?: "設定未完成，請再試一次" }
            finally { changing.value = false }
        }
    }
    fun connect(uri: Uri, flags: Int) = change {
        require(uri.scheme == "content" && uri.authority == DRIVE_DOCUMENT_AUTHORITY) { "請在檔案選單左側選擇 Google Drive，再建立備份檔案" }
        val grant = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val required = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        require(grant == required) { "Google Drive 沒有提供讀寫權限，請重新選擇" }
        val previousTarget = app.autoBackupStore.settings.first().destination
        app.contentResolver.takePersistableUriPermission(uri, grant)
        var committed = false
        try { AutoBackupEngine.mutex.withLock {
            val old = app.autoBackupStore.settings.first()
            val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "FitAI-auto-backup.json"
            check(AutoBackupEngine.hasAccess(app, uri.toString())) { "無法保留 Google Drive 授權，請重新選擇" }
            AutoBackupScheduler.cancel(app)
            app.autoBackupStore.connect(uri.toString(), name)
            committed = true
            if (old.destination.isNotBlank() && old.destination != uri.toString()) release(old.destination)
            AutoBackupScheduler.now(app, app.autoBackupStore.settings.first())
            message.value = "已連結 Google Drive，等待首次備份；若設定僅 Wi-Fi，會等待非計量網路。"
        } } catch (error: Exception) {
            if (!committed && previousTarget != uri.toString()) release(uri.toString())
            throw error
        }
    }
    fun disconnect() = change {
        AutoBackupEngine.mutex.withLock {
            val old = app.autoBackupStore.settings.first()
            AutoBackupScheduler.cancel(app)
            app.autoBackupStore.disconnect()
            if (old.destination.isNotBlank()) release(old.destination)
            message.value = "已停止自動備份並解除授權，Drive 上的檔案保留。"
        }
    }
    private fun release(destination: String) {
        try { app.contentResolver.releasePersistableUriPermission(Uri.parse(destination), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { }
    }
    fun enabled(value: Boolean) = change {
        AutoBackupEngine.mutex.withLock {
            val snapshot = app.autoBackupStore.settings.first()
            require(!value || AutoBackupEngine.hasAccess(app, snapshot.destination)) { "請先重新選擇 Google Drive 備份檔" }
            app.autoBackupStore.setEnabled(value)
        }
    }
    fun interval(minutes: Long) = change { app.autoBackupStore.setInterval(minutes) }
    fun wifi(value: Boolean) = change {
        AutoBackupEngine.mutex.withLock {
            val pending = manager.getWorkInfosForUniqueWork(AutoBackupScheduler.MANUAL).get().any { !it.state.isFinished }
            if (pending) manager.cancelUniqueWork(AutoBackupScheduler.MANUAL).result.get()
            app.autoBackupStore.setWifiOnly(value)
            if (pending) AutoBackupScheduler.now(app, app.autoBackupStore.settings.first())
        }
    }
    fun now() = change {
        val snapshot = app.autoBackupStore.settings.first()
        check(AutoBackupEngine.hasAccess(app, snapshot.destination)) { "請重新選擇 Google Drive 備份檔" }
        AutoBackupScheduler.now(app, snapshot)
    }
}
