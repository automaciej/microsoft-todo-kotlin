package pl.blizinski.microsofttodostore

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.serializer
import pl.blizinski.microsofttodostore.internal.MicrosoftSyncErrorClassifier
import pl.blizinski.microsofttodostore.internal.MicrosoftTask
import pl.blizinski.microsofttodostore.internal.MicrosoftTaskList
import pl.blizinski.microsofttodostore.internal.network.MicrosoftGraphNetworkSource
import pl.blizinski.microsofttodostore.internal.toPublic
import pl.blizinski.microsofttodostore.internal.toTask
import pl.blizinski.microsofttodostore.internal.toTaskList
import pl.blizinski.microsofttodostore.models.FatalStorageError
import pl.blizinski.microsofttodostore.models.SyncStatus
import pl.blizinski.microsofttodostore.models.Task
import pl.blizinski.microsofttodostore.models.TaskList
import pl.blizinski.tasksync.AdaptivePoller
import pl.blizinski.tasksync.OpType
import pl.blizinski.tasksync.PendingOp
import pl.blizinski.tasksync.PendingOpsProcessor
import pl.blizinski.tasksync.RoomLocalStore
import pl.blizinski.tasksync.SyncConfig
import pl.blizinski.tasksync.SyncEngine
import pl.blizinski.tasksync.SyncWorkerDependencies
import pl.blizinski.tasksync.SyncedListRecord
import pl.blizinski.tasksync.SyncedRecord
import pl.blizinski.tasksync.db.TaskSyncDatabase
import java.io.Closeable
import java.util.UUID
import kotlinx.serialization.json.Json

private const val TAG = "MicrosoftToDoStore"

/**
 * Local-first store for Microsoft To Do. Reads always come from the Room cache; writes are
 * applied locally and queued for background sync. The library manages all network interaction
 * internally, except token acquisition (see [tokenProvider]).
 *
 * Only one instance per connected Microsoft account is supported per process — each account
 * gets its own [MicrosoftToDoStore] instance with its own [config]-supplied database file name
 * (see [MicrosoftToDoStoreConfig.dbName]), analogous to [pl.blizinski.googletasksstore.GoogleTasksStore]
 * except Google historically supported only one signed-in account per process, so this
 * distinction didn't previously need calling out.
 */
