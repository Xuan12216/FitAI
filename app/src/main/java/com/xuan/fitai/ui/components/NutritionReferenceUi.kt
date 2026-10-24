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
            Text("自動輪替 5 大網路 AI 與台灣食藥署資料（僅查名稱與份量）",
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

    AssistChip(
        onClick = { show = true },
        label = { Text(chipLabel) },
        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, Modifier.size(18.dp)) }
    )

    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("營養資料來源", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. 摘要與配方假設卡
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (webFoods.isNotEmpty() || tfdaFoods.isNotEmpty() || googleFoods.isNotEmpty()) {
                            Text(
                                text = (if (reference.fromCache) "快取資料 · " else "即時取得 · ") +
                                    Instant.ofEpochMilli(reference.fetchedAt).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (reference.recipeEstimate) {
                            Text(
                                text = "配方估算（依一般配方，非完整營養標示）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!reference.servingAssumption.isNullOrBlank()) {
                                Text(
                                    text = "食材與份量假設：${reference.servingAssumption}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (googleFoods.isNotEmpty()) {
                            Text(
                                text = "${googleFoods.first().category} 公開網頁估算（非官方營養標示）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        val counts = buildList {
                            if (webFoods.isNotEmpty()) add("網路營養庫 ${webFoods.size} 筆")
                            if (twFoods.isNotEmpty()) add("內建配方 ${twFoods.size} 筆")
                            if (tfdaFoods.isNotEmpty()) add("食藥署 TFDA ${tfdaFoods.size} 筆")
                        }
                        if (counts.isNotEmpty()) {
                            Text(
                                text = counts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. 採用之單位基準數值卡
                reference.foods.forEach { food ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = food.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = foodShortSource(food),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val basisLabel = if (food.isPer100g) "每 100g" else "每${food.servingLabel}"
                            Text(
                                text = "$basisLabel：${food.calories} kcal" +
                                    if (food.hasMacros) "  ｜  P ${food.protein}g · C ${food.carbs}g · F ${food.fat}g" else "（三大營養素另由 AI 估算）",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            // 僅在非網路 AI（如 TFDA、FatSecret、內建配方）時顯示補充描述，避免與上方假設重複
                            if (food.webAiProvider == null && food.description.isNotBlank()) {
                                Text(
                                    text = food.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            food.sourceUrl?.let { source ->
                                OutlinedButton(
                                    onClick = { linkError = runCatching { uriHandler.openUri(source) }.isFailure },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (food.webAiProvider != null) "開啟 ${food.webAiProvider?.label} 網站" else "開啟原始營養頁",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. AI 原始回答節錄與參考連結
                reference.googleAiAnswer?.let { answer ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${googleFoods.firstOrNull()?.category ?: "網路 AI"} 回答節錄（取得時）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (reference.googleAiCitations.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(
                                    text = "參考資料連結",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                reference.googleAiCitations.forEach { citation ->
                                    val host = runCatching { java.net.URI(citation.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
                                    val cleanTitle = citation.title.replace(Regex("\\s+"), " ").trim()
                                        .takeIf { it.isNotBlank() }
                                        ?: if (host.isNotBlank()) "參考來源 ($host)" else "開啟參考來源"
                                    OutlinedButton(
                                        onClick = { linkError = runCatching { uriHandler.openUri(citation.url) }.isFailure },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (host.isNotBlank() && !cleanTitle.contains(host, ignoreCase = true)) {
                                                "🔗 $cleanTitle ($host)"
                                            } else {
                                                "🔗 $cleanTitle"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (tfdaFoods.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { linkError = runCatching { uriHandler.openUri(TFDA_SOURCE_URL) }.isFailure },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("查看食藥署 TFDA 資料集") }
                }

                Text(
                    text = "註：估算數值若為區間取中值顯示，實際熱量可能因店家配方與份量而異。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GoogleAiTrafficStatus()
                if (linkError) {
                    Text("無法開啟瀏覽器，請稍後再試。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { show = false }) { Text("關閉") } }
    )
}
