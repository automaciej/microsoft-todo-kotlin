# microsoft-todo-store

[![](https://jitpack.io/v/automaciej/microsoft-todo-kotlin.svg)](https://jitpack.io/#automaciej/microsoft-todo-kotlin)

Android library that wraps the [Microsoft Graph To Do API](https://learn.microsoft.com/en-us/graph/api/resources/todo-overview)
with a local Room cache and exposes a reactive `MicrosoftToDoStoreApi`, built
on top of [task-sync-kotlin](https://github.com/automaciej/task-sync-kotlin)'s
shared offline-first sync engine.

This is not a stateless network wrapper — it has a real offline cache and
sync engine behind it, shared with (not copy-pasted from)
[google-tasks-kotlin](https://github.com/automaciej/google-tasks-kotlin) via
`task-sync-kotlin`. Reads and writes go through a local Room database that
serves the UI directly and is reconciled with Microsoft Graph in the
background — the app keeps working (reading and queuing edits) with no
network connection.

This library never talks to MSAL or handles authentication itself — it takes
a small `MicrosoftAccessTokenProvider` interface (a single `suspend fun
getAccessToken(): String`) supplied by the consuming app, which owns the
actual MSAL `PublicClientApplication` and interactive sign-in flow. This
keeps the library free of any client ID, redirect URI, or other
app-specific credential.

## Features

- **`MicrosoftToDoStoreApi`**: reactive `Flow`s of task lists and tasks per
  list, plus a `Flow<SyncStatus>` for sync errors/progress.
- **Optimistic writes**: `createTask`/`updateTask`/`completeTask`/
  `uncompleteTask`/`deleteTask` and list-level equivalents apply locally
  immediately and queue for background push.
- **Adaptive background polling and pending-op merging** inherited from
  `task-sync-kotlin`: op-merging (redundant creates/deletes/updates collapse
  before hitting the network), per-account polling isolation via
  `AdaptivePoller`'s `instanceKey`, and structured `SyncErrorKind`
  classification — including a `MicrosoftReauthRequiredException` path
  distinguishing "interactive sign-in required" from an ordinary transient
  failure.
- **`forceSync()` / `fullSync()`**: run a sync cycle synchronously on
  demand; `fullSync()` re-pulls every list from scratch.
- **Incremental pull via `lastModifiedDateTime` filtering** against the
  plain Graph `tasks` REST endpoint (`$filter=lastModifiedDateTime ge ...`),
  fitting `task-sync-kotlin`'s stateless `updatedMin` contract without
  needing a stored continuation token.

## Known limitation: deletions aren't detected on incremental sync

Microsoft Graph's plain REST `tasks` endpoint has no delta/tombstone
mechanism — only the separate `todoTask: delta` API, which returns a
continuation token rather than fitting the shared engine's `updatedMin`
contract. Because of this, a task deleted directly in Microsoft To Do (on
another device, or the web) is not detected by an incremental sync — it's
only cleaned up locally on the next full pull (`fullSync()`), or if deleted
through this library itself (which removes it locally immediately, same as
any other optimistic write). See the doc comment on
`MicrosoftGraphNetworkSource.getRecords` for the exact mechanics. Closing
this gap properly means extending `task-sync-kotlin`'s `NetworkSource` with
a delta-token concept; it hasn't been done because none of `automaciej`'s
own apps have hit it in practice yet.

## What it is *not*

- **Android-only.** Built on `task-sync-kotlin`, which currently declares
  only an `androidTarget` — see that repo's README for what a
  multiplatform port would require.
- **Not an MSAL replacement.** This library expects the host app to already
  have a working MSAL sign-in flow and just calls back into it for tokens.

## Usage

Add the JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.automaciej:microsoft-todo-kotlin:0.1.0")
}
```

Implement `MicrosoftAccessTokenProvider` against your app's own MSAL
`PublicClientApplication`, construct a `MicrosoftToDoStore` with it, then
consume it through `MicrosoftToDoStoreApi`.

## Build

```
./gradlew build
```
