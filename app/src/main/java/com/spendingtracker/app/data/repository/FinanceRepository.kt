package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.*
import com.spendingtracker.app.data.local.entity.*
import androidx.room.withTransaction
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.domain.AccountBalances
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
    val type: String, // "EXPENSE" or "TOPUP"
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

@Singleton
class FinanceRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val topUpDao: TopUpDao,
    private val settingsDao: AppSettingsDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val recurringPaymentDao: RecurringPaymentDao,
    private val paymentDao: CreditCardPaymentDao,
    private val database: AppDatabase
) {
    // ---- Accounts ----
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts().map { list -> list.filter { it.type == "MAIN" } }
    suspend fun getAllAccountsSync(): List<Account> = accountDao.getAllAccountsSync()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)
    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)
    suspend fun getAccountCount(): Int = accountDao.getAllAccountsSync().count { it.type == "MAIN" }

    /** The account currently selected in settings (falls back to the first account). */
    fun observeActiveAccount(): Flow<Account?> =
        combine(settingsDao.getSettings(), accountDao.getAllAccounts()) { settings, accounts ->
            val activeId = settings?.activeAccountId
            accounts.find { it.id == activeId && it.type == "MAIN" } ?: accounts.firstOrNull { it.type == "MAIN" }
        }

    suspend fun getActiveAccountId(): Long {
        val fromSettings = settingsDao.getSettingsSync()?.activeAccountId
        if (fromSettings != null && accountDao.getAccountById(fromSettings)?.type == "MAIN") return fromSettings
        return accountDao.getAllAccountsSync().firstOrNull { it.type == "MAIN" }?.id ?: 1L
    }

    /**
     * Deletes an account together with all account-scoped data (expenses, top-ups,
     * recurring payments). Loans & debts are shared between accounts and are kept.
     */
    suspend fun deleteAccountCascade(account: Account) = database.withTransaction {
        accountDao.getAllAccountsSync().filter { it.parentAccountId == account.id }.forEach { child ->
            paymentDao.deleteForAccount(child.id)
            topUpDao.deleteForAccount(child.id)
            accountDao.deleteAccount(child)
        }
        paymentDao.deleteForAccount(account.id)
        recurringPaymentDao.deleteForAccount(account.id)
        topUpDao.deleteForAccount(account.id)
        expenseDao.deleteForAccount(account.id)
        accountDao.deleteAccount(account)
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
            paymentDao.observeAll()
        ) { expenses, topups, accounts, payments ->
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
            (expenseItems + topUpItems + paymentItems).sortedByDescending { it.date }
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

    suspend fun eraseAll() {
        paymentDao.deleteAll()
        recurringPaymentDao.deleteAll()
        expenseDao.deleteAll()
        topUpDao.deleteAll()
        accountDao.getAllAccountsSync().forEach { accountDao.deleteAccount(it) }
        // Keep settings structure but reset; categories are kept (defaults)
        settingsDao.deleteAll()
    }
    suspend fun <T> atomic(block: suspend () -> T): T = database.withTransaction { block() }
    suspend fun restoreExpense(expense: Expense): Long = expenseDao.insertExpense(expense)

    /** Called before committing a restore; malformed references or money roll back the whole import. */
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
                require(parent.type == "MAIN" && parent.currency == account.currency) { "Invalid balance parent/currency" }
            }
            if (account.type == "CASH") require(accounts.count { it.type == "CASH" && it.parentAccountId == account.parentAccountId } == 1)
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
        accountDao.getAllAccountsSync(), expenseDao.getAllExpensesSync(), topUpDao.getAllTopUpsSync(), paymentDao.getAll())

    private suspend fun validateExpense(expense: Expense, allowOverdraft: Boolean) {
        require(expense.originalAsBigDecimal() > BigDecimal.ZERO && expense.convertedAsBigDecimal() > BigDecimal.ZERO && expense.rateAsBigDecimal() > BigDecimal.ZERO) { "Amount and exchange rate must be positive" }
        val main = accountDao.getAccountById(expense.accountId) ?: error("Account not found")
        val target = accountDao.getAccountById(expense.balanceAccountId ?: expense.accountId) ?: error("Payment account not found")
        require(target.id == main.id || target.parentAccountId == main.id) { "Payment account belongs to another account" }
        require(target.currency == expense.currencyConverted) { "Payment currency mismatch" }
        if (target.type == "CASH" && !allowOverdraft) {
            val remaining = AccountBalances.calculate(accountDao.getAllAccountsSync(), expenseDao.getAllExpensesSync().filter { it.id != expense.id }, topUpDao.getAllTopUpsSync(), paymentDao.getAll())[target.id] ?: BigDecimal.ZERO
            require(expense.convertedAsBigDecimal() <= remaining) { "Insufficient cash. Explicitly allow overdraft to continue." }
        }
    }

    suspend fun createBalanceAccount(mainId: Long, name: String, type: String, opening: BigDecimal): Long = database.withTransaction {
        val main = accountDao.getAccountById(mainId) ?: error("Main account not found")
        require(main.type == "MAIN" && type in listOf("CASH", "CREDIT_CARD") && name.isNotBlank())
        require(opening >= BigDecimal.ZERO) { "Opening amount must not be negative" }
        require(type != "CASH" || accountDao.getAllAccountsSync().none { it.parentAccountId == mainId && it.type == "CASH" }) { "Cash account already exists" }
        accountDao.insertAccount(Account(name = name.trim(), currency = main.currency,
            startingBalance = (if (type == "CREDIT_CARD") opening.negate() else opening).toPlainString(), type = type, parentAccountId = mainId))
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
