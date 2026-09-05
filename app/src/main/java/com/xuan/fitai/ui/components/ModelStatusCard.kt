package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.ModelType
import java.io.File
import java.util.Locale

@Composable
fun ModelStatusCard(
    model: LocalModelInfo,
    downloadState: ModelDownloadState,
    loadState: ModelLoadState,
    onDownloadClick: () -> Unit,
    onLoadClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val downloaded = model.isDownloaded && File(model.localPath).exists()
    val loaded = downloaded && isActive && loadState is ModelLoadState.Loaded
    val downloading = downloadState is ModelDownloadState.Downloading
    val loading = loadState is ModelLoadState.Loading
    var showDetails by rememberSaveable(model.id) { mutableStateOf(false) }
    SettingsCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (model.type == ModelType.LLM) Icons.Default.SmartToy else Icons.Default.PhotoCamera,
                null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${if (model.type == ModelType.LLM) "對話助理" else "食物辨識"} · ${model.fileSizeBytes.fileSize()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (loaded) Icons.Default.CheckCircle else Icons.Default.Circle, null,
                Modifier.size(if (loaded) 18.dp else 8.dp), tint = if (loaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when { loaded -> "使用中"; loading -> "模型載入中…"; downloading -> "下載中…"; downloaded -> "已下載，尚未載入"; else -> "尚未下載" },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (downloadState) {
            is ModelDownloadState.Downloading -> {
                LinearProgressIndicator(progress = { downloadState.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(downloadState.progress.coerceIn(0f, 1f) * 100).toInt()}% · ${downloadState.downloadedBytes.fileSize()} / ${downloadState.totalBytes.fileSize()}",
                    style = MaterialTheme.typography.bodySmall)
            }
            is ModelDownloadState.Failed -> Text("下載失敗：${downloadState.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
        if (isActive && loadState is ModelLoadState.Failed) {
            Text("載入失敗：${loadState.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = if (downloaded) onLoadClick else onDownloadClick,
                enabled = !downloading && !loading && !loaded, modifier = Modifier.weight(1f)) {
                Text(when { loaded -> "使用中"; downloaded -> "載入模型"; else -> "下載模型" })
            }
            OutlinedButton(onClick = onImportClick, enabled = !downloading && !loading, modifier = Modifier.weight(1f)) {
                Text(if (downloaded) "重新匯入" else "匯入檔案")
            }
        }
        TextButton(onClick = { showDetails = !showDetails }, contentPadding = PaddingValues(0.dp)) {
            Text(if (showDetails) "收合檔案資訊" else "查看檔案資訊")
            Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        if (showDetails) {
            HorizontalDivider()
            Text("檔案：${model.fileName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("路徑：${model.localPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Long.fileSize(): String = when {
    this <= 0 -> "大小未知"
    this >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", this.toDouble() / (1024L * 1024 * 1024))
    else -> "${this / (1024 * 1024)} MB"
}
