package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.*
import com.spendingtracker.app.data.local.entity.*
import androidx.room.withTransaction
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.domain.AccountBalances
import com.spendingtracker.app.domain.TransferMath
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import com.spendingtracker.app.util.CurrencyProvider

data class TransactionItem(
    val id: Long,
    val type: String, // "EXPENSE", "TOPUP", "CARD_PAYMENT", "WITHDRAWAL", "LOAN_REPAYMENT" or "LOAN_SETTLEMENT"
    val amountOriginal: BigDecimal,
    val currencyOriginal: String,
    val amountConverted: BigDecimal,
    val currencyConverted: String,
    val rateUsed: BigDecimal?,
    val categoryId: Long?,
    val date: Long,
    val note: String?,
    val paymentMethod: String?
)

/** Entry count and derived balance of one balance account. */
data class AccountSummary(val entries: Int, val balance: BigDecimal)

@Singleton
class FinanceRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val topUpDao: TopUpDao,
    private val settingsDao: AppSettingsDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val recurringPaymentDao: RecurringPaymentDao,
    private val paymentDao: CreditCardPaymentDao,
    private val withdrawalDao: WithdrawalDao,
    private val loanDebtDao: LoanDebtDao,
    private val repaymentDao: RepaymentDao,
    private val database: AppDatabase
) {
    // ---- Accounts ----
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts().map { list -> list.filter { it.type == "MAIN" } }
    suspend fun getAllAccountsSync(): List<Account> = accountDao.getAllAccountsSync()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)
    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)
    suspend fun getAccountCount(): Int = accountDao.getAllAccountsSync().count { it.type == "MAIN" }

    /**
     * Main accounts in a deterministic order. `ORDER BY createdAt` alone is not a
     * total order (accounts created in the same millisecond tie), and a tie used to
     * decide which ledger the app opened.
     */
    private fun List<Account>.mainAccountsInOrder(): List<Account> =
        filter { it.type == "MAIN" }.sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** True only when the app has no ledger at all - the one case that needs setup. */
    suspend fun needsSetup(): Boolean = accountDao.getAllAccountsSync().mainAccountsInOrder().isEmpty()

    /** The account currently selected in settings (falls back to the first account). */
    fun observeActiveAccount(): Flow<Account?> =
        combine(settingsDao.getSettings(), accountDao.getAllAccounts()) { settings, accounts ->
            val mains = accounts.mainAccountsInOrder()
            val activeId = settings?.activeAccountId
            mains.find { it.id == activeId } ?: mains.firstOrNull()
        }

    suspend fun getActiveAccountId(): Long = resolveActiveAccountId() ?: 1L

    /**
     * Returns the active main account id and repairs it when it points at nothing.
     *
     * `activeAccountId` can survive the row it referenced (an account deleted from
     * Settings, a restore, a stale whole-row settings write). Every screen filters
     * by this id, so a dangling pointer silently renders an empty ledger. This
     * resolves it to a real main account, persists the fix and clears a setup flag
     * that no longer matches reality.
     *
     * @return the active account id, or null when the app has no ledger yet.
     */
    suspend fun resolveActiveAccountId(): Long? = database.withTransaction {
        val mains = accountDao.getAllAccountsSync().mainAccountsInOrder()
        if (mains.isEmpty()) return@withTransaction null
        val saved = settingsDao.getSettingsSync()
        val resolved = mains.firstOrNull { it.id == saved?.activeAccountId } ?: mains.first()
        if (saved == null) {
            settingsDao.insertSettings(
                AppSettings(
                    balanceCurrency = resolved.currency,
                    startingBalance = resolved.startingBalance,
                    isSetupComplete = true,
                    activeAccountId = resolved.id
                )
            )
        } else {
            if (saved.activeAccountId != resolved.id) settingsDao.setActiveAccount(resolved.id)
            if (!saved.isSetupComplete) settingsDao.markSetupComplete()
        }
        resolved.id
    }

    /**
     * Makes [accountId] active. Creates the settings row when it is missing instead
     * of returning silently (the old behaviour made the account switcher a no-op
     * after an interrupted erase).
     */
    suspend fun setActiveAccount(accountId: Long) = database.withTransaction {
        val target = accountDao.getAccountById(accountId)
        require(target != null && target.type == "MAIN") { "Choose a main account" }
        val saved = settingsDao.getSettingsSync()
        if (saved == null) {
            settingsDao.insertSettings(
                AppSettings(
                    balanceCurrency = target.currency,
                    startingBalance = target.startingBalance,
                    isSetupComplete = true,
                    activeAccountId = target.id
                )
            )
        } else {
            // Targeted update: never rewrite the whole row from a stale snapshot.
            if (saved.activeAccountId != target.id) settingsDao.setActiveAccount(target.id)
            if (!saved.isSetupComplete) settingsDao.markSetupComplete()
        }
    }

    /**
     * Finishes first-run setup in ONE transaction.
     *
     * Reuses an existing main account instead of inserting a second one (a double
     * tap, or a setup screen reached while a ledger already exists, used to create
     * two accounts both called "Main"). The account, the active pointer, the
     * "setup complete" flag and the default categories commit together, so an app
     * kill can never leave an account the app refuses to open.
     *
     * @return the main account id
     */
    suspend fun completeSetup(accountName: String, currency: String, startingBalance: String): Long =
        database.withTransaction {
            val existing = accountDao.getAllAccountsSync().mainAccountsInOrder().firstOrNull()
            val accountId = existing?.id ?: accountDao.insertAccount(
                Account(
                    name = accountName.trim().ifBlank { "Main" },
                    currency = currency,
                    startingBalance = startingBalance
                )
            )
            val saved = settingsDao.getSettingsSync()
            if (saved == null) {
                settingsDao.insertSettings(
                    AppSettings(
                        balanceCurrency = currency,
                        startingBalance = startingBalance,
                        isSetupComplete = true,
                        activeAccountId = accountId
                    )
                )
            } else {
                settingsDao.setActiveAccount(accountId)
                settingsDao.markSetupComplete()
            }
            ensureDefaultCategories()
            accountId
        }

    /**
     * Deletes a balance together with everything scoped to it. Loans & debts are
     * shared between accounts and are deliberately kept.
     *
     * Guards, all inside the transaction:
     * - the last main account can never be deleted (that is what "all history gone,
     *   loans left" looked like);
     * - deleting a main account takes its cash/card children and the expenses paid
     *   from them, which otherwise survive with a dangling balanceAccountId;
     * - the active pointer is moved to a surviving main account, so the app cannot
     *   reopen on an account that no longer exists.
     *
     * @return the main account that is active afterwards, if any
     */
    suspend fun deleteAccountCascade(account: Account): Account? = database.withTransaction {
        val all = accountDao.getAllAccountsSync()
        if (account.type == "MAIN") {
            require(all.mainAccountsInOrder().size > 1) {
                "This is the only main account and holds the whole ledger. Create another account first."
            }
        }
        all.filter { it.parentAccountId == account.id }.forEach { child ->
            paymentDao.deleteForAccount(child.id)
            // Expenses paid from this balance belong to the ledger but only this row
            // references them; leaving them behind made money silently reappear.
            expenseDao.deleteForBalanceAccount(child.id)
            topUpDao.deleteForAccount(child.id)
            withdrawalDao.deleteInvolving(child.id)
            // Loans are shared and survive the account: their settlements become
            // external records instead of dangling references (or reopened loans).
            repaymentDao.unlinkAccount(child.id)
            accountDao.deleteAccount(child)
        }
        paymentDao.deleteForAccount(account.id)
        recurringPaymentDao.deleteForAccount(account.id)
        topUpDao.deleteForAccount(account.id)
        withdrawalDao.deleteInvolving(account.id)
        repaymentDao.unlinkAccount(account.id)
        // Expenses paid *from* this balance, not only those booked *on* it. Without
        // this the rows outlive their balance and quietly stop reducing anything.
        expenseDao.deleteForBalanceAccount(account.id)
        expenseDao.deleteForAccount(account.id)
        accountDao.deleteAccount(account)

        val remaining = accountDao.getAllAccountsSync().mainAccountsInOrder()
        val active = settingsDao.getSettingsSync()?.activeAccountId
        val survivor = remaining.firstOrNull { it.id == active } ?: remaining.firstOrNull()
        if (survivor != null && survivor.id != active) settingsDao.setActiveAccount(survivor.id)
        survivor
    }

    /**
     * How much data sits in each balance. Used to tell a genuinely empty account
     * apart from one that only *looks* empty because another account is active -
     * the confusion that made a healed ledger read as "all history wiped".
     */
    fun observeAccountSummaries(): Flow<Map<Long, AccountSummary>> = combine(
        accountDao.getAllAccounts(),
        expenseDao.getAllExpenses(),
        topUpDao.getAllTopUps(),
        paymentDao.observeAll(),
        combine(withdrawalDao.observeAll(), repaymentDao.observeAll(), loanDebtDao.getAllLoanDebts()) { withdrawals, repayments, loans ->
            Triple(withdrawals, repayments, loans)
        }
    ) { accounts, expenses, topUps, payments, loanLedger ->
        val (withdrawals, repayments, loans) = loanLedger
        val balances = AccountBalances.calculate(accounts, expenses, topUps, payments, withdrawals, repayments, loans)
        accounts.associate { account ->
            val entries = expenses.count { it.accountId == account.id } +
                topUps.count { it.accountId == account.id } +
                withdrawals.count { it.fromAccountId == account.id || it.toAccountId == account.id } +
                payments.count { it.cardId == account.id || it.sourceAccountId == account.id } +
                repayments.count { it.accountId == account.id }
            account.id to AccountSummary(entries, balances[account.id] ?: BigDecimal.ZERO)
        }
    }

    // ---- Transactions (account-scoped) ----
    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()
    fun getAllTopUps(): Flow<List<TopUp>> = topUpDao.getAllTopUps()
    fun getExpensesForAccount(accountId: Long): Flow<List<Expense>> = expenseDao.getExpensesForAccount(accountId)
    fun getTopUpsForAccount(accountId: Long): Flow<List<TopUp>> = topUpDao.getTopUpsForAccount(accountId)

    // ---- Settings / categories ----
    fun getSettings(): Flow<AppSettings?> = settingsDao.getSettings()
    fun getCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun getSettingsSync(): AppSettings? = settingsDao.getSettingsSync()
    suspend fun getAllExpensesSync(): List<Expense> = expenseDao.getAllExpensesSync()
    suspend fun getAllTopUpsSync(): List<TopUp> = topUpDao.getAllTopUpsSync()

    fun getTransactions(): Flow<List<TransactionItem>> {
        return combine(
            expenseDao.getAllExpenses(),
            topUpDao.getAllTopUps(),
            accountDao.getAllAccounts(),
            paymentDao.observeAll(),
            combine(withdrawalDao.observeAll(), repaymentDao.observeAll(), loanDebtDao.getAllLoanDebts()) { withdrawals, repayments, loans ->
                Triple(withdrawals, repayments, loans)
            }
        ) { expenses, topups, accounts, payments, loanLedger ->
            val (withdrawals, repayments, loans) = loanLedger
            val currencyByAccount = accounts.associate { it.id to it.currency }
            val expenseItems = expenses.map {
                TransactionItem(
                    id = it.id,
                    type = "EXPENSE",
                    amountOriginal = it.originalAsBigDecimal(),
                    currencyOriginal = it.currencyOriginal,
                    amountConverted = it.convertedAsBigDecimal(),
                    currencyConverted = it.currencyConverted,
                    rateUsed = it.rateAsBigDecimal(),
                    categoryId = it.categoryId,
                    date = it.date,
                    note = it.note,
                    paymentMethod = it.paymentMethod
                )
            }
            val topUpItems = topups.map {
                // Top-ups are stored in the account's balance currency.
                val accountCurrency = currencyByAccount[it.accountId] ?: ""
                TransactionItem(
                    id = it.id,
                    type = "TOPUP",
                    amountOriginal = it.amountAsBigDecimal(),
                    currencyOriginal = accountCurrency,
                    amountConverted = it.amountAsBigDecimal(),
                    currencyConverted = accountCurrency,
                    rateUsed = null,
                    categoryId = null,
                    date = it.date,
                    note = it.note,
                    paymentMethod = null
                )
            }
            val paymentItems = payments.map {
                val currency = currencyByAccount[it.sourceAccountId] ?: ""
                TransactionItem(it.id, "CARD_PAYMENT", BigDecimal(it.amount), currency, BigDecimal(it.amount), currency,
                    null, null, it.date, "Credit card payment · ${it.status}", "MAIN")
            }
            val withdrawalItems = withdrawals.map {
                TransactionItem(
                    id = it.id,
                    type = "WITHDRAWAL",
                    amountOriginal = it.amountFromAsBigDecimal(),
                    currencyOriginal = it.currencyFrom,
                    amountConverted = it.amountToAsBigDecimal(),
                    currencyConverted = it.currencyTo,
                    rateUsed = it.rateAsBigDecimal(),
                    categoryId = null,
                    date = it.date,
                    note = it.note ?: "Transfer between balances",
                    paymentMethod = "MAIN"
                )
            }
            // Settled loans (and their repayments) are transactions too: they are
            // listed here whether the money moved through a tracked balance or was
            // external revenue that never touched one.
            val loansById = loans.associateBy { it.id }
            val loanItems = repayments.mapNotNull { repayment ->
                val loan = loansById[repayment.loanDebtId] ?: return@mapNotNull null
                val settles = loan.settlementRepaymentId == repayment.id
                TransactionItem(
                    id = repayment.id,
                    type = if (settles) "LOAN_SETTLEMENT" else "LOAN_REPAYMENT",
                    amountOriginal = repayment.amountAsBigDecimal(),
                    currencyOriginal = loan.currencyOriginal,
                    amountConverted = repayment.accountAmountAsBigDecimalOrNull() ?: repayment.amountAsBigDecimal(),
                    currencyConverted = repayment.accountCurrency ?: loan.currencyOriginal,
                    rateUsed = repayment.rateAsBigDecimalOrNull(),
                    categoryId = null,
                    date = repayment.date,
                    note = (if (settles) "Loan settled · " else "Loan repayment · ") + loan.personName +
                        (repayment.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    paymentMethod = if (repayment.accountId == null) "EXTERNAL" else "ACCOUNT"
                )
            }
            (expenseItems + topUpItems + paymentItems + withdrawalItems + loanItems).sortedByDescending { it.date }
        }
    }

    suspend fun insertExpense(expense: Expense, allowOverdraft: Boolean = false): Long = database.withTransaction {
        validateExpense(expense, allowOverdraft)
        expenseDao.insertExpense(expense)
    }
    suspend fun updateExpense(expense: Expense, allowOverdraft: Boolean = false) = database.withTransaction {
        validateExpense(expense, allowOverdraft)
        expenseDao.updateExpense(expense)
    }
    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)
    suspend fun getExpenseById(id: Long): Expense? = expenseDao.getExpenseById(id)

    suspend fun insertTopUp(topUp: TopUp): Long = database.withTransaction {
        validateIncome(topUp)
        topUpDao.insertTopUp(topUp)
    }
    suspend fun updateTopUp(topUp: TopUp) = database.withTransaction {
        validateIncome(topUp)
        topUpDao.updateTopUp(topUp)
    }
    private suspend fun validateIncome(topUp: TopUp) {
        require(topUp.amountAsBigDecimal() > BigDecimal.ZERO) { "Income must be positive" }
        require(accountDao.getAccountById(topUp.accountId)?.type in listOf("MAIN", "CASH")) { "Income requires a main or cash account" }
    }
    suspend fun deleteTopUp(topUp: TopUp) = topUpDao.deleteTopUp(topUp)
    suspend fun getTopUpById(id: Long): TopUp? = topUpDao.getTopUpById(id)

    // ---- Transfers between balances (stored as Withdrawal rows) ----
    fun observeWithdrawals(): Flow<List<Withdrawal>> = withdrawalDao.observeAll()
    suspend fun getWithdrawalsSync(): List<Withdrawal> = withdrawalDao.getAllSync()

    /** Deleting a transfer returns the money to the source balance and removes it from the target. */
    suspend fun deleteWithdrawal(withdrawal: Withdrawal) = withdrawalDao.delete(withdrawal)

    suspend fun restoreWithdrawal(withdrawal: Withdrawal): Long = withdrawalDao.insert(withdrawal)

    /** The main account a balance belongs to (itself, for a main balance). */
    private fun Account.mainAccountId(): Long? = if (type == "MAIN") id else parentAccountId

    /**
     * Moves money between any two balances of the same main account - main → cash
     * (a withdrawal), cash → main (a deposit funded from cash) or **cash → cash**.
     * Credit cards can never take part: card debt is paid with [payCard].
     *
     * A transfer is stored as ONE row carrying both sides in their own
     * currencies. [domain.AccountBalances] debits the source by [Withdrawal.amountFrom]
     * and credits the target by [Withdrawal.amountTo] no matter which of them is
     * "set aside" (`includedInTotal = false`), so both balances stay accurate and
     * the total available moves by exactly the transferred value when money crosses
     * the included / set-aside boundary. The row itself is the adjustment - a
     * transfer is deliberately never recorded as an expense plus a deposit, which
     * would inflate spending and income totals.
     *
     * [rate] is 1 [from] unit expressed in the destination currency; when the
     * currencies match the ratio is stored as 1 and the amount moves unchanged.
     * [amountTo] may be supplied when the user typed the destination side, so the
     * stored row is exactly what they saw. The source balance is checked inside
     * the transaction, so a transfer can never overdraw the account it leaves.
     */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountFrom: BigDecimal,
        rate: BigDecimal,
        note: String? = null,
        amountTo: BigDecimal? = null,
        date: Long = System.currentTimeMillis()
    ): Withdrawal = database.withTransaction {
        require(amountFrom > BigDecimal.ZERO) { "Amount must be positive" }
        require(rate > BigDecimal.ZERO) { "Exchange rate must be positive" }
        val from = accountDao.getAccountById(fromAccountId) ?: error("Source account not found")
        val to = accountDao.getAccountById(toAccountId) ?: error("Destination account not found")
        require(from.id != to.id) { "Source and destination must differ" }
        // Both sides are main or cash balances of the same main account. This
        // allows cash -> cash while still refusing cards and other ledgers.
        val fromMain = from.mainAccountId()
        val toMain = to.mainAccountId()
        val validPair = from.type in listOf("MAIN", "CASH") && to.type in listOf("MAIN", "CASH") &&
            fromMain != null && fromMain == toMain
        require(validPair) { "Transfers move money between balances of the same main account" }

        val available = balances()[from.id] ?: BigDecimal.ZERO
        require(amountFrom <= available) {
            "Insufficient ${from.currency} balance (available ${available.toPlainString()})"
        }
        val sameCurrency = from.currency == to.currency
        val converted = when {
            sameCurrency -> amountFrom
            amountTo != null -> amountTo
            else -> amountFrom.multiply(rate).setScale(TransferMath.SCALE, java.math.RoundingMode.HALF_EVEN)
        }
        require(converted > BigDecimal.ZERO) { "Converted amount is too small" }

        val withdrawal = Withdrawal(
            fromAccountId = from.id,
            toAccountId = to.id,
            amountFrom = amountFrom.toPlainString(),
            amountTo = converted.toPlainString(),
            currencyFrom = from.currency,
            currencyTo = to.currency,
            exchangeRate = if (sameCurrency) BigDecimal.ONE.toPlainString() else rate.toPlainString(),
            date = date,
            note = note
        )
        // Return the stored row (with its generated id) so callers can delete it.
        withdrawal.copy(id = withdrawalDao.insert(withdrawal))
    }

    /**
     * Historical name for [transfer]: existing callers (withdraw to cash, deposits
     * funded from cash) keep working unchanged.
     */
    suspend fun withdraw(
        fromAccountId: Long,
        toAccountId: Long,
        amountFrom: BigDecimal,
        rate: BigDecimal,
        note: String? = null,
        amountTo: BigDecimal? = null
    ): Withdrawal = transfer(fromAccountId, toAccountId, amountFrom, rate, note, amountTo)

    // ---- Loans & debts: repayments and settlements ----
    fun observeLoans(): Flow<List<LoanDebt>> = loanDebtDao.getAllLoanDebts()
    fun observeRepayments(): Flow<List<Repayment>> = repaymentDao.observeAll()
    suspend fun getLoansSync(): List<LoanDebt> = loanDebtDao.getAllLoanDebtsSync()
    suspend fun getRepaymentsSync(): List<Repayment> = repaymentDao.getAllRepaymentsSync()

    /** What is still outstanding on a loan, in the loan's currency (never negative). */
    private suspend fun remainingOf(loan: LoanDebt): BigDecimal {
        val repaid = repaymentDao.getRepaymentsForLoanSync(loan.id)
            .fold(BigDecimal.ZERO) { sum, r -> sum + try { r.amountAsBigDecimal() } catch (e: Exception) { BigDecimal.ZERO } }
        val remaining = loan.amountAsBigDecimal() - repaid
        return if (remaining > BigDecimal.ZERO) remaining else BigDecimal.ZERO
    }

    /**
     * Records money paid back on a loan or debt, atomically with the balance it
     * came from / went to, and settles the loan when nothing remains.
     *
     * [amount] is in the *loan's* currency and may not exceed what is still owed.
     * [accountId] selects the balance the money moves through:
     * - a debt ("I owe") is paid *from* it, so its balance must cover the payment;
     * - a loan ("owed to me") is received *into* it, so no balance limit applies;
     * - null is the default, external revenue: the settlement is recorded as a
     *   transaction but no tracked balance changes.
     *
     * [rate] is 1 unit of the loan currency expressed in the account currency and
     * is ignored (stored as 1) when the currencies match or no account is linked.
     * When the loan is already fully repaid, [amount] may be zero and the loan is
     * simply moved to the settled archive.
     *
     * @return the stored repayment, or null when the loan was only archived.
     */
    suspend fun recordRepayment(
        loanId: Long,
        amount: BigDecimal,
        accountId: Long? = null,
        rate: BigDecimal = BigDecimal.ONE,
        note: String? = null,
        date: Long = System.currentTimeMillis()
    ): Repayment? = database.withTransaction {
        val loan = loanDebtDao.getLoanDebtById(loanId) ?: error("Loan not found")
        require(!loan.isSettled) { "This loan is already settled" }
        val remaining = remainingOf(loan)
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            // Fully repaid earlier but never marked settled: archive it without
            // inventing a payment that never happened.
            require(amount.compareTo(BigDecimal.ZERO) == 0) { "Nothing is left to pay on this loan" }
            loanDebtDao.updateLoanDebt(loan.copy(isSettled = true, settledAt = date, settlementRepaymentId = null))
            return@withTransaction null
        }
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(amount <= remaining) {
            "More than the remaining ${remaining.toPlainString()} ${loan.currencyOriginal}"
        }

        var accountAmount: BigDecimal? = null
        var accountCurrency: String? = null
        var storedRate: BigDecimal? = null
        if (accountId != null) {
            val account = accountDao.getAccountById(accountId) ?: error("Account not found")
            require(account.type in listOf("MAIN", "CASH")) { "Choose a main or cash balance" }
            require(rate > BigDecimal.ZERO) { "Exchange rate must be positive" }
            val sameCurrency = account.currency == loan.currencyOriginal
            val converted = if (sameCurrency) amount
            else amount.multiply(rate).setScale(TransferMath.SCALE, java.math.RoundingMode.HALF_EVEN)
            require(converted > BigDecimal.ZERO) { "Converted amount is too small" }
            if (loan.type == LoanDebt.TYPE_DEBT) {
                val available = balances()[account.id] ?: BigDecimal.ZERO
                require(converted <= available) {
                    "Insufficient ${account.currency} balance (available ${available.toPlainString()})"
                }
            }
            accountAmount = converted
            accountCurrency = account.currency
            storedRate = if (sameCurrency) BigDecimal.ONE else rate
        }

        val repayment = Repayment(
            loanDebtId = loan.id,
            amount = amount.toPlainString(),
            date = date,
            note = note,
            accountId = accountId,
            accountAmount = accountAmount?.toPlainString(),
            accountCurrency = accountCurrency,
            exchangeRate = storedRate?.toPlainString()
        )
        val stored = repayment.copy(id = repaymentDao.insertRepayment(repayment))
        if (amount.compareTo(remaining) == 0) {
            // Fully settled: the loan leaves the active list for the archive and
            // remembers which transaction closed it, so reopening can reverse it.
            loanDebtDao.updateLoanDebt(
                loan.copy(isSettled = true, settledAt = date, settlementRepaymentId = stored.id)
            )
        }
        stored
    }

    /** Marks an active loan settled by paying its whole remaining balance. */
    suspend fun settleLoan(
        loanId: Long,
        accountId: Long? = null,
        rate: BigDecimal = BigDecimal.ONE,
        note: String? = null,
        date: Long = System.currentTimeMillis()
    ): Repayment? = database.withTransaction {
        val loan = loanDebtDao.getLoanDebtById(loanId) ?: error("Loan not found")
        recordRepayment(loanId, remainingOf(loan), accountId, rate, note, date)
    }

    /**
     * Brings a settled loan back to the active list. The repayment that settled it
     * is deleted in the same transaction, which returns the settlement money to
     * the account it came from (or removes it from the account it was received
     * into) instead of leaving a settled amount with nowhere to go.
     */
    suspend fun reopenLoan(loanId: Long) = database.withTransaction {
        val loan = loanDebtDao.getLoanDebtById(loanId) ?: error("Loan not found")
        if (!loan.isSettled) return@withTransaction
        loan.settlementRepaymentId?.let { repaymentId ->
            repaymentDao.getRepaymentById(repaymentId)?.let { repaymentDao.deleteRepayment(it) }
        }
        loanDebtDao.updateLoanDebt(loan.copy(isSettled = false, settledAt = null, settlementRepaymentId = null))
    }

    /**
     * Deletes a repayment / settlement transaction. If it was the transaction that
     * settled its loan, the loan reopens - its remaining balance is owed again and
     * the money flows back through the linked account automatically.
     */
    suspend fun deleteRepayment(repayment: Repayment) = database.withTransaction {
        repaymentDao.deleteRepayment(repayment)
        val loan = loanDebtDao.getLoanDebtById(repayment.loanDebtId)
        if (loan != null && loan.settlementRepaymentId == repayment.id) {
            loanDebtDao.updateLoanDebt(loan.copy(isSettled = false, settledAt = null, settlementRepaymentId = null))
        }
    }

    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    /** Deletes a category and unlinks it from expenses that used it. */
    suspend fun deleteCategory(category: Category) {
        expenseDao.clearCategoryFromExpenses(category.id)
        categoryDao.deleteCategory(category)
    }

    suspend fun getAllCategoriesSync(): List<Category> = categoryDao.getAllCategoriesSync()

    suspend fun ensureDefaultCategories() {
        if (categoryDao.getCount() == 0) {
            categoryDao.insertCategories(Category.defaultCategories())
        }
    }

    suspend fun saveSettings(settings: AppSettings) = settingsDao.insertSettings(settings)
    suspend fun saveTheme(theme: String) = settingsDao.setTheme(theme)
    suspend fun saveRatesTimestamp(timestamp: Long) = settingsDao.setLastRatesUpdate(timestamp)
    suspend fun saveCurrencySortMode(mode: String) = settingsDao.setCurrencySortMode(mode)
    suspend fun saveFavoriteCurrencies(json: String?) = settingsDao.setFavoriteCurrencies(json)
    suspend fun saveCustomCurrencyOrder(json: String?, mode: String) = settingsDao.setCustomCurrencyOrder(json, mode)
    suspend fun saveRecentCurrencies(json: String?) = settingsDao.setRecentCurrencies(json)

    /**
     * Currencies in the order the user configured in Settings (sort mode,
     * custom order, favorites, recents, account currency first). Every picker
     * must use this - several used to fall back to the raw provider list, so
     * the currency-order setting had no effect there.
     */
    fun orderedCurrenciesFor(settings: AppSettings?): List<CurrencyProvider.CurrencyInfo> =
        CurrencyProvider.getOrderedCurrencies(
            sortMode = settings?.currencySortMode ?: "ALPHABETICAL",
            customOrderJson = settings?.customCurrencyOrder,
            favoriteJson = settings?.favoriteCurrencies,
            recentJson = settings?.recentCurrencies,
            balanceCurrency = settings?.balanceCurrency
        )

    suspend fun orderedCurrencies(): List<CurrencyProvider.CurrencyInfo> =
        orderedCurrenciesFor(getSettingsSync())

    /** Best-effort: remember a picked currency for the RECENT sort mode. */
    suspend fun recordRecentCurrency(code: String) {
        runCatching {
            val settings = getSettingsSync() ?: return
            saveRecentCurrencies(CurrencyProvider.addRecentCurrency(settings.recentCurrencies, code))
        }
    }

    /**
     * Rewrites the settings row for a fresh ledger. Must run inside the same
     * transaction that erased the data, otherwise an interrupted erase leaves
     * "history gone, loans kept, no settings row" - which boots into setup while
     * loans are still on screen.
     */
    suspend fun resetSettingsAfterErase() = settingsDao.insertSettings(
        AppSettings(balanceCurrency = "USD", startingBalance = "0", isSetupComplete = false)
    )

    suspend fun eraseAll() {
        paymentDao.deleteAll()
        recurringPaymentDao.deleteAll()
        expenseDao.deleteAll()
        topUpDao.deleteAll()
        withdrawalDao.deleteAll()
        accountDao.getAllAccountsSync().forEach { accountDao.deleteAccount(it) }
        // Keep settings structure but reset; categories are kept (defaults)
        settingsDao.deleteAll()
    }
    suspend fun <T> atomic(block: suspend () -> T): T = database.withTransaction { block() }
    suspend fun restoreExpense(expense: Expense): Long = expenseDao.insertExpense(expense)

    /** Called before committing a restore; malformed references or money roll back the whole import. */
    suspend fun clearCategoriesForRestore() {
        categoryDao.getAllCategoriesSync().forEach { categoryDao.deleteCategory(it) }
    }

    suspend fun validateRestoredLedger() {
        val accounts = accountDao.getAllAccountsSync()
        val byId = accounts.associateBy { it.id }
        require(accounts.any { it.type == "MAIN" }) { "Backup must contain a main account" }
        for (account in accounts) {
            BigDecimal(account.startingBalance)
            account.lowBalanceThreshold?.let { require(BigDecimal(it) >= BigDecimal.ZERO) }
            require(account.type in listOf("MAIN", "CASH", "CREDIT_CARD"))
            if (account.type == "MAIN") require(account.parentAccountId == null)
            else {
                val parent = byId[account.parentAccountId] ?: error("Missing balance parent")
                require(parent.type == "MAIN") { "Invalid balance parent" }
                // Cards debit main at face value so they must share its currency;
                // cash may live in another currency (withdrawals convert).
                if (account.type == "CREDIT_CARD") require(parent.currency == account.currency) { "Invalid balance currency" }
            }
            require(account.autopayDay in 1..31 && account.autopayMode in listOf("FULL_BALANCE", "FIXED_AMOUNT"))
            if (account.autopayEnabled && account.autopayMode == "FIXED_AMOUNT") require(BigDecimal(account.autopayFixedAmount ?: "0") > BigDecimal.ZERO)
            account.autopayStartDate?.let { LocalDate.parse(it) }
        }
        for (expense in expenseDao.getAllExpensesSync()) {
            val owner = byId[expense.accountId] ?: error("Missing expense owner")
            val target = byId[expense.balanceAccountId ?: expense.accountId] ?: error("Missing payment account")
            require(owner.type == "MAIN" && (target.id == owner.id || target.parentAccountId == owner.id)) { "Invalid expense account" }
            require(target.currency == expense.currencyConverted)
            require(expense.originalAsBigDecimal() > BigDecimal.ZERO && expense.convertedAsBigDecimal() >= BigDecimal.ZERO && expense.rateAsBigDecimal() > BigDecimal.ZERO)
        }
        topUpDao.getAllTopUpsSync().forEach { validateIncome(it) }
        for (withdrawal in withdrawalDao.getAllSync()) {
            val from = byId[withdrawal.fromAccountId] ?: error("Missing withdrawal source")
            val to = byId[withdrawal.toAccountId] ?: error("Missing withdrawal target")
            // Any two main/cash balances of one main account: main <-> cash and
            // cash <-> cash are all valid transfers; cards and foreign ledgers not.
            val validPair = from.type in listOf("MAIN", "CASH") && to.type in listOf("MAIN", "CASH") &&
                from.id != to.id && from.mainAccountId() != null && from.mainAccountId() == to.mainAccountId()
            require(validPair) { "Invalid withdrawal accounts" }
            require(withdrawal.currencyFrom == from.currency && withdrawal.currencyTo == to.currency) { "Invalid withdrawal currency" }
            require(
                withdrawal.amountFromAsBigDecimal() > BigDecimal.ZERO &&
                    withdrawal.amountToAsBigDecimal() >= BigDecimal.ZERO &&
                    withdrawal.rateAsBigDecimal() > BigDecimal.ZERO
            ) { "Invalid withdrawal amounts" }
        }
        for (payment in paymentDao.getAll()) {
            val card = byId[payment.cardId] ?: error("Missing card")
            require(card.type == "CREDIT_CARD" && card.parentAccountId == payment.sourceAccountId && byId[payment.sourceAccountId]?.type == "MAIN") { "Invalid payment accounts" }
            require(payment.status in listOf("COMPLETED", "FAILED_INSUFFICIENT_FUNDS") && BigDecimal(payment.amount) >= BigDecimal.ZERO)
            payment.autopayPeriod?.let { YearMonth.parse(it) }
        }
        // Loans are shared, but a settlement linked to a balance must reference a
        // real main/cash account in the currency it claims to have moved.
        val loansById = loanDebtDao.getAllLoanDebtsSync().associateBy { it.id }
        val repaymentsById = repaymentDao.getAllRepaymentsSync().associateBy { it.id }
        for (loan in loansById.values) {
            require(loan.type in listOf(LoanDebt.TYPE_DEBT, LoanDebt.TYPE_LOAN)) { "Invalid loan type" }
            require(loan.amountAsBigDecimal() > BigDecimal.ZERO) { "Invalid loan amount" }
            loan.settlementRepaymentId?.let { repaymentId ->
                val settlement = repaymentsById[repaymentId] ?: error("Missing settlement repayment")
                require(settlement.loanDebtId == loan.id) { "Settlement belongs to another loan" }
                require(loan.isSettled) { "Settlement recorded on an open loan" }
            }
        }
        for (repayment in repaymentsById.values) {
            loansById[repayment.loanDebtId] ?: error("Missing loan for repayment")
            require(repayment.amountAsBigDecimal() > BigDecimal.ZERO) { "Invalid repayment amount" }
            val linkedAccountId = repayment.accountId
            if (linkedAccountId != null) {
                val account = byId[linkedAccountId] ?: error("Missing settlement account")
                require(account.type in listOf("MAIN", "CASH")) { "Invalid settlement account" }
                val accountAmount = repayment.accountAmountAsBigDecimalOrNull() ?: error("Missing settlement account amount")
                require(accountAmount > BigDecimal.ZERO) { "Invalid settlement account amount" }
                require(repayment.accountCurrency == account.currency) { "Invalid settlement currency" }
                require((repayment.rateAsBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO) { "Invalid settlement rate" }
            }
        }
    }

    fun observeBalanceAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()
    fun observeCardPayments(): Flow<List<CreditCardPayment>> = paymentDao.observeAll()
    suspend fun getCardPayments(): List<CreditCardPayment> = paymentDao.getAll()
    suspend fun restoreCardPayment(payment: CreditCardPayment) = paymentDao.insert(payment)

    suspend fun balances(): Map<Long, BigDecimal> = AccountBalances.calculate(
        accountDao.getAllAccountsSync(), expenseDao.getAllExpensesSync(), topUpDao.getAllTopUpsSync(),
        paymentDao.getAll(), withdrawalDao.getAllSync(),
        repaymentDao.getAllRepaymentsSync(), loanDebtDao.getAllLoanDebtsSync())

    private suspend fun validateExpense(expense: Expense, allowOverdraft: Boolean) {
        require(expense.originalAsBigDecimal() > BigDecimal.ZERO && expense.convertedAsBigDecimal() > BigDecimal.ZERO && expense.rateAsBigDecimal() > BigDecimal.ZERO) { "Amount and exchange rate must be positive" }
        val main = accountDao.getAccountById(expense.accountId) ?: error("Account not found")
        val target = accountDao.getAccountById(expense.balanceAccountId ?: expense.accountId) ?: error("Payment account not found")
        require(target.id == main.id || target.parentAccountId == main.id) { "Payment account belongs to another account" }
        require(target.currency == expense.currencyConverted) { "Payment currency mismatch" }
        if (target.type == "CASH" && !allowOverdraft) {
            val remaining = AccountBalances.calculate(
                accountDao.getAllAccountsSync(),
                expenseDao.getAllExpensesSync().filter { it.id != expense.id },
                topUpDao.getAllTopUpsSync(),
                paymentDao.getAll(),
                withdrawalDao.getAllSync(),
                repaymentDao.getAllRepaymentsSync(),
                loanDebtDao.getAllLoanDebtsSync()
            )[target.id] ?: BigDecimal.ZERO
            require(expense.convertedAsBigDecimal() <= remaining) { "Insufficient cash. Explicitly allow overdraft to continue." }
        }
    }

    /**
     * Creates a CASH or CREDIT_CARD child of [mainId]. Cash accounts may declare
     * their own [currency] - withdrawals into them convert automatically - and a
     * main balance may own any number of them. Credit cards always stay in the
     * main currency because card payments debit main at face value.
     */
    suspend fun createBalanceAccount(
        mainId: Long,
        name: String,
        type: String,
        opening: BigDecimal,
        currency: String? = null
    ): Long = database.withTransaction {
        val main = accountDao.getAccountById(mainId) ?: error("Main account not found")
        require(main.type == "MAIN" && type in listOf("CASH", "CREDIT_CARD") && name.isNotBlank())
        require(opening >= BigDecimal.ZERO) { "Opening amount must not be negative" }
        val requested = currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val accountCurrency = when (type) {
            "CASH" -> requested ?: main.currency
            else -> main.currency
        }
        require(type != "CREDIT_CARD" || accountCurrency == main.currency) { "Cards must use the main currency" }
        accountDao.insertAccount(Account(
            name = name.trim(),
            currency = accountCurrency,
            startingBalance = (if (type == "CREDIT_CARD") opening.negate() else opening).toPlainString(),
            type = type,
            parentAccountId = mainId
        ))
    }

    /**
     * Updates an existing balance. Currency and parent are immutable - changing
     * them would silently re-value historical rows - so only editable fields are
     * copied onto the stored account.
     */
    suspend fun updateBalanceAccountFields(account: Account) = database.withTransaction {
        val saved = accountDao.getAccountById(account.id) ?: error("Balance not found")
        require(account.name.isNotBlank()) { "Name is required" }
        require(account.lowBalanceThreshold == null || BigDecimal(account.lowBalanceThreshold) >= BigDecimal.ZERO) { "Threshold must not be negative" }
        require(account.type != "CREDIT_CARD" || account.creditLimit == null || BigDecimal(account.creditLimit) >= BigDecimal.ZERO) { "Credit limit must not be negative" }
        accountDao.updateAccount(
            saved.copy(
                name = account.name.trim(),
                lowBalanceThreshold = account.lowBalanceThreshold,
                creditLimit = if (saved.type == "CREDIT_CARD") account.creditLimit else saved.creditLimit,
                // Only cash balances can be set aside.
                includedInTotal = if (saved.type == "CASH") account.includedInTotal else true
            )
        )
    }

    suspend fun configureAutopay(card: Account) {
        require(card.type == "CREDIT_CARD" && card.autopayDay in 1..31)
        require(card.autopayMode in listOf("FULL_BALANCE", "FIXED_AMOUNT"))
        require(card.autopayMode != "FIXED_AMOUNT" || BigDecimal(card.autopayFixedAmount ?: "0") > BigDecimal.ZERO) { "Fixed payment must be positive" }
        val saved = accountDao.getAccountById(card.id) ?: error("Card not found")
        require(saved.type == "CREDIT_CARD" && saved.parentAccountId == card.parentAccountId && saved.currency == card.currency)
        accountDao.updateAccount(card.copy(autopayStartDate = if (card.autopayEnabled && !saved.autopayEnabled && card.autopayStartDate == null) LocalDate.now().toString() else card.autopayStartDate ?: LocalDate.now().toString()))
    }

    suspend fun payCard(cardId: Long, amount: BigDecimal, allowOverpayment: Boolean = false, period: String? = null): CreditCardPayment = database.withTransaction {
        val card = accountDao.getAccountById(cardId) ?: error("Card not found")
        require(card.type == "CREDIT_CARD")
        val source = card.parentAccountId ?: error("Missing main account")
        val current = balances()
        val debt = (current[cardId] ?: BigDecimal.ZERO).negate().max(BigDecimal.ZERO)
        require(amount > BigDecimal.ZERO) { "Payment must be positive" }
        require(allowOverpayment || amount <= debt) { "Payment exceeds outstanding debt. Enable overpayment explicitly." }
        val payment = CreditCardPayment(cardId = cardId, sourceAccountId = source, amount = amount.toPlainString(),
            date = System.currentTimeMillis(), status = if ((current[source] ?: BigDecimal.ZERO) >= amount) "COMPLETED" else "FAILED_INSUFFICIENT_FUNDS", autopayPeriod = period)
        val saved = payment.copy(id = paymentDao.insert(payment))
        if (saved.status == "COMPLETED") paymentDao.getAll().filter { it.cardId == cardId && it.status == "FAILED_INSUFFICIENT_FUNDS" && !it.dismissed }.forEach { paymentDao.update(it.copy(dismissed = true)) }
        saved
    }

    suspend fun dismissPayment(payment: CreditCardPayment) = paymentDao.update(payment.copy(dismissed = true))

    /** Persistent monthly deduplication, including failed attempts. Manual retries are separate ledger entries. */
    suspend fun processAutopay(today: LocalDate = LocalDate.now()): List<CreditCardPayment> = database.withTransaction {
        val results = mutableListOf<CreditCardPayment>()
        for (card in accountDao.getAllAccountsSync().filter { it.type == "CREDIT_CARD" && it.autopayEnabled }) {
            val month = YearMonth.from(today)
            val due = AccountBalances.dueDate(card, month)
            val start = card.autopayStartDate?.let { LocalDate.parse(it) } ?: today
            if (today < due || due < start || paymentDao.getAll().any { it.cardId == card.id && it.autopayPeriod == month.toString() }) continue
            val amount = AccountBalances.paymentAmount(card, balances()[card.id] ?: BigDecimal.ZERO)
            if (amount > BigDecimal.ZERO) results += payCard(card.id, amount, period = month.toString())
            else paymentDao.insert(CreditCardPayment(cardId = card.id, sourceAccountId = card.parentAccountId!!, amount = "0", date = System.currentTimeMillis(), status = "COMPLETED", autopayPeriod = month.toString()))
        }
        results
    }

}
