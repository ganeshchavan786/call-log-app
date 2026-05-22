package com.calllog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calllog.app.data.api.ApiClient
import com.calllog.app.data.api.models.VerifyRequest
import com.calllog.app.data.local.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = SecureStorage(application)
    private val api = ApiClient.service

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(email: String, password: String, uniqueCode: String) {
        if (email.isBlank() || password.isBlank() || uniqueCode.isBlank()) {
            _state.value = LoginState.Error("Please fill all fields")
            return
        }

        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val response = api.verify(
                    VerifyRequest(
                        email      = email.trim(),
                        password   = password,
                        uniqueCode = uniqueCode.trim().uppercase()
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    Timber.i("Login success: ${body.identity?.userName}")

                    // Token save
                    body.token?.let { storage.saveToken(it) }

                    // Identity save
                    body.identity?.let {
                        storage.saveUserId(it.userId)
                        storage.saveUserName(it.userName)
                        storage.saveUserEmail(it.userEmail)
                        storage.saveCodeType(it.codeType)
                    }

                    // Organization save
                    body.organization?.let {
                        storage.saveOrgId(it.id)
                        storage.saveOrgName(it.name)
                        storage.saveRole(it.role)
                    }

                    // Sync config save
                    body.syncConfig?.let {
                        storage.saveSyncInterval(it.syncIntervalMinutes)
                        storage.saveMaxRecordsPerSync(it.maxRecordsPerSync)
                    }

                    // SIM registration status
                    body.registeredSIMs?.forEach { sim ->
                        when (sim.simSlot) {
                            "SIM_1" -> storage.saveSim1Registered(true)
                            "SIM_2" -> storage.saveSim2Registered(true)
                        }
                    }

                    _state.value = LoginState.Success

                } else {
                    val errorMsg = when (response.code()) {
                        401 -> {
                            val body = response.errorBody()?.string() ?: ""
                            if (body.contains("INVALID_CODE"))
                                "Invalid Code. Please check the exact code from your Web Dashboard."
                            else
                                "Invalid email or password."
                        }
                        403 -> "Account not linked to any organization. Contact admin."
                        400 -> "Please check all fields and try again."
                        else -> "Login failed. Please try again."
                    }
                    Timber.w("Login failed: ${response.code()}")
                    _state.value = LoginState.Error(errorMsg)
                }

            } catch (e: Exception) {
                Timber.e(e, "Login network error")
                _state.value = LoginState.Error("Network error. Check your connection.")
            }
        }
    }
}
