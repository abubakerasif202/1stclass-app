package com.example.domain.repository

import com.example.domain.model.AuthenticatedSession

/**
 * Secure storage for TMS bearer tokens.
 *
 * Deliberately *not* Room: tokens must not sit in the same plaintext SQLite file as operational
 * data. The production implementation keeps them in `EncryptedSharedPreferences` backed by the
 * Android Keystore.
 */
interface TokenRepository {
    suspend fun save(session: AuthenticatedSession)

    suspend fun load(): AuthenticatedSession?

    suspend fun clear()

    /**
     * Non-suspending snapshot for the OkHttp interceptor, which runs on a network thread and
     * cannot suspend. Returns null when no token is held or the held token is known to be expired.
     */
    fun peek(nowMillis: Long = System.currentTimeMillis()): AuthenticatedSession?
}
