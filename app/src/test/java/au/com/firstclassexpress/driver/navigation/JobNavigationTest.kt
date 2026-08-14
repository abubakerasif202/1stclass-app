package au.com.firstclassexpress.driver.navigation

import au.com.firstclassexpress.driver.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JobNavigationTest {
    private fun location(address: String = "1 Test St", lat: Double = 0.0, lng: Double = 0.0) =
        Location(address, "Sydney NSW", lat, lng, "Depot", "", "")

    @Test fun coordinatesArePreferred() {
        assertEquals("google.navigation:q=-33.86,151.2", JobNavigation.destinationUri(location(lat = -33.86, lng = 151.2)).toString())
    }
    @Test fun addressIsUsedWhenCoordinatesAreUnavailable() {
        assertEquals("geo:0,0?q=1%20Test%20St%2C%20Sydney%20NSW", JobNavigation.destinationUri(location()).toString())
    }
    @Test fun blankDestinationIsRejected() {
        assertNull(JobNavigation.destinationUri(location(address = "").copy(suburb = "")))
    }
}
