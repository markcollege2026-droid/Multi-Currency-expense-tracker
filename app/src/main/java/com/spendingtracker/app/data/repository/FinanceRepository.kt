package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.*
import com.spendingtracker.app.data.local.entity.*
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
    private val recurringPaymentDao: RecurringPaymentDao
) {
    // ---- Accounts ----
    fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()
    suspend fun getAllAccountsSync(): List<Account> = accountDao.getAllAccountsSync()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)
    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)
    suspend fun getAccountCount(): Int = accountDao.getCount()

    /** The account currently selected in settings (falls back to the first account). */
    fun observeActiveAccount(): Flow<Account?> =
        combine(settingsDao.getSettings(), accountDao.getAllAccounts()) { settings, accounts ->
            val activeId = settings?.activeAccountId
            accounts.find { it.id == activeId } ?: accounts.firstOrNull()
        }

    suspend fun getActiveAccountId(): Long {
        val fromSettings = settingsDao.getSettingsSync()?.activeAccountId
        if (fromSettings != null && accountDao.getAccountById(fromSettings) != null) return fromSettings
        return accountDao.getAllAccountsSync().firstOrNull()?.id ?: 1L
    }

    /**
     * Deletes an account together with all account-scoped data (expenses, top-ups,
     * recurring payments). Loans & debts are shared between accounts and are kept.
     */
    suspend fun deleteAccountCascade(account: Account) {
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
        return combine(expenseDao.getAllExpenses(), topUpDao.getAllTopUps()) { expenses, topups ->
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
                TransactionItem(
                    id = it.id,
                    type = "TOPUP",
                    amountOriginal = it.amountAsBigDecimal(),
                    currencyOriginal = "", // will be filled from the account
                    amountConverted = it.amountAsBigDecimal(),
                    currencyConverted = "",
                    rateUsed = null,
                    categoryId = null,
                    date = it.date,
                    note = it.note,
                    paymentMethod = null
                )
            }
            (expenseItems + topUpItems).sortedByDescending { it.date }
        }
    }

    suspend fun insertExpense(expense: Expense): Long = expenseDao.insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.updateExpense(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)
    suspend fun getExpenseById(id: Long): Expense? = expenseDao.getExpenseById(id)

    suspend fun insertTopUp(topUp: TopUp): Long = topUpDao.insertTopUp(topUp)
    suspend fun updateTopUp(topUp: TopUp) = topUpDao.updateTopUp(topUp)
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
        recurringPaymentDao.deleteAll()
        expenseDao.deleteAll()
        topUpDao.deleteAll()
        accountDao.getAllAccountsSync().forEach { accountDao.deleteAccount(it) }
        // Keep settings structure but reset; categories are kept (defaults)
        settingsDao.deleteAll()
    }
}
