package au.com.firstclassexpress.driver.push

import android.content.Intent

sealed interface PushDestination {
    data class JobDetail(val jobId: String) : PushDestination
    data object Messages : PushDestination
}

data class PushMessage(
    val messageId: String?,
    val type: String,
    val title: String,
    val body: String,
    val jobId: String?,
    val destination: PushDestination
)

object PushMessageRouter {
    private val JOB_TYPES = setOf(
        "new_job",
        "job_update",
        "job_updated",
        "job_cancelled",
        "job_canceled",
        "job_revision",
        "job_reassigned",
        "delivery_window_changed"
    )

    // These categories intentionally open Messages: there is no separate vehicle/alert
    // destination in the current navigation graph, and the full operational context lives there.
    private val VEHICLE_TYPES = setOf(
        "vehicle_alert",
        "vehicle_update",
        "vehicle_maintenance",
        "vehicle_issue",
        "vehicle_assignment"
    )
    private val URGENT_TYPES = setOf(
        "urgent",
        "urgent_alert",
        "safety_alert",
        "fleet_alert"
    )
    private val MESSAGE_TYPES = setOf(
        "message",
        "dispatch",
        "dispatch_message",
        "driver_message"
    )

    fun destination(type: String?, jobId: String?): PushDestination {
        val normalizedType = type.normalized()
        return when {
            normalizedType in JOB_TYPES && !jobId.isNullOrBlank() ->
                PushDestination.JobDetail(jobId)
            normalizedType in VEHICLE_TYPES ||
                normalizedType in URGENT_TYPES ||
                normalizedType in MESSAGE_TYPES -> PushDestination.Messages
            else -> PushDestination.Messages
        }
    }

    fun fromData(
        data: Map<String, String>,
        notificationTitle: String?,
        notificationBody: String?,
        messageId: String?
    ): PushMessage? {
        val type = data["message_type"] ?: data["notification_type"] ?: data["type"] ?: "dispatch"
        val title = data["title"] ?: notificationTitle ?: defaultTitle(type)
        val body = data["body"] ?: notificationBody ?: data["message"] ?: "New operational message received"
        if (title.isBlank() && body.isBlank()) return null
        val jobId = data["jobId"] ?: data["job_id"]
        return PushMessage(
            messageId = messageId,
            type = type,
            title = title.ifBlank { "1st Class Express" },
            body = body.ifBlank { "New operational message received" },
            jobId = jobId,
            destination = destination(type, jobId)
        )
    }

    fun fromIntent(intent: Intent?): PushDestination? {
        if (intent == null) return null
        return when (intent.getStringExtra(EXTRA_SCREEN)) {
            SCREEN_JOB_DETAIL -> intent.getStringExtra(EXTRA_JOB_ID)
                ?.takeIf(String::isNotBlank)
                ?.let(PushDestination::JobDetail)
            SCREEN_MESSAGES -> PushDestination.Messages
            else -> null
        }
    }

    fun applyToIntent(intent: Intent, destination: PushDestination): Intent = intent.apply {
        when (destination) {
            is PushDestination.JobDetail -> {
                putExtra(EXTRA_SCREEN, SCREEN_JOB_DETAIL)
                putExtra(EXTRA_JOB_ID, destination.jobId)
            }
            PushDestination.Messages -> putExtra(EXTRA_SCREEN, SCREEN_MESSAGES)
        }
    }

    private fun defaultTitle(type: String): String = when (type.normalized()) {
        in URGENT_TYPES -> "Urgent operational alert"
        in VEHICLE_TYPES -> "Vehicle operations update"
        in JOB_TYPES -> "Job update"
        else -> "Dispatch message"
    }

    private fun String?.normalized(): String = this.orEmpty()
        .trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')

    private const val EXTRA_SCREEN = "deep_link_screen"
    private const val EXTRA_JOB_ID = "jobId"
    private const val SCREEN_JOB_DETAIL = "job_detail"
    private const val SCREEN_MESSAGES = "messages"
}
