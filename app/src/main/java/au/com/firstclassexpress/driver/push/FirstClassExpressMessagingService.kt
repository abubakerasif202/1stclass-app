package au.com.firstclassexpress.driver.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import au.com.firstclassexpress.driver.BuildConfig
import au.com.firstclassexpress.driver.FirstClassExpressApplication
import au.com.firstclassexpress.driver.notification.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FirstClassExpressMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (!BuildConfig.FCM_ENABLED || token.isBlank()) return
        serviceScope.launch {
            val application = application as? FirstClassExpressApplication ?: return@launch
            val store = application.container.pushTokenStore
            store.savePendingToken(token)
            application.container.pushRegistrationCoordinator.registerIfAuthenticated()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FCM_ENABLED) return
        val payload = PushMessageRouter.fromData(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            messageId = message.messageId
        ) ?: return
        AppNotificationManager.showPushMessageNotification(this, payload)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
