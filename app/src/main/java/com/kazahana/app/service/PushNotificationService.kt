package com.kazahana.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kazahana.app.MainActivity
import com.kazahana.app.R
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.remote.PushTokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PushNotificationService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            if (settingsStore.pushNotificationsEnabled.first()) {
                pushTokenManager.registerTokenForAllAccounts(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "kazahana"
        val body = message.notification?.body ?: return
        val targetDid = message.data["target_did"]
        showNotification(title, body, targetDid)
    }

    private fun showNotification(title: String, body: String, targetDid: String?) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Push Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Bluesky push notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_PUSH_NOTIFICATION
            targetDid?.let { putExtra(EXTRA_TARGET_DID, it) }
        }

        val requestCode = targetDid?.hashCode() ?: 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + (targetDid?.hashCode() ?: 0)
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "kazahana_push_notifications"
        const val ACTION_PUSH_NOTIFICATION = "com.kazahana.app.PUSH_NOTIFICATION"
        const val EXTRA_TARGET_DID = "target_did"
        private const val NOTIFICATION_ID_BASE = 2000
    }
}
