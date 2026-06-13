package com.kazahana.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kazahana.app.MainActivity
import com.kazahana.app.R

/**
 * Posts a local notification when new group join requests are detected while the
 * app is running (Phase 5 in-app notification — the push backend can't observe
 * authenticated chat events, so this is client-side only). Tapping deep-links to
 * the group's settings where requests can be approved/rejected.
 */
object GroupRequestNotifier {

    private const val NOTIFICATION_ID_BASE = 3000

    fun notify(context: Context, convoId: String, title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PushNotificationService.CHANNEL_ID,
                "Push Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Bluesky push notifications" }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("kazahana://group-requests/$convoId"),
            context,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            convoId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, PushNotificationService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + convoId.hashCode(), notification)
    }
}
