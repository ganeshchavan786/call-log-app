package com.calllog.app.data.api.models

data class SyncResponse(
    val success: Boolean,
    val sync: SyncResult?,
    val ownership: Ownership?,
    val message: String?,
    val error: String?
) {
    data class SyncResult(
        val batchId: String,
        val totalRows: Int,
        val successRows: Int,
        val failedRows: Int,
        val syncedAt: String
    )

    data class Ownership(
        val organization: OrgInfo?,
        val employee: EmployeeInfo?,
        val sim: SimInfo?
    )

    data class OrgInfo(val id: String, val name: String)

    data class EmployeeInfo(
        val id: String,
        val name: String,
        val email: String,
        val uniqueCode: String
    )

    data class SimInfo(
        val slot: String,
        val ownNumber: String?,
        val deviceName: String?
    )
}
