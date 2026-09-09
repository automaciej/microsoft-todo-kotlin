# microsoft-todo-kotlin

[![](https://jitpack.io/v/automaciej/microsoft-todo-kotlin.svg)](https://jitpack.io/#automaciej/microsoft-todo-kotlin)

Kotlin library that wraps the [Microsoft Graph To Do API](https://learn.microsoft.com/en-us/graph/api/resources/todo-overview)
with a local Room cache and exposes it through the shared
[`TaskStore`](https://github.com/automaciej/task-sync-kotlin) contract, built on
[task-sync-kotlin](https://github.com/automaciej/task-sync-kotlin)'s offline-first
sync engine (shared with, not copy-pasted from, the other source libraries).

Reads and writes go through a local Room database that serves the UI directly and
is reconciled with Microsoft Graph in the background, so the app keeps working —
reading and queuing edits — with no network. Microsoft Graph remains the ultimate
source of truth.

The library never talks to MSAL — it takes a `MicrosoftAccessTokenProvider` (a
single `suspend fun getAccessToken(): String`, plus a `MicrosoftReauthRequiredException`
the app throws when interactive sign-in is required) supplied by the consuming
app, which owns the `PublicClientApplication` and sign-in flow.

## One contract, four sources

`microsoft-todo-kotlin`, `google-tasks-kotlin`, `github-issues-kotlin` and
`todoist-kotlin` are separate, independently-versioned libraries that **all
expose the same `pl.blizinski.tasksync.store.TaskStore` interface over the same
`pl.blizinski.tasksync.model.Task` / `TaskList` types**. A consuming app can hold
several side by side and treat them uniformly, branching only on each one's
`StoreCapabilities` (`MicrosoftToDo.capabilities`). Microsoft To Do supports a
real due *time*, native importance (0–2), categories-as-labels, and a **structured**
recurrence pattern (`RecurrenceStyle.EXPLICIT` → `RecurrenceRule.StructuredRule`);
it has no native cross-list move.

## API

```kotlin
val store: TaskStore = microsoftToDoStore(
    context,
    tokenProvider,                     // MicrosoftAccessTokenProvider (wraps MSAL)
    StoreConfig(dbName = "microsoft_todo_store_$accountId"),
)
```

`TaskStore` gives you `Flow`s of task lists and tasks per list, a
`Flow<SyncStatus>`, optimistic write methods and list-level equivalents, and
`forceSync()`/`fullSync()`. Op-merging, per-account polling isolation, and
`SyncErrorKind` classification — including the `MicrosoftReauthRequiredException`
→ auth-failed path — are inherited from `task-sync-kotlin`. Incremental pull uses
`$filter=lastModifiedDateTime ge ...` against the plain Graph `tasks` endpoint,
fitting the engine's stateless `updatedMin` contract with no stored continuation
token.

## Known limitation: deletions aren't detected on incremental sync

Microsoft Graph's plain REST `tasks` endpoint has no delta/tombstone mechanism —
only the separate `todoTask: delta` API, which returns a continuation token
rather than fitting the shared engine's `updatedMin` contract. A task deleted
directly in Microsoft To Do (another device, or the web) is not seen by an
incremental sync — only cleaned up on the next `fullSync()`, or if deleted
through this library. Closing this properly means extending `task-sync-kotlin`'s
`NetworkSource` with a delta-token concept; not done because no consuming app has
hit it in practice.

## Targets

`androidTarget` only. The task model, wire DTOs, content adapter and the
`MicrosoftAccessTokenProvider` interface are in `commonMain` — a `wasmJs` target
is a bundled follow-up (a browser OAuth token provider is needed, since MSAL is
Android-only, plus a Ktor `MicrosoftGraphNetworkSource`).

## Usage

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven { url = uri("https://jitpack.io") } }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.automaciej:microsoft-todo-kotlin:v0.2.0")
}
```

Implement `MicrosoftAccessTokenProvider` against your app's MSAL
`PublicClientApplication`, call `microsoftToDoStore(...)`, and consume the
returned `TaskStore`.

## Build

```
./build.sh build
```
