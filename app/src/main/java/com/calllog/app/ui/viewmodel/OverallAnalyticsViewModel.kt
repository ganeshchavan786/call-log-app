package com.calllog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calllog.app.data.database.CallLogDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType

class OverallAnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = CallLogDatabase.getDatabase(application).callLogDao()

    // ── Date Range ────────────────────────────────────────────────────────────
    enum class DateRange { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, ALL_TIME }

    private val _dateRange = MutableStateFlow(DateRange.TODAY)
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    private val _callsList = MutableStateFlow<List<CallLog>>(emptyList())
    // Keep internal callsList for raw data
    
    private val _selectedCallType = MutableStateFlow<CallType?>(null)
    val selectedCallType: StateFlow<CallType?> = _selectedCallType.asStateFlow()

    // Expose filtered list
    val callsList: Flow<List<CallLog>> = combine(_callsList, _selectedCallType) { calls, type ->
        if (type == null) calls else calls.filter { it.callType == type }
    }

    private var callsJob: Job? = null

    // ── Stats ─────────────────────────────────────────────────────────────────
    data class OverallStats(
        val total:           Int  = 0,
        val incoming:        Int  = 0,
        val outgoing:        Int  = 0,
        val missed:          Int  = 0,
        val rejected:        Int  = 0,
        val uniqueNumbers:   Int  = 0,
        val totalDuration:   Long = 0L,
        val incomingDuration:Long = 0L,
        val outgoingDuration:Long = 0L,
        val isLoading:       Boolean = true
    )

    private val _stats = MutableStateFlow(OverallStats())
    val stats: StateFlow<OverallStats> = _stats.asStateFlow()

    init {
        // Date range बदलल्यावर stats reload करतो
        viewModelScope.launch {
            _dateRange.collect { range ->
                loadStats(range)
            }
        }
    }

    fun setDateRange(range: DateRange) {
        _dateRange.value = range
        // Reset filter on date range change
        _selectedCallType.value = null
    }

    fun setCallTypeFilter(type: CallType?) {
        _selectedCallType.value = type
    }

    private suspend fun loadStats(range: DateRange) {
        _stats.value = _stats.value.copy(isLoading = true)

        val (from, to) = getDateRangeMillis(range)

        val total    = dao.getTotalCallsInRange(from, to)
        val incoming = dao.getIncomingCountInRange(from, to)
        val outgoing = dao.getOutgoingCountInRange(from, to)
        val missed   = dao.getMissedCountInRange(from, to)
        val rejected = dao.getRejectedCountInRange(from, to)
        val unique   = dao.getUniqueNumbersCountInRange(from, to)
        val totalDur = dao.getTotalDurationInRange(from, to)
        val inDur    = dao.getIncomingDurationInRange(from, to)
        val outDur   = dao.getOutgoingDurationInRange(from, to)

        _stats.value = OverallStats(
            total            = total,
            incoming         = incoming,
            outgoing         = outgoing,
            missed           = missed,
            rejected         = rejected,
            uniqueNumbers    = unique,
            totalDuration    = totalDur,
            incomingDuration = inDur,
            outgoingDuration = outDur,
            isLoading        = false
        )

        callsJob?.cancel()
        callsJob = viewModelScope.launch {
            dao.getCallsByDateRange(from, to).collect { calls ->
                _callsList.value = calls
            }
        }
    }

    private fun getDateRangeMillis(range: DateRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (range) {
            DateRange.TODAY -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            DateRange.YESTERDAY -> {
                val start = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                Pair(start, end)
            }
            DateRange.THIS_WEEK -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            DateRange.THIS_MONTH -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            DateRange.ALL_TIME -> Pair(0L, now)
        }
    }

    companion object {
        fun formatDuration(seconds: Long): String {
            if (seconds == 0L) return "—"
            val h = TimeUnit.SECONDS.toHours(seconds)
            val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
            val s = seconds % 60
            return if (h > 0) "${h}h ${m}m ${s}s"
            else if (m > 0) "${m}m ${s}s"
            else "${s}s"
        }
    }
}
