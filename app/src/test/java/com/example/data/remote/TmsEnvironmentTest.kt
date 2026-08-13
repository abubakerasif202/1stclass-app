package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the "no endpoint means offline, and production means HTTPS" rules. */
class TmsEnvironmentTest {

    @Test
    fun `an unset base url leaves the app in local offline mode`() {
        assertEquals(TmsEnvironment.NotConfigured, TmsEnvironment.from("", isDebugBuild = true))
        assertEquals(TmsEnvironment.NotConfigured, TmsEnvironment.from("   ", isDebugBuild = false))
        assertFalse(TmsEnvironment.from("", isDebugBuild = false).isRemoteAvailable)
    }

    @Test
    fun `an https endpoint is accepted and normalised for Retrofit`() {
        val environment = TmsEnvironment.from("https://tms.example.com", isDebugBuild = false)

        assertEquals(TmsEnvironment.Configured("https://tms.example.com/"), environment)
        assertTrue(environment.isRemoteAvailable)
    }

    @Test
    fun `a trailing slash is not doubled`() {
        assertEquals(
            TmsEnvironment.Configured("https://tms.example.com/api/"),
            TmsEnvironment.from("https://tms.example.com/api/", isDebugBuild = false)
        )
    }

    @Test
    fun `a release build refuses a cleartext endpoint`() {
        val environment = TmsEnvironment.from("http://tms.example.com", isDebugBuild = false)

        assertTrue(environment is TmsEnvironment.Rejected)
        assertFalse("Driver evidence must never travel in the clear", environment.isRemoteAvailable)
    }

    @Test
    fun `a debug build may use cleartext only against a local dev host`() {
        assertTrue(TmsEnvironment.from("http://10.0.2.2:8080", isDebugBuild = true).isRemoteAvailable)
        assertTrue(TmsEnvironment.from("http://localhost:8080", isDebugBuild = true).isRemoteAvailable)
        assertFalse(
            "Even debug may not send cleartext to a real host",
            TmsEnvironment.from("http://tms.example.com", isDebugBuild = true).isRemoteAvailable
        )
    }

    @Test
    fun `a nonsense scheme is rejected rather than guessed at`() {
        assertTrue(TmsEnvironment.from("tms.example.com", isDebugBuild = true) is TmsEnvironment.Rejected)
        assertTrue(TmsEnvironment.from("ftp://tms.example.com", isDebugBuild = true) is TmsEnvironment.Rejected)
    }
}
