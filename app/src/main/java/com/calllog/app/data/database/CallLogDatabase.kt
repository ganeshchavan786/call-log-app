package com.calllog.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.calllog.app.data.dao.CallLogDao
import com.calllog.app.data.dao.SimInfoDao
import com.calllog.app.data.model.CallLog
import com.calllog.app.data.model.SimInfo

/**
 * Room Database — Main offline storage for the application.
 */
@Database(
    entities = [CallLog::class, SimInfo::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallLogDatabase : RoomDatabase() {

    abstract fun callLogDao(): CallLogDao
    abstract fun simInfoDao(): SimInfoDao

    companion object {
        @Volatile
        private var INSTANCE: CallLogDatabase? = null

        fun getDatabase(context: Context): CallLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallLogDatabase::class.java,
                    "call_log_database"
                )
                    .fallbackToDestructiveMigration() // Dev मध्ये OK — schema change handle करतो
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
