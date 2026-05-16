package com.calllog.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calllog.app.R
import com.calllog.app.ui.MainActivity

/**
 * NotificationHelper — Manages missed call reminders and daily summary notifications.
 */
object NotificationHelper {

    private const val CHANNEL_ID_MISSED   = "missed_call_channel"
    private const val CHANNEL_ID_SUMMARY  = "daily_summary_channel"
    private const val NOTIF_ID_MISSED     = 1001
    private const val NOTIF_ID_SUMMARY    = 1002

    // ============================
    // Channel setup — Call once during App start
    // ============================
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // Missed call channel
            NotificationChannel(
                CHANNEL_ID_MISSED,
                "Missed Call Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for missed calls"
                manager.createNotificationChannel(this)
            }

            // Daily summary channel
            NotificationChannel(
                CHANNEL_ID_SUMMARY,
                "Daily Call Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Summary of calls for the day"
                manager.createNotificationChannel(this)
            }
        }
    }

    // ============================
    // Missed call notification
    // ============================
    fun showMissedCallNotification(
        context: Context,
        callerName: String,
        callerNumber: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = if (callerName != "Unknown") callerName else callerNumber

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MISSED)
            .setSmallIcon(R.drawable.ic_call_missed)
            .setContentTitle("📵 Missed Call")
            .setContentText("Missed call from $displayName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Missed call from $displayName ($callerNumber).\nOpen app to view details."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_MISSED, notification)
    }

    // ============================
    // Daily summary notification
    // ============================
    fun showDailySummaryNotification(
        context: Context,
        totalCalls: Int,
        missedCalls: Int
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SUMMARY)
            .setSmallIcon(R.drawable.ic_call_log)
            .setContentTitle("📊 Daily Call Summary")
            .setContentText("Total: $totalCalls calls | Missed: $missedCalls")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_SUMMARY, notification)
    }
}
