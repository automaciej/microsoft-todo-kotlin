package pl.blizinski.microsofttodostore.internal.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Microsoft Graph `todoTaskList`/`todoTask` resources — the only place in this
 * library that knows Graph's own JSON shape and `dateTimeTimeZone` date format.
 * [pl.blizinski.tasksync.SyncEngine]/[pl.blizinski.tasksync.PendingOpsProcessor] never see these.
 *
 * All requests carry `Prefer: outlook.timezone="UTC"`, so every [GraphDateTimeTimeZone] this
 * library reads or writes is always UTC — this is what keeps the local <-> epoch-ms conversion
 * a single-timezone problem instead of needing IANA/Windows timezone-name translation.
 */
@Serializable
internal data class GraphTaskListsResponse(
    val value: List<GraphTaskList> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null,
)

@Serializable
internal data class GraphTaskList(
    val id: String? = null,
    val displayName: String = "",
)

@Serializable
internal data class GraphTasksResponse(
    val value: List<GraphTask> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null,
)

@Serializable
internal data class GraphTask(
    val id: String? = null,
    val title: String = "",
    val body: GraphItemBody? = null,
    /** "notStarted" | "inProgress" | "completed" | "waitingOnOthers" | "deferred". */
    val status: String = "notStarted",
    /** "low" | "normal" | "high". */
    val importance: String = "normal",
    val categories: List<String> = emptyList(),
    val dueDateTime: GraphDateTimeTimeZone? = null,
    val completedDateTime: GraphDateTimeTimeZone? = null,
    val createdDateTime: String? = null,
    val lastModifiedDateTime: String? = null,
)

@Serializable
internal data class GraphItemBody(
    val content: String? = null,
    val contentType: String = "text",
)

/**
 * [timeZone] is `@EncodeDefault`-forced because callers always pass "UTC" explicitly (see this
 * file's top doc comment) — a value that happens to equal the property's own declared default,
 * which kotlinx.serialization's `Json.encodeDefaults = false` (the default, and what this
 * library's `Json` instance uses) would otherwise silently drop from the outgoing request body.
 * Graph's own server-side deserializer requires `timeZone` present on a `dateTimeTimeZone`
 * object and rejects one missing it with a 400 whose body reads "Cannot write null for property
 * 'TimeZone'. For 'DueDateTime'." — confirmed by reproducing the encoding with a throwaway test
 * against this exact class before this fix (a user-reported crash, not found by inspection).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class GraphDateTimeTimeZone(
    val dateTime: String,
    @EncodeDefault val timeZone: String = "UTC",
)
