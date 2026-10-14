package com.spendingtracker.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import com.spendingtracker.app.domain.BalanceCalculator
import com.spendingtracker.app.util.Constants
import com.spendingtracker.app.util.CurrencyFormatter
import com.spendingtracker.app.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceCurrency: String = "USD",
    val startingBalance: BigDecimal = BigDecimal.ZERO,
    val totalSpentWeek: BigDecimal = BigDecimal.ZERO,
    val totalSpentMonth: BigDecimal = BigDecimal.ZERO,
    val recentExpenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val topCategories: List<Pair<Category?, BigDecimal>> = emptyList(),
    val spendingTrend: List<Pair<Long, BigDecimal>> = emptyList(),
    val isLoading: Boolean = true,
    val isRatesStale: Boolean = false,
    val lastRatesUpdate: Long? = null,
    val lowBalanceThreshold: BigDecimal? = null,
    val isLowBalance: Boolean = false,
    val isNegativeBalance: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val activeAccount: Account? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val recurringRepository: RecurringRepository,
    private val balanceCalculator: BalanceCalculator,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Only notify on the transition into the low-balance state, not on every data change.
    private var lowBalanceNotified = false

    init {
        // Post any recurring payments that came due (also runs daily via WorkManager)
        viewModelScope.launch {
            try { recurringRepository.processDuePayments() } catch (e: Exception) { /* next run retries */ }
        }

        viewModelScope.launch {
            financeRepository.ensureDefaultCategories()
            val accountAndList = combine(
                financeRepository.observeActiveAccount(),
                financeRepository.getAllAccounts()
            ) { active, all -> active to all }

            combine(
                accountAndList,
                financeRepository.getAllExpenses(),
                financeRepository.getAllTopUps(),
                financeRepository.getCategories(),
                financeRepository.getSettings()
            ) { (account, accounts), allExpenses, allTopUps, categories, settings ->
                val appSettings = settings ?: AppSettings()
                val accountId = account?.id
                val expenses = if (accountId == null) emptyList() else allExpenses.filter { it.accountId == accountId }
                val topUps = if (accountId == null) emptyList() else allTopUps.filter { it.accountId == accountId }

                val starting = account?.startingBalanceAsBigDecimal() ?: BigDecimal.ZERO
                val topUpAmounts = topUps.map { try { BigDecimal(it.amount) } catch (e: Exception) { BigDecimal.ZERO } }
                val expenseConverted = expenses.map { try { BigDecimal(it.amountConverted) } catch (e: Exception) { BigDecimal.ZERO } }

                val balanceResult = balanceCalculator.calculate(starting, topUpAmounts, expenseConverted)

                val threshold = account?.thresholdAsBigDecimal()
                val isLow = balanceCalculator.isLowBalance(balanceResult.balance, threshold)
                val isNegative = balanceCalculator.isNegative(balanceResult.balance)
                val balanceCurrency = account?.currency ?: "USD"

                if (isLow && !lowBalanceNotified) {
                    lowBalanceNotified = true
                    notificationHelper.showLowBalanceNotification(
                        CurrencyFormatter.formatSimple(balanceResult.balance, balanceCurrency),
                        threshold?.let { CurrencyFormatter.formatSimple(it, balanceCurrency) } ?: ""
                    )
                } else if (!isLow) {
                    lowBalanceNotified = false
                }

                // Weekly and monthly
                val now = System.currentTimeMillis()
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                val monthAgo = now - 30 * 24 * 60 * 60 * 1000L

                val spentWeek = expenses.filter { it.date >= weekAgo }
                    .fold(BigDecimal.ZERO) { acc, e -> acc + try { BigDecimal(e.amountConverted) } catch (ex: Exception) { BigDecimal.ZERO } }
                val spentMonth = expenses.filter { it.date >= monthAgo }
                    .fold(BigDecimal.ZERO) { acc, e -> acc + try { BigDecimal(e.amountConverted) } catch (ex: Exception) { BigDecimal.ZERO } }

                // Top categories
                val byCategory = expenses.groupBy { it.categoryId }
                    .map { (catId, list) ->
                        val cat = categories.find { it.id == catId }
                        val total = list.fold(BigDecimal.ZERO) { acc, e -> acc + try { BigDecimal(e.amountConverted) } catch (ex: Exception) { BigDecimal.ZERO } }
                        cat to total
                    }
                    .sortedByDescending { it.second }
                    .take(5)

                // Spending trend last 7 days
                val trend = (0..6).map { dayOffset ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -dayOffset)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    val end = cal.timeInMillis
                    val dayTotal = expenses.filter { it.date in start..end }
                        .fold(BigDecimal.ZERO) { acc, e -> acc + try { BigDecimal(e.amountConverted) } catch (ex: Exception) { BigDecimal.ZERO } }
                    start to dayTotal
                }.reversed()

                val isStale = appSettings.lastRatesUpdate?.let {
                    System.currentTimeMillis() - it > Constants.RATE_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L * 2
                } ?: true

                HomeUiState(
                    balance = balanceResult.balance,
                    balanceCurrency = balanceCurrency,
                    startingBalance = starting,
                    totalSpentWeek = spentWeek,
                    totalSpentMonth = spentMonth,
                    recentExpenses = expenses.take(10),
                    categories = categories,
                    topCategories = byCategory,
                    spendingTrend = trend,
                    isLoading = false,
                    isRatesStale = isStale,
                    lastRatesUpdate = appSettings.lastRatesUpdate,
                    lowBalanceThreshold = threshold,
                    isLowBalance = isLow,
                    isNegativeBalance = isNegative,
                    accounts = accounts,
                    activeAccount = account
                )
            }
                // Heavy aggregation (BigDecimal math, grouping, trend) runs off the main thread.
                .flowOn(Dispatchers.Default)
                .collect { state ->
                    _uiState.value = state
                }
        }

        // Refresh rates if needed
        viewModelScope.launch {
            val accountId = financeRepository.getActiveAccountId()
            val account = financeRepository.getAccountById(accountId)
            val base = account?.currency ?: "USD"
            val lastUpdate = financeRepository.getSettingsSync()?.lastRatesUpdate ?: 0L
            val shouldRefresh = System.currentTimeMillis() - lastUpdate > Constants.RATE_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L
            if (shouldRefresh) {
                refreshRates(base)
            }
        }
    }

    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            financeRepository.saveSettings(current.copy(activeAccountId = accountId))
        }
    }

    fun refreshRates(baseCurrency: String) {
        viewModelScope.launch {
            val result = rateRepository.refreshRates(baseCurrency)
            result.onSuccess { timestamp ->
                val current = financeRepository.getSettingsSync()
                if (current != null) {
                    financeRepository.saveSettings(current.copy(lastRatesUpdate = timestamp))
                }
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            financeRepository.deleteExpense(expense)
        }
    }

    fun deleteTopUp(topUp: TopUp) {
        viewModelScope.launch {
            financeRepository.deleteTopUp(topUp)
        }
    }

    fun adjustBalance(desiredBalance: BigDecimal) {
        viewModelScope.launch {
            val currentBalance = _uiState.value.balance
            val diff = desiredBalance.subtract(currentBalance)
            if (diff.compareTo(BigDecimal.ZERO) == 0) return@launch

            val account = financeRepository.getAccountById(financeRepository.getActiveAccountId())
                ?: return@launch
            val balanceCurrency = account.currency
            val now = System.currentTimeMillis()

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // Need to add money
                val topUp = TopUp(
                    accountId = account.id,
                    amount = diff.abs().toPlainString(),
                    date = now,
                    note = "Balance adjustment"
                )
                financeRepository.insertTopUp(topUp)
            } else {
                // Need to subtract money -> create expense in the account currency
                val absDiff = diff.abs()
                val expense = Expense(
                    accountId = account.id,
                    amountOriginal = absDiff.toPlainString(),
                    currencyOriginal = balanceCurrency,
                    amountConverted = absDiff.toPlainString(),
                    currencyConverted = balanceCurrency,
                    exchangeRateUsed = BigDecimal.ONE.toPlainString(),
                    categoryId = null,
                    date = now,
                    note = "Balance adjustment",
                    paymentMethod = null
                )
                financeRepository.insertExpense(expense)
            }
        }
    }
}
