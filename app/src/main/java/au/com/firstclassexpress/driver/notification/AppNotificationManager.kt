package au.com.firstclassexpress.driver.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import au.com.firstclassexpress.driver.MainActivity
import au.com.firstclassexpress.driver.R
import au.com.firstclassexpress.driver.push.PushMessage
import au.com.firstclassexpress.driver.push.PushMessageRouter
import au.com.firstclassexpress.driver.push.PushDestination

object AppNotificationManager {
    const val CHANNEL_DISPATCH = "ops_dispatch"
    const val CHANNEL_JOB_UPDATES = "job_updates"
    const val CHANNEL_URGENT = "urgent_alerts"
    const val CHANNEL_TRACKING = "shift_tracking"
    const val CHANNEL_GENERAL = "general_notices"

    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DISPATCH,
                    "Dispatch Messages & Instructions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Direct operational communications from dispatch"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_JOB_UPDATES,
                    "Job Updates & Revisions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Updates to assigned delivery addresses, windows and priorities"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_URGENT,
                    "Urgent Safety & Fleet Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority safety and road notices"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_TRACKING,
                    "Shift Live Tracking",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active driver location telemetry transmission"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Driver Notices",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Standard operational notices and news"
                }
            )

            channels.forEach(manager::createNotificationChannel)
        }
    }

    fun showJobUpdateNotification(
        context: Context,
        notificationId: Int,
        jobId: String,
        jobReference: String,
        updateSummary: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link_screen", "job_detail")
            putExtra("jobId", jobId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Lock screen privacy version
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_JOB_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("1st Class Express")
            .setContentText("Job update received for assigned manifest")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_JOB_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Job Update: $jobReference")
            .setContentText(updateSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(updateSummary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    fun showDispatchMessageNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        jobId: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link_screen", if (jobId != null) "job_detail" else "messages")
            if (jobId != null) putExtra("jobId", jobId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_DISPATCH)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("1st Class Express Dispatch")
            .setContentText("New operational message received")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_DISPATCH)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Operations: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(notificationId, notification)
    }

    /** Renders an FCM message with a generic public version for lock-screen privacy. */
    fun showPushMessageNotification(context: Context, message: PushMessage) {
        val notificationId = message.messageId?.hashCode()?.and(Int.MAX_VALUE)
            ?: (message.type.hashCode() xor message.jobId.orEmpty().hashCode()).and(Int.MAX_VALUE)
        val intent = PushMessageRouter.applyToIntent(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            message.destination
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = when (message.type.trim().lowercase().replace('-', '_').replace(' ', '_')) {
            "urgent", "urgent_alert", "safety_alert", "fleet_alert" -> CHANNEL_URGENT
            "job_update", "job_updated", "job_revision", "job_reassigned",
            "new_job", "job_cancelled", "job_canceled", "delivery_window_changed" -> CHANNEL_JOB_UPDATES
            else -> CHANNEL_DISPATCH
        }
        val publicNotification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("1st Class Express")
            .setContentText("New operational notification received")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(notificationId, notification)
    }
}
