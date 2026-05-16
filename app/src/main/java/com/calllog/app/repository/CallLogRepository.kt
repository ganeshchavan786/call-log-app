package com.calllog.app.repository

import com.calllog.app.data.dao.CallLogDao
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import kotlinx.coroutines.flow.Flow

/**
 * CallLogRepository — ViewModel आणि DB मधला bridge
 */
class CallLogRepository(private val dao: CallLogDao) {

    // 📋 सगळे calls (UI साठी) — DB level वर 500 limit, performance साठी
    val allCalls: Flow<List<CallLog>> = dao.getAllCallLogsLimited(500)

    // 📊 Dashboard counts
    val missedCallCount: Flow<Int> = dao.getMissedCallCount()
    val incomingCallCount: Flow<Int> = dao.getIncomingCallCount()
    val outgoingCallCount: Flow<Int> = dao.getOutgoingCallCount()
    val rejectedCallCount: Flow<Int> = dao.getRejectedCallCount()
    val totalCallCount: Flow<Int> = dao.getTotalCallCount()
    val recentCalls: Flow<List<CallLog>> = dao.getRecentCalls(10)

    // 🔍 Search
    fun searchCalls(query: String): Flow<List<CallLog>> = dao.searchCalls(query)

    // 📱 Filter by type
    fun getCallsByType(type: CallType): Flow<List<CallLog>> = dao.getCallsByType(type)

    // 📅 Date filter
    fun getCallsByDate(start: Long, end: Long): Flow<List<CallLog>> =
        dao.getCallsByDateRange(start, end)

    // 🗂 Phone वरून delete झालेले — backup मध्ये दाखवायला
    val deletedFromPhoneCalls: Flow<List<CallLog>> = dao.getDeletedFromPhoneCalls()
}
