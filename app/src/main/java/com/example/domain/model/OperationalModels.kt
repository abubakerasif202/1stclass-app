package com.example.domain.model

import com.example.model.JobStatus

enum class InspectionItemStatus { UNANSWERED, PASS, DEFECT, NOT_APPLICABLE }
enum class DefectSeverity { MINOR, MAJOR, CRITICAL }
enum class EvidenceStatus { NONE, PENDING_CAPTURE, SAVED_LOCAL, PENDING_SYNC, SYNCED, FAILED_SYNC }
enum class EvidenceType { PICKUP_PHOTO, DELIVERY_PHOTO, PICKUP_SIGNATURE, DELIVERY_SIGNATURE, DEFECT_PHOTO, DOCUMENT }
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
    val createdAt: Long
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
