package au.com.firstclassexpress.driver

import android.app.Application
import au.com.firstclassexpress.driver.notification.AppNotificationManager

class FirstClassExpressApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppNotificationManager.initializeChannels(this)
    }
}
