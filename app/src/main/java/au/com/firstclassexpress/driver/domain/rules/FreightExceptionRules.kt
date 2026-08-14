package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import au.com.firstclassexpress.driver.domain.model.FreightExceptionReason
import au.com.firstclassexpress.driver.domain.model.ValidationResult

/**
 * What a driver must supply before a freight exception can be saved.
 *
 * Notes are always required. Damage additionally requires a persisted photo — opening the camera
 * is not enough, the image has to exist on disk.
 */
object FreightExceptionRules {
    const val NOTES_REQUIRED = "Add notes describing the exception."
    const val PHOTO_REQUIRED = "Add at least one photo of the damage before saving this exception."

    fun validate(
        reason: FreightExceptionReason,
        notes: String,
        evidence: List<EvidenceRecord>
    ): ValidationResult {
        val reasons = mutableListOf<String>()
        if (reason.requiresNotes && notes.isBlank()) reasons += NOTES_REQUIRED
        if (reason.requiresPhoto && !hasSavedPhoto(evidence)) reasons += PHOTO_REQUIRED
        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    private fun hasSavedPhoto(evidence: List<EvidenceRecord>): Boolean =
        evidence.any {
            it.type in PHOTO_TYPES && EvidenceRules.isSatisfied(it.status)
        }

    private val PHOTO_TYPES = setOf(
        EvidenceType.PICKUP_PHOTO,
        EvidenceType.DELIVERY_PHOTO,
        EvidenceType.DEFECT_PHOTO
    )
}
