package com.calllog.app.data.dao

import androidx.room.*
import com.calllog.app.data.model.SimInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface SimInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(simInfo: SimInfo)

    @Query("SELECT * FROM sim_details ORDER BY slotIndex ASC")
    fun getAllSims(): Flow<List<SimInfo>>

    @Query("SELECT * FROM sim_details WHERE slotIndex = :slot LIMIT 1")
    suspend fun getSimBySlot(slot: Int): SimInfo?

    @Query("DELETE FROM sim_details")
    suspend fun clearAll()
}
