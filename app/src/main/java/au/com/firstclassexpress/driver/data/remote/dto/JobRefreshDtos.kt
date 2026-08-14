package au.com.firstclassexpress.driver.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Shape of a driver's assigned work as downloaded from the TMS.
 *
 * Provisional — no live endpoint exists. [updatedAt] is the field the merge policy relies on to
 * decide whether server data is genuinely newer than what the driver has on device, so it is
 * mandatory in any real contract we accept.
 */
@JsonClass(generateAdapter = true)
data class RemoteJobDto(
    val jobId: String,
    val reference: String,
    val status: String,
    val customerName: String,
    val pickupAddress: String,
    val deliveryAddress: String,
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val deliveryLatitude: Double?,
    val deliveryLongitude: Double?,
    val scheduledPickupAt: Long?,
    val scheduledDeliveryAt: Long?,
    val instructions: String?,
    val updatedAt: Long
)

@JsonClass(generateAdapter = true)
data class RemoteJobListDto(
    val jobs: List<RemoteJobDto>,
    val serverTime: Long
)
