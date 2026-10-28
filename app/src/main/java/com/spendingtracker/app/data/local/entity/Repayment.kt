package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Money paid back on a loan or debt. [amount] is always in the loan's currency.
 *
 * A repayment may be linked to a balance account (main or cash): settling a debt
 * then *takes money out of* that account, and settling a loan owed to me *puts
 * money into* it. [accountAmount] is the same payment expressed in the account's
 * currency ([accountCurrency]) at [exchangeRate] (1 unit of the loan currency =
 * [exchangeRate] units of the account currency), so each ledger stays in its own
 * currency even when the loan and the account differ.
 *
 * `accountId == null` is the default, **external revenue**: the repayment is a
 * loan transaction that does not touch any tracked balance. All historical
 * repayments are external, so upgrading never re-values an existing balance.
 */
@Entity(
    tableName = "repayments",
    foreignKeys = [
        ForeignKey(
            entity = LoanDebt::class,
            parentColumns = ["id"],
            childColumns = ["loanDebtId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Repayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanDebtId: Long,
    val amount: String, // BigDecimal in same currency as loan
    val date: Long,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    /** Balance account the money left / entered; null = external (no balance change). */
    val accountId: Long? = null,
    /** [amount] expressed in the linked account's currency; null when external. */
    val accountAmount: String? = null,
    val accountCurrency: String? = null,
    /** 1 unit of the loan currency in account-currency units; null when external. */
    val exchangeRate: String? = null
) {
    fun amountAsBigDecimal(): BigDecimal = BigDecimal(amount)

    /** True when this repayment never touched a tracked balance. */
    val isExternal: Boolean get() = accountId == null

    fun accountAmountAsBigDecimalOrNull(): BigDecimal? = accountAmount?.let {
        try { BigDecimal(it) } catch (e: Exception) { null }
    }

    fun rateAsBigDecimalOrNull(): BigDecimal? = exchangeRate?.let {
        try { BigDecimal(it) } catch (e: Exception) { null }
    }
}
