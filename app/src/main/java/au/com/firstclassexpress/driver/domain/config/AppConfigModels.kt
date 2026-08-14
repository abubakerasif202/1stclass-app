package au.com.firstclassexpress.driver.domain.config

data class FeatureFlags(
    val liveTrackingEnabled: Boolean = true,
    val barcodeScannerEnabled: Boolean = true,
    val geofenceSuggestionsEnabled: Boolean = true,
    val driverMessagingEnabled: Boolean = true,
    val offlineSyncEnabled: Boolean = true,
    val delayPromptsEnabled: Boolean = true
)

data class RemoteAppConfig(
    val minSupportedAppVersion: String = "1.0.0",
    val latestAppVersion: String = "1.0.0",
    val isUpdateMandatory: Boolean = false,
    val supportPhoneNumber: String = "1300000178",
    val supportEmail: String = "dispatch@1stclassexpress.com.au",
    val maxImageUploadSizeBytes: Long = 1_500_000L,
    val featureFlags: FeatureFlags = FeatureFlags()
) {
    companion object {
        fun isUpdateRequired(currentVersion: String, minSupportedVersion: String): Boolean {
            val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
            val minParts = minSupportedVersion.split(".").mapNotNull { it.toIntOrNull() }

            val length = maxOf(currentParts.size, minParts.size)
            for (i in 0 until length) {
                val current = currentParts.getOrElse(i) { 0 }
                val min = minParts.getOrElse(i) { 0 }
                if (current < min) return true
                if (current > min) return false
            }
            return false
        }
    }
}
