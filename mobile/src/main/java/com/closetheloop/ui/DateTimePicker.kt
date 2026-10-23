package com.closetheloop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.closetheloop.domain.DateTimePick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Calendar, then clock. Calls [onPicked] with a future epoch-millis deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerFlow(initial: Long?, now: Long, is24h: Boolean, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = remember { initial?.takeIf { it > now } ?: (now + 3_600_000) }
    var pickedDay by remember { mutableStateOf<Long?>(null) }
    val day = pickedDay

    if (day == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = DateTimePick.pickerDate(start, zone),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = DateTimePick.isSelectableDay(utcTimeMillis, now, zone)
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickedDay = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    } else {
        val startTime = remember { Instant.ofEpochMilli(start).atZone(zone) }
        val timeState = rememberTimePickerState(startTime.hour, startTime.minute, is24h)
        val result = DateTimePick.combine(day, timeState.hour, timeState.minute, zone)
        val valid = result > now
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(Instant.ofEpochMilli(result).atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) },
            text = {
                Column {
                    TimePicker(timeState)
                    if (!valid) {
                        Text(
                            "That time has already passed",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onPicked(result) }, enabled = valid) { Text("Set") } },
            dismissButton = { TextButton(onClick = { pickedDay = null }) { Text("Back") } },
        )
    }
}
