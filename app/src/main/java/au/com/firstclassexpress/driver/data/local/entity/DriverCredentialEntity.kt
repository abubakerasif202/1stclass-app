package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sign-in credential for a driver. Only a salt and a derived hash are persisted — never the PIN.
 *
 * [loginId] and [email] are stored pre-lowercased so lookups are case-insensitive without relying
 * on SQLite collation behaviour.
 */
@Entity(
    tableName = "driver_credentials",
    indices = [Index(value = ["loginId"], unique = true), Index("email")]
)
data class DriverCredentialEntity(
    @PrimaryKey val driverId: String,
    val loginId: String,
    val displayName: String,
    val email: String,
    val phone: String?,
    val pinSalt: String,
    val pinHash: String,
    val source: String,
    val createdAt: Long
)
