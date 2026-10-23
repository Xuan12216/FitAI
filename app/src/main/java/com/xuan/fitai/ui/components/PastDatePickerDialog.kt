package com.xuan.fitai.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.xuan.fitai.ui.adaptive.AdaptiveDialogPlacement
import com.xuan.fitai.util.currentDateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material date-picker values represent dates at UTC midnight, regardless of the device time zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PastDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val dayFlow = remember { currentDateFlow() }
    val today by dayFlow.collectAsState(initial = LocalDate.now())
    val lastSelectable = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val selectableDates = remember(lastSelectable, today.year) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastSelectable
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val initial = selectedDate.coerceIn(LocalDate.of(1900, 1, 1), today)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..today.year,
        initialDisplayMode = if (LocalConfiguration.current.screenHeightDp < 480) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = selectableDates,
    )
    val chosen = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = chosen != null && chosen <= today, onClick = {
                chosen?.takeIf { it <= today }?.let(onDateSelected)
                onDismiss()
            }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        AdaptiveDialogPlacement()
        DatePicker(state = state, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}
