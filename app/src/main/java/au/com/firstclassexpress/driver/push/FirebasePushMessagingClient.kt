package au.com.firstclassexpress.driver.push

import android.content.Context
import au.com.firstclassexpress.driver.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FCM access is conditional because this checkout intentionally does not contain a project-specific
 * google-services.json. A configured Firebase app enables the client; an unconfigured build stays
 * a healthy offline/local build instead of crashing during startup.
 */
class FirebasePushMessagingClient(private val context: Context) {
    suspend fun currentToken(): Result<String?> {
        if (!BuildConfig.FCM_ENABLED) return Result.success(null)

        return runCatching {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(context)
                ?: return@runCatching null
            FirebaseMessaging.getInstance().token.await().takeIf { it.isNotBlank() }
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener { continuation.cancel() }
    }
}
