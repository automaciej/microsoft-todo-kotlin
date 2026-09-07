package pl.blizinski.microsofttodostore.internal.network

import pl.blizinski.microsofttodostore.models.RecurrenceEnd
import pl.blizinski.microsofttodostore.models.RecurrenceFrequency
import pl.blizinski.microsofttodostore.models.RecurrenceRule
import pl.blizinski.microsofttodostore.models.Weekday
import pl.blizinski.microsofttodostore.models.WeekIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure mapping tests for `GraphPatternedRecurrence <-> RecurrenceRule` — no live `EKEventStore`/
 * HTTP call needed, matching this library's existing pure-mapping test style
 * (`MicrosoftGraphNetworkSourceTest`'s date-parsing tests). Only the read direction is wired
 * into [MicrosoftGraphNetworkSource] today (see `GraphTask.recurrence`'s doc comment); the write
 * direction is still tested here since Stage 3 of the recurring-tasks design will wire it once
 * the PATCH clear-semantics question is resolved.
 */
class RecurrenceRuleMappingTest {

    private val startDate = "2026-09-07"

    @Test
    fun dailyRoundTrips() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 2)
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun weeklyWithMultipleWeekdaysRoundTrips() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            daysOfWeek = listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
        )
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun absoluteMonthlyByDayOfMonthRoundTrips() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.ABSOLUTE_MONTHLY, interval = 1, dayOfMonth = 15)
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun relativeMonthlyByNthWeekdayRoundTrips() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.RELATIVE_MONTHLY,
            interval = 1,
            daysOfWeek = listOf(Weekday.TUESDAY),
            weekIndex = WeekIndex.SECOND,
        )
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun absoluteYearlyRoundTrips() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.ABSOLUTE_YEARLY, interval = 1, dayOfMonth = 25, month = 12)
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun relativeYearlyRoundTrips() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.RELATIVE_YEARLY,
            interval = 1,
            daysOfWeek = listOf(Weekday.THURSDAY),
            weekIndex = WeekIndex.LAST,
            month = 11,
        )
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun endNeverRoundTrips() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1, end = RecurrenceEnd.Never)
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun endAfterOccurrencesRoundTrips() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1, end = RecurrenceEnd.AfterOccurrences(10))
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun endOnDateRoundTrips() {
        // 2026-12-31, date-only, UTC midnight.
        val endDate = 1798675200000L
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1, end = RecurrenceEnd.OnDate(endDate))
        assertEquals(rule, rule.toGraphPatternedRecurrence(startDate).toRecurrenceRule())
    }

    @Test
    fun unknownPatternTypeMapsToNullRatherThanFabricating() {
        val graph = GraphPatternedRecurrence(
            pattern = GraphRecurrencePattern(type = "someFuturePatternType", interval = 1),
            range = GraphRecurrenceRange(type = "noEnd", startDate = startDate),
        )
        assertNull(graph.toRecurrenceRule())
    }

    @Test
    fun unknownRangeTypeMapsToNullRatherThanFabricating() {
        val graph = GraphPatternedRecurrence(
            pattern = GraphRecurrencePattern(type = "daily", interval = 1),
            range = GraphRecurrenceRange(type = "someFutureRangeType", startDate = startDate),
        )
        assertNull(graph.toRecurrenceRule())
    }

    @Test
    fun unrecognizedWeekdayStringsAreDroppedRatherThanFabricated() {
        val graph = GraphPatternedRecurrence(
            pattern = GraphRecurrencePattern(type = "weekly", interval = 1, daysOfWeek = listOf("monday", "someFutureDay")),
            range = GraphRecurrenceRange(type = "noEnd", startDate = startDate),
        )
        assertEquals(listOf(Weekday.MONDAY), graph.toRecurrenceRule()?.daysOfWeek)
    }

    @Test
    fun graphDateOnlyRoundTrips() {
        // 2026-09-07, UTC midnight.
        val epochMs = 1788739200000L
        assertEquals(epochMs, epochMs.toGraphDateOnly().parseGraphDateOnlyToEpochMs())
    }

    @Test
    fun graphDateOnlyMalformedParsesToNull() {
        assertNull("not-a-date".parseGraphDateOnlyToEpochMs())
    }
}
