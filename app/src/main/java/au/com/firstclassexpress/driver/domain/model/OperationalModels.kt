package au.com.firstclassexpress.driver.domain.model

import au.com.firstclassexpress.driver.model.JobStatus

enum class InspectionItemStatus { UNANSWERED, PASS, DEFECT, NOT_APPLICABLE }
enum class DefectSeverity { MINOR, MAJOR, CRITICAL }
enum class EvidenceStatus { NONE, PENDING_CAPTURE, SAVED_LOCAL, PENDING_SYNC, SYNCED, FAILED_SYNC }
enum class EvidenceType(val label: String) {
    PICKUP_PHOTO("Pickup Freight Photo"),
    PICKUP_CONDITION_PHOTO("Pickup Condition Photo"),
    DELIVERY_PHOTO("Delivery Photo"),
    DAMAGED_FREIGHT_PHOTO("Damaged Freight Photo"),
    CONSIGNMENT_PHOTO("Consignment Note / Waybill"),
    PICKUP_SIGNATURE("Sender Signature"),
    DELIVERY_SIGNATURE("Recipient Signature"),
    DEFECT_PHOTO("Vehicle Defect Photo"),
    DOCUMENT("Document / Proof"),
    OTHER_ATTACHMENT("Other Attachment")
}
enum class SyncStatus { PENDING, IN_PROGRESS, SYNCED, FAILED }
enum class ShiftPhase { OFF_DUTY, PRESTART_REQUIRED, READY_TO_START, ON_DUTY, ON_BREAK }

data class InspectionAnswer(
    val itemCode: String,
    val mandatory: Boolean,
    val status: InspectionItemStatus,
    val defectDescription: String? = null,
    val defectSeverity: DefectSeverity? = null
)

data class ShiftRecord(
    val id: String,
    val driverId: String,
    val vehicleId: String,
    val trailerId: String?,
    val startOdometer: Long,
    val endOdometer: Long?,
    val phase: ShiftPhase,
    val createdAt: Long,
    val startedAt: Long?,
    val endedAt: Long?
)

data class InspectionItemRecord(
    val id: String,
    val shiftId: String,
    val code: String,
    val label: String,
    val category: String,
    val mandatory: Boolean,
    val status: InspectionItemStatus,
    val defectDescription: String?,
    val defectSeverity: DefectSeverity?
)

data class EvidenceRecord(
    val id: String,
    val jobId: String,
    val type: EvidenceType,
    val localUri: String?,
    val status: EvidenceStatus,
    val createdAt: Long,
    val driverId: String? = null,
    val shiftId: String? = null,
    val signerName: String? = null,
    val notes: String? = null,
    val fileSizeBytes: Long? = null,
    val savedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationRecordedAt: Long? = null
)

/** Everything needed to open a capture screen and attribute the resulting file. */
data class EvidenceCaptureRequest(
    val jobId: String,
    val type: EvidenceType,
    val driverId: String,
    val shiftId: String? = null
)

data class SyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
    val status: SyncStatus
)

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasons: List<String>) : ValidationResult
    data class Blocked(val reasons: List<String>) : ValidationResult
}

data class AllowedJobAction(val from: JobStatus, val to: JobStatus)
