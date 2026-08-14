package au.com.firstclassexpress.driver.data.startup

import au.com.firstclassexpress.driver.data.auth.DevelopmentDriverProvisioner
import au.com.firstclassexpress.driver.data.seed.PrototypeSeedData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-time local bootstrap: seed prototype reference data and install the development credential.
 *
 * Sign-in awaits this so a first launch cannot race the credential insert. It is idempotent and
 * safe to call from several places.
 */
class AppBootstrapper(
    private val seedData: PrototypeSeedData,
    private val provisioner: DevelopmentDriverProvisioner,
    private val developmentPin: String,
    private val developmentFixturesEnabled: Boolean
) {
    private val mutex = Mutex()
    private var completed = false

    suspend fun ensureReady(): Result<Unit> = mutex.withLock {
        if (completed) return@withLock Result.success(Unit)
        runCatching {
            if (developmentFixturesEnabled) {
                seedData.seedIfEmpty()
                provisioner.provision(seedData.driver, developmentPin).getOrThrow()
            }
            completed = true
        }
    }
}
