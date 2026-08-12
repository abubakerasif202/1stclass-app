package com.example.domain.rules

import com.example.domain.model.EvidenceStatus

object EvidenceRules {
    fun isSatisfied(status: EvidenceStatus): Boolean = status in setOf(
        EvidenceStatus.SAVED_LOCAL,
        EvidenceStatus.PENDING_SYNC,
        EvidenceStatus.SYNCED
    )
}
