package pl.blizinski.microsofttodostore.internal

import pl.blizinski.tasksync.SyncedListRecord
import pl.blizinski.tasksync.SyncedRecord
import pl.blizinski.tasksync.model.RecurrenceEnd
import pl.blizinski.tasksync.model.RecurrenceFrequency
import pl.blizinski.tasksync.model.RecurrenceRule
import pl.blizinski.tasksync.model.TaskDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicrosoftToDoContentAdapterTest {

    private val adapter = MicrosoftToDoContentAdapter

    private val everyMonday = RecurrenceRule.StructuredRule(
        frequency = RecurrenceFrequency.WEEKLY, interval = 1,
        daysOfWeek = listOf(pl.blizinski.tasksync.model.Weekday.MONDAY),
        end = RecurrenceEnd.Never,
    )

    @Test
    fun toTask_mapsEnvelopeAndContentFields_includingStructuredRecurrence() {
        val record = SyncedRecord(
            localId = "local-1",
            remoteId = "remote-1",
            listLocalId = "list-1",
            content = MicrosoftTask(
                title = "Buy milk", notes = "2%", createdDate = 10L, dueDate = 5_000L, dueHasTime = true,
                priority = 2, labels = listOf("errand"), recurrenceRule = everyMonday,
            ),
            isCompleted = true,
        )

        val task = adapter.toTask(record)

        assertEquals("local-1", task.id.localId)
        assertEquals("remote-1", task.id.remoteId)
        assertEquals("Buy milk", task.title)
        assertEquals("2%", task.notes)
        assertTrue(task.isCompleted)
        assertEquals(5_000L, task.dueDate)
        assertTrue(task.dueHasTime)
        assertEquals(2, task.priority)
        assertEquals(listOf("errand"), task.labels)
        assertEquals(everyMonday, task.recurrenceRule)
    }

    @Test
    fun toTaskList_mapsFields() {
        val record = SyncedListRecord(localId = "local-1", remoteId = "r", content = MicrosoftTaskList(title = "Work"))
        assertEquals("Work", adapter.toTaskList(record).title)
        assertEquals("local-1", adapter.toTaskList(record).id)
    }

    @Test
    fun newContent_carriesDraftFields_includingStructuredRecurrence() {
        val content = adapter.newContent(
            TaskDraft(title = "t", dueDate = 9L, dueHasTime = true, priority = 1, labels = listOf("a"), recurrenceRule = everyMonday),
            now = 42L,
        )
        assertEquals("t", content.title)
        assertEquals(42L, content.createdDate)
        assertEquals(9L, content.dueDate)
        assertTrue(content.dueHasTime)
        assertEquals(1, content.priority)
        assertEquals(everyMonday, content.recurrenceRule)
    }

    @Test
    fun newContent_textRecurrence_isIgnored() {
        val content = adapter.newContent(
            TaskDraft(title = "t", recurrenceRule = RecurrenceRule.TextRule("every day")),
            now = 1L,
        )
        assertNull(content.recurrenceRule)
    }

    @Test
    fun applyDraft_updatesFields() {
        val existing = MicrosoftTask(title = "old", createdDate = 1L, recurrenceRule = everyMonday)
        val updated = adapter.applyDraft(existing, TaskDraft(title = "new", dueDate = null, recurrenceRule = null))
        assertEquals("new", updated.title)
        assertNull(updated.dueDate)
        assertNull(updated.recurrenceRule)
        assertEquals(1L, updated.createdDate)
    }

    @Test
    fun listContent_roundTrips() {
        assertEquals("P", adapter.newListContent("P").title)
        assertEquals("Q", adapter.applyListTitle(MicrosoftTaskList("P"), "Q").title)
    }
}
