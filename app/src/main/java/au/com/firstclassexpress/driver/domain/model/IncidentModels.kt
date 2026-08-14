package au.com.firstclassexpress.driver.domain.model

enum class IncidentCategory(val label: String, val requiresPhoto: Boolean) {
    DELAY("Delay / Traffic", false),
    BREAKDOWN("Vehicle Breakdown", false),
    ACCIDENT("Accident / Collision", true),
    FREIGHT_DAMAGE("Freight Damage", true),
    LOADING_PROBLEM("Loading / Unloading Problem", false),
    CUSTOMER_UNAVAILABLE("Customer Unavailable / Site Closed", false),
    ACCESS_PROBLEM("Address / Site Access Problem", false),
    OTHER("Other Operational Incident", false)
}

enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class IncidentDraft(
    val driverId: String,
    val shiftId: String? = null,
    val jobId: String? = null,
    val category: IncidentCategory,
    val severity: IncidentSeverity = IncidentSeverity.MEDIUM,
    val description: String,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class IncidentRecord(
    val id: String,
    val driverId: String,
    val shiftId: String?,
    val jobId: String?,
    val category: IncidentCategory,
    val severity: IncidentSeverity,
    val description: String,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Long,
    val syncStatus: SyncStatus
)
