package com.spendingtracker.app.domain

import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal

/**
 * Which balances a loan settlement may move money through, and how they read.
 *
 * Loans are deliberately shared across every main account (Settings says "loans
 * are shared"), so a settlement must be able to pay from - or receive into -
 * **any** main account or cash balance in the database, not only the ones that
 * belong to the account that happens to be active. The old filter in
 * `LoansViewModel` kept the active main plus its own cash children, so a user
 * with two accounts could never settle a loan through the other one.
 *
 * Credit cards are excluded on purpose: they hold debt, not spendable money, and
 * `FinanceRepository.recordRepayment` rejects them.
 */
object SettlementAccounts {

    /** A MAIN or CASH balance - the only accounts a repayment may be linked to. */
    fun isSettleable(account: Account): Boolean =
        account.type == Account.TYPE_MAIN || account.type == Account.TYPE_CASH

    /**
     * Every settleable balance, ordered so the current ledger comes first:
     * the active main account, then its own cash balances, then the other main
     * accounts, then their cash balances. Within a group the order is creation
     * order with id as the tie-break, so the dropdown is stable.
     */
    fun options(accounts: List<Account>, activeAccountId: Long?): List<Account> {
        fun group(account: Account): Int = when {
            account.type == Account.TYPE_MAIN && account.id == activeAccountId -> 0
            account.type == Account.TYPE_MAIN -> 2
            account.parentAccountId == activeAccountId -> 1
            else -> 3
        }
        return accounts
            .filter(::isSettleable)
            .sortedWith(compareBy({ group(it) }, { it.createdAt }, { it.id }))
    }

    /**
     * One-line label for a settlement source/destination.
     *
     * A cash balance hanging off a *different* main account is prefixed with that
     * account's name, because two ledgers may each hold a balance simply called
     * "Cash" and the dropdown would otherwise show two identical rows. The own
     * balance of the active ledger keeps its short name, which is what existing
     * users are used to.
     */
    fun label(account: Account, all: List<Account>, balance: BigDecimal, activeAccountId: Long? = null): String {
        val parent = account.parentAccountId?.let { id -> all.firstOrNull { it.id == id } }
        val prefix = if (
            account.type == Account.TYPE_CASH &&
            parent != null &&
            parent.id != activeAccountId
        ) parent.name + " · " else ""
        val setAside = account.type == Account.TYPE_CASH && !account.includedInTotal
        return prefix + account.name + " (" + account.currency + ") · " +
            CurrencyFormatter.formatSimple(balance, account.currency) +
            if (setAside) " · set aside" else ""
    }
}
