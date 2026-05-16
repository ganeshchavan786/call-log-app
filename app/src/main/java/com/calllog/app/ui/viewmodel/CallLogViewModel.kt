package com.calllog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.CallType
import com.calllog.app.repository.CallLogRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CallLogRepository

    init {
        val dao = CallLogDatabase.getDatabase(application).callLogDao()
        repository = CallLogRepository(dao)
    }

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected filter
    private val _selectedFilter = MutableStateFlow<CallType?>(null)
    val selectedFilter: StateFlow<CallType?> = _selectedFilter.asStateFlow()

    // Sort order
    enum class SortOrder { NEWEST, OLDEST, LONGEST }
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Main call list — search + filter + sort combined
    // 69k+ records असल्यास crash टाळण्यासाठी 500 limit
    val callLogs: StateFlow<List<CallLog>> = combine(
        _searchQuery,
        _selectedFilter,
        _sortOrder,
        repository.allCalls
    ) { query, filter, sort, calls ->
        var result = calls
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.phoneNumber.contains(query)
            }
        }
        if (filter != null) {
            result = result.filter { it.callType == filter }
        }
        result = when (sort) {
            SortOrder.NEWEST  -> result.sortedByDescending { it.callDate }
            SortOrder.OLDEST  -> result.sortedBy { it.callDate }
            SortOrder.LONGEST -> result.sortedByDescending { it.duration }
        }
        // Performance: DB level वर आधीच 500 limit आहे
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard data
    val missedCallCount: StateFlow<Int> = repository.missedCallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val incomingCallCount: StateFlow<Int> = repository.incomingCallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val outgoingCallCount: StateFlow<Int> = repository.outgoingCallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val rejectedCallCount: StateFlow<Int> = repository.rejectedCallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCallCount: StateFlow<Int> = repository.totalCallCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentCalls: StateFlow<List<CallLog>> = repository.recentCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(type: CallType?) {
        _selectedFilter.value = type
    }

    fun onSortSelected(order: SortOrder) {
        _sortOrder.value = order
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}
