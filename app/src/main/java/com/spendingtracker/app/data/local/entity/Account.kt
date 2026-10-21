package com.spendingtracker.app.data.local.entity

import androidx.room.ColumnInfo
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
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'MAIN'") val type: String = "MAIN",
    val parentAccountId: Long? = null,
    val creditLimit: String? = null,
    @ColumnInfo(defaultValue = "0") val autopayEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val autopayDay: Int = 1,
    @ColumnInfo(defaultValue = "'FULL_BALANCE'") val autopayMode: String = "FULL_BALANCE",
    val autopayFixedAmount: String? = null,
    val autopayStartDate: String? = null,
    /**
     * Cash accounts only: when false the balance is kept "set aside" and is not
     * counted into total available money (main + cash − card debt).
     */
    @ColumnInfo(defaultValue = "1") val includedInTotal: Boolean = true
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
