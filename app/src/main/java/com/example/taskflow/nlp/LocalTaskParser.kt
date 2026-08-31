package com.example.taskflow.nlp

import com.example.taskflow.data.CapturedTask
import com.example.taskflow.data.RecurrenceFrequency
import com.example.taskflow.data.TaskRecurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Private, zero-cost task language parsing. It deliberately favours explicit phrases
 * over guessing, while handling the natural date and time wording people use every day.
 */
class LocalTaskParser(private val today: () -> LocalDate = { LocalDate.now() }) {
    fun parse(input: String): CapturedTask {
        val original = input.trim()
        var remaining = normaliseTimePunctuation(original)
        val recurrence = parseRecurrence(remaining)
        remaining = recurrence.second
        val date = parseDate(remaining)
        remaining = date.second
        val time = parseTime(remaining)
        remaining = tidyTitle(time.second)
        return CapturedTask(
            title = remaining.ifBlank { original },
            dueDate = date.first ?: recurrence.first?.firstOccurrence(today()),
            dueTime = time.first,
            recurrence = recurrence.first,
            sourceText = input
        )
    }

    private fun parseRecurrence(text: String): Pair<TaskRecurrence?, String> {
        fun match(rule: Regex, recurrence: (MatchResult) -> TaskRecurrence): Pair<TaskRecurrence?, String>? {
            val found = rule.find(text) ?: return null
            return recurrence(found) to text.removeRange(found.range)
        }

        match(Regex("\\b(?:every|each)\\s+(?:weekday|workday)s?\\b", RegexOption.IGNORE_CASE)) {
            TaskRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                label = "every weekday"
            )
        }?.let { return it }
        match(Regex("\\b(?:every|each)\\s+weekends?\\b", RegexOption.IGNORE_CASE)) {
            TaskRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                label = "every weekend"
            )
        }?.let { return it }
        match(Regex("\\b(?:every|each)\\s+(?:other|second)\\s+(day|week|month)s?\\b", RegexOption.IGNORE_CASE)) { found ->
            recurrenceFor(found.groupValues[1], 2)
        }?.let { return it }
        match(Regex("\\b(?:every|each)\\s+(\\d+)\\s+(day|week|month)s?\\b", RegexOption.IGNORE_CASE)) { found ->
            recurrenceFor(found.groupValues[2], found.groupValues[1].toInt())
        }?.let { return it }
        match(Regex("\\b(?:every|each)\\s+(day|week|month)s?\\b", RegexOption.IGNORE_CASE)) { found ->
            recurrenceFor(found.groupValues[1], 1)
        }?.let { return it }
        match(Regex("\\bdaily\\b", RegexOption.IGNORE_CASE)) {
            TaskRecurrence(RecurrenceFrequency.DAILY, label = "every day")
        }?.let { return it }
        match(Regex("\\bweekly\\b", RegexOption.IGNORE_CASE)) {
            TaskRecurrence(RecurrenceFrequency.WEEKLY, label = "every week")
        }?.let { return it }
        match(Regex("\\b(?:every|each)\\s+(${weekdayPattern})\\b", RegexOption.IGNORE_CASE)) { found ->
            val day = weekdays[found.groupValues[1].lowercase(Locale.US)] ?: DayOfWeek.MONDAY
            TaskRecurrence(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(day), label = "every ${day.name.lowercase().replaceFirstChar { it.uppercase() }}")
        }?.let { return it }
        return null to text
    }

    private fun recurrenceFor(unit: String, interval: Int): TaskRecurrence {
        val frequency = when (unit.lowercase(Locale.US)) {
            "day" -> RecurrenceFrequency.DAILY
            "week" -> RecurrenceFrequency.WEEKLY
            else -> RecurrenceFrequency.MONTHLY
        }
        val noun = if (interval == 1) unit.lowercase(Locale.US) else "${unit.lowercase(Locale.US)}s"
        val label = if (interval == 1) "every $noun" else "every $interval $noun"
        return TaskRecurrence(frequency, interval, label = label)
    }

    private fun parseDate(text: String): Pair<LocalDate?, String> {
        val base = today()

        Regex("\\b(day after tomorrow|the day after tomorrow)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            return base.plusDays(2) to text.removeRange(match.range)
        }
        Regex("\\b(day before yesterday|the day before yesterday)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            return base.minusDays(2) to text.removeRange(match.range)
        }
        Regex("\\byesterday\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            return base.minusDays(1) to text.removeRange(match.range)
        }
        Regex("\\b(today|tomorrow|tmrw)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            return (if (match.value.lowercase(Locale.US) in setOf("tomorrow", "tmrw")) base.plusDays(1) else base) to text.removeRange(match.range)
        }
        // Keep "tonight" in the remaining text so the time parser can give it 8 PM.
        if (Regex("\\btonight\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return base to text

        Regex("\\bin\\s+(a|an|\\d+)\\s+(day|week|month|year)s?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: 1
            val date = when (match.groupValues[2].lowercase(Locale.US)) {
                "day" -> base.plusDays(amount.toLong())
                "week" -> base.plusWeeks(amount.toLong())
                "month" -> base.plusMonths(amount.toLong())
                else -> base.plusYears(amount.toLong())
            }
            return date to text.removeRange(match.range)
        }
        Regex("\\b(next|this|coming)\\s+weekend\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val saturday = base.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            val date = if (match.groupValues[1].equals("next", true)) saturday.plusWeeks(1) else saturday
            return date to text.removeRange(match.range)
        }
        Regex("\\b(next|this|coming)\\s+week\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val monday = base.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            val date = if (match.groupValues[1].equals("next", true)) monday.plusWeeks(1) else monday
            return date to text.removeRange(match.range)
        }
        Regex("\\b(next|this|coming)?\\s*(${weekdayPattern})\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val requested = weekdays[match.groupValues[2].lowercase(Locale.US)] ?: return@let
            var date = base.with(TemporalAdjusters.nextOrSame(requested))
            if (match.groupValues[1].equals("next", true)) date = date.plusWeeks(1)
            return date to text.removeRange(match.range)
        }

        Regex("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b").find(text)?.let { match ->
            val date = runCatching {
                LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }.getOrNull()
            if (date != null) return date to text.removeRange(match.range)
        }
        Regex("\\b(?:on\\s+)?(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+of)?\\s+(${monthNames})(?:\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            dateFor(base, match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let { date ->
                return date to text.removeRange(match.range)
            }
        }
        Regex("\\b(?:on\\s+)?(${monthNames})\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            dateFor(base, match.groupValues[2], match.groupValues[1], match.groupValues[3])?.let { date ->
                return date to text.removeRange(match.range)
            }
        }
        // TaskFlow uses day/month ordering, matching the phone's UK locale.
        Regex("\\b(?:on\\s+)?(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(text)?.let { match ->
            val rawYear = match.groupValues[3]
            var year = when (rawYear.length) {
                2 -> 2000 + rawYear.toInt()
                4 -> rawYear.toInt()
                else -> base.year
            }
            var date = runCatching { LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[1].toInt()) }.getOrNull()
            if (date != null && rawYear.isBlank() && date.isBefore(base)) {
                year += 1
                date = runCatching { LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[1].toInt()) }.getOrNull()
            }
            if (date != null) return date to text.removeRange(match.range)
        }
        return null to text
    }

    private fun parseTime(text: String): Pair<LocalTime?, String> {
        Regex("\\bhalf\\s+past\\s+(\\d{1,2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            clockTime(match.groupValues[1], "30", match.groupValues[2])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\bquarter\\s+past\\s+(\\d{1,2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            clockTime(match.groupValues[1], "15", match.groupValues[2])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\bquarter\\s+(?:to|till)\\s+(\\d{1,2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            clockTime(match.groupValues[1], "0", match.groupValues[2])?.let { return it.minusMinutes(15) to text.removeRange(match.range) }
        }
        Regex("\\b(?:at|by|around)?\\s*(\\d{1,2})(?::|\\.)(\\d{2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            clockTime(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\b([01]?\\d|2[0-3]):(\\d{2})\\b").find(text)?.let { match ->
            twentyFourHourTime(match.groupValues[1], match.groupValues[2])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\b(?:at|by|around)\\s+(\\d{1,2})(?::|\\.)(\\d{2})\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            twentyFourHourTime(match.groupValues[1], match.groupValues[2])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\b(?:at|by|around)?\\s*(\\d{1,2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            clockTime(match.groupValues[1], "0", match.groupValues[2])?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\b(?:at|by|around)\\s+(\\d{1,2})\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            twentyFourHourTime(match.groupValues[1], "0")?.let { return it to text.removeRange(match.range) }
        }
        Regex("\\b(noon|midday)\\b", RegexOption.IGNORE_CASE).find(text)?.let { return LocalTime.NOON to text.removeRange(it.range) }
        Regex("\\bmidnight\\b", RegexOption.IGNORE_CASE).find(text)?.let { return LocalTime.MIDNIGHT to text.removeRange(it.range) }
        Regex("\\b(end of day|eod)\\b", RegexOption.IGNORE_CASE).find(text)?.let { return LocalTime.of(17, 0) to text.removeRange(it.range) }
        Regex("\\b(morning|afternoon|evening|tonight|night)\\b", RegexOption.IGNORE_CASE).find(text)?.let { match ->
            val time = when (match.value.lowercase(Locale.US)) {
                "morning" -> LocalTime.of(9, 0)
                "afternoon" -> LocalTime.of(14, 0)
                "evening" -> LocalTime.of(18, 0)
                else -> LocalTime.of(20, 0)
            }
            return time to text.removeRange(match.range)
        }
        return null to text
    }

    private fun dateFor(base: LocalDate, day: String, month: String, rawYear: String): LocalDate? {
        val parsedMonth = months[month.lowercase(Locale.US)] ?: return null
        var year = rawYear.toIntOrNull() ?: base.year
        var date = runCatching { LocalDate.of(year, parsedMonth, day.toInt()) }.getOrNull() ?: return null
        if (rawYear.isBlank() && date.isBefore(base)) {
            year += 1
            date = runCatching { LocalDate.of(year, parsedMonth, day.toInt()) }.getOrNull() ?: return null
        }
        return date
    }

    private fun clockTime(hourText: String, minuteText: String, meridiem: String): LocalTime? {
        val hour12 = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        if (hour12 !in 1..12 || minute !in 0..59) return null
        if (meridiem.isBlank()) return LocalTime.of(hour12, minute)
        val hour24 = if (meridiem.equals("am", true)) {
            if (hour12 == 12) 0 else hour12
        } else {
            if (hour12 == 12) 12 else hour12 + 12
        }
        return LocalTime.of(hour24, minute)
    }

    private fun twentyFourHourTime(hourText: String, minuteText: String): LocalTime? = runCatching {
        LocalTime.of(hourText.toInt(), minuteText.toInt())
    }.getOrNull()

    private fun tidyTitle(text: String): String = text
        .replace(Regex("^\\s*(?:please\\s+)?(?:remind me to|reminder to|add a task to|add task to|schedule|todo|to-do|due)\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(' ', ',', '-', ':')
        .replace(Regex("\\s+(?:on|at|by|around|for|in)$", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun normaliseTimePunctuation(text: String): String = text
        .replace(Regex("\\ba\\.m\\.?", RegexOption.IGNORE_CASE), "am")
        .replace(Regex("\\bp\\.m\\.?", RegexOption.IGNORE_CASE), "pm")

    companion object {
        private val months = mapOf(
            "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
            "nov" to 11, "november" to 11, "dec" to 12, "december" to 12
        )
        private val monthNames = months.keys.sortedByDescending { it.length }.joinToString("|")
        private const val weekdayPattern = "mon(?:day)?|tues?(?:day)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?"
        private val weekdays = mapOf(
            "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY
        )
    }
}
