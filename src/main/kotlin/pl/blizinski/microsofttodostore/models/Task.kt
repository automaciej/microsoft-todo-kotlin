package pl.blizinski.microsofttodostore.models

data class Task(
    val id: TaskId,
    val listId: String,
    val title: String,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val createdDate: Long? = null,
    /** Due date as epoch milliseconds. Microsoft To Do carries a real time component,
     * unlike Google Tasks — see [dueHasTime]. */
    val dueDate: Long? = null,
    /** True when [dueDate] carries a meaningful time-of-day, not just a calendar date. */
    val dueHasTime: Boolean = false,
    /** Completion date as epoch milliseconds. Null if the task is not completed. */
    val completedDate: Long? = null,
    /** Microsoft's "importance" field: 0 = low, 1 = normal, 2 = high. Null if unknown. */
    val priority: Int? = null,
    /** Microsoft's free-form "categories" field. */
    val labels: List<String> = emptyList(),
)
