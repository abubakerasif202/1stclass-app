package au.com.firstclassexpress.driver.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.com.firstclassexpress.driver.domain.model.AuthenticatedDriver
import au.com.firstclassexpress.driver.domain.model.DriverSession
import au.com.firstclassexpress.driver.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driverSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "driver_session"
)

/**
 * Session storage backed by DataStore, held outside the operational Room database on purpose:
 * signing out clears this store and nothing else, so jobs, inspection history and the durable sync
 * queue are untouched by a logout.
 */
class DataStoreSessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis
) : SessionRepository {

    constructor(context: Context) : this(context.applicationContext.driverSessionDataStore)

    override fun observeSession(): Flow<DriverSession?> =
        dataStore.data.map { preferences -> preferences.toSession() }

    override suspend fun currentSession(): DriverSession? = observeSession().first()

    override suspend fun startSession(driver: AuthenticatedDriver): Result<DriverSession> =
        runCatching {
            require(driver.driverId.isNotBlank()) { "Driver ID is required for a session" }
            val session = DriverSession(
                driverId = driver.driverId,
                name = driver.name,
                email = driver.email,
                phone = driver.phone,
                authenticatedAt = clock()
            )
            dataStore.edit { preferences ->
                preferences[KEY_DRIVER_ID] = session.driverId
                preferences[KEY_NAME] = session.name
                preferences[KEY_EMAIL] = session.email
                session.phone?.let { preferences[KEY_PHONE] = it } ?: preferences.remove(KEY_PHONE)
                preferences[KEY_AUTHENTICATED_AT] = session.authenticatedAt
            }
            session
        }

    override suspend fun clearSession(): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences.remove(KEY_DRIVER_ID)
            preferences.remove(KEY_NAME)
            preferences.remove(KEY_EMAIL)
            preferences.remove(KEY_PHONE)
            preferences.remove(KEY_AUTHENTICATED_AT)
        }
    }

    private fun Preferences.toSession(): DriverSession? {
        val driverId = this[KEY_DRIVER_ID]?.takeIf { it.isNotBlank() } ?: return null
        return DriverSession(
            driverId = driverId,
            name = this[KEY_NAME].orEmpty(),
            email = this[KEY_EMAIL].orEmpty(),
            phone = this[KEY_PHONE],
            authenticatedAt = this[KEY_AUTHENTICATED_AT] ?: 0L
        )
    }

    private companion object {
        val KEY_DRIVER_ID = stringPreferencesKey("driver_id")
        val KEY_NAME = stringPreferencesKey("driver_name")
        val KEY_EMAIL = stringPreferencesKey("driver_email")
        val KEY_PHONE = stringPreferencesKey("driver_phone")
        val KEY_AUTHENTICATED_AT = longPreferencesKey("authenticated_at")
    }
}
