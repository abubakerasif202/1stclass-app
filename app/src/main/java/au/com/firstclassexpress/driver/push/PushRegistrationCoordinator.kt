package au.com.firstclassexpress.driver.push

import au.com.firstclassexpress.driver.domain.repository.DeviceRegistrationRepository
import au.com.firstclassexpress.driver.domain.repository.SessionRepository
import au.com.firstclassexpress.driver.util.SafeOpsLogger

/** Registers a device only for an authenticated session and preserves failed token work for retry. */
class PushRegistrationCoordinator(
    private val sessionRepository: SessionRepository,
    private val deviceRegistrationRepository: DeviceRegistrationRepository,
    private val tokenStore: PushTokenStore,
    private val tokenProvider: suspend () -> Result<String?>,
    private val appVersionName: String,
    private val deviceIdProvider: () -> String = tokenStore::deviceId
) {
    suspend fun registerIfAuthenticated(): Result<Unit> {
        val session = sessionRepository.currentSession() ?: return Result.success(Unit)

        tokenProvider()
            .onSuccess { token -> token?.takeIf(String::isNotBlank)?.let(tokenStore::savePendingToken) }
            .onFailure { error ->
                SafeOpsLogger.w("PUSH", "FCM token refresh unavailable; pending token retained", error)
            }

        val deviceId = deviceIdProvider()
        val pendingToken = tokenStore.pendingToken()
        val registeredToken = tokenStore.registeredToken()
            ?: deviceRegistrationRepository.getRegisteredPushToken()

        val result = when {
            pendingToken != null && pendingToken == registeredToken -> Result.success(Unit)
            pendingToken != null && registeredToken != null ->
                deviceRegistrationRepository.updatePushToken(
                    deviceId = deviceId,
                    driverId = session.driverId,
                    pushToken = pendingToken
                )
            tokenStore.registeredDeviceId() == deviceId &&
                tokenStore.registeredDriverId() == session.driverId &&
                pendingToken == null -> Result.success(Unit)
            else -> deviceRegistrationRepository.registerDevice(
                deviceId = deviceId,
                driverId = session.driverId,
                appVersion = appVersionName,
                pushToken = pendingToken
            )
        }

        return result.onSuccess {
            pendingToken?.let(tokenStore::markRegistered)
            tokenStore.markDeviceRegistered(deviceId, session.driverId)
        }.onFailure { error ->
            SafeOpsLogger.w("PUSH", "Authenticated device registration deferred", error)
        }
    }
}
