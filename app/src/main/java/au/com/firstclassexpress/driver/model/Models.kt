package au.com.firstclassexpress.driver.model

enum class JobStatus {
    UNASSIGNED,
    ASSIGNED,
    ACCEPTED,
    IN_PROGRESS, // En route to pickup
    AT_PICKUP,   // Arrived pickup
    PICKED_UP,   // Loaded / pickup complete
    EN_ROUTE_DELIVERY,
    AT_DELIVERY, // Arrived delivery
    DELIVERED,   // POD captured
    COMPLETED,   // Job finalized
    DELAYED,
    FAILED_DELIVERY,
    CUSTOMER_UNAVAILABLE,
    VEHICLE_ISSUE,
    OTHER_EXCEPTION,
    ISSUE;

    val isException: Boolean get() = this in setOf(
        DELAYED, FAILED_DELIVERY, CUSTOMER_UNAVAILABLE, VEHICLE_ISSUE, OTHER_EXCEPTION, ISSUE
    )

    val isTerminal: Boolean get() = this == COMPLETED

    val displayLabel: String get() = when (this) {
        UNASSIGNED -> "Unassigned"
        ASSIGNED -> "Assigned"
        ACCEPTED -> "Accepted"
        IN_PROGRESS -> "En Route to Pickup"
        AT_PICKUP -> "Arrived at Pickup"
        PICKED_UP -> "Loaded / Pickup Complete"
        EN_ROUTE_DELIVERY -> "En Route to Delivery"
        AT_DELIVERY -> "Arrived at Delivery"
        DELIVERED -> "Delivered (POD Ready)"
        COMPLETED -> "Completed"
        DELAYED -> "Delayed"
        FAILED_DELIVERY -> "Failed Delivery"
        CUSTOMER_UNAVAILABLE -> "Customer Unavailable"
        VEHICLE_ISSUE -> "Vehicle Issue"
        OTHER_EXCEPTION -> "Exception"
        ISSUE -> "Action Required"
    }
}

enum class ShiftStatus {
    OFF_DUTY,
    ON_DUTY,
    ON_BREAK
}

enum class Priority {
    NORMAL,
    HIGH,
    URGENT
}

data class Location(
    val address: String,
    val suburb: String,
    val lat: Double,
    val lng: Double,
    val companyName: String,
    val contactName: String,
    val contactPhone: String,
    val notes: String = ""
)

data class Job(
    val id: String,
    val reference: String,
    val status: JobStatus,
    val pickup: Location,
    val delivery: Location,
    val pickupWindowStart: String,
    val pickupWindowEnd: String,
    val deliveryWindowStart: String,
    val deliveryWindowEnd: String,
    val freightDescription: String,
    val itemCount: Int,
    val priority: Priority,
    val isDangerousGoods: Boolean = false,
    val temperatureRequired: String? = null,
    val specialInstructions: String = "",
    val revision: Long = 1L,
    val serverUpdatedAt: Long? = null
)

data class Driver(
    val id: String,
    val name: String,
    val email: String,
    val shiftStatus: ShiftStatus,
    val currentVehicleId: String? = null,
    /** Null when the TMS has not supplied one; the UI shows "Not provided" rather than inventing it. */
    val phone: String? = null
)
