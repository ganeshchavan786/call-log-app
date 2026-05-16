package com.calllog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calllog.app.data.database.CallLogDatabase
import com.calllog.app.data.model.SimInfo
import com.calllog.app.util.SimDetailsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SimViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = CallLogDatabase.getDatabase(application).simInfoDao()

    val simList: StateFlow<List<SimInfo>> = dao.getAllSims()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshSimDetails()
    }

    fun refreshSimDetails() {
        viewModelScope.launch {
            val sims = SimDetailsManager.getSimDetails(getApplication())
            dao.clearAll()
            sims.forEach { dao.insertOrUpdate(it) }
        }
    }
}
