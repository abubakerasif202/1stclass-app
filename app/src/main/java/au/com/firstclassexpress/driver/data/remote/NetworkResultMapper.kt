package au.com.firstclassexpress.driver.data.remote

import au.com.firstclassexpress.driver.domain.sync.SyncOutcome
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns HTTP status codes and thrown exceptions into a [SyncOutcome].
 *
 * The reason strings produced here are stored against the queued operation and shown on the sync
 * diagnostics screen, so they are deliberately short and contain no response body, no headers and
 * no credentials — only the status code and a plain-English summary.
 */
object NetworkResultMapper {

    fun fromHttpCode(code: Int): SyncOutcome = when {
        code in 200..299 -> SyncOutcome.Success

        code == 401 || code == 403 -> SyncOutcome.Unauthorized

        // Timeout, too-early, throttled, or the server is unwell — all worth another attempt.
        code == 408 || code == 425 || code == 429 || code in 500..599 ->
            SyncOutcome.Retryable("HTTP $code from TMS")

        // Any other 4xx is the client's fault and will not fix itself by repeating.
        code in 400..499 -> SyncOutcome.Permanent("HTTP $code rejected by TMS")

        else -> SyncOutcome.Permanent("Unexpected HTTP $code from TMS")
    }

    fun fromThrowable(error: Throwable): SyncOutcome = when (error) {
        is SSLException -> SyncOutcome.Retryable("Secure connection to TMS failed")
        is SocketTimeoutException -> SyncOutcome.Retryable("TMS request timed out")
        is UnknownHostException -> SyncOutcome.Retryable("TMS host unreachable")
        is IOException -> SyncOutcome.Retryable("Network unavailable")
        else -> SyncOutcome.Permanent(error::class.simpleName ?: "Unexpected sync failure")
    }
}
