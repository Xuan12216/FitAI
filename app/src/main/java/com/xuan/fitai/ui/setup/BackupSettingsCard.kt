package com.xuan.fitai.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class BackupViewModel(private val app: FitAIApplication) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pending = MutableStateFlow<BackupRecords?>(null)
    private fun run(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "檔案操作失敗，原有資料未更動" }
            finally { busy.value = false }
        }
    }
    fun export(uri: Uri) = run {
        val raw = RecordBackup.export(app.database)
        require(raw.toByteArray().size <= RecordBackup.MAX_BYTES) { "紀錄超過單一備份大小上限" }
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("無法寫入檔案")
        message.value = "備份完成"
    }
    fun preview(uri: Uri) = run {
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= RecordBackup.MAX_BYTES) { "備份檔過大" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("無法讀取檔案")
        require(bytes.size <= RecordBackup.MAX_BYTES) { "備份檔過大" }
        pending.value = RecordBackup.parse(bytes.toString(Charsets.UTF_8))
    }
    fun restore() = run {
        val records = pending.value ?: return@run
        RecordBackup.restore(app.database, records)
        pending.value = null; message.value = "紀錄還原完成"
    }
}

@Composable
internal fun BackupSettingsCard() {
    val app = LocalContext.current.applicationContext as FitAIApplication
    val vm: BackupViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T { @Suppress("UNCHECKED_CAST") return BackupViewModel(app) as T }
    })
    val busy by vm.busy.collectAsState(); val pending by vm.pending.collectAsState(); val message by vm.message.collectAsState()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("備份與還原", style = MaterialTheme.typography.titleMedium)
            Text("備份餐點、常用標記、體重、課表與逐組紀錄。不含聊天、個人設定或模型檔案。", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { export.launch("FitAI-${java.time.LocalDate.now()}.json") }) { Text("匯出備份") }
                OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/json", "text/plain")) }) { Text("選擇還原檔案") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            AutoBackupSettingsSection(onRestore = vm::preview)
        }
    }
    pending?.let { records -> AlertDialog(onDismissRequest = { if (!busy) vm.pending.value = null }, title = { Text("確認取代目前紀錄？") },
        text = { Text("備份含 ${records.meals.size} 筆餐點、${records.weights.size} 筆體重、${records.plans.size} 個課表項目、${records.sets.size} 組訓練。還原將取代目前這些紀錄，建議先匯出現有資料。個人設定、聊天與模型不會更動。") },
        confirmButton = { TextButton(enabled = !busy, onClick = vm::restore) { Text("確認還原") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { vm.pending.value = null }) { Text("取消") } }) }
}
