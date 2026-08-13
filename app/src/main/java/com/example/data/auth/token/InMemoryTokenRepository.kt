package com.example.data.auth.token

import com.example.domain.model.AuthenticatedSession
import com.example.domain.repository.TokenRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-lifetime token storage.
 *
 * Used by tests, and as the fallback when encrypted storage cannot be opened on a device. Falling
 * back to memory rather than plaintext means a broken keystore costs the driver a re-login, not
 * a leaked credential.
 */
class InMemoryTokenRepository(
    initial: AuthenticatedSession? = null
) : TokenRepository {
    private val current = AtomicReference(initial)

    override suspend fun save(session: AuthenticatedSession) {
        current.set(session)
    }

    override suspend fun load(): AuthenticatedSession? = current.get()

    override suspend fun clear() {
        current.set(null)
    }

    override fun peek(nowMillis: Long): AuthenticatedSession? =
        current.get()?.takeUnless { it.accessToken.isExpiredAt(nowMillis) }
}
