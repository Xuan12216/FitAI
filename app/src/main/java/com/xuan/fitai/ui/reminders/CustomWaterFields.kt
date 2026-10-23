package com.xuan.fitai.ui.reminders
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CustomWaterFields(target: Int, serving: Int, onSave: (Int, Int) -> Unit) {
    var targetText by rememberSaveable(target) { mutableStateOf(target.toString()) }
    var servingText by rememberSaveable(serving) { mutableStateOf(serving.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("自訂飲水", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(targetText, { targetText = it }, label = { Text("每日目標 ml（250–10000）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(servingText, { servingText = it }, label = { Text("我的水杯 ml（50–2000）") }, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = targetText.toIntOrNull()?.let { it in 250..10000 } == true && servingText.toIntOrNull()?.let { it in 50..2000 } == true,
            onClick = { onSave(targetText.toInt(), servingText.toInt()) }) { Text("儲存自訂水量") }
    }
}
