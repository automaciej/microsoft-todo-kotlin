package pl.blizinski.microsofttodostore.internal

import pl.blizinski.tasksync.SyncedListRecord
import pl.blizinski.tasksync.SyncedRecord
import pl.blizinski.tasksync.model.RecurrenceRule
import pl.blizinski.tasksync.model.Task
import pl.blizinski.tasksync.model.TaskDraft
import pl.blizinski.tasksync.model.TaskList
import pl.blizinski.tasksync.model.TaskRef
import pl.blizinski.tasksync.store.ContentAdapter

/**
 * Maps between Microsoft's opaque content types and the shared [Task]/[TaskList]. Microsoft's
 * recurrence is a structured pattern ([RecurrenceRule.StructuredRule] — [RecurrenceStyle.EXPLICIT]);
 * a [TaskDraft] carrying any other variant is ignored (the app only ever builds the matching
 * one). The `RecurrenceRule.StructuredRule` type is now the shared one — the ~90-line
 * library-model ↔ app-model rename that used to live in the consuming app is gone.
 */
internal object MicrosoftToDoContentAdapter : ContentAdapter<MicrosoftTask, MicrosoftTaskList> {

    override fun toTask(record: SyncedRecord<MicrosoftTask>) = Task(
        id = TaskRef(localId = record.localId, remoteId = record.remoteId),
        listId = record.listLocalId,
        title = record.content.title,
        notes = record.content.notes,
        isCompleted = record.isCompleted,
        createdDate = record.content.createdDate,
        dueDate = record.content.dueDate,
        dueHasTime = record.content.dueHasTime,
        completedDate = record.content.completedDate,
        priority = record.content.priority,
        labels = record.content.labels,
        recurrenceRule = record.content.recurrenceRule,
    )

    override fun toTaskList(list: SyncedListRecord<MicrosoftTaskList>) = TaskList(
        id = list.localId,
        title = list.content.title,
    )

    override fun newContent(draft: TaskDraft, now: Long) = MicrosoftTask(
        title = draft.title,
        notes = draft.notes,
        createdDate = now,
        dueDate = draft.dueDate,
        dueHasTime = draft.dueHasTime,
        priority = draft.priority,
        labels = draft.labels,
        recurrenceRule = draft.recurrenceRule as? RecurrenceRule.StructuredRule,
    )

    // priority/labels are not part of the app's task editor today, so [TaskDraft] never carries a
    // meaningful value for them on an update — preserve whatever the task already has (this also
    // matches the pre-refactor MicrosoftToDoStore.updateTask, which never touched them).
    override fun applyDraft(existing: MicrosoftTask, draft: TaskDraft) = existing.copy(
        title = draft.title,
        notes = draft.notes,
        dueDate = draft.dueDate,
        dueHasTime = draft.dueHasTime,
        recurrenceRule = draft.recurrenceRule as? RecurrenceRule.StructuredRule,
    )

    override fun applyCompletion(existing: MicrosoftTask, completed: Boolean, at: Long?) =
        existing.copy(completedDate = at)

    override fun newListContent(title: String) = MicrosoftTaskList(title = title)

    override fun applyListTitle(existing: MicrosoftTaskList, title: String) = MicrosoftTaskList(title = title)
}
