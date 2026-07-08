package pl.blizinski.microsofttodostore.internal.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pl.blizinski.microsofttodostore.MicrosoftAccessTokenProvider
import pl.blizinski.microsofttodostore.internal.MicrosoftTask
import pl.blizinski.microsofttodostore.internal.MicrosoftTaskList
import pl.blizinski.tasksync.NetworkSource
import pl.blizinski.tasksync.RemoteListRecord
import pl.blizinski.tasksync.RemoteRecord
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * The only place in this library that knows [MicrosoftTask]/[MicrosoftTaskList]'s shape and
 * Microsoft Graph's own REST/date-time-timezone format — [pl.blizinski.tasksync.SyncEngine]/
 * [pl.blizinski.tasksync.PendingOpsProcessor] never see either.
 *
 * **Known v1 limitation**: unlike [pl.blizinski.googletasksstore]'s Google Tasks source, this
 * source cannot signal remote deletions on an incremental pull — Graph's plain REST `tasks`
 * endpoint has no tombstone/delta mechanism, only the separate `todoTask: delta` API, which
 * doesn't fit [NetworkSource]'s stateless `updatedMin` contract. [getRecords] always returns
 * `isDeleted = false`; a task deleted on the Microsoft side will keep showing up locally until
 * the surrounding list is dropped and recreated (which forces a fresh full pull). This was an
 * explicit, flagged tradeoff (see the shared-task-sync-engine design doc's Stage C log), not an
 * oversight — closing it properly means extending [NetworkSource] with a delta-token concept,
 * deferred to a future stage.
 */
internal class MicrosoftGraphNetworkSource(
    private val tokenProvider: MicrosoftAccessTokenProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : NetworkSource<MicrosoftTask, MicrosoftTaskList> {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun request(
        method: String,
        url: String,
        body: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "outlook.timezone=\"UTC\"")
        when (method) {
            "GET" -> requestBuilder.get()
            "DELETE" -> requestBuilder.delete()
            "POST" -> requestBuilder.post((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> requestBuilder.patch((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
            else -> error("Unsupported method $method")
        }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GraphApiException(response.code, "Graph API $method $url failed: ${response.code} $responseBody")
            }
            responseBody
        }
    }

    // -----------------------------------------------------------------------
    // Lists
    // -----------------------------------------------------------------------

    override suspend fun getLists(): List<RemoteListRecord<MicrosoftTaskList>> {
        val result = mutableListOf<RemoteListRecord<MicrosoftTaskList>>()
        var url: String? = "$GRAPH_BASE/me/todo/lists?\$top=100"
        while (url != null) {
            val response = json.decodeFromString(GraphTaskListsResponse.serializer(), request("GET", url))
            result += response.value.map {
                RemoteListRecord(remoteId = requireNotNull(it.id), content = MicrosoftTaskList(title = it.displayName))
            }
            url = response.nextLink
        }
        return result
    }

    override suspend fun createList(content: MicrosoftTaskList): RemoteListRecord<MicrosoftTaskList> {
        val body = json.encodeToString(GraphTaskList.serializer(), GraphTaskList(displayName = content.title))
        val result = json.decodeFromString(GraphTaskList.serializer(), request("POST", "$GRAPH_BASE/me/todo/lists", body))
        return RemoteListRecord(remoteId = requireNotNull(result.id), content = MicrosoftTaskList(title = result.displayName))
    }

    override suspend fun updateList(remoteListId: String, content: MicrosoftTaskList) {
        val body = json.encodeToString(GraphTaskList.serializer(), GraphTaskList(displayName = content.title))
        request("PATCH", "$GRAPH_BASE/me/todo/lists/$remoteListId", body)
    }

    override suspend fun deleteList(remoteListId: String) {
        request("DELETE", "$GRAPH_BASE/me/todo/lists/$remoteListId")
    }

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    override suspend fun getRecords(remoteListId: String, updatedMin: Long?): List<RemoteRecord<MicrosoftTask>> {
        val result = mutableListOf<RemoteRecord<MicrosoftTask>>()
        val filter = updatedMin?.let { "&\$filter=" + "lastModifiedDateTime ge ${it.toIso8601Utc()}".urlEncodeQueryValue() }
        var url: String? = "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks?\$top=100${filter.orEmpty()}"
        while (url != null) {
            val response = json.decodeFromString(GraphTasksResponse.serializer(), request("GET", url))
            result += response.value.map { it.toRemoteRecord() }
            url = response.nextLink
        }
        return result
    }

    override suspend fun createRecord(remoteListId: String, content: MicrosoftTask): RemoteRecord<MicrosoftTask> {
        val body = json.encodeToString(GraphTask.serializer(), content.toGraphTask())
        val result = json.decodeFromString(GraphTask.serializer(), request("POST", "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks", body))
        return result.toRemoteRecord()
    }

    override suspend fun updateRecord(remoteListId: String, remoteId: String, content: MicrosoftTask) {
        val body = json.encodeToString(GraphTask.serializer(), content.toGraphTask())
        request("PATCH", "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks/$remoteId", body)
    }

    override suspend fun completeRecord(remoteListId: String, remoteId: String) {
        val body = json.encodeToString(GraphTask.serializer(), GraphTask(status = "completed"))
        request("PATCH", "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks/$remoteId", body)
    }

    override suspend fun uncompleteRecord(remoteListId: String, remoteId: String) {
        val body = json.encodeToString(GraphTask.serializer(), GraphTask(status = "notStarted"))
        request("PATCH", "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks/$remoteId", body)
    }

    override suspend fun deleteRecord(remoteListId: String, remoteId: String) {
        request("DELETE", "$GRAPH_BASE/me/todo/lists/$remoteListId/tasks/$remoteId")
    }
}

/** Thrown for any non-2xx Graph API response; [httpStatus] drives [pl.blizinski.tasksync.SyncErrorClassifier]. */
internal class GraphApiException(val httpStatus: Int, message: String) : Exception(message)

// ---------------------------------------------------------------------------
// Mapping + date helpers. This is the only place in the library that touches Graph's
// dateTimeTimeZone/ISO-8601 formats — everywhere above deals in epoch milliseconds.
// All requests carry `Prefer: outlook.timezone="UTC"`, so every dateTime string here is UTC.
// ---------------------------------------------------------------------------

private fun GraphTask.toRemoteRecord(): RemoteRecord<MicrosoftTask> {
    val due = dueDateTime?.dateTime?.parseGraphDateTimeToEpochMs()
    return RemoteRecord(
        remoteId = requireNotNull(id),
        isCompleted = status == "completed",
        isDeleted = false, // see MicrosoftGraphNetworkSource's known-limitation doc comment
        remoteUpdatedAt = lastModifiedDateTime?.parseIso8601ToEpochMs(),
        content = MicrosoftTask(
            title = title,
            notes = body?.content?.takeIf { it.isNotEmpty() },
            createdDate = createdDateTime?.parseIso8601ToEpochMs(),
            dueDate = due,
            dueHasTime = due?.let { !it.isUtcMidnight() } ?: false,
            completedDate = completedDateTime?.dateTime?.parseGraphDateTimeToEpochMs(),
            priority = importance.toPriorityInt(),
            labels = categories,
        ),
    )
}

private fun MicrosoftTask.toGraphTask(): GraphTask = GraphTask(
    title = title,
    body = GraphItemBody(content = notes ?: "", contentType = "text"),
    importance = priority.toImportanceString(),
    categories = labels,
    dueDateTime = dueDate?.let { GraphDateTimeTimeZone(dateTime = it.toGraphDateTime(), timeZone = "UTC") },
)

private fun String.toPriorityInt(): Int? = when (this) {
    "low" -> 0
    "high" -> 2
    else -> 1
}

private fun Int?.toImportanceString(): String = when (this) {
    0 -> "low"
    2 -> "high"
    else -> "normal"
}

private fun Long.isUtcMidnight(): Boolean {
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = this
    return cal.get(java.util.Calendar.HOUR_OF_DAY) == 0 &&
        cal.get(java.util.Calendar.MINUTE) == 0 &&
        cal.get(java.util.Calendar.SECOND) == 0
}

/** Graph's `dateTimeTimeZone.dateTime`: no trailing 'Z', variable fractional-second digits. */
private fun Long.toGraphDateTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}

private fun String.parseGraphDateTimeToEpochMs(): Long? = try {
    val truncated = if (length > 23) substring(0, 23) else this
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    sdf.parse(truncated)?.time
} catch (e: Exception) { null }

/** `createdDateTime`/`lastModifiedDateTime`: plain ISO-8601 UTC, trailing 'Z'. */
private fun String.parseIso8601ToEpochMs(): Long? = try {
    val truncated = (if (length > 24) substring(0, 23) + "Z" else this)
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    sdf.parse(truncated)?.time
} catch (e: Exception) { null }

private fun Long.toIso8601Utc(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(this))
}

private fun String.urlEncodeQueryValue(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
