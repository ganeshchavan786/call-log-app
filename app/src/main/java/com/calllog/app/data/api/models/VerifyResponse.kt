package com.calllog.app.data.api.models

data class VerifyResponse(
    val success: Boolean,
    val token: String?,
    val identity: Identity?,
    val organization: Organization?,
    val registeredSIMs: List<RegisteredSIM>?,
    val syncConfig: SyncConfig?,
    val error: String?,
    val message: String?
) {
    data class Identity(
        val userId: String,
        val userName: String,
        val userEmail: String,
        val uniqueCode: String,
        val codeType: String  // "OWNER" or "EMPLOYEE"
    )

    data class Organization(
        val id: String,
        val name: String,
        val slug: String?,
        val timezone: String?,
        val role: String
    )

    data class RegisteredSIM(
        val simSlot: String,
        val phoneNumber: String?,
        val deviceName: String?,
        val isActive: Boolean,
        val lastSyncAt: String?,
        val totalSynced: Int
    )

    data class SyncConfig(
        val maxRecordsPerSync: Int,
        val syncIntervalMinutes: Long,
        val allowedSIMSlots: List<String>
    )
}
