package com.calllog.app

import android.app.Application
import androidx.work.*
import com.calllog.app.util.NotificationHelper
import com.calllog.app.worker.CallLogSyncWorker
import com.calllog.app.worker.DailySummaryWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CallLogApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("Application started — Smart Call Log")

        NotificationHelper.createNotificationChannels(this)

        // Apply Dark Mode Preference
        val storage = com.calllog.app.data.local.SecureStorage(this)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (storage.isDarkMode()) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        // Note: CallLogService should not be started from Application.onCreate()
        // because starting a Foreground Service from the background on Android 12+
        // throws ForegroundServiceStartNotAllowedException and crashes the app.
        // It is safely started from MainActivity and BootReceiver.

        // App start झाल्यावर लगेच एकदा sync
        scheduleImmediateSync()

        // दर 30 मिनिटांनी auto sync (background)
        schedulePeriodicSync()

        DailySummaryWorker.schedule(this)
    }

    /**
     * App open झाल्यावर लगेच sync — नवीन calls असतील तर server ला जातात
     */
    private fun scheduleImmediateSync() {
        Timber.d("Scheduling immediate sync on app start...")

        val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED) // Internet असेल तरच
                    .build()
            )
            .addTag("immediate_sync")
            .build()

        // REPLACE — app restart झाला तरी fresh sync होतो
        WorkManager.getInstance(this).enqueueUniqueWork(
            "immediate_sync_on_start",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * दर 30 मिनिटांनी background auto sync
     * WorkManager minimum interval = 15 min, आपण 30 min ठेवतो
     */
    private fun schedulePeriodicSync() {
        Timber.d("Scheduling periodic auto sync every 15 minutes...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Internet असेल तरच
            .build()

        val request = PeriodicWorkRequestBuilder<CallLogSyncWorker>(
            15, TimeUnit.MINUTES  // दर 15 मिनिटांनी
        )
            .setConstraints(constraints)
            .addTag("periodic_sync")
            .build()

        // REPLACE — already scheduled असेल तर नवीन schedule घेईल
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_call_log_sync",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}
