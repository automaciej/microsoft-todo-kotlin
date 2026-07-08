package pl.blizinski.microsofttodostore.internal

import kotlinx.serialization.Serializable

/**
 * Opaque content type for [pl.blizinski.tasksync.SyncEngine]/[pl.blizinski.tasksync.PendingOpsProcessor]
 * — everything about a Microsoft To Do task except the fields promoted into the shared sync
 * envelope (localId, remoteId, listLocalId, isCompleted, isDeleted, lastSyncedAt,
 * remoteUpdatedAt). [dueDate]/[completedDate] are epoch milliseconds — Graph's own
 * `dateTimeTimeZone` format conversion is entirely [pl.blizinski.microsofttodostore.internal.network.MicrosoftGraphNetworkSource]'s
 * concern, never above it.
 */
@Serializable
internal data class MicrosoftTask(
    val title: String,
    val notes: String? = null,
    val createdDate: Long? = null,
    val dueDate: Long? = null,
    /** True when [dueDate] carries a meaningful time-of-day (Microsoft always returns one, but
     * a task created with only a calendar-date due date rounds to local midnight — see
     * [pl.blizinski.microsofttodostore.internal.network.MicrosoftGraphNetworkSource]). */
    val dueHasTime: Boolean = false,
    val completedDate: Long? = null,
    /** Microsoft's "importance" field: 0 = low, 1 = normal, 2 = high. */
    val priority: Int? = null,
    val labels: List<String> = emptyList(),
)

@Serializable
internal data class MicrosoftTaskList(
    val title: String,
)
