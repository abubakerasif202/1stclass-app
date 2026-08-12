package com.example.domain.rules

import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import com.example.domain.model.FreightExceptionRecord
import com.example.domain.model.ValidationResult
import com.example.model.JobStatus

/**
 * Gate for confirming a pickup.
 *
 * These checks are the single source of truth — the UI asks for the same [ValidationResult] it
 * displays, so a screen cannot enable a button the domain would reject.
 */
object PickupCompletionRules {
    const val WRONG_STATUS = "Mark yourself as arrived at pickup before confirming."
    const val QUANTITY_REQUIRED = "Enter how many items or pallets were collected."
    const val PHOTO_REQUIRED = "Add at least one pickup photo before confirming pickup."
    const val UNRESOLVED_EXCEPTION =
        "Resolve the open freight exception, or contact dispatch, before confirming pickup."

    fun validate(
        status: JobStatus?,
        itemsCollected: String,
        evidence: List<EvidenceRecord>,
        exceptions: List<FreightExceptionRecord> = emptyList()
    ): ValidationResult {
        if (status != JobStatus.AT_PICKUP) return ValidationResult.Blocked(listOf(WRONG_STATUS))

        val reasons = mutableListOf<String>()
        if (itemsCollected.trim().toIntOrNull()?.takeIf { it >= 0 } == null) {
            reasons += QUANTITY_REQUIRED
        }
        if (!hasSaved(evidence, EvidenceType.PICKUP_PHOTO)) reasons += PHOTO_REQUIRED
        if (exceptions.any { !it.resolved }) reasons += UNRESOLVED_EXCEPTION

        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }
}

/**
 * Gate for completing a delivery. A job only reaches COMPLETED once every mandatory POD element
 * exists on disk.
 */
object DeliveryCompletionRules {
    const val WRONG_STATUS = "Mark yourself as arrived at delivery before completing."
    const val QUANTITY_REQUIRED = "Enter how many items or pallets were delivered."
    const val RECIPIENT_REQUIRED = "Enter the name of the person receiving the freight."
    const val PHOTO_REQUIRED = "Add at least one delivery photo before completing delivery."
    const val SIGNATURE_REQUIRED = "Capture the recipient signature before completing delivery."
    const val SIGNER_NAME_REQUIRED = "The captured signature is missing a signer name."
    const val UNRESOLVED_EXCEPTION =
        "Resolve the open freight exception, or contact dispatch, before completing delivery."

    fun validate(
        status: JobStatus?,
        itemsDelivered: String,
        recipientName: String,
        evidence: List<EvidenceRecord>,
        exceptions: List<FreightExceptionRecord> = emptyList()
    ): ValidationResult {
        if (status != JobStatus.AT_DELIVERY) return ValidationResult.Blocked(listOf(WRONG_STATUS))

        val reasons = mutableListOf<String>()
        if (itemsDelivered.trim().toIntOrNull()?.takeIf { it >= 0 } == null) {
            reasons += QUANTITY_REQUIRED
        }
        if (recipientName.isBlank()) reasons += RECIPIENT_REQUIRED
        if (!hasSaved(evidence, EvidenceType.DELIVERY_PHOTO)) reasons += PHOTO_REQUIRED

        val signature = savedOfType(evidence, EvidenceType.DELIVERY_SIGNATURE)
        if (signature == null) {
            reasons += SIGNATURE_REQUIRED
        } else if (signature.signerName.isNullOrBlank()) {
            reasons += SIGNER_NAME_REQUIRED
        }
        if (exceptions.any { !it.resolved }) reasons += UNRESOLVED_EXCEPTION

        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }
}

internal fun hasSaved(evidence: List<EvidenceRecord>, type: EvidenceType): Boolean =
    savedOfType(evidence, type) != null

internal fun savedOfType(evidence: List<EvidenceRecord>, type: EvidenceType): EvidenceRecord? =
    evidence.firstOrNull { it.type == type && EvidenceRules.isSatisfied(it.status) }
