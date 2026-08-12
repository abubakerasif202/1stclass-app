package com.example.domain.rules

import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import com.example.domain.model.ExceptionStage
import com.example.domain.model.FreightExceptionReason
import com.example.domain.model.FreightExceptionRecord
import com.example.domain.model.ValidationResult
import com.example.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupCompletionRulesTest {
    @Test
    fun missingPickupPhotoBlocksCompletion() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.AT_PICKUP,
            itemsCollected = "4",
            evidence = emptyList()
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(PickupCompletionRules.PHOTO_REQUIRED)
        )
    }

    @Test
    fun pendingCaptureDoesNotCountAsAPickupPhoto() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.AT_PICKUP,
            itemsCollected = "4",
            evidence = listOf(evidence(EvidenceType.PICKUP_PHOTO, EvidenceStatus.PENDING_CAPTURE))
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun missingQuantityBlocksCompletion() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.AT_PICKUP,
            itemsCollected = "",
            evidence = listOf(evidence(EvidenceType.PICKUP_PHOTO, EvidenceStatus.SAVED_LOCAL))
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(PickupCompletionRules.QUANTITY_REQUIRED)
        )
    }

    @Test
    fun wrongStatusIsBlockedRatherThanInvalid() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.IN_PROGRESS,
            itemsCollected = "4",
            evidence = listOf(evidence(EvidenceType.PICKUP_PHOTO, EvidenceStatus.SAVED_LOCAL))
        )

        assertTrue(result is ValidationResult.Blocked)
    }

    @Test
    fun unresolvedExceptionBlocksCompletion() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.AT_PICKUP,
            itemsCollected = "4",
            evidence = listOf(evidence(EvidenceType.PICKUP_PHOTO, EvidenceStatus.SAVED_LOCAL)),
            exceptions = listOf(openException(ExceptionStage.PICKUP))
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun savedPickupPhotoAndQuantityPermitsCompletion() {
        val result = PickupCompletionRules.validate(
            status = JobStatus.AT_PICKUP,
            itemsCollected = "4",
            evidence = listOf(evidence(EvidenceType.PICKUP_PHOTO, EvidenceStatus.SAVED_LOCAL))
        )

        assertEquals(ValidationResult.Valid, result)
    }
}

class DeliveryCompletionRulesTest {
    private val fullPod = listOf(
        evidence(EvidenceType.DELIVERY_PHOTO, EvidenceStatus.SAVED_LOCAL),
        evidence(EvidenceType.DELIVERY_SIGNATURE, EvidenceStatus.SAVED_LOCAL, "Jane Receiver")
    )

    @Test
    fun missingRecipientBlocksCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "  ",
            evidence = fullPod
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(DeliveryCompletionRules.RECIPIENT_REQUIRED)
        )
    }

    @Test
    fun missingSignatureBlocksCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "Jane Receiver",
            evidence = listOf(evidence(EvidenceType.DELIVERY_PHOTO, EvidenceStatus.SAVED_LOCAL))
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(DeliveryCompletionRules.SIGNATURE_REQUIRED)
        )
    }

    @Test
    fun missingDeliveryImageBlocksCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "Jane Receiver",
            evidence = listOf(
                evidence(EvidenceType.DELIVERY_SIGNATURE, EvidenceStatus.SAVED_LOCAL, "Jane")
            )
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(DeliveryCompletionRules.PHOTO_REQUIRED)
        )
    }

    @Test
    fun signatureWithoutSignerNameBlocksCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "Jane Receiver",
            evidence = listOf(
                evidence(EvidenceType.DELIVERY_PHOTO, EvidenceStatus.SAVED_LOCAL),
                evidence(EvidenceType.DELIVERY_SIGNATURE, EvidenceStatus.SAVED_LOCAL, null)
            )
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(DeliveryCompletionRules.SIGNER_NAME_REQUIRED)
        )
    }

    @Test
    fun unresolvedExceptionBlocksCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "Jane Receiver",
            evidence = fullPod,
            exceptions = listOf(openException(ExceptionStage.DELIVERY))
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun completePodPermitsCompletion() {
        val result = DeliveryCompletionRules.validate(
            status = JobStatus.AT_DELIVERY,
            itemsDelivered = "4",
            recipientName = "Jane Receiver",
            evidence = fullPod,
            exceptions = listOf(openException(ExceptionStage.DELIVERY).copy(resolved = true))
        )

        assertEquals(ValidationResult.Valid, result)
    }
}

private fun evidence(
    type: EvidenceType,
    status: EvidenceStatus,
    signerName: String? = null
) = EvidenceRecord(
    id = "$type-$status",
    jobId = "job-1",
    type = type,
    localUri = if (status == EvidenceStatus.PENDING_CAPTURE) null else "file:///evidence/x",
    status = status,
    createdAt = 1L,
    signerName = signerName
)

private fun openException(stage: ExceptionStage) = FreightExceptionRecord(
    id = "x1",
    jobId = "job-1",
    stage = stage,
    reason = FreightExceptionReason.DAMAGED,
    notes = "Pallet corner crushed",
    driverId = "DRV-8492",
    shiftId = null,
    resolved = false,
    createdAt = 1L,
    status = EvidenceStatus.SAVED_LOCAL
)
