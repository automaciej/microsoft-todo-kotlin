package pl.blizinski.microsofttodostore.internal.network

import pl.blizinski.tasksync.model.RecurrenceEnd
import pl.blizinski.tasksync.model.RecurrenceFrequency
import pl.blizinski.tasksync.model.RecurrenceRule
import pl.blizinski.tasksync.model.Weekday
import pl.blizinski.tasksync.model.WeekIndex
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure `GraphPatternedRecurrence` <-> [RecurrenceRule] mapping — no network/store dependency, so
 * it's unit-testable in isolation (see `RecurrenceRuleMappingTest.kt`). Both directions are
 * wired into [MicrosoftGraphNetworkSource] (`toRemoteRecord` reads, `toGraphTask` writes) — see
 * [pl.blizinski.microsofttodostore.internal.network.GraphTask.recurrence]'s doc comment for the
 * live-API verification behind both.
 */

/** Returns null for a pattern/range shape this mapping doesn't recognize (an unknown
 *  `pattern.type`/`range.type` string) rather than fabricating a best guess — consistent with
 *  this library's "unsupported fields are left at default, never fabricated" mapping
 *  convention. */
internal fun GraphPatternedRecurrence.toRecurrenceRule(): RecurrenceRule.StructuredRule? {
    val frequency = pattern.type.toRecurrenceFrequencyOrNull() ?: return null
    val end = range.toRecurrenceEndOrNull() ?: return null
    // Graph's `index` field always carries a value ("first" by default) even for pattern types
    // that ignore it — only relativeMonthly/relativeYearly actually use it, so it's only
    // surfaced for those, keeping a plain daily/weekly/absolute* rule's weekIndex null on a
    // round trip rather than resurrecting Graph's own default as a fabricated FIRST.
    val weekIndex = if (frequency == RecurrenceFrequency.RELATIVE_MONTHLY || frequency == RecurrenceFrequency.RELATIVE_YEARLY) {
        pattern.index.toWeekIndexOrNull()
    } else {
        null
    }
    return RecurrenceRule.StructuredRule(
        frequency = frequency,
        interval = pattern.interval,
        daysOfWeek = pattern.daysOfWeek.mapNotNull { it.toWeekdayOrNull() },
        dayOfMonth = pattern.dayOfMonth.takeIf { it != 0 },
        month = pattern.month.takeIf { it != 0 },
        weekIndex = weekIndex,
        end = end,
    )
}

/** [startDate] is Graph's required `range.startDate` (`yyyy-MM-dd`) — this library defaults it
 *  to the task's own due date when the write path is eventually wired. */
internal fun RecurrenceRule.StructuredRule.toGraphPatternedRecurrence(startDate: String): GraphPatternedRecurrence =
    GraphPatternedRecurrence(
        pattern = GraphRecurrencePattern(
            type = frequency.toGraphTypeString(),
            interval = interval,
            month = month ?: 0,
            dayOfMonth = dayOfMonth ?: 0,
            daysOfWeek = daysOfWeek.map { it.toGraphDayString() },
            index = weekIndex?.toGraphIndexString() ?: "first",
        ),
        range = end.toGraphRecurrenceRange(startDate),
    )

private fun String.toRecurrenceFrequencyOrNull(): RecurrenceFrequency? = when (this) {
    "daily" -> RecurrenceFrequency.DAILY
    "weekly" -> RecurrenceFrequency.WEEKLY
    "absoluteMonthly" -> RecurrenceFrequency.ABSOLUTE_MONTHLY
    "relativeMonthly" -> RecurrenceFrequency.RELATIVE_MONTHLY
    "absoluteYearly" -> RecurrenceFrequency.ABSOLUTE_YEARLY
    "relativeYearly" -> RecurrenceFrequency.RELATIVE_YEARLY
    else -> null
}

private fun RecurrenceFrequency.toGraphTypeString(): String = when (this) {
    RecurrenceFrequency.DAILY -> "daily"
    RecurrenceFrequency.WEEKLY -> "weekly"
    RecurrenceFrequency.ABSOLUTE_MONTHLY -> "absoluteMonthly"
    RecurrenceFrequency.RELATIVE_MONTHLY -> "relativeMonthly"
    RecurrenceFrequency.ABSOLUTE_YEARLY -> "absoluteYearly"
    RecurrenceFrequency.RELATIVE_YEARLY -> "relativeYearly"
}

private fun String.toWeekdayOrNull(): Weekday? = when (this) {
    "sunday" -> Weekday.SUNDAY
    "monday" -> Weekday.MONDAY
    "tuesday" -> Weekday.TUESDAY
    "wednesday" -> Weekday.WEDNESDAY
    "thursday" -> Weekday.THURSDAY
    "friday" -> Weekday.FRIDAY
    "saturday" -> Weekday.SATURDAY
    else -> null
}

private fun Weekday.toGraphDayString(): String = when (this) {
    Weekday.SUNDAY -> "sunday"
    Weekday.MONDAY -> "monday"
    Weekday.TUESDAY -> "tuesday"
    Weekday.WEDNESDAY -> "wednesday"
    Weekday.THURSDAY -> "thursday"
    Weekday.FRIDAY -> "friday"
    Weekday.SATURDAY -> "saturday"
}

private fun String.toWeekIndexOrNull(): WeekIndex? = when (this) {
    "first" -> WeekIndex.FIRST
    "second" -> WeekIndex.SECOND
    "third" -> WeekIndex.THIRD
    "fourth" -> WeekIndex.FOURTH
    "last" -> WeekIndex.LAST
    else -> null
}

private fun WeekIndex.toGraphIndexString(): String = when (this) {
    WeekIndex.FIRST -> "first"
    WeekIndex.SECOND -> "second"
    WeekIndex.THIRD -> "third"
    WeekIndex.FOURTH -> "fourth"
    WeekIndex.LAST -> "last"
}

private fun GraphRecurrenceRange.toRecurrenceEndOrNull(): RecurrenceEnd? = when (type) {
    "noEnd" -> RecurrenceEnd.Never
    "numbered" -> RecurrenceEnd.AfterOccurrences(numberOfOccurrences)
    "endDate" -> endDate?.parseGraphDateOnlyToEpochMs()?.let { RecurrenceEnd.OnDate(it) }
    else -> null
}

private fun RecurrenceEnd.toGraphRecurrenceRange(startDate: String): GraphRecurrenceRange = when (this) {
    is RecurrenceEnd.Never -> GraphRecurrenceRange(type = "noEnd", startDate = startDate)
    is RecurrenceEnd.AfterOccurrences -> GraphRecurrenceRange(type = "numbered", startDate = startDate, numberOfOccurrences = count)
    is RecurrenceEnd.OnDate -> GraphRecurrenceRange(type = "endDate", startDate = startDate, endDate = date.toGraphDateOnly())
}

/** Graph's `recurrenceRange.startDate`/`endDate`: date-only, `yyyy-MM-dd`, no time component. */
internal fun Long.toGraphDateOnly(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}

internal fun String.parseGraphDateOnlyToEpochMs(): Long? = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    sdf.isLenient = false
    sdf.parse(this)?.time
} catch (e: Exception) { null }
