package com.calllog.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CallLog Entity — Represents a table in the Room Database.
 * Records are retained even if deleted from the system call log.
 *
 * Unique constraint on (phoneNumber + callDate) — duplicate insert automatically ignored
 */
@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["phoneNumber", "callDate"], unique = true)]
)
data class CallLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "Unknown",
    val phoneNumber: String,
    val callType: CallType,
    val duration: Long = 0L,
    val callDate: Long,
    val simSlot: Int = 0,

    val isDeletedFromPhone: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    UNKNOWN
}
