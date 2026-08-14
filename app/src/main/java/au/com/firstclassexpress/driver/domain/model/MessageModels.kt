package au.com.firstclassexpress.driver.domain.model

enum class MessageCategory(val label: String) {
    DISPATCH("Dispatch"),
    JOB_UPDATE("Job Update"),
    DRIVER_NOTICE("Driver Notice"),
    URGENT("Urgent Operations")
}

enum class MessageUrgency {
    NORMAL,
    HIGH,
    CRITICAL
}

data class DriverMessage(
    val id: String,
    val category: MessageCategory,
    val title: String,
    val body: String,
    val jobId: String? = null,
    val timestamp: Long,
    val isRead: Boolean = false,
    val urgency: MessageUrgency = MessageUrgency.NORMAL
)
