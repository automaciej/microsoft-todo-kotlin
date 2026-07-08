# microsoft-todo-store

[![](https://jitpack.io/v/automaciej/microsoft-todo-kotlin.svg)](https://jitpack.io/#automaciej/microsoft-todo-kotlin)

Android library that wraps the [Microsoft Graph To Do API](https://learn.microsoft.com/en-us/graph/api/resources/todo-overview)
with a local Room cache and exposes a reactive `MicrosoftToDoStoreApi`, built
on top of [task-sync-kotlin](https://github.com/automaciej/task-sync-kotlin)'s
shared offline-first sync engine.

This library never talks to MSAL or handles authentication itself — it takes
a small `MicrosoftAccessTokenProvider` interface (a single `suspend fun
getAccessToken(): String`) supplied by the consuming app, which owns the
actual MSAL `PublicClientApplication` and interactive sign-in flow. This
keeps the library free of any client ID, redirect URI, or other
app-specific credential.

**Known limitation**: Microsoft Graph's plain REST `tasks` endpoint has no
delta/tombstone mechanism (only the separate `todoTask: delta` API, which
doesn't fit the shared engine's stateless `updatedMin` sync contract), so a
task deleted directly in Microsoft To Do isn't detected on an incremental
sync — see the doc comment on `MicrosoftGraphNetworkSource`.

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

## Build

```
./gradlew build
```
