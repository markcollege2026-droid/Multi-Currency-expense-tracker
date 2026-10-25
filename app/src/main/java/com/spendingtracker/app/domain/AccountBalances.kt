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
        withdrawals: List<Withdrawal> = emptyList(),
        repayments: List<Repayment> = emptyList(),
        loans: List<LoanDebt> = emptyList()
    ): Map<Long, BigDecimal> {
        // A repayment only moves a balance when it is linked to one. Paying a debt
        // ("I owe") takes money out of the linked account; being repaid a loan
        // ("owed to me") puts money into it. External repayments (accountId == null,
        // which includes every repayment recorded before settlements existed) and
        // repayments whose loan is unknown never touch a balance.
        val loanTypeById = loans.associate { it.id to it.type }
        val linkedRepayments = repayments.filter {
            it.accountId != null && it.accountAmountAsBigDecimalOrNull() != null &&
                loanTypeById[it.loanDebtId] != null
        }
        return accounts.associate { account ->
            val incoming = income.filter { it.accountId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + t.amountAsBigDecimal() }
            val spent = expenses.filter { (it.balanceAccountId ?: it.accountId) == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + t.convertedAsBigDecimal() }
            val completed = payments.filter { it.status == "COMPLETED" }
            val paidToCard = completed.filter { it.cardId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + BigDecimal(t.amount) }
            val paidFromMain = completed.filter { it.sourceAccountId == account.id }.fold(BigDecimal.ZERO) { sum, t -> sum + BigDecimal(t.amount) }
            // A transfer leaves the source balance in its currency and lands on the
            // target balance in the target's currency (they may differ). This holds
            // for main -> cash, cash -> main and cash -> cash alike: one row, both
            // sides, so setting a cash balance aside (includedInTotal = false) can
            // change the *total* but never makes either balance wrong.
            val withdrawn = withdrawals.filter { it.fromAccountId == account.id }
                .fold(BigDecimal.ZERO) { sum, w -> sum + w.amountFromAsBigDecimal() }
            val deposited = withdrawals.filter { it.toAccountId == account.id }
                .fold(BigDecimal.ZERO) { sum, w -> sum + w.amountToAsBigDecimal() }
            val loanMoneyOut = linkedRepayments
                .filter { it.accountId == account.id && loanTypeById[it.loanDebtId] == LoanDebt.TYPE_DEBT }
                .fold(BigDecimal.ZERO) { sum, r -> sum + (r.accountAmountAsBigDecimalOrNull() ?: BigDecimal.ZERO) }
            val loanMoneyIn = linkedRepayments
                .filter { it.accountId == account.id && loanTypeById[it.loanDebtId] == LoanDebt.TYPE_LOAN }
                .fold(BigDecimal.ZERO) { sum, r -> sum + (r.accountAmountAsBigDecimalOrNull() ?: BigDecimal.ZERO) }
            account.id to (account.startingBalanceAsBigDecimal() + incoming - spent + paidToCard - paidFromMain - withdrawn + deposited - loanMoneyOut + loanMoneyIn)
        }
    }

    /**
     * Total available money of one main account, expressed in the main currency:
     * the main balance plus its counted child balances. Cash balances that are set
     * aside ([Account.includedInTotal] = false) are excluded; credit cards always
     * count (negative = debt). [rateToMain] converts 1 unit of a child currency
     * into the main currency.
     *
     * Because a transfer is a single row credited to one balance and debited from
     * the other, moving money into a set-aside balance lowers this total by exactly
     * the transferred value and moving it back raises it again - the transfer
     * itself is the adjustment, never an expense or income row.
     */
    fun totalAvailable(
        accounts: List<Account>,
        balances: Map<Long, BigDecimal>,
        mainId: Long,
        rateToMain: (String) -> BigDecimal
    ): BigDecimal {
        val main = accounts.firstOrNull { it.id == mainId } ?: return BigDecimal.ZERO
        var total = balances[mainId] ?: BigDecimal.ZERO
        for (child in accounts.filter { it.parentAccountId == mainId }) {
            if (child.type == "CASH" && !child.includedInTotal) continue
            val balance = balances[child.id] ?: BigDecimal.ZERO
            total += if (child.currency == main.currency) balance
            else balance.multiply(rateToMain(child.currency))
        }
        return total
    }

    /** Whether a balance counts toward [totalAvailable]: main and cards always, cash only when not set aside. */
    fun countsTowardTotal(account: Account): Boolean =
        account.type != "CASH" || account.includedInTotal

    fun paymentAmount(card: Account, balance: BigDecimal): BigDecimal {
        val debt = balance.negate().max(BigDecimal.ZERO)
        return if (card.autopayMode == "FIXED_AMOUNT") BigDecimal(card.autopayFixedAmount ?: "0").min(debt) else debt
    }

    fun dueDate(card: Account, month: YearMonth): LocalDate = month.atDay(card.autopayDay.coerceIn(1, month.lengthOfMonth()))
}
