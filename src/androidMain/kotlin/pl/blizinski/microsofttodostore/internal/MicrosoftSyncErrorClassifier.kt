package pl.blizinski.microsofttodostore.internal

import pl.blizinski.microsofttodostore.MicrosoftReauthRequiredException
import pl.blizinski.microsofttodostore.internal.network.GraphApiException
import pl.blizinski.tasksync.SyncErrorClassifier
import pl.blizinski.tasksync.SyncErrorKind

internal class MicrosoftSyncErrorClassifier : SyncErrorClassifier {

    override fun classifySpecial(e: Exception): SyncErrorKind? = when {
        e is MicrosoftReauthRequiredException -> SyncErrorKind.AUTH_FAILED
        (e as? GraphApiException)?.httpStatus == 401 -> SyncErrorKind.AUTH_FAILED
        else -> null
    }

    override fun httpStatus(e: Exception): Int? = (e as? GraphApiException)?.httpStatus

    /**
     * Always null: unlike Google's `UserRecoverableAuthIOException`, MSAL's interactive
     * re-authentication needs a live foreground `Activity` passed directly to the SDK call — it
     * cannot hand back a storable, launch-later `Intent`. Recovery for this source is "reconnect
     * the account" (an explicit user action that already has an Activity to hand MSAL), not a
     * stored consent intent. See [MicrosoftReauthRequiredException]'s doc comment.
     */
    override fun extractConsentIntent(e: Exception): Any? = null
}
