package com.example.domain.model

/** Which leg of the job an exception was raised on. */
enum class ExceptionStage { PICKUP, DELIVERY }

/**
 * The exception reasons a driver can report.
 *
 * Every reason needs notes so dispatch has context. Damage additionally needs photographic proof —
 * a damage claim without an image is not defensible.
 */
enum class FreightExceptionReason(
    val label: String,
    val requiresPhoto: Boolean
) {
    DAMAGED("Damaged", requiresPhoto = true),
    MISSING("Missing", requiresPhoto = false),
    SHORT_DELIVERED("Short delivered", requiresPhoto = false),
    OVER_SUPPLIED("Over supplied", requiresPhoto = false),
    REJECTED("Rejected", requiresPhoto = false),
    UNABLE_TO_ACCESS("Unable to access", requiresPhoto = false),
    CUSTOMER_UNAVAILABLE("Customer unavailable", requiresPhoto = false),
    INCORRECT_ADDRESS("Incorrect address", requiresPhoto = false),
    DELAYED("Delayed", requiresPhoto = false),
    OTHER("Other", requiresPhoto = false);

    /** Notes are mandatory for every reason. */
    val requiresNotes: Boolean get() = true
}

data class FreightExceptionDraft(
    val jobId: String,
    val stage: ExceptionStage,
    val reason: FreightExceptionReason,
    val notes: String,
    val driverId: String,
    val shiftId: String? = null
)

data class FreightExceptionRecord(
    val id: String,
    val jobId: String,
    val stage: ExceptionStage,
    val reason: FreightExceptionReason,
    val notes: String,
    val driverId: String,
    val shiftId: String?,
    val resolved: Boolean,
    val createdAt: Long,
    val status: EvidenceStatus
)
