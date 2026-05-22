package com.calllog.app.data.api.models

data class SyncRequest(
    val simSlot: String,
    val records: List<CallRecord>
) {
    data class CallRecord(
        val mobileNumber: String,
        val contactName: String?,
        val callType: String,   // "INCOMING", "OUTGOING", "MISSED"
        val date: String,       // ISO 8601: "2024-01-15T10:30:00Z"
        val duration: Int,      // seconds
        val simSlot: String,
        val deviceName: String?,
        val deviceId: String
    )
}
