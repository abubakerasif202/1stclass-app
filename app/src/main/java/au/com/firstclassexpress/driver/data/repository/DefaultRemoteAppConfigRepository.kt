package au.com.firstclassexpress.driver.data.repository

import au.com.firstclassexpress.driver.data.remote.api.AppConfigApi
import au.com.firstclassexpress.driver.domain.config.FeatureFlags
import au.com.firstclassexpress.driver.domain.config.RemoteAppConfig
import au.com.firstclassexpress.driver.domain.config.RemoteAppConfigRepository
import au.com.firstclassexpress.driver.util.SafeOpsLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultRemoteAppConfigRepository(
    private val currentAppVersion: String,
    private val apiProvider: () -> AppConfigApi?
) : RemoteAppConfigRepository {

    private val _config = MutableStateFlow(
        RemoteAppConfig(
            minSupportedAppVersion = "1.0.0",
            latestAppVersion = currentAppVersion,
            isUpdateMandatory = false
        )
    )
    override val config: StateFlow<RemoteAppConfig> = _config.asStateFlow()

    override suspend fun refreshConfig(): Result<RemoteAppConfig> = runCatching {
        val api = apiProvider()
        if (api != null) {
            val response = api.getAppConfig()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val updateRequired = RemoteAppConfig.isUpdateRequired(
                        currentVersion = currentAppVersion,
                        minSupportedVersion = body.minSupportedAppVersion
                    )
                    val newConfig = RemoteAppConfig(
                        minSupportedAppVersion = body.minSupportedAppVersion,
                        latestAppVersion = body.latestAppVersion,
                        isUpdateMandatory = updateRequired,
                        featureFlags = FeatureFlags(
                            liveTrackingEnabled = body.features["liveTracking"] ?: true,
                            barcodeScannerEnabled = body.features["barcodeScanner"] ?: true,
                            geofenceSuggestionsEnabled = body.features["geofencing"] ?: true,
                            driverMessagingEnabled = body.features["messaging"] ?: true,
                            offlineSyncEnabled = body.features["offlineSync"] ?: true,
                            delayPromptsEnabled = body.features["delayPrompts"] ?: true
                        )
                    )
                    _config.value = newConfig
                    return@runCatching newConfig
                }
            } else {
                SafeOpsLogger.w("CONFIG", "Remote config HTTP failed: ${response.code()}")
            }
        }
        _config.value
    }
}
