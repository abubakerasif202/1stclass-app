package com.example.domain.rules

import com.example.domain.model.EvidenceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceRulesTest {
    @Test fun pendingCaptureDoesNotCount() =
        assertFalse(EvidenceRules.isSatisfied(EvidenceStatus.PENDING_CAPTURE))

    @Test fun savedLocalCounts() =
        assertTrue(EvidenceRules.isSatisfied(EvidenceStatus.SAVED_LOCAL))
}
