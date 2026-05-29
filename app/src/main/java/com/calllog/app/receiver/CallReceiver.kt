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

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Outgoing call starts
        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
            if (number.isNotEmpty()) {
                currentNumber = number
                isIncoming = false

                val subId = intent.getIntExtra("subscription", -1)
                resolvedSlot = if (subId != -1) {
                    com.calllog.app.util.SimDetailsManager.getSimSlotFromAccountId(context, subId.toString())
                } else {
                    0
                }

                Timber.d("Outgoing call initialized to: ${maskNumber(number)} on SIM Slot: $resolvedSlot")

                if (android.provider.Settings.canDrawOverlays(context)) {
                    com.calllog.app.service.CallOverlayService.show(context, number, false, resolvedSlot)
                }
            }
            return
        }

        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val subId = intent.getIntExtra("subscription", -1)
            if (subId != -1) {
                resolvedSlot = com.calllog.app.util.SimDetailsManager.getSimSlotFromAccountId(context, subId.toString())
            }

            Timber.d("Phone state changed: $state | subId: $subId -> resolvedSlot: $resolvedSlot")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
                    if (number.isNotEmpty()) {
                        currentNumber = number
                    }
                    isIncoming = true
                    lastState = TelephonyManager.EXTRA_STATE_RINGING
                    Timber.d("Incoming call ringing — ${maskNumber(currentNumber)}")

                    if (android.provider.Settings.canDrawOverlays(context) && currentNumber.isNotEmpty()) {
                        com.calllog.app.service.CallOverlayService.show(context, currentNumber, true, resolvedSlot)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Timber.d("Call answered / outgoing call started")
                    if (!isIncoming && currentNumber.isNotEmpty()) {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            com.calllog.app.service.CallOverlayService.show(context, currentNumber, false, resolvedSlot)
                        }
                    }
                    lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Timber.d("Call ended — lastState: $lastState, currentNumber: ${maskNumber(currentNumber)}")
                    
                    com.calllog.app.service.CallOverlayService.hide(context)

                    if (lastState == TelephonyManager.EXTRA_STATE_RINGING && isIncoming && currentNumber.isNotEmpty()) {
                        Timber.i("Missed call detected — ${maskNumber(currentNumber)}")
                        NotificationHelper.showMissedCallNotification(
                            context,
                            callerName = "Unknown",
                            callerNumber = currentNumber
                        )
                    }
                    scheduleSyncWork(context)
                    
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                    currentNumber = ""
                    isIncoming = false
                    resolvedSlot = 0
                }
            }
        }
    }

    private fun scheduleSyncWork(context: Context) {
        Timber.d("Scheduling sync after call ended...")
        val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
            .setInitialDelay(1, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("call_log_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "call_log_sync", ExistingWorkPolicy.REPLACE, request
        )
    }

    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var currentNumber = ""
        private var isIncoming = false
        private var resolvedSlot = 0

        private fun maskNumber(number: String): String {
            if (number.length < 5) return "****"
            return "${number.take(2)}****${number.takeLast(4)}"
        }
    }
}
