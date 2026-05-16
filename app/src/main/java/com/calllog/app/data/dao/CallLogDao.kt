package com.calllog.app.data.dao

import androidx.room.*
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import kotlinx.coroutines.flow.Flow

/**
 * CallLog DAO — Handles all database operations for call logs.
 */
@Dao
interface CallLogDao {

    // Saves a new call log entry
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallLog(callLog: CallLog): Long

    // Bulk insert multiple call logs
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCallLogs(callLogs: List<CallLog>): List<Long>

    // Retrieves all call logs including those deleted from the phone
    @Query("SELECT * FROM call_logs ORDER BY callDate DESC")
    fun getAllCallLogs(): Flow<List<CallLog>>

    // Retrieves all call logs with limit — performance साठी
    @Query("SELECT * FROM call_logs ORDER BY callDate DESC LIMIT :limit")
    fun getAllCallLogsLimited(limit: Int = 500): Flow<List<CallLog>>

    // Retrieves active call logs that haven't been deleted from the phone
    @Query("SELECT * FROM call_logs WHERE isDeletedFromPhone = 0 ORDER BY callDate DESC")
    fun getActiveCalls(): Flow<List<CallLog>>

    // Retrieves call logs specifically marked as deleted from the phone
    @Query("SELECT * FROM call_logs WHERE isDeletedFromPhone = 1 ORDER BY callDate DESC")
    fun getDeletedFromPhoneCalls(): Flow<List<CallLog>>

    // Filter call logs by their type
    @Query("SELECT * FROM call_logs WHERE callType = :callType ORDER BY callDate DESC")
    fun getCallsByType(callType: CallType): Flow<List<CallLog>>

    // Search calls by number or contact name
    @Query("SELECT * FROM call_logs WHERE phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY callDate DESC")
    fun searchCalls(query: String): Flow<List<CallLog>>

    // Filter call logs by a specific date range
    @Query("SELECT * FROM call_logs WHERE callDate BETWEEN :startDate AND :endDate ORDER BY callDate DESC")
    fun getCallsByDateRange(startDate: Long, endDate: Long): Flow<List<CallLog>>

    // Check if a call log already exists to avoid duplication
    @Query("SELECT * FROM call_logs WHERE phoneNumber = :number AND callDate = :date LIMIT 1")
    suspend fun findCallByNumberAndDate(number: String, date: Long): CallLog?

    // Mark a call log entry as deleted from the phone
    @Query("UPDATE call_logs SET isDeletedFromPhone = 1 WHERE phoneNumber = :number AND callDate = :date")
    suspend fun markAsDeletedFromPhone(number: String, date: Long)

    // Statistics for the Dashboard
    @Query("SELECT COUNT(*) FROM call_logs WHERE callType = 'MISSED'")
    fun getMissedCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE callType = 'INCOMING'")
    fun getIncomingCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE callType = 'OUTGOING'")
    fun getOutgoingCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE callType = 'REJECTED'")
    fun getRejectedCallCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs")
    fun getTotalCallCount(): Flow<Int>

    // Retrieves recent call logs for the Dashboard
    @Query("SELECT * FROM call_logs ORDER BY callDate DESC LIMIT :limit")
    fun getRecentCalls(limit: Int = 10): Flow<List<CallLog>>

    // ── Contact Analytics ────────────────────────────────────────────────────

    // एका number चे सगळे calls
    @Query("SELECT * FROM call_logs WHERE phoneNumber = :number ORDER BY callDate DESC")
    fun getCallsByNumber(number: String): Flow<List<CallLog>>

    // एका number चे calls — date range filter सह
    @Query("SELECT * FROM call_logs WHERE phoneNumber = :number AND callDate BETWEEN :from AND :to ORDER BY callDate DESC")
    fun getCallsByNumberAndDateRange(number: String, from: Long, to: Long): Flow<List<CallLog>>

    // एका number चा total call count
    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :number")
    fun getCallCountByNumber(number: String): Flow<Int>

    // एका number चा type wise count
    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumber = :number AND callType = :type")
    fun getCallCountByNumberAndType(number: String, type: CallType): Flow<Int>

    // एका number चा total duration
    @Query("SELECT COALESCE(SUM(duration), 0) FROM call_logs WHERE phoneNumber = :number")
    fun getTotalDurationByNumber(number: String): Flow<Long>

    // एका number चा type wise total duration
    @Query("SELECT COALESCE(SUM(duration), 0) FROM call_logs WHERE phoneNumber = :number AND callType = :type")
    fun getDurationByNumberAndType(number: String, type: CallType): Flow<Long>
}
