package com.kazahana.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import com.kazahana.app.data.bsaf.BsafService
import com.kazahana.app.data.local.SettingsStore
import com.kazahana.app.data.remote.PushTokenManager
import com.kazahana.app.worker.NotificationWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class KazahanaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var bsafService: BsafService

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleNotificationCheck()
        checkBsafBotUpdates()
        registerPushTokenIfEnabled()
    }

    private fun checkBsafBotUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!settingsStore.bsafEnabled.first()) return@launch
                val bots = settingsStore.bsafRegisteredBots.first()
                val updatedBots = bots.map { bot ->
                    bsafService.checkBotUpdate(bot) ?: bot
                }
                if (updatedBots != bots) {
                    settingsStore.setBsafRegisteredBots(updatedBots)
                }
            } catch (_: Exception) { /* silent failure */ }
        }
    }

    private fun registerPushTokenIfEnabled() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!settingsStore.pushNotificationsEnabled.first()) return@launch
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        pushTokenManager.registerTokenForAllAccounts(token)
                    }
                }
            } catch (_: Exception) { /* silent failure */ }
        }
    }

    private fun scheduleNotificationCheck() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
