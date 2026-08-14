package au.com.firstclassexpress.driver.data.auth

import au.com.firstclassexpress.driver.data.local.dao.DriverCredentialDao
import au.com.firstclassexpress.driver.data.local.entity.DriverCredentialEntity
import au.com.firstclassexpress.driver.domain.model.CredentialSource
import au.com.firstclassexpress.driver.model.Driver

/**
 * Installs the single local test-account credential used by development builds.
 *
 * The PIN is supplied by the build (empty in release builds), hashed with a fresh salt, and stored
 * only as salt + hash. Provisioning is a no-op when no PIN is configured or when the credential
 * already exists, so re-running it never rotates a working credential.
 */
class DevelopmentDriverProvisioner(
    private val credentialDao: DriverCredentialDao,
    private val hasher: PinHasher = PinHasher(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun provision(driver: Driver, pin: String): Result<Boolean> = runCatching {
        if (pin.isBlank()) return@runCatching false
        if (credentialDao.findByDriverId(driver.id) != null) return@runCatching false

        val salt = hasher.newSalt()
        credentialDao.upsert(
            DriverCredentialEntity(
                driverId = driver.id,
                loginId = driver.id.trim().lowercase(),
                displayName = driver.name,
                email = driver.email.trim().lowercase(),
                phone = null,
                pinSalt = salt,
                pinHash = hasher.hash(pin.trim(), salt),
                source = CredentialSource.LOCAL_DEVELOPMENT.name,
                createdAt = clock()
            )
        )
        true
    }
}
