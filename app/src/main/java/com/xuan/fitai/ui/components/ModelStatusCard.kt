package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.data.model.LocalModelInfo
import com.xuan.fitai.data.model.ModelDownloadState
import com.xuan.fitai.data.model.ModelLoadState
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
    val fileExists = File(model.localPath).exists()

    // Determine the actual load state visual for this specific card
    val cardLoadState = if (model.type == com.xuan.fitai.data.model.ModelType.LLM) {
        if (isActive && loadState is ModelLoadState.Loaded) {
            ModelLoadState.Loaded
        } else if (loadState is ModelLoadState.Failed && isActive) {
            loadState
        } else {
            ModelLoadState.NotFound
        }
    } else {
        loadState
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                
                // Status Icon
                if (fileExists && cardLoadState is ModelLoadState.Loaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Loaded",
                        tint = Color(0xFF4CAF50)
                    )
                } else if (fileExists) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded but not loaded",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not found",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
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

            // Display Download State
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
                                    "(${(downloadState.downloadedBytes / 1024 / 1024)}MB / ${(downloadState.totalBytes / 1024 / 1024)}MB)",
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
                        text = if (fileExists) "本地檔案已存在" else "本地檔案未下載",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (fileExists) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Display Load State
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
                        Text(text = "狀態: 已成功載入本地記憶體", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                    }
                    is ModelLoadState.Failed -> {
                        Text(text = "載入失敗: ${(cardLoadState as ModelLoadState.Failed).message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        Text(text = "狀態: 尚未載入", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!fileExists) {
                    Button(
                        onClick = onDownloadClick,
                        enabled = downloadState !is ModelDownloadState.Downloading
                    ) {
                        Text("下載模型")
                    }
                    
                    OutlinedButton(
                        onClick = onImportClick
                    ) {
                        Text("匯入檔案")
                    }
                } else {
                    Button(
                        onClick = onLoadClick,
                        enabled = loadState !is ModelLoadState.Loading && cardLoadState !is ModelLoadState.Loaded
                    ) {
                        Text(if (cardLoadState is ModelLoadState.Loaded) "已載入" else "載入模型")
                    }
                    
                    OutlinedButton(
                        onClick = onImportClick
                    ) {
                        Text("重新匯入")
                    }
                }
            }
        }
    }
}
