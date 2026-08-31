package com.example.taskflow.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek

data class CapturedTask(
    val title: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val recurrence: TaskRecurrence? = null,
    val sourceText: String
)

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }

/** A portable, human-readable recurrence that also maps directly to a Calendar RRULE. */
data class TaskRecurrence(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val label: String
) {
    fun calendarRule(): String = buildString {
        append("RRULE:FREQ=").append(frequency.name)
        if (interval > 1) append(";INTERVAL=").append(interval)
        if (daysOfWeek.isNotEmpty()) {
            append(";BYDAY=")
            append(daysOfWeek.sortedBy { it.value }.joinToString(",") { day -> day.name.take(2) })
        }
    }

    fun firstOccurrence(from: LocalDate): LocalDate {
        if (frequency != RecurrenceFrequency.WEEKLY || daysOfWeek.isEmpty()) return from
        return daysOfWeek
            .map { day -> from.plusDays(((day.value - from.dayOfWeek.value + 7) % 7).toLong()) }
            .minOrNull() ?: from
    }
}
