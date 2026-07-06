package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.ModelType
import java.io.File

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
    val localFileExists = File(model.localPath).exists()
    val isDownloaded = model.isDownloaded && localFileExists

    val cardLoadState = if (model.type == ModelType.LLM) {
        when {
            isActive && loadState is ModelLoadState.Loaded -> ModelLoadState.Loaded
            isActive && loadState is ModelLoadState.Failed -> loadState
            else -> ModelLoadState.NotFound
        }
    } else {
        loadState
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val loaded = isDownloaded && cardLoadState is ModelLoadState.Loaded
                Icon(
                    imageVector = if (isDownloaded || loaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (isDownloaded) "Downloaded" else "Not downloaded",
                    tint = when {
                        loaded -> Color(0xFF4CAF50)
                        isDownloaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "檔案名稱: ${model.fileName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "儲存路徑: ${model.localPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (downloadState) {
                is ModelDownloadState.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "下載中: ${(downloadState.progress * 100).toInt()}% " +
                                "(${downloadState.downloadedBytes.toMb()}MB / ${downloadState.totalBytes.toMb()}MB)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                is ModelDownloadState.Failed -> {
                    Text(
                        text = "下載失敗: ${downloadState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    Text(
                        text = if (isDownloaded) "本地檔案已下載" else "本地檔案未下載",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDownloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (loadState is ModelLoadState.Loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(text = "載入模型中...", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                when (cardLoadState) {
                    is ModelLoadState.Loaded -> {
                        Text(
                            text = "狀態: 已載入使用中",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    is ModelLoadState.Failed -> {
                        Text(
                            text = "載入失敗: ${cardLoadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(text = "狀態: 尚未載入", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isDownloaded) {
                    Button(
                        onClick = onDownloadClick,
                        enabled = downloadState !is ModelDownloadState.Downloading
                    ) {
                        Text("下載模型")
                    }

                    OutlinedButton(onClick = onImportClick) {
                        Text("匯入檔案")
                    }
                } else {
                    Button(
                        onClick = onLoadClick,
                        enabled = loadState !is ModelLoadState.Loading && cardLoadState !is ModelLoadState.Loaded
                    ) {
                        Text(if (cardLoadState is ModelLoadState.Loaded) "已載入" else "載入模型")
                    }

                    OutlinedButton(onClick = onImportClick) {
                        Text("重新匯入")
                    }
                }
            }
        }
    }
}

private fun Long.toMb(): Long = if (this > 0) this / 1024 / 1024 else 0
