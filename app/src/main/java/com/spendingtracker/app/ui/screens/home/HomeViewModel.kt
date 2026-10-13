package com.spendingtracker.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.domain.BalanceCalculator
import com.spendingtracker.app.util.Constants
import com.spendingtracker.app.util.CurrencyFormatter
import com.spendingtracker.app.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isNegativeBalance: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val balanceCalculator: BalanceCalculator,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            financeRepository.ensureDefaultCategories()
            combine(
                financeRepository.getSettings(),
                financeRepository.getAllExpenses(),
                financeRepository.getAllTopUps(),
                financeRepository.getCategories()
            ) { settings, expenses, topUps, categories ->
                val appSettings = settings ?: AppSettings()
                val starting = try { BigDecimal(appSettings.startingBalance) } catch (e: Exception) { BigDecimal.ZERO }
                val topUpAmounts = topUps.map { try { BigDecimal(it.amount) } catch (e: Exception) { BigDecimal.ZERO } }
                val expenseConverted = expenses.map { try { BigDecimal(it.amountConverted) } catch (e: Exception) { BigDecimal.ZERO } }

                val balanceResult = balanceCalculator.calculate(starting, topUpAmounts, expenseConverted)

                val threshold = appSettings.lowBalanceThreshold?.let { try { BigDecimal(it) } catch (e: Exception) { null } }
                val isLow = balanceCalculator.isLowBalance(balanceResult.balance, threshold)
                val isNegative = balanceCalculator.isNegative(balanceResult.balance)

                if (isLow) {
                    notificationHelper.showLowBalanceNotification(
                        CurrencyFormatter.formatSimple(balanceResult.balance, appSettings.balanceCurrency),
                        threshold?.let { CurrencyFormatter.formatSimple(it, appSettings.balanceCurrency) } ?: ""
                    )
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
                    balanceCurrency = appSettings.balanceCurrency,
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
                    isNegativeBalance = isNegative
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Refresh rates if needed
        viewModelScope.launch {
            val settings = financeRepository.getSettingsSync()
            val base = settings?.balanceCurrency ?: "USD"
            val lastUpdate = settings?.lastRatesUpdate ?: 0L
            val shouldRefresh = System.currentTimeMillis() - lastUpdate > Constants.RATE_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L
            if (shouldRefresh) {
                refreshRates(base)
            }
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
}
