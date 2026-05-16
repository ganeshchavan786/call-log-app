package com.calllog.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.*
import com.calllog.app.util.NotificationHelper
import com.calllog.app.worker.CallLogSyncWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * CallReceiver — Phone state changes detect करतो आणि sync trigger करतो.
 */
class CallReceiver : BroadcastReceiver() {

    private var lastState      = TelephonyManager.EXTRA_STATE_IDLE
    private var incomingNumber = ""
    private var isMissed       = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        Timber.d("Phone state changed: $state | number: ${maskNumber(number)}")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Timber.d("Incoming call ringing — ${maskNumber(number)}")
                incomingNumber = number
                isMissed       = true
                lastState      = TelephonyManager.EXTRA_STATE_RINGING
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Timber.d("Call answered / outgoing call started")
                isMissed  = false
                lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Timber.d("Call ended — lastState: $lastState, isMissed: $isMissed")
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING && isMissed) {
                    Timber.i("Missed call detected — ${maskNumber(incomingNumber)}")
                    NotificationHelper.showMissedCallNotification(
                        context,
                        callerName   = "Unknown",
                        callerNumber = incomingNumber
                    )
                }
                scheduleSyncWork(context)
                lastState = TelephonyManager.EXTRA_STATE_IDLE
                isMissed  = false
            }
        }
    }

    private fun scheduleSyncWork(context: Context) {
        Timber.d("Scheduling sync after call ended...")
        val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag("call_log_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "call_log_sync", ExistingWorkPolicy.REPLACE, request
        )
    }

    /**
     * Privacy साठी phone number mask करतो — log मध्ये full number दिसणार नाही.
     * उदा: 9876543210 → 98****3210
     */
    private fun maskNumber(number: String): String {
        if (number.length < 5) return "****"
        return "${number.take(2)}****${number.takeLast(4)}"
    }
}
