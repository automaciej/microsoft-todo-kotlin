package pl.blizinski.microsofttodostore

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class MicrosoftToDoStoreConfig(
    val minPollInterval: Duration = 1.minutes,
    val maxPollInterval: Duration = 30.minutes,
    val dbName: String = "microsoft_todo_store",
    val maxRecentErrors: Int = 50,
)
