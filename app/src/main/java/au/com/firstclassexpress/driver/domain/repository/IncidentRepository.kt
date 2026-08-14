package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.IncidentDraft
import au.com.firstclassexpress.driver.domain.model.IncidentRecord
import kotlinx.coroutines.flow.Flow

interface IncidentRepository {
    fun observeIncidents(): Flow<List<IncidentRecord>>
    fun observeIncidentsForJob(jobId: String): Flow<List<IncidentRecord>>
    suspend fun reportIncident(draft: IncidentDraft): Result<IncidentRecord>
}
