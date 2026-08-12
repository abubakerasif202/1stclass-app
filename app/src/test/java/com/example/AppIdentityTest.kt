package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIdentityTest {
    @Test
    fun appLabelIsFirstClassExpress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("1st Class Express", context.getString(R.string.app_name))
    }
}
