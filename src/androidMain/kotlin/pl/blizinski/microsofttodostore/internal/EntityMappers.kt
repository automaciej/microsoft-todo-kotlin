package pl.blizinski.microsofttodostore.internal

import pl.blizinski.microsofttodostore.models.Task
import pl.blizinski.microsofttodostore.models.TaskId
import pl.blizinski.microsofttodostore.models.TaskList
import pl.blizinski.tasksync.SyncedListRecord
import pl.blizinski.tasksync.SyncedRecord

internal fun SyncedRecord<MicrosoftTask>.toTask(): Task = Task(
    id = TaskId(localId = localId, remoteId = remoteId),
    listId = listLocalId,
    title = content.title,
    notes = content.notes,
    isCompleted = isCompleted,
    createdDate = content.createdDate,
    dueDate = content.dueDate,
    dueHasTime = content.dueHasTime,
    completedDate = content.completedDate,
    priority = content.priority,
    labels = content.labels,
)

internal fun SyncedListRecord<MicrosoftTaskList>.toTaskList(): TaskList = TaskList(
    id = localId,
    title = content.title,
)
