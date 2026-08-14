package au.com.firstclassexpress.driver.domain.model

data class JobTimelineEvent(
    val id: String,
    val jobId: String,
    val status: String,
    val title: String,
    val description: String? = null,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
