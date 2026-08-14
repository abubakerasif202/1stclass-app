package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.domain.model.EvidenceStatus

object EvidenceRules {
    fun isSatisfied(status: EvidenceStatus): Boolean = status in setOf(
        EvidenceStatus.SAVED_LOCAL,
        EvidenceStatus.PENDING_SYNC,
        EvidenceStatus.SYNCED
    )
}
