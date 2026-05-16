package com.calllog.app.data.database

import androidx.room.TypeConverter
import com.calllog.app.data.model.CallType

/**
 * Room TypeConverters — CallType enum ला String म्हणून DB मध्ये store करतो.
 * यामुळे DAO queries मध्ये 'INCOMING', 'OUTGOING' etc. strings directly वापरता येतात.
 */
class Converters {

    @TypeConverter
    fun fromCallType(callType: CallType): String = callType.name

    @TypeConverter
    fun toCallType(value: String): CallType = CallType.valueOf(value)
}
