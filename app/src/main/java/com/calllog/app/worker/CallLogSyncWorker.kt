package com.calllog.app.worker

import android.content.Context
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.model.CallLog as AppCallLog
import com.calllog.app.data.model.CallType
import timber.log.Timber

/**
 * CallLogSyncWorker — Phone call logs Room DB मध्ये sync करतो.
 * Phone वरून delete झाले तरी records retain होतात.
 */
class CallLogSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db  = CallLogDatabase.getDatabase(context)
    private val dao = db.callLogDao()

    override suspend fun doWork(): Result {
        Timber.d("Sync started...")
        return try {
            syncCallLogs()
            Timber.i("Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Sync failed")
            Result.retry()
        }
    }

    private suspend fun syncCallLogs() {
        val phoneCalls = readPhoneCallLogs()
        Timber.d("Phone call log read: ${phoneCalls.size} total records")

        val newCalls = phoneCalls.filter { call ->
            dao.findCallByNumberAndDate(call.phoneNumber, call.callDate) == null
        }

        if (newCalls.isNotEmpty()) {
            dao.insertAllCallLogs(newCalls)
            Timber.i("${newCalls.size} new calls saved to DB")
        } else {
            Timber.d("No new calls to save")
        }

        markDeletedCalls(phoneCalls)
    }

    private fun readPhoneCallLogs(): List<AppCallLog> {
        val calls = mutableListOf<AppCallLog>()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        if (cursor == null) {
            Timber.w("ContentResolver returned null cursor — permission missing?")
            return calls
        }

        cursor.use {
            val nameIndex     = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIndex   = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex     = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIndex     = it.getColumnIndex(CallLog.Calls.DATE)
            val simIndex      = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (it.moveToNext()) {
                val rawType  = it.getInt(typeIndex)
                val callType = when (rawType) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE   -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    else -> {
                        Timber.w("Unknown call type: $rawType")
                        CallType.UNKNOWN
                    }
                }

                calls.add(
                    AppCallLog(
                        name        = it.getString(nameIndex) ?: "Unknown",
                        phoneNumber = it.getString(numberIndex) ?: "",
                        callType    = callType,
                        duration    = it.getLong(durationIndex),
                        callDate    = it.getLong(dateIndex),
                        simSlot     = if (it.getString(simIndex)?.contains("2") == true) 1 else 0
                    )
                )
            }
        }

        Timber.d("Parsed ${calls.size} calls from cursor")
        return calls
    }

    private suspend fun markDeletedCalls(currentPhoneCalls: List<AppCallLog>) {
        // TODO: Phase 2 — DB मधले calls phone वर नसतील तर isDeletedFromPhone = true
        Timber.d("markDeletedCalls: ${currentPhoneCalls.size} phone records checked")
    }
}
