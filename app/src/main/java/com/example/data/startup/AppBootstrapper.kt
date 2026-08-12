package com.example.data.startup

import com.example.data.auth.DevelopmentDriverProvisioner
import com.example.data.seed.PrototypeSeedData
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
    private val developmentPin: String
) {
    private val mutex = Mutex()
    private var completed = false

    suspend fun ensureReady(): Result<Unit> = mutex.withLock {
        if (completed) return@withLock Result.success(Unit)
        runCatching {
            seedData.seedIfEmpty()
            provisioner.provision(seedData.driver, developmentPin).getOrThrow()
            completed = true
        }
    }
}
