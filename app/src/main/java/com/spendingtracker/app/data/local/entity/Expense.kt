package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountOriginal: String, // BigDecimal as plain string
    val currencyOriginal: String,
    val amountConverted: String, // in balance currency
    val currencyConverted: String,
    val exchangeRateUsed: String, // rate used for conversion
    val categoryId: Long?,
    val date: Long,
    val note: String?,
    val paymentMethod: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun originalAsBigDecimal(): BigDecimal = BigDecimal(amountOriginal)
    fun convertedAsBigDecimal(): BigDecimal = BigDecimal(amountConverted)
    fun rateAsBigDecimal(): BigDecimal = BigDecimal(exchangeRateUsed)
}
