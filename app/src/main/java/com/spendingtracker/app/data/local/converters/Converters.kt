package com.spendingtracker.app.data.local.converters

import androidx.room.TypeConverter
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { 
            try { BigDecimal(it) } catch (e: Exception) { null }
        }
    }
}
