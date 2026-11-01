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
     * Cash accounts only: when false this balance is "set aside" and is not
     * counted into total available money. Cards and main are always included.
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

    companion object {
        /** Ledger owner. Holds expenses, top-ups and children. */
        const val TYPE_MAIN = "MAIN"
        /** Cash balance inside a main account; may hold its own currency. */
        const val TYPE_CASH = "CASH"
        /** Credit card: its balance is debt (negative) and never spendable. */
        const val TYPE_CREDIT_CARD = "CREDIT_CARD"
    }
}
