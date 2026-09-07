package pl.blizinski.microsofttodostore.models

import kotlinx.serialization.Serializable

/**
 * A task's recurrence rule, restricted to the common patterns this library's consumers author
 * (frequency, interval, weekday/day-of-month/Nth-weekday constraints, end condition) — a
 * structural mirror of Microsoft Graph's own `patternedRecurrence` (`pattern` + `range`), not a
 * raw pass-through of every field Graph exposes. [frequency]'s six values mirror Graph's own
 * `pattern.type` enum directly, so the wire mapping in `internal/network` is a rename, not a
 * reshaping.
 */
@Serializable
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    /** "every N ___", >= 1. */
    val interval: Int,
    /** Used by [RecurrenceFrequency.WEEKLY], and by the relative-monthly/yearly frequencies
     *  together with [weekIndex] (e.g. "the second Tuesday"). Empty = unconstrained. */
    val daysOfWeek: List<Weekday> = emptyList(),
    /** Used by [RecurrenceFrequency.ABSOLUTE_MONTHLY]/[RecurrenceFrequency.ABSOLUTE_YEARLY]. */
    val dayOfMonth: Int? = null,
    /** Used by [RecurrenceFrequency.ABSOLUTE_YEARLY]/[RecurrenceFrequency.RELATIVE_YEARLY]. */
    val month: Int? = null,
    /** Which occurrence of [daysOfWeek] within the period — used by the relative-monthly/yearly
     *  frequencies only. */
    val weekIndex: WeekIndex? = null,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
)

/** Mirrors Microsoft Graph's `recurrencePattern.type` values exactly. */
@Serializable
enum class RecurrenceFrequency { DAILY, WEEKLY, ABSOLUTE_MONTHLY, RELATIVE_MONTHLY, ABSOLUTE_YEARLY, RELATIVE_YEARLY }

/** Mirrors Microsoft Graph's `recurrencePattern.index` values. */
@Serializable
enum class WeekIndex { FIRST, SECOND, THIRD, FOURTH, LAST }

@Serializable
enum class Weekday { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY }

/** Mirrors Microsoft Graph's `recurrenceRange.type` values. */
@Serializable
sealed interface RecurrenceEnd {
    @Serializable
    object Never : RecurrenceEnd
    @Serializable
    data class AfterOccurrences(val count: Int) : RecurrenceEnd
    /** Epoch milliseconds, date-only (midnight UTC). */
    @Serializable
    data class OnDate(val date: Long) : RecurrenceEnd
}
