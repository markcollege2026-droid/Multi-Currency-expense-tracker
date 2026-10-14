package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val currency: String,
    val startingBalance: String, // BigDecimal
    val lowBalanceThreshold: String? = null, // BigDecimal
    val createdAt: Long = System.currentTimeMillis()
) {
    fun startingBalanceAsBigDecimal(): BigDecimal = try {
        BigDecimal(startingBalance)
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    fun thresholdAsBigDecimal(): BigDecimal? = lowBalanceThreshold?.let {
        try { BigDecimal(it) } catch (e: Exception) { null }
    }
}
