package au.com.firstclassexpress.driver.domain.config

import kotlinx.coroutines.flow.StateFlow

interface RemoteAppConfigRepository {
    val config: StateFlow<RemoteAppConfig>
    suspend fun refreshConfig(): Result<RemoteAppConfig>
}
