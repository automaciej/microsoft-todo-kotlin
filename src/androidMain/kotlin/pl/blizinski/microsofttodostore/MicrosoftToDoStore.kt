package pl.blizinski.microsofttodostore

import android.content.Context
import kotlinx.serialization.serializer
import pl.blizinski.microsofttodostore.internal.MicrosoftTask
import pl.blizinski.microsofttodostore.internal.MicrosoftTaskList
import pl.blizinski.microsofttodostore.internal.MicrosoftToDoContentAdapter
import pl.blizinski.microsofttodostore.internal.MicrosoftToDoContentMerger
import pl.blizinski.microsofttodostore.internal.network.MicrosoftGraphNetworkSource
import pl.blizinski.tasksync.model.StoreConfig
import pl.blizinski.tasksync.store.TaskStore
import pl.blizinski.tasksync.store.buildAndroidTaskStore

/**
 * Builds a local-first [TaskStore] for Microsoft To Do on Android. Reads come from the Room
 * cache; writes are optimistic and synced in the background. Lists support real create/rename/
 * delete; there is no native cross-list move (the caller falls back to create+delete). One
 * instance per connected account, keyed by [config]`.dbName`. No legacy on-disk schema, so no
 * Room migrations.
 *
 * [tokenProvider] wraps MSAL (this library never depends on MSAL directly) — see
 * [MicrosoftAccessTokenProvider].
 */
fun microsoftToDoStore(
    context: Context,
    tokenProvider: MicrosoftAccessTokenProvider,
    config: StoreConfig,
): TaskStore = buildAndroidTaskStore(
    context = context,
    config = config,
    capabilities = MicrosoftToDo.capabilities,
    network = MicrosoftGraphNetworkSource(tokenProvider),
    errorClassifier = microsoftGraphErrorClassifier(),
    recordSerializer = serializer<MicrosoftTask>(),
    listSerializer = serializer<MicrosoftTaskList>(),
    adapter = MicrosoftToDoContentAdapter,
    merger = MicrosoftToDoContentMerger,
)
