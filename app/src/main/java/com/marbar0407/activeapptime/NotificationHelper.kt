package com.marbar0407.activeapptime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {

    const val CHANNEL_ID = "monitor"
    const val NOTIFICATION_ID = 42

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active app time",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows today's usage time of the current app next to the clock."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun build(context: Context, chipText: String, title: String, text: String): Notification {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(chipText)
            // Notification.EXTRA_REQUEST_PROMOTED_ONGOING (API 36.1, flagged). Set directly so the
            // app also builds against compileSdk 36.
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.build()
    }
}
