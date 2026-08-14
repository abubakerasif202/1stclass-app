package au.com.firstclassexpress.driver.util

import android.util.Log

object SafeOpsLogger {
    private const val TAG_PREFIX = "1stClassExpress."

    private val SENSITIVE_PATTERNS = listOf(
        Regex("(?i)bearer\\s+[A-Za-z0-9-_=.]+"),
        Regex("(?i)pin[\"':\\s]+[0-9]{4,8}"),
        Regex("(?i)password[\"':\\s]+[^,}\"\\s]+"),
        Regex("(?i)token[\"':\\s]+[A-Za-z0-9-_=.]+")
    )

    fun d(category: String, message: String) {
        Log.d("$TAG_PREFIX$category", redact(message))
    }

    fun i(category: String, message: String) {
        Log.i("$TAG_PREFIX$category", redact(message))
    }

    fun w(category: String, message: String, throwable: Throwable? = null) {
        Log.w("$TAG_PREFIX$category", redact(message), throwable)
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG_PREFIX$category", redact(message), throwable)
    }

    fun redact(input: String): String {
        var result = input
        for (pattern in SENSITIVE_PATTERNS) {
            result = pattern.replace(result, "[REDACTED]")
        }
        return result
    }
}
