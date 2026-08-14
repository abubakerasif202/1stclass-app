package au.com.firstclassexpress.driver

import android.content.Context
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppIdentityTest {
    @Test
    fun appLabelIsFirstClassExpress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("1st Class Express", context.getString(R.string.app_name))
    }

    @Test
    fun manifestUsesBrandedLauncherAndStartingTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationInfo = context.applicationInfo
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )

        assertEquals(R.mipmap.ic_launcher, applicationInfo.icon)
        assertEquals(R.style.Theme_MyApplication_Starting, activityInfo.themeResource)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
        assertNotNull(context.getDrawable(R.drawable.first_class_express_logo))
        assertNotNull(context.getDrawable(R.drawable.ic_splash_mark))
    }
}
