package com.example.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun restoredSessionStartsAtHomeWithoutRenderingLogin() {
        assertEquals(Screen.Home.route, initialRoute(isLoggedIn = true))
    }

    @Test
    fun signedOutSessionStartsAtLogin() {
        assertEquals(Screen.Login.route, initialRoute(isLoggedIn = false))
    }
}
