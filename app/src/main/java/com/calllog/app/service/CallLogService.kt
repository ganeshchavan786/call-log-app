package com.calllog.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import androidx.work.*
import com.calllog.app.util.NotificationHelper
import com.calllog.app.worker.CallLogSyncWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CallLogService : Service() {

    private var callLogObserver: ContentObserver? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CallLogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallLogService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("CallLogService created — Registering real-time CallLog observer")
        registerCallLogObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("CallLogService starting foreground...")
        
        val notification = NotificationHelper.getServiceNotification(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_ID_SERVICE,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_SERVICE, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerCallLogObserver() {
        try {
            val handler = Handler(Looper.getMainLooper())
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Timber.d("CallLogObserver: Call log change detected at: $uri")
                    // Trigger sync immediately with debouncing (via WorkManager unique replace)
                    triggerSync()
                }
            }
            
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver!!
            )
            Timber.i("ContentObserver registered successfully for CallLog.Calls.CONTENT_URI")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register CallLog observer")
        }
    }

    private fun triggerSync() {
        val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
            .setInitialDelay(500, TimeUnit.MILLISECONDS) // 500ms delay to allow writing database details
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("call_log_sync")
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "call_log_sync",
            ExistingWorkPolicy.REPLACE, // REPLACE ensures we overwrite and debounce multiple changes
            request
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("CallLogService destroyed — Unregistering CallLog observer")
        callLogObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
    }
}
