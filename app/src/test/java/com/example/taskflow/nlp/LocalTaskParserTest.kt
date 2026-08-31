package com.example.taskflow.nlp

import java.time.LocalDate
import java.time.LocalTime
import com.example.taskflow.data.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTaskParserTest {
    private val parser = LocalTaskParser { LocalDate.of(2026, 8, 31) }

    @Test
    fun understandsReminderWithTomorrowAndMeridiem() {
        val task = parser.parse("Remind me to call Amma tomorrow at 3pm")

        assertEquals("call Amma", task.title)
        assertEquals(LocalDate.of(2026, 9, 1), task.dueDate)
        assertEquals(LocalTime.of(15, 0), task.dueTime)
    }

    @Test
    fun understandsRelativeDateAndNoon() {
        val task = parser.parse("Book dentist in 2 weeks at noon")

        assertEquals("Book dentist", task.title)
        assertEquals(LocalDate.of(2026, 9, 14), task.dueDate)
        assertEquals(LocalTime.NOON, task.dueTime)
    }

    @Test
    fun understandsWeekdaysAndTwentyFourHourTime() {
        val task = parser.parse("Submit report next Tuesday 14:30")

        assertEquals("Submit report", task.title)
        assertEquals(LocalDate.of(2026, 9, 8), task.dueDate)
        assertEquals(LocalTime.of(14, 30), task.dueTime)
    }

    @Test
    fun understandsDaypartsAndUkDates() {
        val morning = parser.parse("Send invoice on 12/09 at 4:30pm")
        val evening = parser.parse("Walk dog tonight")

        assertEquals("Send invoice", morning.title)
        assertEquals(LocalDate.of(2026, 9, 12), morning.dueDate)
        assertEquals(LocalTime.of(16, 30), morning.dueTime)
        assertEquals("Walk dog", evening.title)
        assertEquals(LocalDate.of(2026, 8, 31), evening.dueDate)
        assertEquals(LocalTime.of(20, 0), evening.dueTime)
    }

    @Test
    fun understandsEndOfDayWithoutInventingADate() {
        val task = parser.parse("Review slides eod")

        assertEquals("Review slides", task.title)
        assertEquals(null, task.dueDate)
        assertEquals(LocalTime.of(17, 0), task.dueTime)
    }

    @Test
    fun understandsDailyRecurrenceWithDottedMeridiem() {
        val task = parser.parse("Remind me to take supplements every day at 9 p.m.")

        assertEquals("take supplements", task.title)
        assertEquals(LocalDate.of(2026, 8, 31), task.dueDate)
        assertEquals(LocalTime.of(21, 0), task.dueTime)
        assertEquals(RecurrenceFrequency.DAILY, task.recurrence?.frequency)
        assertEquals("every day", task.recurrence?.label)
        assertEquals("RRULE:FREQ=DAILY", task.recurrence?.calendarRule())
    }

    @Test
    fun understandsRelativeDaysAndThisWeekend() {
        val after = parser.parse("Call dentist day after tomorrow")
        val before = parser.parse("Review notes day before yesterday")
        val weekend = parser.parse("Plan meals this weekend")

        assertEquals(LocalDate.of(2026, 9, 2), after.dueDate)
        assertEquals(LocalDate.of(2026, 8, 29), before.dueDate)
        assertEquals(LocalDate.of(2026, 9, 5), weekend.dueDate)
    }

    @Test
    fun understandsWeekdayRecurrence() {
        val task = parser.parse("Take bins out every Monday at 7pm")

        assertEquals("Take bins out", task.title)
        assertEquals(LocalDate.of(2026, 8, 31), task.dueDate)
        assertEquals(LocalTime.of(19, 0), task.dueTime)
        assertEquals("RRULE:FREQ=WEEKLY;BYDAY=MO", task.recurrence?.calendarRule())
    }
}
