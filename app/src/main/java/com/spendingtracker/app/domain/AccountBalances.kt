package com.spendingtracker.app.domain

import com.spendingtracker.app.data.local.entity.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Single source of truth: assets positive, card debt negative. No cached mutable balances. */
object AccountBalances {
    fun calculate(
        accounts: List<Account>,
        expenses: List<Expense>,
        income: List<TopUp>,
        payments: List<CreditCardPayment>,
        withdrawals: List<Withdrawal> = emptyList()
    ): Map<Long, BigDecimal> =
        accounts.associate { account ->
            val incoming = income.filter { it.accountId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + t.amountAsBigDecimal() }
            val spent = expenses.filter { (it.balanceAccountId ?: it.accountId) == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + t.convertedAsBigDecimal() }
            val completed = payments.filter { it.status == "COMPLETED" }
            val paidToCard = completed.filter { it.cardId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + BigDecimal(t.amount) }
            val paidFromMain = completed.filter { it.sourceAccountId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + BigDecimal(t.amount) }
            // A withdrawal leaves the main balance in its currency and lands on the
            // cash balance in the cash account's currency (they may differ).
            val withdrawn = withdrawals.filter { it.fromAccountId == account.id }.fold(BigDecimal.ZERO) { sum, w -> sum + w.amountFromAsBigDecimal() }
            val deposited = withdrawals.filter { it.toAccountId == account.id }.fold(BigDecimal.ZERO) { sum, w -> sum + w.amountToAsBigDecimal() }
            account.id to (account.startingBalanceAsBigDecimal() + incoming - spent + paidToCard - paidFromMain - withdrawn + deposited)
        }

    fun paymentAmount(card: Account, balance: BigDecimal): BigDecimal {
        val debt = balance.negate().max(BigDecimal.ZERO)
        return if (card.autopayMode == "FIXED_AMOUNT") BigDecimal(card.autopayFixedAmount ?: "0").min(debt) else debt
    }

    fun dueDate(card: Account, month: YearMonth): LocalDate = month.atDay(card.autopayDay.coerceIn(1, month.lengthOfMonth()))
}
