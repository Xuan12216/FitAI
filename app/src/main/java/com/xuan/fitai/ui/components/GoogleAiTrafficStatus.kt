package com.xuan.fitai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xuan.fitai.FitAIApplication
import com.xuan.fitai.nutrition.GoogleAiTrafficMonitor.Outcome
import kotlinx.coroutines.delay

@Composable
fun GoogleAiTrafficStatus() {
    val monitor = (LocalContext.current.applicationContext as? FitAIApplication)?.googleAiTrafficMonitor ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val snapshot by produceState(monitor.snapshot(), monitor, expanded) {
        do {
            value = monitor.snapshot()
            delay(1_000) // Local counters only; does not issue a web request.
        } while (true)
    }
    Column(Modifier.fillMaxWidth()) {
        if (snapshot.dense) Text("近期查詢較密集，將減少自動重試。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起查詢監測" else "查詢監測") }
        if (expanded) {
            Text("本次開啟 App：AI 提問 ${snapshot.requests} 次 · 快取 ${snapshot.cacheHits} 次 · 重複請求已略過 ${snapshot.suppressed} 次",
                style = MaterialTheme.typography.bodySmall)
            val outcome = when (snapshot.lastOutcome) {
                Outcome.SUCCESS -> "已取得估算"
                Outcome.INCOMPLETE -> "回答資料不完整"
                Outcome.TIMEOUT -> "等待逾時"
                Outcome.NETWORK_ERROR -> "連線或網頁錯誤"
                Outcome.BLOCKED -> "要求驗證或限制存取，已停止本次查詢"
                Outcome.CANCELLED -> "已取消"
                null -> "尚無紀錄"
            }
            Text(if (snapshot.inFlight) "${snapshot.lastProvider ?: "網路 AI"} 查詢進行中…" else
                "上次 ${snapshot.lastProvider.orEmpty()}：$outcome · ${snapshot.lastDurationMs / 1000.0} 秒${snapshot.lastHttpStatus?.let { " · HTTP $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall)
            Text("只記錄本機次數及狀態，不儲存查詢內容。密集提醒為 App 自訂，不能保證不受網站限流。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
