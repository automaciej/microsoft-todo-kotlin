package pl.blizinski.microsofttodostore.internal.network

import pl.blizinski.microsofttodostore.internal.MicrosoftTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicrosoftGraphNetworkSourceTest {

    // -----------------------------------------------------------------------
    // Graph dateTimeTimeZone.dateTime: no trailing 'Z', variable fractional-second digits.
    // -----------------------------------------------------------------------

    @Test
    fun graphDateTimeRoundTrips() {
        // 2026-03-07T13:45:30.000
        val epochMs = 1772891130000L
        assertEquals(epochMs, epochMs.toGraphDateTime().parseGraphDateTimeToEpochMs())
    }

    @Test
    fun graphDateTimeTruncatesExcessFractionalDigitsAtTheThirdOne() {
        // Graph itself may return e.g. "2026-03-07T13:45:30.1234567" (7 digits) — longer than
        // the 3 this library always generates on write. The truncate-to-23-chars logic parses
        // the first 3 fractional digits as milliseconds ("123"), not the full 7 and not zero —
        // this is a boundary worth pinning down explicitly since it's easy to assume it either
        // rejects the extra digits or zero-fills them, neither of which is what happens.
        assertEquals(1772891130123L, "2026-03-07T13:45:30.1234567".parseGraphDateTimeToEpochMs())
    }

    @Test
    fun graphDateTimeMalformedParsesToNull() {
        assertNull("not-a-date".parseGraphDateTimeToEpochMs())
    }

    // -----------------------------------------------------------------------
    // createdDateTime/lastModifiedDateTime: plain ISO-8601 UTC, trailing 'Z'.
    // -----------------------------------------------------------------------

    @Test
    fun iso8601ParsesGraphResponseFormat() {
        // 2026-03-07T13:45:30.000Z — the shape Graph actually returns for createdDateTime/
        // lastModifiedDateTime. Not a round-trip with toIso8601Utc() below — that function
        // builds a different, query-filter-only string, never parsed back by this library.
        assertEquals(1772891130000L, "2026-03-07T13:45:30.000Z".parseIso8601ToEpochMs())
    }

    @Test
    fun iso8601TruncatesExcessFractionalDigitsAtTheThirdOne() {
        // Same truncation boundary as parseGraphDateTimeToEpochMs above, applied to the
        // trailing-'Z' format instead: keeps the first 3 fractional digits ("123"), not 7 and
        // not zero.
        assertEquals(1772891130123L, "2026-03-07T13:45:30.1234567Z".parseIso8601ToEpochMs())
    }

    @Test
    fun iso8601MalformedParsesToNull() {
        assertNull("not-a-date".parseIso8601ToEpochMs())
    }

    @Test
    fun toIso8601UtcFormatsWithoutFractionalSeconds() {
        // Used only to build the outgoing `$filter=lastModifiedDateTime ge ...` query value —
        // no fractional-second component, unlike parseIso8601ToEpochMs's expected input shape.
        assertEquals("2026-03-07T13:45:30Z", 1772891130000L.toIso8601Utc())
    }

    // -----------------------------------------------------------------------
    // isUtcMidnight — distinguishes a calendar-date-only due date from one with a real time.
    // -----------------------------------------------------------------------

    @Test
    fun isUtcMidnightTrueAtExactMidnight() {
        // 2026-03-07T00:00:00.000Z
        assertTrue(1772841600000L.isUtcMidnight())
    }

    @Test
    fun isUtcMidnightFalseWithNonZeroTimeOfDay() {
        assertFalse(1772841600000L.plus(1000L).isUtcMidnight())
    }

    // -----------------------------------------------------------------------
    // priority <-> importance
    // -----------------------------------------------------------------------

    @Test
    fun priorityRoundTripsThroughImportance() {
        assertEquals(0, "low".toPriorityInt())
        assertEquals(1, "normal".toPriorityInt())
        assertEquals(2, "high".toPriorityInt())
        assertEquals("low", 0.toImportanceString())
        assertEquals("normal", 1.toImportanceString())
        assertEquals("high", 2.toImportanceString())
    }

    @Test
    fun unknownImportanceDefaultsToNormalPriority() {
        assertEquals(1, "unexpected".toPriorityInt())
    }

    @Test
    fun unknownPriorityDefaultsToNormalImportance() {
        assertEquals("normal", null.toImportanceString())
        assertEquals("normal", 99.toImportanceString())
    }

    // -----------------------------------------------------------------------
    // GraphTask <-> RemoteRecord<MicrosoftTask>
    // -----------------------------------------------------------------------

    @Test
    fun toRemoteRecordMapsCompletedStatusAndDueDateWithTime() {
        val due = 1772891130000L // has a non-midnight time component
        val graphTask = GraphTask(
            id = "remote-1",
            title = "Buy milk",
            body = GraphItemBody(content = "2%"),
            status = "completed",
            importance = "high",
            categories = listOf("errand"),
            dueDateTime = GraphDateTimeTimeZone(dateTime = due.toGraphDateTime()),
            lastModifiedDateTime = due.toIso8601Utc().let { "${it.dropLast(1)}.000Z" },
        )

        val record = graphTask.toRemoteRecord()

        assertEquals("remote-1", record.remoteId)
        assertEquals(true, record.isCompleted)
        assertEquals(false, record.isDeleted) // known v1 limitation — Graph REST has no tombstone
        assertEquals(due, record.remoteUpdatedAt)
        assertEquals("Buy milk", record.content.title)
        assertEquals("2%", record.content.notes)
        assertEquals(due, record.content.dueDate)
        assertTrue(record.content.dueHasTime, "a non-midnight due date must set dueHasTime")
        assertEquals(2, record.content.priority)
        assertEquals(listOf("errand"), record.content.labels)
    }

    @Test
    fun toRemoteRecordSetsDueHasTimeFalseForMidnightDueDate() {
        val graphTask = GraphTask(
            id = "remote-2",
            title = "No specific time",
            dueDateTime = GraphDateTimeTimeZone(dateTime = 1772841600000L.toGraphDateTime()),
        )
        assertFalse(graphTask.toRemoteRecord().content.dueHasTime)
    }

    @Test
    fun toRemoteRecordTreatsEmptyBodyContentAsNullNotes() {
        val graphTask = GraphTask(id = "remote-3", title = "x", body = GraphItemBody(content = ""))
        assertNull(graphTask.toRemoteRecord().content.notes)
    }

    @Test
    fun toGraphTaskMapsFieldsForPush() {
        val due = 1772891130000L
        val task = MicrosoftTask(
            title = "Buy milk",
            notes = "2%",
            dueDate = due,
            priority = 2,
            labels = listOf("errand"),
        )

        val graphTask = task.toGraphTask()

        assertEquals("Buy milk", graphTask.title)
        assertEquals("2%", graphTask.body?.content)
        assertEquals("high", graphTask.importance)
        assertEquals(listOf("errand"), graphTask.categories)
        assertEquals(due.toGraphDateTime(), graphTask.dueDateTime?.dateTime)
    }

    @Test
    fun toGraphTaskOmitsDueDateTimeWhenNull() {
        val graphTask = MicrosoftTask(title = "No due date").toGraphTask()
        assertNull(graphTask.dueDateTime)
    }
}
