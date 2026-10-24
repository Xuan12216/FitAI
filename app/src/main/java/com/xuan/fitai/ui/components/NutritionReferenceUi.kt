package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuan.fitai.nutrition.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun foodSourceTag(food: NutritionFood): String = when {
    food.webAiProvider != null -> "${food.category} 估算｜非官方營養標示"
    food.id.startsWith("WEB-NTU-") -> "網路資料｜臺大膳食協調委員會"
    food.id.startsWith("WEB-") -> "網路資料｜FatSecret 公開營養庫"
    food.id.startsWith("TW-") -> "近似配方｜非官方或品牌營養標示"
    else -> "官方資料｜衛福部食藥署 (TFDA)"
}

private fun foodShortSource(food: NutritionFood): String = when {
    food.webAiProvider != null -> "${food.category} 估算"
    food.id.startsWith("WEB-") -> "網路資料"
    food.id.startsWith("TW-") -> "配方估算"
    else -> "食藥署TFDA"
}

@Composable
fun NutritionReferenceToggle(enabled: Boolean, onChange: (Boolean) -> Unit, interactive: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("營養資料輔助", style = MaterialTheme.typography.titleSmall)
            Text("Google AI、Gemini、ChatGPT、Brave AI、Duck.ai 隨機輪替，實際查詢不連續選同一家；自動最多 3 次，失敗再以台灣資料及本地 Gemma 估算。僅查詢餐點名稱與份量，不會上傳照片或個人檔案。",
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

    val webFoods = reference.foods.filter { it.id.startsWith("WEB-") }
    val googleFoods = reference.foods.filter { it.webAiProvider != null }
    val twFoods = reference.foods.filter { it.id.startsWith("TW-") }
    val tfdaFoods = reference.foods.filter { !it.id.startsWith("WEB-") && !it.id.startsWith("TW-") && it.webAiProvider == null }

    val chipLabel = when {
        googleFoods.isNotEmpty() -> "${googleFoods.first().category} ${if (reference.recipeEstimate) "配方估算" else "估算"}"
        webFoods.isNotEmpty() && (twFoods.isNotEmpty() || tfdaFoods.isNotEmpty()) ->
            "資料來源：網路資料(${webFoods.size}) ＋ 本地庫(${twFoods.size + tfdaFoods.size})"
        webFoods.isNotEmpty() ->
            "資料來源：網路營養資料 (${webFoods.size}筆)"
        twFoods.isNotEmpty() && tfdaFoods.isNotEmpty() ->
            "資料來源：配方估算(${twFoods.size}) ＋ 食藥署(${tfdaFoods.size})"
        twFoods.isNotEmpty() ->
            "資料來源：內建配方估算 (${twFoods.size}筆)"
        else ->
            "資料來源：衛福部食藥署 TFDA (${tfdaFoods.size}筆)"
    }

    val inlineBreakdown = remember(reference) {
        reference.foods.joinToString("、") { "${it.name}[${foodShortSource(it)}]" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AssistChip(
            onClick = { show = true },
            label = { Text(chipLabel) },
            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, Modifier.size(18.dp)) }
        )
        Text(
            text = "參考樣品與來源：$inlineBreakdown（點擊上方標籤可查看完整來源明細）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("營養資料來源") },
        text = {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("參考資料摘要", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (reference.recipeEstimate) {
                            Text("配方估算（依一般配方，非完整營養標示）", style = MaterialTheme.typography.labelLarge)
                            Text("食材與份量假設：${reference.servingAssumption}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (googleFoods.isNotEmpty()) Text("${googleFoods.first().category} 生成估算，並非官方或品牌營養標示。數值若為區間，取中值顯示。", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "網路營養資料： ${webFoods.size} 筆",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "內建近似配方（ ${twFoods.size} 筆）、食藥署 TFDA 資料（ ${tfdaFoods.size} 筆）",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (webFoods.isNotEmpty() || tfdaFoods.isNotEmpty() || googleFoods.isNotEmpty()) Text(
                            text = (if (reference.fromCache) "使用快取 · 資料取得時間：" else "聯網取得 · 資料取得時間：") + Instant.ofEpochMilli(reference.fetchedAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                reference.googleAiAnswer?.let { answer ->
                    Text("${googleFoods.firstOrNull()?.category ?: "網路 AI"} 回答節錄（取得時）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(answer, style = MaterialTheme.typography.bodySmall)
                    reference.googleAiCitations.forEach { citation ->
                        TextButton(onClick = { linkError = runCatching { uriHandler.openUri(citation.url) }.isFailure }) {
                            Text(citation.title.ifBlank { "開啟 Google 引用來源" })
                        }
                    }
                }

                reference.foods.forEach { food ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "【${foodSourceTag(food)}】",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${if (reference.selectedFoodId == food.id) "★ AI 採用樣品：" else "候選樣品："}${food.name}（編號 ${food.id}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${if (food.isPer100g) "每 100 克可食部分" else "每${food.servingLabel}（${if (reference.recipeEstimate) "食材重量為 AI 假設" else "克重未提供"}）"}：${food.calories} kcal" +
                                    if (food.hasMacros) "｜蛋白質 ${food.protein}g · 碳水 ${food.carbs}g · 脂肪 ${food.fat}g" else "｜三大營養素未提供，仍需估算",
                                style = MaterialTheme.typography.bodySmall
                            )
                            food.sourceUrl?.let { source ->
                                TextButton(onClick = { linkError = runCatching { uriHandler.openUri(source) }.isFailure }) {
                                    Text(if (food.webAiProvider != null) "開啟 ${food.webAiProvider?.label} 網站" else "開啟這筆原始營養頁")
                                }
                            }
                            if (food.description.isNotBlank()) {
                                Text(
                                    text = food.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (googleFoods.isNotEmpty()) "本次採用 ${googleFoods.first().category} 的營養估算，再依相同單位換算份量；未交由 Gemma 重估。請依實際餐點確認。" else
                        "以上 ${reference.foods.size} 筆候選樣品已提供給本地 Gemma 模型作為推理依據。最終數值仍可依實際份量手動微調。",
                    style = MaterialTheme.typography.bodySmall
                )

                if (twFoods.isNotEmpty()) Text(
                    "內建資料為近似配方範例，並非食藥署或品牌官方標示；實際份量、配料與料理方式可能不同。",
                    style = MaterialTheme.typography.bodySmall
                )
                if (tfdaFoods.isNotEmpty()) TextButton(
                    onClick = { linkError = runCatching { uriHandler.openUri(TFDA_SOURCE_URL) }.isFailure }
                ) { Text("查看食藥署資料集") }
                if (linkError) {
                    Text("無法開啟瀏覽器，請稍後再試。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { show = false }) { Text("關閉") } }
    )
}
