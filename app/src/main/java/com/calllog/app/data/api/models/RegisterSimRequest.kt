package com.calllog.app.data.api.models

data class RegisterSimRequest(
    val simSlot: String,       // "SIM_1" or "SIM_2"
    val phoneNumber: String,
    val deviceName: String?,
    val deviceId: String
)
