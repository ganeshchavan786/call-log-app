package com.calllog.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.service.CallLogService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.i("BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val storage = SecureStorage(context)
            if (storage.isLoggedIn()) {
                Timber.i("User logged in — starting CallLogService from BootReceiver")
                CallLogService.start(context)
            } else {
                Timber.d("User not logged in — skipping CallLogService start")
            }
        }
    }
}
