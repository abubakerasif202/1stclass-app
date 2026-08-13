package com.example.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Typed wire payloads for every queued operation.
 *
 * These are built by the sync processor from what is actually persisted in Room at send time —
 * never from fabricated defaults. Optional fields stay nullable and are omitted rather than
 * padded with zeroes, because "we did not record a speed" and "the speed was 0" are different
 * facts and dispatch must be able to tell them apart.
 *
 * The field names here are the app's proposal. They must be reconciled with the real TMS contract
 * before this transport is switched on — see docs/tms-sync-contract.md.
 */

@JsonClass(generateAdapter = true)
data class JobStatusSyncDto(
    val jobId: String,
    val fromStatus: String?,
    val toStatus: String,
    val driverId: String?,
    val shiftId: String?,
    /** The moment the driver made the change on device, not the moment we managed to send it. */
    val changedAt: Long
)

@JsonClass(generateAdapter = true)
data class ShiftEventSyncDto(
    val shiftId: String,
    val event: String,
    val driverId: String,
    val vehicleId: String,
    val trailerId: String?,
    val startOdometer: Long,
    val endOdometer: Long?,
    val occurredAt: Long,
    val startedAt: Long?,
    val endedAt: Long?
)

@JsonClass(generateAdapter = true)
data class InspectionItemSyncDto(
    val itemId: String,
    val shiftId: String,
    val code: String,
    val label: String,
    val category: String,
    val mandatory: Boolean,
    val status: String,
    val defectDescription: String?,
    val defectSeverity: String?,
    val answeredAt: Long
)

@JsonClass(generateAdapter = true)
data class InspectionSyncDto(
    val inspectionId: String,
    val shiftId: String,
    val driverId: String,
    val vehicleId: String,
    val trailerId: String?,
    val declarationAccepted: Boolean,
    val validationState: String?,
    val completedAt: Long?,
    val occurredAt: Long,
    val items: List<InspectionItemSyncDto>
)

@JsonClass(generateAdapter = true)
data class FreightExceptionSyncDto(
    val exceptionId: String,
    val jobId: String,
    val stage: String,
    val reason: String,
    val notes: String,
    val driverId: String,
    val shiftId: String?,
    val resolved: Boolean,
    val createdAt: Long,
    /** Evidence already acknowledged by the TMS. Never references an unsynced upload. */
    val evidenceIds: List<String>
)

@JsonClass(generateAdapter = true)
data class LocationPointSyncDto(
    val locationId: String,
    val driverId: String,
    val shiftId: String,
    val jobId: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val recordedAt: Long
)

@JsonClass(generateAdapter = true)
data class LocationBatchSyncDto(val points: List<LocationPointSyncDto>)

@JsonClass(generateAdapter = true)
data class EvidenceMetadataSyncDto(
    val evidenceId: String,
    val jobId: String,
    val driverId: String?,
    val shiftId: String?,
    val type: String,
    val createdAt: Long,
    val savedAt: Long?,
    val signerName: String?,
    val notes: String?,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationRecordedAt: Long?
)

@JsonClass(generateAdapter = true)
data class EvidenceDeleteSyncDto(
    val evidenceId: String,
    val jobId: String,
    val deletedAt: Long
)
