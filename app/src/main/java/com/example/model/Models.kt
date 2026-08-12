package com.example.model

enum class JobStatus {
    UNASSIGNED,
    ASSIGNED,
    IN_PROGRESS, // En route to pickup
    AT_PICKUP,
    PICKED_UP,
    EN_ROUTE_DELIVERY,
    AT_DELIVERY,
    COMPLETED,
    ISSUE
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
    val specialInstructions: String = ""
)

data class Driver(
    val id: String,
    val name: String,
    val email: String,
    val shiftStatus: ShiftStatus,
    val currentVehicleId: String? = null
)