class MicrosoftToDoStore(
    context: Context,
    private val tokenProvider: MicrosoftAccessTokenProvider,
    private val config: MicrosoftToDoStoreConfig = MicrosoftToDoStoreConfig(),
) : MicrosoftToDoStoreApi, Closeable {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val db: TaskSyncDatabase = Room.databaseBuilder(
        appContext,
        TaskSyncDatabase::class.java,
        config.dbName,
    ).build() // fresh schema, no legacy on-disk format to migrate from — see google-tasks-kotlin's
              // GoogleTasksMigrations.kt for why Google's equivalent needs migrations and this doesn't.

    private val store = RoomLocalStore<MicrosoftTask, MicrosoftTaskList>(
        db.recordsDao(),
        db.listsDao(),
        db.pendingOpsDao(),
        serializer(),
        serializer(),
    )

    private val network = MicrosoftGraphNetworkSource(tokenProvider)
    private val errorClassifier = MicrosoftSyncErrorClassifier()
    private val pendingOpsProcessor = PendingOpsProcessor(store, network, serializer<MicrosoftTask>(), errorClassifier)
    private val syncEngine = SyncEngine(store, network, pendingOpsProcessor, errorClassifier)

    private val syncConfig = SyncConfig(config.minPollInterval, config.maxPollInterval)
    private val workManager = WorkManager.getInstance(appContext)
    // config.dbName is unique per connected account (AppViewModelFactory suffixes it with the
    // accountId), so it doubles as the poller's instance key — keeps this account's background
    // sync chain independent of any other concurrently-connected account's (Microsoft or Google).
    private val poller = AdaptivePoller(workManager, syncConfig, instanceKey = config.dbName)

    private val _syncStatus = MutableStateFlow(SyncStatus())

    init {
        SyncWorkerDependencies.put(config.dbName, SyncWorkerDependencies.Deps(syncEngine, syncConfig))
        poller.start()
    }

    /**
     * Logs and records a fatal, unrecoverable local-storage failure instead of letting it
     * propagate and crash the process. Only the first such error is kept — later ones are
     * almost certainly the same root cause retried.
     */
    private fun reportFatalStorageError(e: Throwable) {
        Log.e(TAG, "Local storage unusable", e)
        _syncStatus.update { current ->
            if (current.fatalStorageError != null) current
            else current.copy(
                fatalStorageError = FatalStorageError(
                    occurredAt = System.currentTimeMillis(),
                    summary = e.message ?: e::class.simpleName ?: "Unknown error",
                    details = e.stackTraceToString(),
                )
            )
        }
    }

    /** Catches any exception from a Room-backed flow, reports it, and substitutes [default]
     *  instead of letting the exception propagate up and crash the process. */
    private fun <T> Flow<T>.guardStorage(default: T): Flow<T> = catch { e ->
        reportFatalStorageError(e)
        emit(default)
    }

    // -----------------------------------------------------------------------
    // Public read API
    // -----------------------------------------------------------------------

    override fun taskLists(): Flow<List<TaskList>> =
        store.lists().guardStorage(emptyList()).map { lists -> lists.map { it.toTaskList() } }

    override fun tasks(listLocalId: String): Flow<List<Task>> =
        store.records(listLocalId).guardStorage(emptyList()).map { records -> records.map { it.toTask() } }

    override fun syncStatus(): Flow<SyncStatus> = combine(
        _syncStatus,
        store.pendingOpCount().guardStorage(0),
    ) { status, count -> status.copy(pendingOpCount = count) }

    // -----------------------------------------------------------------------
    // Public write API — optimistic local write + pending op + trigger sync
    // -----------------------------------------------------------------------

    private suspend fun <T> guardWrite(onError: T, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        reportFatalStorageError(e)
        onError
    }

    override suspend fun createList(title: String): String = guardWrite(onError = "") {
        val localId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        store.upsertList(
            SyncedListRecord(
                localId = localId,
                remoteId = null,
                content = MicrosoftTaskList(title = title),
                lastSyncedAt = null,
                position = Int.MAX_VALUE,
            )
        )
        store.enqueuePendingOp(
            PendingOp(id = UUID.randomUUID().toString(), type = OpType.CREATE_LIST, entityLocalId = localId, listLocalId = localId, createdAt = now)
        )
        poller.onLocalWrite()
        localId
    }

    override suspend fun updateList(localId: String, title: String): Unit = guardWrite(onError = Unit) {
        val entity = store.getListByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        store.upsertList(entity.copy(content = MicrosoftTaskList(title = title)))
        store.enqueuePendingOp(
            PendingOp(id = UUID.randomUUID().toString(), type = OpType.UPDATE_LIST, entityLocalId = localId, listLocalId = localId, createdAt = now)
        )
        poller.onLocalWrite()
    }

    override suspend fun deleteList(localId: String): Unit = guardWrite(onError = Unit) {
        val entity = store.getListByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        for (task in store.getAllRecordsForList(localId)) {
            store.removeAllPendingOpsForEntity(task.localId)
            if (task.remoteId == null) store.hardDeleteRecord(task.localId)
        }
        store.upsertList(entity.copy(isDeleted = true))
        if (entity.remoteId != null) {
            store.enqueuePendingOp(
                PendingOp(id = UUID.randomUUID().toString(), type = OpType.DELETE_LIST, entityLocalId = localId, listLocalId = localId, contentJson = entity.remoteId, createdAt = now)
            )
            poller.onLocalWrite()
        } else {
            store.hardDeleteList(localId)
        }
    }

    override suspend fun createTask(listLocalId: String, title: String, notes: String?, dueDate: Long?): String = guardWrite(onError = "") {
        val localId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val content = MicrosoftTask(title = title, notes = notes, createdDate = now, dueDate = dueDate)
        store.upsertRecord(
            SyncedRecord(localId = localId, remoteId = null, listLocalId = listLocalId, content = content, isCompleted = false, lastSyncedAt = null)
        )
        store.enqueuePendingOp(
            PendingOp(
                id = UUID.randomUUID().toString(), type = OpType.CREATE_RECORD, entityLocalId = localId, listLocalId = listLocalId,
                contentJson = json.encodeToString(serializer(), content), createdAt = now,
            )
        )
        poller.onLocalWrite()
        localId
    }

    override suspend fun updateTask(localId: String, title: String, notes: String?, dueDate: Long?): Unit = guardWrite(onError = Unit) {
        val entity = store.getRecordByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        val newContent = entity.content.copy(title = title, notes = notes, dueDate = dueDate)
        store.upsertRecord(entity.copy(content = newContent))
        store.enqueuePendingOp(
            PendingOp(
                id = UUID.randomUUID().toString(), type = OpType.UPDATE_RECORD, entityLocalId = localId, listLocalId = entity.listLocalId,
                contentJson = json.encodeToString(serializer(), newContent), createdAt = now,
            )
        )
        poller.onLocalWrite()
    }

    override suspend fun completeTask(localId: String): Unit = guardWrite(onError = Unit) {
        val entity = store.getRecordByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        store.upsertRecord(entity.copy(isCompleted = true, content = entity.content.copy(completedDate = now)))
        store.enqueuePendingOp(
            PendingOp(id = UUID.randomUUID().toString(), type = OpType.COMPLETE_RECORD, entityLocalId = localId, listLocalId = entity.listLocalId, createdAt = now)
        )
        poller.onLocalWrite()
    }

    override suspend fun uncompleteTask(localId: String): Unit = guardWrite(onError = Unit) {
        val entity = store.getRecordByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        store.upsertRecord(entity.copy(isCompleted = false, content = entity.content.copy(completedDate = null)))
        store.enqueuePendingOp(
            PendingOp(id = UUID.randomUUID().toString(), type = OpType.UNCOMPLETE_RECORD, entityLocalId = localId, listLocalId = entity.listLocalId, createdAt = now)
        )
        poller.onLocalWrite()
    }

    override suspend fun deleteTask(localId: String): Unit = guardWrite(onError = Unit) {
        val entity = store.getRecordByLocalId(localId) ?: return@guardWrite
        val now = System.currentTimeMillis()
        store.softDeleteRecord(localId)
        store.enqueuePendingOp(
            PendingOp(id = UUID.randomUUID().toString(), type = OpType.DELETE_RECORD, entityLocalId = localId, listLocalId = entity.listLocalId, createdAt = now)
        )
        poller.onLocalWrite()
    }

    override suspend fun forceSync() {
        _syncStatus.update { it.copy(isSyncing = true, consentIntent = null) }
        try {
            val result = syncEngine.sync()
            val now = System.currentTimeMillis()
            _syncStatus.update { current ->
                val mappedErrors = result.errors.map { it.toPublic() }
                val allErrors = mappedErrors + current.recentErrors
                current.copy(
                    isSyncing = false,
                    lastSyncedAt = now,
                    recentErrors = if (mappedErrors.isEmpty()) emptyList()
                                   else allErrors.take(config.maxRecentErrors),
                    consentIntent = result.consentIntent,
                )
            }
        } catch (e: Exception) {
            reportFatalStorageError(e)
            _syncStatus.update { it.copy(isSyncing = false) }
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun close() {
        poller.cancel()
        db.close()
        SyncWorkerDependencies.remove(config.dbName)
    }
}
