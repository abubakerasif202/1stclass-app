package au.com.firstclassexpress.driver.data.remote

/**
 * Where — and whether — this build may talk to the 1st Class Express TMS.
 *
 * The base URL is supplied at build time from `local.properties` or the `TMS_BASE_URL` environment
 * variable (CI secret). It is deliberately never hardcoded, and an unset value is a first-class
 * state rather than an error: the app runs fully offline, keeps every queued operation `PENDING`
 * and tells the driver that remote sync is unavailable. Nothing is ever reported as synced without
 * a real server acknowledgement.
 */
sealed interface TmsEnvironment {

    /** No endpoint configured for this build. Local/offline development mode. */
    data object NotConfigured : TmsEnvironment

    /**
     * An endpoint was supplied but is unusable — for example plain HTTP in a release build.
     * Treated exactly like [NotConfigured] at runtime, but the reason is surfaced for diagnostics.
     */
    data class Rejected(val reason: String) : TmsEnvironment

    /** A usable endpoint. [baseUrl] always ends in `/` so Retrofit accepts it. */
    data class Configured(val baseUrl: String) : TmsEnvironment

    val isRemoteAvailable: Boolean get() = this is Configured

    companion object {
        /**
         * HTTP is tolerated only for a debug build pointed at a developer machine. A production
         * endpoint must be HTTPS — driver evidence, signatures and customer names never travel in
         * the clear. Cleartext is additionally gated by the network security config, so a debug
         * `http://` URL to anything other than a loopback host still fails at the socket.
         */
        private val DEBUG_CLEARTEXT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "10.0.3.2")

        fun from(rawBaseUrl: String, isDebugBuild: Boolean): TmsEnvironment {
            val trimmed = rawBaseUrl.trim()
            if (trimmed.isEmpty()) return NotConfigured

            val normalised = if (trimmed.endsWith("/")) trimmed else "$trimmed/"

            return when {
                normalised.startsWith("https://") -> Configured(normalised)

                !normalised.startsWith("http://") ->
                    Rejected("TMS base URL must start with https://")

                !isDebugBuild ->
                    Rejected("Release builds require an https:// TMS base URL")

                hostOf(normalised) !in DEBUG_CLEARTEXT_HOSTS ->
                    Rejected("Cleartext TMS URLs are only allowed for local development hosts")

                else -> Configured(normalised)
            }
        }

        private fun hostOf(url: String): String =
            url.removePrefix("http://").substringBefore('/').substringBefore(':')
    }
}
