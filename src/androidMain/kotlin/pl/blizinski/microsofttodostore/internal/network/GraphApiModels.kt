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
    /**
     * Null when the task doesn't recur. Read and written (see `toGraphTask`/
     * `toUpdateRequestJson`) — both directions verified against a live account (see
     * `Docs/2026-09-07-recurrence-write-path-verification.md` in the composeApp repo):
     * - Setting a non-null recurrence requires `dueDateTime` present in the *same* PATCH — Graph
     *   400s with `"The property 'dueDateTime' is required when updating/creating Recurrence in
     *   the task entity"` otherwise.
     * - An explicit `"recurrence": null` genuinely clears it (confirmed via an independent
     *   follow-up `GET`), the same omitted-vs-null PATCH semantics as [GraphDateTimeTimeZone]
     *   above — `toUpdateRequestJson` applies the identical explicit-null-injection fix.
     */
    val recurrence: GraphPatternedRecurrence? = null,
)

/** Microsoft Graph's `patternedRecurrence` — a `pattern` + `range` pair. */
@Serializable
internal data class GraphPatternedRecurrence(
    val pattern: GraphRecurrencePattern,
    val range: GraphRecurrenceRange,
)

/** Graph's `recurrencePattern`. [type] is one of "daily"/"weekly"/"absoluteMonthly"/
 *  "relativeMonthly"/"absoluteYearly"/"relativeYearly"; [index] is one of "first"/"second"/
 *  "third"/"fourth"/"last", meaningful only for the two "relative*" pattern types. */
@Serializable
internal data class GraphRecurrencePattern(
    val type: String,
    val interval: Int,
    val month: Int = 0,
    val dayOfMonth: Int = 0,
    val daysOfWeek: List<String> = emptyList(),
    val firstDayOfWeek: String = "sunday",
    val index: String = "first",
)

/** Graph's `recurrenceRange`. [type] is one of "endDate"/"noEnd"/"numbered". [startDate] is
 *  always required by Graph's API, `yyyy-MM-dd` — this library defaults it to the task's own
 *  due date when writing (once the write path is wired; see [GraphTask.recurrence]'s doc
 *  comment). */
@Serializable
internal data class GraphRecurrenceRange(
    val type: String,
    val startDate: String,
    val endDate: String? = null,
    val numberOfOccurrences: Int = 0,
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
