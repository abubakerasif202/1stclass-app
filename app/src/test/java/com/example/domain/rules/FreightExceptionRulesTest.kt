package com.example.domain.rules

import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import com.example.domain.model.FreightExceptionReason
import com.example.domain.model.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreightExceptionRulesTest {
    @Test
    fun damageRequiresAPhoto() {
        val result = FreightExceptionRules.validate(
            reason = FreightExceptionReason.DAMAGED,
            notes = "Two cartons crushed on the top layer",
            evidence = emptyList()
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(FreightExceptionRules.PHOTO_REQUIRED)
        )
    }

    @Test
    fun damagePhotoMustActuallyBeSaved() {
        val result = FreightExceptionRules.validate(
            reason = FreightExceptionReason.DAMAGED,
            notes = "Two cartons crushed",
            evidence = listOf(photo(EvidenceStatus.PENDING_CAPTURE))
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun damageWithSavedPhotoIsAccepted() {
        val result = FreightExceptionRules.validate(
            reason = FreightExceptionReason.DAMAGED,
            notes = "Two cartons crushed",
            evidence = listOf(photo(EvidenceStatus.SAVED_LOCAL))
        )

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun rejectedFreightRequiresAReason() {
        val result = FreightExceptionRules.validate(
            reason = FreightExceptionReason.REJECTED,
            notes = "   ",
            evidence = emptyList()
        )

        assertTrue(result is ValidationResult.Invalid)
        assertTrue(
            (result as ValidationResult.Invalid).reasons
                .contains(FreightExceptionRules.NOTES_REQUIRED)
        )
    }

    @Test
    fun rejectedFreightWithReasonIsAcceptedWithoutAPhoto() {
        val result = FreightExceptionRules.validate(
            reason = FreightExceptionReason.REJECTED,
            notes = "Receiver refused: incorrect purchase order",
            evidence = emptyList()
        )

        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun everyReasonRequiresNotes() {
        FreightExceptionReason.entries.forEach { reason ->
            val result = FreightExceptionRules.validate(reason, "", emptyList())
            assertTrue("$reason should require notes", result is ValidationResult.Invalid)
        }
    }

    private fun photo(status: EvidenceStatus) = EvidenceRecord(
        id = "e1",
        jobId = "job-1",
        type = EvidenceType.DELIVERY_PHOTO,
        localUri = if (status == EvidenceStatus.PENDING_CAPTURE) null else "file:///x.jpg",
        status = status,
        createdAt = 1L
    )
}
