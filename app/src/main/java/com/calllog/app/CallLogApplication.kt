package com.calllog.app

import android.app.Application
import androidx.work.*
import com.calllog.app.util.NotificationHelper
import com.calllog.app.worker.CallLogSyncWorker
import com.calllog.app.worker.DailySummaryWorker
import timber.log.Timber

class CallLogApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber — debug build मध्ये full logging, release मध्ये silent
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("Application started — Smart Call Log")

        NotificationHelper.createNotificationChannels(this)
        scheduleInitialSync()
        DailySummaryWorker.schedule(this)
    }

    private fun scheduleInitialSync() {
        Timber.d("Scheduling initial call log sync...")

        val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("initial_sync")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_call_log_sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
