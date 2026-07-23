package pl.blizinski.microsofttodostore.internal

import pl.blizinski.tasksync.SyncedListRecord
import pl.blizinski.tasksync.SyncedRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMappersTest {

    @Test
    fun toTaskMapsEnvelopeAndContentFields() {
        val record = SyncedRecord(
            localId = "local-1",
            remoteId = "remote-1",
            listLocalId = "list-1",
            content = MicrosoftTask(title = "Buy milk", notes = "2%", priority = 2, labels = listOf("errand")),
            isCompleted = true,
        )

        val task = record.toTask()

        assertEquals("local-1", task.id.localId)
        assertEquals("remote-1", task.id.remoteId)
        assertEquals("list-1", task.listId)
        assertEquals("Buy milk", task.title)
        assertEquals("2%", task.notes)
        assertEquals(true, task.isCompleted)
        assertEquals(2, task.priority)
        assertEquals(listOf("errand"), task.labels)
    }

    @Test
    fun toTaskListMapsFields() {
        val record = SyncedListRecord(localId = "local-1", remoteId = "remote-1", content = MicrosoftTaskList(title = "Work"))
        val list = record.toTaskList()
        assertEquals("local-1", list.id)
        assertEquals("Work", list.title)
    }
}
