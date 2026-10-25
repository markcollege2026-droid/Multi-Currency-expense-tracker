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

data class TransactionItem(
    val id: Long,
    val type: String, // "EXPENSE", "TOPUP", "CARD_PAYMENT" or "WITHDRAWAL"
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
            accountDao.deleteAccount(child)
        }
        paymentDao.deleteForAccount(account.id)
        recurringPaymentDao.deleteForAccount(account.id)
        topUpDao.deleteForAccount(account.id)
        withdrawalDao.deleteInvolving(account.id)
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
        withdrawalDao.observeAll()
    ) { accounts, expenses, topUps, payments, withdrawals ->
        val balances = AccountBalances.calculate(accounts, expenses, topUps, payments, withdrawals)
        accounts.associate { account ->
            val entries = expenses.count { it.accountId == account.id } +
                topUps.count { it.accountId == account.id } +
                withdrawals.count { it.fromAccountId == account.id || it.toAccountId == account.id } +
                payments.count { it.cardId == account.id || it.sourceAccountId == account.id }
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
            withdrawalDao.observeAll()
        ) { expenses, topups, accounts, payments, withdrawals ->
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
                    note = it.note ?: "Withdrawal to cash",
                    paymentMethod = "MAIN"
                )
            }
            (expenseItems + topUpItems + paymentItems + withdrawalItems).sortedByDescending { it.date }
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

    // ---- Withdrawals (main -> cash, converted when currencies differ) ----
    fun observeWithdrawals(): Flow<List<Withdrawal>> = withdrawalDao.observeAll()
    suspend fun getWithdrawalsSync(): List<Withdrawal> = withdrawalDao.getAllSync()

    /** Deleting a withdrawal returns the money to main and removes it from the cash balance. */
    suspend fun deleteWithdrawal(withdrawal: Withdrawal) = withdrawalDao.delete(withdrawal)

    suspend fun restoreWithdrawal(withdrawal: Withdrawal): Long = withdrawalDao.insert(withdrawal)

    /**
     * Moves money between a main balance and one of its cash accounts, in either
     * direction: main → cash (a withdrawal) or cash → main (a deposit funded from
     * cash). [rate] is 1 [from] unit expressed in the destination currency; when
     * the currencies match the ratio is stored as 1 and the amount moves unchanged.
     *
     * [amountTo] may be supplied when the user typed the destination side, so the
     * stored row is exactly what they saw. The source balance is checked inside
     * the transaction, so a transfer can never overdraw the account it leaves.
     */
    suspend fun withdraw(
        fromAccountId: Long,
        toAccountId: Long,
        amountFrom: BigDecimal,
        rate: BigDecimal,
        note: String? = null,
        amountTo: BigDecimal? = null
    ): Withdrawal = database.withTransaction {
        require(amountFrom > BigDecimal.ZERO) { "Amount must be positive" }
        require(rate > BigDecimal.ZERO) { "Exchange rate must be positive" }
        val from = accountDao.getAccountById(fromAccountId) ?: error("Source account not found")
        val to = accountDao.getAccountById(toAccountId) ?: error("Destination account not found")
        require(from.id != to.id) { "Source and destination must differ" }
        // One side is the main balance and the other is a cash account of it.
        val validPair = when {
            from.type == "MAIN" -> to.type == "CASH" && to.parentAccountId == from.id
            to.type == "MAIN" -> from.type == "CASH" && from.parentAccountId == to.id
            else -> false
        }
        require(validPair) { "Transfers move money between a main balance and one of its cash accounts" }

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
            date = System.currentTimeMillis(),
            note = note
        )
        // Return the stored row (with its generated id) so callers can delete it.
        withdrawal.copy(id = withdrawalDao.insert(withdrawal))
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
            val validPair = (from.type == "MAIN" && to.type == "CASH" && to.parentAccountId == from.id) ||
                (to.type == "MAIN" && from.type == "CASH" && from.parentAccountId == to.id)
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
    }

    fun observeBalanceAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()
    fun observeCardPayments(): Flow<List<CreditCardPayment>> = paymentDao.observeAll()
    suspend fun getCardPayments(): List<CreditCardPayment> = paymentDao.getAll()
    suspend fun restoreCardPayment(payment: CreditCardPayment) = paymentDao.insert(payment)

    suspend fun balances(): Map<Long, BigDecimal> = AccountBalances.calculate(
        accountDao.getAllAccountsSync(), expenseDao.getAllExpensesSync(), topUpDao.getAllTopUpsSync(),
        paymentDao.getAll(), withdrawalDao.getAllSync())

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
                withdrawalDao.getAllSync()
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
