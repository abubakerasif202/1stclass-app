package com.example.domain.model

/** A driver whose credentials have been verified by an [com.example.domain.repository.AuthRepository]. */
data class AuthenticatedDriver(
    val driverId: String,
    val name: String,
    val email: String,
    val phone: String? = null
)

/** Persisted proof that a driver is signed in on this device. Never contains the PIN. */
data class DriverSession(
    val driverId: String,
    val name: String,
    val email: String,
    val phone: String?,
    val authenticatedAt: Long
)

/** Where a credential record came from. Production credentials will arrive from the TMS. */
enum class CredentialSource { LOCAL_DEVELOPMENT, TMS }

/** Failure modes for a sign-in attempt. Messages are deliberately non-enumerating. */
sealed class AuthFailure(message: String) : Exception(message) {
    data object MissingFields : AuthFailure("Driver ID and PIN are required")
    data object InvalidCredentials : AuthFailure("Incorrect Driver ID or PIN")
    data class Unavailable(val detail: String) : AuthFailure(detail)
}
