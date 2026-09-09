package pl.blizinski.microsofttodostore

import pl.blizinski.microsofttodostore.internal.network.GraphApiException
import pl.blizinski.tasksync.HttpStatusSyncErrorClassifier
import pl.blizinski.tasksync.SyncErrorClassifier
import pl.blizinski.tasksync.SyncErrorKind
import pl.blizinski.tasksync.model.RecurrenceStyle
import pl.blizinski.tasksync.model.StoreCapabilities

/**
 * Static facts about the Microsoft To Do source, available before any account is connected.
 * [microsoftToDoStore] builds a [pl.blizinski.tasksync.store.TaskStore] for a connected account.
 */
object MicrosoftToDo {

    /**
     * Lists support real create/rename/delete; tasks carry a real due time, native importance
     * (0–2), categories-as-labels, and a structured recurrence pattern
     * ([RecurrenceStyle.EXPLICIT]). Graph has no native cross-list move and no manual ordering
     * or subtask concept exposed here.
     */
    val capabilities = StoreCapabilities(
        supportsDueTime = true,
        supportsPriority = true,
        supportsLabels = true,
        supportsManualOrdering = false,
        supportsSubtasks = false,
        supportsMultipleLists = true,
        supportsListCreation = true,
        supportsManualDelete = true,
        supportsNativeMove = false,
        recurrenceStyle = RecurrenceStyle.EXPLICIT,
    )
}

/**
 * `401` (or a [MicrosoftReauthRequiredException] from the token provider) → `AUTH_FAILED`; no
 * consent intent (MSAL's interactive flow needs a live Activity, not a storable Intent).
 */
internal fun microsoftGraphErrorClassifier(): SyncErrorClassifier = HttpStatusSyncErrorClassifier(
    statusOf = { (it as? GraphApiException)?.httpStatus },
    extraSpecial = { if (it is MicrosoftReauthRequiredException) SyncErrorKind.AUTH_FAILED else null },
)
