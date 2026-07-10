package pl.blizinski.microsofttodostore.internal

import pl.blizinski.microsofttodostore.MicrosoftReauthRequiredException
import pl.blizinski.microsofttodostore.internal.network.GraphApiException
import pl.blizinski.tasksync.SyncErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MicrosoftSyncErrorClassifierTest {

    private val classifier = MicrosoftSyncErrorClassifier()

    @Test
    fun classifySpecialReturnsAuthFailedForMicrosoftReauthRequiredException() {
        assertEquals(
            SyncErrorKind.AUTH_FAILED,
            classifier.classifySpecial(MicrosoftReauthRequiredException("token expired")),
        )
    }

    @Test
    fun classifySpecialReturnsAuthFailedFor401GraphApiException() {
        assertEquals(
            SyncErrorKind.AUTH_FAILED,
            classifier.classifySpecial(GraphApiException(401, "Unauthorized")),
        )
    }

    @Test
    fun classifySpecialReturnsNullForNon401GraphApiException() {
        assertNull(classifier.classifySpecial(GraphApiException(500, "Server error")))
    }

    @Test
    fun classifySpecialReturnsNullForUnrelatedException() {
        assertNull(classifier.classifySpecial(RuntimeException("network blip")))
    }

    @Test
    fun httpStatusReadsStatusCodeFromGraphApiException() {
        assertEquals(404, classifier.httpStatus(GraphApiException(404, "Not Found")))
    }

    @Test
    fun httpStatusIsNullForUnrelatedException() {
        assertNull(classifier.httpStatus(RuntimeException("network blip")))
    }

    @Test
    fun extractConsentIntentIsAlwaysNull() {
        // By design — MSAL's interactive re-authentication needs a live foreground Activity
        // passed directly to the SDK call, unlike Google's UserRecoverableAuthIOException, so
        // it cannot hand back a storable, launch-later Intent. See this class's own doc comment.
        assertNull(classifier.extractConsentIntent(MicrosoftReauthRequiredException("token expired")))
        assertNull(classifier.extractConsentIntent(RuntimeException("network blip")))
    }
}
