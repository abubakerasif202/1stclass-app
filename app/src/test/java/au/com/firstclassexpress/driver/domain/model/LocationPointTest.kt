package au.com.firstclassexpress.driver.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationPointTest {
    private fun point(latitude: Double = -33.86, longitude: Double = 151.20) = LocationPoint(
        id = "p1", driverId = "d1", shiftId = "s1", latitude = latitude,
        longitude = longitude, accuracyMeters = 8f, recordedAt = 123L, createdAt = 124L
    )

    @Test fun validCoordinatesAreAccepted() { assertEquals(-33.86, point().latitude, 0.0) }
    @Test fun latitudeAbove90IsRejected() { assertThrows(IllegalArgumentException::class.java) { point(latitude = 90.01) } }
    @Test fun latitudeBelowMinus90IsRejected() { assertThrows(IllegalArgumentException::class.java) { point(latitude = -90.01) } }
    @Test fun longitudeAbove180IsRejected() { assertThrows(IllegalArgumentException::class.java) { point(longitude = 180.01) } }
    @Test fun longitudeBelowMinus180IsRejected() { assertThrows(IllegalArgumentException::class.java) { point(longitude = -180.01) } }
}
