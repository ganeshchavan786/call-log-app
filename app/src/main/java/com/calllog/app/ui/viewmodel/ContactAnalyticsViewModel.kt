package com.calllog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ContactAnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = CallLogDatabase.getDatabase(application).callLogDao()

    // ── Date Range Filter ────────────────────────────────────────────────────
    enum class DateRange { ALL_TIME, TODAY, THIS_WEEK, THIS_MONTH }

    private val _dateRange = MutableStateFlow(DateRange.ALL_TIME)
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")

    // ── Calls list (filtered by date range) ─────────────────────────────────
    val calls: StateFlow<List<CallLog>> = combine(_phoneNumber, _dateRange) { number, range ->
        Pair(number, range)
    }.flatMapLatest { (number, range) ->
        if (number.isBlank()) return@flatMapLatest flowOf(emptyList())
        val (from, to) = getDateRangeMillis(range)
        if (from == 0L) dao.getCallsByNumber(number)
        else dao.getCallsByNumberAndDateRange(number, from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Analytics derived from calls ─────────────────────────────────────────
    data class ContactStats(
        val incoming:         Int  = 0,
        val outgoing:         Int  = 0,
        val missed:           Int  = 0,
        val rejected:         Int  = 0,
        val total:            Int  = 0,
        val incomingDuration: Long = 0L,
        val outgoingDuration: Long = 0L,
        val missedDuration:   Long = 0L,
        val rejectedDuration: Long = 0L,
        val totalDuration:    Long = 0L
    )

    val stats: StateFlow<ContactStats> = calls.map { list ->
        val incoming  = list.filter { it.callType == CallType.INCOMING }
        val outgoing  = list.filter { it.callType == CallType.OUTGOING }
        val missed    = list.filter { it.callType == CallType.MISSED }
        val rejected  = list.filter { it.callType == CallType.REJECTED }
        ContactStats(
            incoming         = incoming.size,
            outgoing         = outgoing.size,
            missed           = missed.size,
            rejected         = rejected.size,
            total            = list.size,
            incomingDuration = incoming.sumOf { it.duration },
            outgoingDuration = outgoing.sumOf { it.duration },
            missedDuration   = missed.sumOf { it.duration },
            rejectedDuration = rejected.sumOf { it.duration },
            totalDuration    = list.sumOf { it.duration }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactStats())

    // ── Public API ───────────────────────────────────────────────────────────
    fun init(phoneNumber: String) {
        _phoneNumber.value = phoneNumber
    }

    fun setDateRange(range: DateRange) {
        _dateRange.value = range
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun getDateRangeMillis(range: DateRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (range) {
            DateRange.ALL_TIME  -> Pair(0L, 0L)
            DateRange.TODAY -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            DateRange.THIS_WEEK -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
            DateRange.THIS_MONTH -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                Pair(start, now)
            }
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
