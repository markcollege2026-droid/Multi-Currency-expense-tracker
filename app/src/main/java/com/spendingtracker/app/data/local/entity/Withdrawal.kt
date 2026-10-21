package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A withdrawal out of a main balance into one of its cash accounts (ATM/cash
 * taken out). Both sides are stored in their own currency so each account's
 * ledger stays in its currency even when main and cash use different ones.
 *
 * [amountFrom] is deducted from [fromAccountId] in [currencyFrom];
 * [amountTo] is credited to [toAccountId] in [currencyTo] using
 * [exchangeRate] (1 unit of [currencyFrom] = [exchangeRate] units of [currencyTo]).
 */
@kotlinx.serialization.Serializable
@Entity(tableName = "withdrawals")
data class Withdrawal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountFrom: String, // BigDecimal in the source account currency
    val amountTo: String, // BigDecimal in the target account currency
    val currencyFrom: String,
    val currencyTo: String,
    val exchangeRate: String, // BigDecimal
    val date: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountFromAsBigDecimal(): BigDecimal = try {
        BigDecimal(amountFrom)
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    fun amountToAsBigDecimal(): BigDecimal = try {
        BigDecimal(amountTo)
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    fun rateAsBigDecimal(): BigDecimal = try {
        BigDecimal(exchangeRate)
    } catch (e: Exception) {
        BigDecimal.ONE
    }
}
