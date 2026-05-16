package com.calllog.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CallLog Entity — Represents a table in the Room Database.
 * Records are retained even if deleted from the system call log.
 */
@Entity(tableName = "call_logs")
data class CallLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "Unknown",          // Contact name if saved
    val phoneNumber: String,               // Phone number
    val callType: CallType,                // INCOMING / OUTGOING / MISSED / REJECTED
    val duration: Long = 0L,              // Duration in seconds
    val callDate: Long,                    // Timestamp in milliseconds
    val simSlot: Int = 0,                  // 0 = SIM 1, 1 = SIM 2

    // Flag to indicate if the record was deleted from the system phone log
    val isDeletedFromPhone: Boolean = false,

    val syncedAt: Long = System.currentTimeMillis() // Timestamp when saved to local DB
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    UNKNOWN
}
