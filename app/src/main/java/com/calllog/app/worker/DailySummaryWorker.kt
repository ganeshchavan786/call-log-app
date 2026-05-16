package com.calllog.app.worker

import android.content.Context
import androidx.work.*
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.util.NotificationHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * DailySummaryWorker — रात्री 9 वाजता daily call summary notification दाखवतो.
 */
class DailySummaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("DailySummaryWorker started")
        return try {
            val db     = CallLogDatabase.getDatabase(applicationContext)
            val dao    = db.callLogDao()
            val total  = dao.getTotalCallCount().first()
            val missed = dao.getMissedCallCount().first()

            Timber.i("Daily summary — total: $total, missed: $missed")

            NotificationHelper.showDailySummaryNotification(
                applicationContext, total, missed
            )
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DailySummaryWorker failed")
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val delayMs = calculateDelayUntil9PM()
            Timber.d("DailySummaryWorker scheduled — delay: ${delayMs / 1000 / 60} min")

            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag("daily_summary")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_summary_work",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun calculateDelayUntil9PM(): Long {
            val now    = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 21)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
