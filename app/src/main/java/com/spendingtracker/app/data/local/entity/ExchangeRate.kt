package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "exchange_rates",
    indices = [Index(value = ["baseCurrency", "targetCurrency"], unique = true)]
)
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: String, // BigDecimal
    val timestamp: Long = System.currentTimeMillis()
) {
    fun rateAsBigDecimal(): BigDecimal = BigDecimal(rate)
}
