package pl.blizinski.microsofttodostore

/**
 * Supplies a valid Microsoft Graph access token for `Tasks.ReadWrite`. Implemented by
 * TaskCompass itself (wrapping MSAL) so this library never depends on MSAL directly — same
 * reasoning as `GoogleTasksStore` taking a caller-supplied `GoogleAccountCredential`, except
 * MSAL has no equivalent "credential object with a request interceptor" abstraction, so this
 * library defines the minimal shape it needs instead.
 *
 * Implementations should attempt silent token acquisition (MSAL's `acquireTokenSilent`) first,
 * and throw [MicrosoftReauthRequiredException] when that fails and interactive sign-in is
 * needed — MSAL's interactive flow requires a live foreground `Activity` reference, which is
 * never available during a background sync, so it cannot be resolved from inside this call the
 * way Google's `UserRecoverableAuthIOException`-carried `Intent` can. Recovery for this source
 * happens through the account being reconnected via an explicit user action (see
 * `SyncErrorKind.AUTH_FAILED` handling in the consuming app), not a stored "launch this" intent.
 */
interface MicrosoftAccessTokenProvider {
    suspend fun getAccessToken(): String
}

/** Thrown by [MicrosoftAccessTokenProvider.getAccessToken] when silent token acquisition
 *  failed and interactive re-authentication is required. */
class MicrosoftReauthRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)
