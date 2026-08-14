package au.com.firstclassexpress.driver.data.auth.token

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import au.com.firstclassexpress.driver.domain.model.AccessToken
import au.com.firstclassexpress.driver.domain.model.AuthenticatedSession
import au.com.firstclassexpress.driver.domain.repository.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keystore-backed token storage.
 *
 * Tokens live in `EncryptedSharedPreferences` under an AES-256-GCM master key held by the Android
 * Keystore, in app-private storage. They are never written to Room, never logged, and are cleared
 * on logout and on an unrecoverable 401.
 */
@SuppressLint("ApplySharedPref")
class EncryptedTokenRepository private constructor(
    private val prefs: SharedPreferences
) : TokenRepository {

    override suspend fun save(session: AuthenticatedSession) = withContext(Dispatchers.IO) {
        // commit(), not apply(): the caller is already off the main thread, and a sign-in must not
        // report success before the token is actually on disk.
        prefs.edit(commit = true) {
            putString(KEY_DRIVER_ID, session.driverId)
            putString(KEY_ACCESS_TOKEN, session.accessToken.value)
            putLong(KEY_EXPIRES_AT, session.accessToken.expiresAtMillis ?: NO_EXPIRY)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
        }
    }

    override suspend fun load(): AuthenticatedSession? = withContext(Dispatchers.IO) { read() }

    /** Sign-out and 401 handling both land here; the token must be gone before we return. */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { clear() }
    }

    override fun peek(nowMillis: Long): AuthenticatedSession? =
        read()?.takeUnless { it.accessToken.isExpiredAt(nowMillis) }

    private fun read(): AuthenticatedSession? {
        val driverId = prefs.getString(KEY_DRIVER_ID, null) ?: return null
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, NO_EXPIRY).takeIf { it != NO_EXPIRY }
        return AuthenticatedSession(
            driverId = driverId,
            accessToken = AccessToken(token, expiresAt),
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        )
    }

    companion object {
        private const val FILE_NAME = "tms_tokens"
        private const val KEY_DRIVER_ID = "driverId"
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val NO_EXPIRY = -1L
        private const val TAG = "TokenStore"

        /**
         * Opens encrypted storage, falling back to memory-only storage if the keystore is
         * unavailable. We never degrade to plaintext preferences.
         */
        fun create(context: Context): TokenRepository = runCatching {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedTokenRepository(
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            )
        }.getOrElse { error ->
            // The message is the keystore's, never a token value.
            Log.w(TAG, "Encrypted token storage unavailable; tokens will not persist across restarts", error)
            InMemoryTokenRepository()
        }
    }
}
