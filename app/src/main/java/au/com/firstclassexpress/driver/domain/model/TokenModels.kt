package au.com.firstclassexpress.driver.domain.model

/**
 * A bearer token issued by the TMS.
 *
 * [expiresAtMillis] is null when the server does not tell us when the token dies — in that case the
 * token is treated as valid until a 401 says otherwise, which is the only signal we actually have.
 */
data class AccessToken(
    val value: String,
    val expiresAtMillis: Long? = null
) {
    init {
        require(value.isNotBlank()) { "Access token cannot be blank" }
    }

    fun isExpiredAt(nowMillis: Long): Boolean =
        expiresAtMillis != null && nowMillis >= expiresAtMillis

    /** Never let a token reach a log, a crash report or `toString()` on a data class. */
    override fun toString(): String = "AccessToken(len=${value.length}, expiresAt=$expiresAtMillis)"
}

/**
 * Proof that this device holds a live TMS session for a driver.
 *
 * The PIN is never part of this — it is verified once and discarded. [refreshToken] stays null
 * unless the real TMS contract defines a refresh endpoint; we do not invent one.
 */
data class AuthenticatedSession(
    val driverId: String,
    val accessToken: AccessToken,
    val refreshToken: String? = null
) {
    override fun toString(): String = "AuthenticatedSession(driverId=$driverId, hasRefresh=${refreshToken != null})"
}
