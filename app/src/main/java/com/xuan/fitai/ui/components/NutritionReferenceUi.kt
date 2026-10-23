package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.xuan.fitai.nutrition.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NutritionReferenceToggle(enabled: Boolean, onChange: (Boolean) -> Unit, interactive: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("營養資料輔助", style = MaterialTheme.typography.titleSmall)
            Text("首次聯網下載約 5 MB，之後使用快取。查無資料時使用本地估算；不會上傳照片或個人資料。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(enabled, onChange, enabled = interactive)
    }
}

@Composable
fun NutritionReferenceBadge(raw: String?) {
    val reference = remember(raw) { NutritionReferenceJson.decodeOrNull(raw) } ?: return
    var show by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    AssistChip(onClick = { show = true }, label = { Text(if (reference.fromCache) "資料庫參考 · 快取" else "資料庫參考 · 聯網取得") },
        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, Modifier.size(18.dp)) })
    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("營養資料來源") },
        text = {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("衛生福利部食品藥物管理署｜食品營養成分資料集")
                Text("下載時間：" + Instant.ofEpochMilli(reference.fetchedAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")), style = MaterialTheme.typography.bodySmall)
                reference.foods.forEach { food ->
                    Text("${if (reference.selectedFoodId == food.id) "使用樣品：" else "候選："}${food.name}（${food.id}）", style = MaterialTheme.typography.titleSmall)
                    Text("每 100 克可食部分：${food.calories} kcal；蛋白質 ${food.protein} g、碳水 ${food.carbs} g、脂肪 ${food.fat} g。")
                    if (food.description.isNotBlank()) Text(food.description, style = MaterialTheme.typography.bodySmall)
                }
                Text("以上 ${reference.foods.size} 筆為候選樣品，已提供給本地 AI 選擇參考。最終數值仍為估算，也可能經過手動校正；請確認部位、重量與料理方式。", style = MaterialTheme.typography.bodySmall)
                Text("資料授權：政府資料開放授權條款第 1 版", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { linkError = runCatching { uriHandler.openUri(TFDA_SOURCE_URL) }.isFailure }) { Text("查看官方資料來源") }
                if (linkError) Text("無法開啟瀏覽器，請稍後再試。", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { show = false }) { Text("關閉") } }
    )
}
