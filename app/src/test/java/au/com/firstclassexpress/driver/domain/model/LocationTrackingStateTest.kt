package au.com.firstclassexpress.driver.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTrackingStateTest {
    @Test fun offDutyDoesNotRequestTracking() = assertEquals(GpsStatus.OFF, trackingStatusFor(false, true, true, true, true))
    @Test fun onDutyPermitsTracking() = assertEquals(GpsStatus.ACTIVE, trackingStatusFor(true, true, true, true, true))
    @Test fun endingShiftRequestsTrackingStop() = assertEquals(GpsStatus.OFF, trackingStatusFor(false, true, true, true, false))
    @Test fun missingPermissionIsDegraded() = assertEquals(GpsStatus.PERMISSION_REQUIRED, trackingStatusFor(true, false, false, true, false))
    @Test fun gpsDisabledIsUnavailable() = assertEquals(GpsStatus.GPS_OFF, trackingStatusFor(true, true, true, false, false))
    @Test fun approximateFixIsLimited() = assertEquals(GpsStatus.LIMITED, trackingStatusFor(true, true, false, true, true))
}
