package com.example.domain.repository

import com.example.domain.model.AuthenticatedDriver

/**
 * Verifies driver credentials.
 *
 * The local/development implementation checks a salted PIN hash held in Room. A production
 * implementation will call the 1st Class Express TMS over HTTPS and can be swapped in without
 * touching callers.
 */
interface AuthRepository {
    /**
     * @param loginId driver ID or email; case-insensitive and whitespace is trimmed by the
     *   implementation.
     * @param pin matched exactly.
     */
    suspend fun authenticate(loginId: String, pin: String): Result<AuthenticatedDriver>
}
