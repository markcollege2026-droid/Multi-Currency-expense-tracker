package com.spendingtracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["accountId"])]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val accountId: Long = 1,
    val amountOriginal: String, // BigDecimal as plain string
    val currencyOriginal: String,
    val amountConverted: String, // in the account's balance currency
    val currencyConverted: String,
    val exchangeRateUsed: String, // rate used for conversion
    val categoryId: Long?,
    val date: Long,
    val note: String?,
    val paymentMethod: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val balanceAccountId: Long? = null
) {
    fun originalAsBigDecimal(): BigDecimal = BigDecimal(amountOriginal)
    fun convertedAsBigDecimal(): BigDecimal = BigDecimal(amountConverted)
    fun rateAsBigDecimal(): BigDecimal = BigDecimal(exchangeRateUsed)
}
