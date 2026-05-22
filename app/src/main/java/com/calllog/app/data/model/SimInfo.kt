package com.calllog.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sim_details")
data class SimInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slotIndex: Int = 0,
    val operatorName: String = "",
    val phoneNumber: String = "",
    val countryIso: String = "",
    val networkType: String = "",
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
