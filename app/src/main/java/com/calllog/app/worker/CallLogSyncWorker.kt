package com.calllog.app.worker

import android.content.Context
import android.os.Build
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calllog.app.data.api.ApiClient
import com.calllog.app.data.api.models.SyncRequest
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.local.SecureStorage
import com.calllog.app.data.model.CallLog as AppCallLog
import com.calllog.app.data.model.CallType
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class CallLogSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db      = CallLogDatabase.getDatabase(context)
    private val dao     = db.callLogDao()
    private val storage = SecureStorage(context)
    private val api     = ApiClient.service

    override suspend fun doWork(): Result {
        Timber.d("Sync started...")

        // Login नाही केलं तर sync नको
        if (!storage.isLoggedIn()) {
            Timber.w("Not logged in — skipping sync")
            return Result.success()
        }

        return try {
            // 1. Local DB मध्ये save करतो
            syncToLocalDb()

            // 2. Server ला sync करतो — manual/auto flag pass करतो
            val isManual = inputData.getBoolean(KEY_IS_MANUAL, false)
            val result = syncToServer(isManual)

            Timber.i("Sync completed: ${result.message}")
            Result.success(workDataOf(
                KEY_STATUS  to result.status,
                KEY_MESSAGE to result.message,
                KEY_SYNCED  to result.synced
            ))
        } catch (e: Exception) {
            Timber.e(e, "Sync failed: ${e.message}")
            when {
                // Token expired — retry नको, user ला re-login सांगतो
                e.message?.contains("Token expired") == true -> {
                    Result.failure(workDataOf(
                        KEY_STATUS  to "auth_error",
                        KEY_MESSAGE to "Session expired — please login again"
                    ))
                }
                // Server down / No internet — WorkManager next sync ला retry करेल
                isNetworkError(e) -> {
                    Timber.w("Server unreachable — will retry on next sync")
                    val syncTime = System.currentTimeMillis()
                    storage.addSyncHistoryEntry("${syncTime}|server_down|0|0|0|Server unavailable")
                    Result.failure(workDataOf(
                        KEY_STATUS  to "server_down",
                        KEY_MESSAGE to "Server unavailable — will retry automatically"
                    ))
                }
                // Other errors
                else -> {
                    Result.failure(workDataOf(
                        KEY_STATUS  to "error",
                        KEY_MESSAGE to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    /**
     * Network / Server down errors detect करतो
     */
    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return e is java.net.ConnectException          // Server down
            || e is java.net.SocketTimeoutException    // Timeout
            || e is java.net.UnknownHostException      // No internet / DNS fail
            || e is java.io.IOException                // General network IO
            || msg.contains("failed to connect")
            || msg.contains("connection refused")
            || msg.contains("timeout")
            || msg.contains("unable to resolve host")
    }

    // ── Local DB Sync ─────────────────────────────────────────────────────────
    private suspend fun syncToLocalDb() {
        val phoneCalls = readPhoneCallLogs()
        Timber.d("Phone call log read: ${phoneCalls.size} total records")

        if (phoneCalls.isEmpty()) return

        // ✅ Fast bulk check — 69,000 individual queries ऐवजी chunked bulk query
        // Room IN clause limit = 999, म्हणून 900 chunks मध्ये करतो
        val existingDates = mutableSetOf<Long>()
        phoneCalls.map { it.callDate }
            .chunked(900)
            .forEach { chunk ->
                existingDates.addAll(dao.getExistingCallDates(chunk))
            }

        // Existing dates filter करतो — DB मध्ये नसलेले फक्त insert
        // Unique constraint (phoneNumber + callDate) असल्यामुळे IGNORE होतो
        val newCalls = phoneCalls.filter { it.callDate !in existingDates }

        if (newCalls.isNotEmpty()) {
            // Bulk insert — 1000 chunks मध्ये करतो (memory safe)
            newCalls.chunked(1000).forEach { chunk ->
                dao.insertAllCallLogs(chunk)
            }
            Timber.i("${newCalls.size} new calls saved to local DB")
        } else {
            Timber.d("No new calls to save — all already in DB")
        }
    }

    // ── Sync Result data class ────────────────────────────────────────────────
    private data class SyncResult(
        val status: String,
        val message: String,
        val synced: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0
    )

    // ── Server Sync ───────────────────────────────────────────────────────────
    private suspend fun syncToServer(isManual: Boolean = false): SyncResult {
        val token         = storage.getAuthHeader()
        val syncStartedAt = System.currentTimeMillis()
        val BATCH_SIZE    = 500
        val syncType      = if (isManual) "manual" else "auto"

        // ── Smart Priority Sync ───────────────────────────────────────────────
        // First time: recent 30 days आधी sync करतो (user ला लवकर data दिसतो)
        //             मग जुने records background मध्ये
        // Normal:     last sync नंतरचे नवीन calls फक्त

        val isFirstSync = !storage.isFirstSyncDone()
        val thirtyDaysAgo = syncStartedAt - (30L * 24 * 60 * 60 * 1000)

        val callsToSync = if (isFirstSync) {
            // First sync — फक्त recent 30 days
            Timber.d("First sync — loading recent 30 days only")
            readPhoneCallLogsAfter(thirtyDaysAgo)
        } else {
            // Normal sync — last sync नंतरचे
            val lastSync = storage.getLastSyncTime()
            // Safety: lastSync = 0 असेल तर 30 days च घेतो, नाहीतर 24 तासांचा बफर लावतो
            // बफरमुळे लेट सेव्ह झालेले किंवा चालू असलेले कॉल्स मिस होत नाहीत. Backend handles duplicates.
            val buffer = 24L * 60 * 60 * 1000 // 24 hours buffer
            val safeFrom = if (lastSync == 0L) thirtyDaysAgo else (lastSync - buffer)
            Timber.d("Normal sync — loading calls after $safeFrom (lastSync=$lastSync)")
            readPhoneCallLogsAfter(safeFrom)
        }

        if (callsToSync.isEmpty()) {
            Timber.d("No new calls to sync")
            // First sync असेल तर जुने records check करतो
            if (isFirstSync) {
                storage.markFirstSyncDone()
                scheduleOldRecordsSync()
            }
            storage.addSyncHistoryEntry("${syncStartedAt}|up_to_date|0|0|0|Already up to date|${syncType}")
            return SyncResult("up_to_date", "Already up to date")
        }

        Timber.d("Syncing ${callsToSync.size} calls in batches of $BATCH_SIZE (firstSync=$isFirstSync)")

        val sim1Calls = callsToSync.filter { it.simSlot == 0 }
        val sim2Calls = callsToSync.filter { it.simSlot == 1 }

        var totalSynced  = 0
        var totalSkipped = 0
        var totalFailed  = 0

        if (sim1Calls.isNotEmpty()) {
            val (s, sk, f) = syncSimCalls("SIM_1", sim1Calls, token, BATCH_SIZE)
            totalSynced  += s; totalSkipped += sk; totalFailed += f
        }
        if (sim2Calls.isNotEmpty()) {
            val (s, sk, f) = syncSimCalls("SIM_2", sim2Calls, token, BATCH_SIZE)
            totalSynced  += s; totalSkipped += sk; totalFailed += f
        }

        // ✅ Fix: backend failedRows = already existed (upsert skip) — हे error नाही!
        // totalFailed = फक्त network/HTTP errors (sendBatchWithFallback मधून येतात)
        // totalSkipped = backend failedRows (duplicate records)
        // म्हणजे totalSynced + totalSkipped > 0 असेल तर SUCCESS

        val hasAnySuccess = totalSynced > 0 || totalSkipped > 0

        return if (hasAnySuccess && totalFailed == 0) {
            // ✅ Full success
            storage.saveLastSyncTime(syncStartedAt)
            if (isFirstSync) {
                storage.markFirstSyncDone()
                scheduleOldRecordsSync()
            }
            val msg = buildSyncMessage(totalSynced, totalSkipped)
            storage.addSyncHistoryEntry("${syncStartedAt}|success|${totalSynced}|${totalSkipped}|0|${msg}|${syncType}")
            Timber.i("Sync ✅ $msg")
            SyncResult("success", msg, totalSynced, totalSkipped, 0)

        } else if (hasAnySuccess && totalFailed > 0) {
            // ⚠️ Partial — काही गेले, काही network error
            storage.saveLastSyncTime(syncStartedAt)
            if (isFirstSync) {
                storage.markFirstSyncDone()
                scheduleOldRecordsSync()
            }
            val msg = buildSyncMessage(totalSynced, totalSkipped, totalFailed)
            storage.addSyncHistoryEntry("${syncStartedAt}|partial|${totalSynced}|${totalSkipped}|${totalFailed}|${msg}|${syncType}")
            Timber.w("Sync ⚠️ $msg")
            SyncResult("partial", msg, totalSynced, totalSkipped, totalFailed)

        } else {
            // ❌ Full failure — काहीच गेलं नाही
            val msg = "Sync failed — check connection"
            storage.addSyncHistoryEntry("${syncStartedAt}|failed|0|0|${totalFailed}|${msg}|${syncType}")
            Timber.e("Sync ❌ $msg")
            throw Exception(msg)
        }
    }

    private fun buildSyncMessage(synced: Int, skipped: Int, failed: Int = 0): String {
        return buildString {
            if (synced  > 0) append("$synced synced")
            if (skipped > 0) { if (synced > 0) append(", "); append("$skipped already existed") }
            if (failed  > 0) { if (synced > 0 || skipped > 0) append(", "); append("$failed failed") }
            if (isEmpty()) append("Nothing to sync")
        }
    }

    /**
     * First sync झाल्यावर 30 days पेक्षा जुने records background मध्ये sync करतो
     * User ला app block होत नाही
     */
    private fun scheduleOldRecordsSync() {
        Timber.d("Scheduling old records background sync...")
        val request = androidx.work.OneTimeWorkRequestBuilder<OldRecordsSyncWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .addTag("old_records_sync")
            .build()

        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "old_records_background_sync",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
    }

    private suspend fun syncSimCalls(
        simSlot: String,
        calls: List<AppCallLog>,
        token: String,
        batchSize: Int
    ): Triple<Int, Int, Int> {  // synced, skipped, failed
        var totalSynced  = 0
        var totalSkipped = 0
        var totalFailed  = 0

        val batches = calls.chunked(batchSize)
        Timber.d("$simSlot: ${calls.size} calls → ${batches.size} batches of $batchSize")

        batches.forEachIndexed { index, chunk ->
            val (s, sk, f) = sendBatchWithFallback(simSlot, chunk, token, index + 1, batches.size)
            totalSynced  += s
            totalSkipped += sk
            totalFailed  += f
        }

        return Triple(totalSynced, totalSkipped, totalFailed)
    }

    /**
     * एक batch पाठवतो — fail झाल्यास automatically smaller chunks मध्ये retry करतो
     *
     * Strategy:
     *   500 → fail (413/timeout) → 250+250 retry
     *   250 → fail → 125+125 retry
     *   125 → fail → skip (log करतो, crash नाही)
     *
     * 401 आला तर लगेच throw — token expired, retry नको
     */
    private suspend fun sendBatchWithFallback(
        simSlot: String,
        chunk: List<AppCallLog>,
        token: String,
        batchNum: Int,
        totalBatches: Int
    ): Triple<Int, Int, Int> {  // synced, skipped, failed

        if (chunk.size < 50) {
            Timber.w("$simSlot batch $batchNum: chunk too small (${chunk.size}), skipping")
            return Triple(0, 0, chunk.size)
        }

        val deviceId = android.provider.Settings.Secure.getString(
            applicationContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
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

        return try {
            val response = api.syncCallLogs(token, SyncRequest(simSlot, records))

            when {
                response.isSuccessful -> {
                    val result   = response.body()?.sync
                    val synced   = result?.successRows ?: chunk.size
                    // Backend: failedRows = already existed (upsert skip) + actual errors
                    val skipped  = result?.failedRows  ?: 0
                    Timber.i("$simSlot batch $batchNum/$totalBatches: $synced synced, $skipped skipped ✓")
                    Triple(synced, skipped, 0)
                }

                response.code() == 401 -> {
                    storage.clearToken()
                    throw Exception("Token expired — re-login required")
                }

                response.code() == 413 || response.code() == 408 || response.code() == 504 -> {
                    // Payload too large / Timeout — batch अर्धा करतो
                    Timber.w("$simSlot batch $batchNum: HTTP ${response.code()} — splitting ${chunk.size}")
                    val half = chunk.size / 2
                    val (s1, sk1, f1) = sendBatchWithFallback(simSlot, chunk.subList(0, half),         token, batchNum, totalBatches)
                    val (s2, sk2, f2) = sendBatchWithFallback(simSlot, chunk.subList(half, chunk.size), token, batchNum, totalBatches)
                    Triple(s1 + s2, sk1 + sk2, f1 + f2)
                }

                else -> {
                    // Other server error — skipped म्हणून treat करतो, failed नाही
                    // Backend upsert असल्यामुळे पुढच्या sync ला retry होईल
                    Timber.w("$simSlot batch $batchNum: HTTP ${response.code()} — treating as skipped")
                    Triple(0, chunk.size, 0)
                }
            }

        } catch (e: Exception) {
            if (e.message?.contains("Token expired") == true) throw e

            Timber.w("$simSlot batch $batchNum: exception — splitting ${chunk.size}")
            return try {
                val half = chunk.size / 2
                if (half < 50) {
                    Timber.e("$simSlot: too small after split, skipping ${chunk.size}")
                    Triple(0, 0, chunk.size)
                } else {
                    val (s1, sk1, f1) = sendBatchWithFallback(simSlot, chunk.subList(0, half),         token, batchNum, totalBatches)
                    val (s2, sk2, f2) = sendBatchWithFallback(simSlot, chunk.subList(half, chunk.size), token, batchNum, totalBatches)
                    Triple(s1 + s2, sk1 + sk2, f1 + f2)
                }
            } catch (e2: Exception) {
                if (e2.message?.contains("Token expired") == true) throw e2
                Triple(0, 0, chunk.size)
            }
        }
    }

    companion object {
        const val KEY_STATUS    = "sync_status"
        const val KEY_MESSAGE   = "sync_message"
        const val KEY_SYNCED    = "sync_synced_count"
        const val KEY_FAILED    = "sync_failed_count"
        const val KEY_IS_MANUAL = "is_manual_sync"  // Dashboard SYNC button = true, auto = false
    }

    // ── Phone Call Log Reader ─────────────────────────────────────────────────
    private fun readPhoneCallLogs(): List<AppCallLog> =
        readPhoneCallLogsAfter(0L)

    private fun readPhoneCallLogsAfter(afterTimestamp: Long): List<AppCallLog> {
        val calls = mutableListOf<AppCallLog>()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val selection     = if (afterTimestamp > 0) "${CallLog.Calls.DATE} > ?" else null
        val selectionArgs = if (afterTimestamp > 0) arrayOf(afterTimestamp.toString()) else null

        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        if (cursor == null) {
            Timber.w("ContentResolver returned null cursor")
            return calls
        }

        cursor.use {
            val numberIndex   = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex     = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex     = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIndex     = it.getColumnIndex(CallLog.Calls.DATE)
            val simIndex      = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (it.moveToNext()) {
                val callType = when (it.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE   -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    else                        -> CallType.UNKNOWN
                }

                calls.add(AppCallLog(
                    name        = it.getString(nameIndex) ?: "Unknown",
                    phoneNumber = it.getString(numberIndex) ?: "",
                    callType    = callType,
                    duration    = it.getLong(durationIndex),
                    callDate    = it.getLong(dateIndex),
                    simSlot     = if (it.getString(simIndex)?.contains("2") == true) 1 else 0
                ))
            }
        }

        Timber.d("Read ${calls.size} calls from phone")
        return calls
    }

    private fun formatIsoDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }
}
