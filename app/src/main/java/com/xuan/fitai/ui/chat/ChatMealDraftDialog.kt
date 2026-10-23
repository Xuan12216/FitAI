package com.xuan.fitai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ChatMealDraftDialog(source: String, onDismiss: () -> Unit,
    onSave: suspend (String, Float, Float, Float, Float) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("確認後加入餐點紀錄") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement()
            Text(source, maxLines = 5, style = MaterialTheme.typography.bodySmall)
            Text("聊天建議尚未等於實際進食。請確認餐點與數值後再儲存。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("餐點名稱") })
            OutlinedTextField(calories, { calories = it }, label = { Text("熱量 kcal（必填）") })
            OutlinedTextField(protein, { protein = it }, label = { Text("蛋白質 g") })
            OutlinedTextField(carbs, { carbs = it }, label = { Text("碳水 g") })
            OutlinedTextField(fat, { fat = it }, label = { Text("脂肪 g") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        val valid = name.isNotBlank() && calories.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true &&
            listOf(protein, carbs, fat).all { it.isBlank() || it.toFloatOrNull()?.let { n -> n.isFinite() && n >= 0 } == true }
        TextButton(enabled = valid && !busy, onClick = {
            busy = true
            scope.launch {
                try { onSave(name.trim(), calories.toFloat(), protein.toFloatOrNull() ?: 0f, carbs.toFloatOrNull() ?: 0f, fat.toFloatOrNull() ?: 0f); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "儲存失敗，請再試一次" }
                finally { busy = false }
            }
        }) { Text("確認儲存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
