package au.com.firstclassexpress.driver.domain.sync

/**
 * The operation vocabulary already written into the durable queue by the repositories.
 *
 * These strings are persisted on drivers' devices. Renaming one would orphan queued work that an
 * older app version wrote, so they are recorded here as constants and left alone.
 */
object SyncEntityTypes {
    const val JOB = "JOB"
    const val SHIFT = "SHIFT"
    const val INSPECTION = "INSPECTION"
    const val INSPECTION_ITEM = "INSPECTION_ITEM"
    const val EVIDENCE = "EVIDENCE"
    const val FREIGHT_EXCEPTION = "FREIGHT_EXCEPTION"
    const val LOCATION_POINT = "LOCATION_POINT"
    const val INCIDENT = "INCIDENT"
    const val POD = "POD"
}

object SyncOperationTypes {
    // JOB
    const val STATUS_CHANGE = "STATUS_CHANGE"

    // SHIFT
    const val CREATE_DRAFT = "CREATE_DRAFT"
    const val READY_TO_START = "READY_TO_START"
    const val START = "START"
    const val END = "END"

    // INSPECTION / INSPECTION_ITEM
    const val DECLARATION = "DECLARATION"
    const val COMPLETE = "COMPLETE"
    const val ANSWER = "ANSWER"

    // EVIDENCE / FREIGHT_EXCEPTION
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"

    // LOCATION_POINT
    const val LOCATION_POINT_CREATED = "LOCATION_POINT_CREATED"

    // INCIDENT
    const val INCIDENT_REPORTED = "INCIDENT_REPORTED"

    // POD
    const val POD_COMPLETED = "POD_COMPLETED"
}
