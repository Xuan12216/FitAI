package com.xuan.fitai.ui.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.backup.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AutoBackupSettingsSection(onRestore: (Uri) -> Unit) {
    val app = LocalContext.current.applicationContext as FitAIApplication
    val vm: AutoBackupViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return AutoBackupViewModel(app) as T
        }
    })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val changing by vm.changing.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var disconnect by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let { intent -> intent.data?.let { vm.connect(it, intent.flags) } }
    }
    val connected = settings.destination.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text("Google Drive 自動備份", Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收合自動備份設定" else "展開自動備份設定")
        }
        Text(if (!connected) "尚未連結，點選上方設定" else "${if (settings.enabled) "已開啟" else "已暫停"} · ${backupIntervalLabel(settings.intervalMinutes)} · ${settings.fileName}",
            style = MaterialTheme.typography.bodySmall)
        if (settings.error.isNotBlank()) Text(settings.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            if (changing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("首次請在檔案選單選擇 Google Drive，建立專用備份檔。選好後會先備份一次，再定期更新同一個檔案。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !changing, onClick = {
                picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "FitAI-auto-backup.json")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                })
            }) { Text(if (connected) "更換 Google Drive 備份檔" else "連結 Google Drive 備份檔") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自動備份", Modifier.weight(1f))
                Switch(checked = settings.enabled, enabled = connected && !changing, onCheckedChange = vm::enabled)
            }
            Text("備份間隔：${backupIntervalLabel(settings.intervalMinutes)}", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60L to "每小時", 360L to "每 6 小時", 1440L to "每天", 10080L to "每週").forEach { (minutes, label) ->
                    FilterChip(selected = settings.intervalMinutes == minutes && !custom, enabled = !changing,
                        onClick = { custom = false; vm.interval(minutes) }, label = { Text(label) })
                }
                FilterChip(selected = custom, onClick = { custom = !custom }, label = { Text("自訂") })
            }
            if (custom) {
                val initialUnit = when { settings.intervalMinutes % 1440 == 0L -> "天"; settings.intervalMinutes % 60 == 0L -> "小時"; else -> "分鐘" }
                var unit by rememberSaveable(settings.intervalMinutes) { mutableStateOf(initialUnit) }
                var amount by rememberSaveable(settings.intervalMinutes) { mutableStateOf((settings.intervalMinutes / when (initialUnit) { "天" -> 1440; "小時" -> 60; else -> 1 }).toString()) }
                val multiplier = when (unit) { "天" -> 1440L; "小時" -> 60L; else -> 1L }
                val minutes = amount.toLongOrNull()?.takeIf { it in 1..MAX_BACKUP_MINUTES }?.times(multiplier)
                val valid = minutes != null && minutes in MIN_BACKUP_MINUTES..MAX_BACKUP_MINUTES
                OutlinedTextField(amount, { amount = it }, label = { Text("每隔多久") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("分鐘", "小時", "天").forEach { label -> FilterChip(unit == label, { unit = label }, label = { Text(label) }) }
                }
                Text("可設定 15 分鐘至 30 天。", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = valid && !changing, onClick = { vm.interval(minutes!!); custom = false }) { Text("套用備份間隔") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("僅 Wi-Fi／非計量網路")
                    Text("關閉後允許使用行動數據。", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.wifiOnly, enabled = !changing, onCheckedChange = vm::wifi)
            }
            Text("排程會在網路可用時執行，省電模式可能延後。Drive 接收檔案後，實際雲端同步由 Google Drive App 管理。", style = MaterialTheme.typography.bodySmall)
            if (activity == "running") LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(when (activity) { "running" -> "正在產生並交付備份…"; "waiting" -> "等待網路或系統排程…"; else -> if (settings.outcome == "retrying") "上次未完成，等待背景重試" else "沒有正在執行的備份" }, style = MaterialTheme.typography.bodySmall)
            if (settings.lastDeliveredAt > 0) Text("最近交付 Drive：${Instant.ofEpochMilli(settings.lastDeliveredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))}", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = connected && !changing && activity == "idle", onClick = vm::now) { Text("立即備份") }
                TextButton(enabled = connected && !changing && activity == "idle", onClick = { onRestore(Uri.parse(settings.destination)) }) { Text("從 Drive 還原") }
                if (connected) TextButton(enabled = !changing, onClick = { disconnect = true }) { Text("解除連結") }
            }
            if (AutoBackupEngine.localSnapshot(app).exists()) TextButton(onClick = { onRestore(Uri.fromFile(AutoBackupEngine.localSnapshot(app))) }) { Text("還原最近的本機快照") }
            Text("備份內容與上方手動備份相同；產生時另保留一份本機快照。", style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("解除 Google Drive 備份？") },
        text = { Text("會停止排程並移除這個備份檔的授權，Drive 上已建立的檔案保留。") },
        confirmButton = { TextButton(onClick = { disconnect = false; vm.disconnect() }) { Text("解除連結") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("保留連結") } })
}
