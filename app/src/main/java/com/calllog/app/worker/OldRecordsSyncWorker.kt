package com.calllog.app.worker

import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calllog.app.data.api.ApiClient
import com.calllog.app.data.api.models.SyncRequest
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.data.model.CallLog as AppCallLog
import com.calllog.app.data.model.CallType
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * OldRecordsSyncWorker — First sync नंतर 30 days पेक्षा जुने records
 * background मध्ये हळूहळू sync करतो.
 *
 * User ला app block होत नाही — background मध्ये चालतो.
 * Batch size 200 — server वर कमी load.
 */
class OldRecordsSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val storage = SecureStorage(context)
    private val api     = ApiClient.service

    override suspend fun doWork(): Result {
        Timber.d("OldRecordsSyncWorker started — syncing old records in background")

        if (!storage.isLoggedIn()) return Result.success()

        return try {
            val token         = storage.getAuthHeader()
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

            // 30 days पेक्षा जुने records — oldest first
            val oldCalls = readOldCallLogs(olderThan = thirtyDaysAgo)

            if (oldCalls.isEmpty()) {
                Timber.d("No old records to sync")
                return Result.success()
            }

            Timber.d("Old records background sync: ${oldCalls.size} calls")

            val BATCH_SIZE = 200  // Background मध्ये smaller batch — server friendly
            var totalSynced = 0

            val isSim1Reg = storage.isSim1Registered()
            val isSim2Reg = storage.isSim2Registered()

            val sim1 = if (isSim1Reg) oldCalls.filter { it.simSlot == 0 } else emptyList()
            val sim2 = if (isSim2Reg) oldCalls.filter { it.simSlot == 1 } else emptyList()

            if (sim1.isNotEmpty()) totalSynced += syncOldCalls("SIM_1", sim1, token, BATCH_SIZE)
            if (sim2.isNotEmpty()) totalSynced += syncOldCalls("SIM_2", sim2, token, BATCH_SIZE)

            Timber.i("Old records background sync complete: $totalSynced synced")
            Result.success(workDataOf("old_synced" to totalSynced))

        } catch (e: Exception) {
            Timber.e(e, "OldRecordsSyncWorker failed: ${e.message}")
            // Retry नाही — next app start ला पुन्हा होईल
            Result.failure()
        }
    }

    private suspend fun syncOldCalls(
        simSlot: String,
        calls: List<AppCallLog>,
        token: String,
        batchSize: Int
    ): Int {
        var synced = 0
        calls.chunked(batchSize).forEachIndexed { i, chunk ->
            try {
                val deviceId = Settings.Secure.getString(
                    applicationContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val records = chunk.map { call ->
                    SyncRequest.CallRecord(
                        mobileNumber = call.phoneNumber,
                        contactName  = if (call.name != "Unknown") call.name else null,
                        callType     = when (call.callType) {
                            CallType.INCOMING -> "INCOMING"
                            CallType.OUTGOING -> "OUTGOING"
                            CallType.MISSED   -> "MISSED"
                            CallType.REJECTED -> "REJECTED"
                            else              -> "INCOMING"
                        },
                        date         = formatIsoDate(call.callDate),
                        duration     = call.duration.toInt(),
                        simSlot      = simSlot,
                        deviceName   = Build.MODEL,
                        deviceId     = deviceId
                    )
                }
                val response = api.syncCallLogs(token, SyncRequest(simSlot, records))
                if (response.isSuccessful) {
                    synced += response.body()?.sync?.successRows ?: chunk.size
                    Timber.d("Old $simSlot batch ${i + 1}: ${chunk.size} sent ✓")
                } else if (response.code() == 401) {
                    storage.clearToken()
                    return synced
                }
            } catch (e: Exception) {
                Timber.w("Old $simSlot batch ${i + 1} failed: ${e.message}")
                // Skip करतो — पुढचा batch try करतो
            }
        }
        return synced
    }

    private fun readOldCallLogs(olderThan: Long): List<AppCallLog> {
        val calls = mutableListOf<AppCallLog>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        // 30 days पेक्षा जुने — oldest first
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.DATE} < ?",
            arrayOf(olderThan.toString()),
            "${CallLog.Calls.DATE} ASC"
        ) ?: return calls

        cursor.use {
            val numIdx  = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val durIdx  = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val simIdx  = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (it.moveToNext()) {
                val callType = when (it.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE   -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    else                        -> CallType.UNKNOWN
                }
                calls.add(AppCallLog(
                    name        = it.getString(nameIdx) ?: "Unknown",
                    phoneNumber = it.getString(numIdx)  ?: "",
                    callType    = callType,
                    duration    = it.getLong(durIdx),
                    callDate    = it.getLong(dateIdx),
                    simSlot     = com.calllog.app.util.SimDetailsManager.getSimSlotFromAccountId(applicationContext, it.getString(simIdx))
                ))
            }
        }

        Timber.d("Old records: ${calls.size} calls older than 30 days")
        return calls
    }

    private fun formatIsoDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }
}
