package com.example.taskflow

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.taskflow.data.CapturedTask
import com.example.taskflow.data.RecurrenceFrequency
import com.example.taskflow.data.TaskRecurrence
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

/** The same schedule corrections are available in app and quick-type capture. */
@Composable
fun TaskScheduleControls(task: CapturedTask, onChange: (CapturedTask) -> Unit) {
    val context = LocalContext.current
    var repeatPicker by remember { mutableStateOf(false) }
    Row {
        TextButton(onClick = {
            val date = task.dueDate ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day ->
                onChange(task.copy(dueDate = LocalDate.of(year, month + 1, day)))
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }) { Text("Date") }
        TextButton(onClick = {
            val time = task.dueTime ?: LocalTime.of(9, 0)
            TimePickerDialog(context, { _, hour, minute ->
                onChange(task.copy(dueDate = task.dueDate ?: LocalDate.now(), dueTime = LocalTime.of(hour, minute)))
            }, time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
        }) { Text("Time") }
        TextButton(onClick = { repeatPicker = true }) { Text("Repeat") }
        if (task.dueDate != null) TextButton(onClick = {
            onChange(task.copy(dueDate = null, dueTime = null, recurrence = null))
        }) { Text("Clear") }
    }
    if (repeatPicker) AlertDialog(
        onDismissRequest = { repeatPicker = false },
        title = { Text("Repeat schedule") },
        text = {
            Column {
                val date = task.dueDate ?: LocalDate.now()
                val options = listOf(
                    "Does not repeat" to null,
                    "Every day" to TaskRecurrence(RecurrenceFrequency.DAILY, label = "Every day"),
                    "Weekdays" to TaskRecurrence(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), label = "Every weekday"),
                    "Every week" to TaskRecurrence(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(date.dayOfWeek), label = "Every week"),
                    "Every month" to TaskRecurrence(RecurrenceFrequency.MONTHLY, label = "Every month")
                )
                options.forEach { (label, recurrence) ->
                    TextButton(onClick = {
                        onChange(task.copy(recurrence = recurrence, dueDate = if (recurrence != null) recurrence.firstOccurrence(date) else task.dueDate))
                        repeatPicker = false
                    }) { Text(label) }
                }
                Text("Repeats apply to the Calendar event. Google Tasks keeps a single task.")
            }
        },
        confirmButton = { TextButton(onClick = { repeatPicker = false }) { Text("Cancel") } }
    )
}
