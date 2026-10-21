package com.spendingtracker.app.ui.screens.home

import com.spendingtracker.app.domain.AccountBalances
import com.spendingtracker.app.data.local.entity.CreditCardPayment
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
    val activeAccount: Account? = null,
    val balanceAccounts: List<Account> = emptyList(),
    val accountBalances: Map<Long, BigDecimal> = emptyMap(),
    val totalAvailable: BigDecimal = BigDecimal.ZERO,
    /** Cash balances counted in totals, converted into [balanceCurrency]. */
    val cashTotal: BigDecimal = BigDecimal.ZERO,
    val cardPayments: List<CreditCardPayment> = emptyList(),
    val message: String? = null
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
            try { recurringRepository.processDuePayments(); runAutopay() } catch (e: Exception) { /* next run retries */ }
        }

        viewModelScope.launch {
            financeRepository.ensureDefaultCategories()
            val accountAndList = combine(
                financeRepository.observeActiveAccount(),
                combine(
                    financeRepository.observeBalanceAccounts(),
                    financeRepository.observeCardPayments(),
                    financeRepository.observeWithdrawals()
                ) { accounts, payments, withdrawals -> Triple(accounts, payments, withdrawals) }
            ) { active, ledger -> active to ledger }

            combine(
                accountAndList,
                financeRepository.getAllExpenses(),
                financeRepository.getAllTopUps(),
                financeRepository.getCategories(),
                financeRepository.getSettings()
            ) { (account, ledger), allExpenses, allTopUps, categories, settings ->
                val (accounts, payments, withdrawals) = ledger
                val balances = AccountBalances.calculate(accounts, allExpenses, allTopUps, payments, withdrawals)
                val appSettings = settings ?: AppSettings()
                val accountId = account?.id
                val expenses = if (accountId == null) emptyList() else allExpenses.filter { it.accountId == accountId }

                val starting = account?.startingBalanceAsBigDecimal() ?: BigDecimal.ZERO
                val mainBalance = balances[accountId] ?: BigDecimal.ZERO

                val threshold = account?.thresholdAsBigDecimal()
                val isLow = balanceCalculator.isLowBalance(mainBalance, threshold)
                val isNegative = balanceCalculator.isNegative(mainBalance)
                val balanceCurrency = account?.currency ?: "USD"

                if (isLow && !lowBalanceNotified) {
                    lowBalanceNotified = true
                    notificationHelper.showLowBalanceNotification(
                        CurrencyFormatter.formatSimple(mainBalance, balanceCurrency),
                        threshold?.let { CurrencyFormatter.formatSimple(it, balanceCurrency) } ?: ""
                    )
                } else if (!isLow) {
                    lowBalanceNotified = false
                }

                // Expenses paid from a foreign-currency cash account store the amount
                // converted into that cash currency; translate totals back into the
                // main currency using cached rates so week/month/category stats add up.
                val ratesToMain: Map<String, BigDecimal> = expenses
                    .mapNotNull { e -> e.currencyConverted.takeIf { c -> c != balanceCurrency } }
                    .distinct()
                    .associateWith { cur -> rateRepository.getConversionRateOrFallback(cur, balanceCurrency) }
                fun inMain(e: Expense): BigDecimal {
                    val value = try { BigDecimal(e.amountConverted) } catch (ex: Exception) { return BigDecimal.ZERO }
                    val rate = ratesToMain[e.currencyConverted] ?: return value
                    return value.multiply(rate)
                }

                // Weekly and monthly
                val now = System.currentTimeMillis()
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                val monthAgo = now - 30 * 24 * 60 * 60 * 1000L

                val spentWeek = expenses.filter { it.date >= weekAgo }
                    .fold(BigDecimal.ZERO) { acc, e -> acc + inMain(e) }
                val spentMonth = expenses.filter { it.date >= monthAgo }
                    .fold(BigDecimal.ZERO) { acc, e -> acc + inMain(e) }

                // Top categories
                val byCategory = expenses.groupBy { it.categoryId }
                    .map { (catId, list) ->
                        val cat = categories.find { it.id == catId }
                        val total = list.fold(BigDecimal.ZERO) { acc, e -> acc + inMain(e) }
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
                        .fold(BigDecimal.ZERO) { acc, e -> acc + inMain(e) }
                    start to dayTotal
                }.reversed()

                val isStale = appSettings.lastRatesUpdate?.let {
                    System.currentTimeMillis() - it > Constants.RATE_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L * 2
                } ?: true

                // Available money in the main currency: main + children. Cash that was
                // explicitly set aside is excluded; foreign-currency cash is converted
                // with cached rates so mixed-currency totals stay meaningful.
                val childAccounts = if (accountId == null) emptyList() else accounts.filter { it.parentAccountId == accountId }
                val includedChildren = childAccounts.filter { it.type != "CASH" || it.includedInTotal }
                val childRates: Map<String, BigDecimal> = includedChildren
                    .map { it.currency }
                    .distinct()
                    .filter { it != balanceCurrency }
                    .associateWith { cur -> rateRepository.getConversionRateOrFallback(cur, balanceCurrency) }
                fun inBalanceCurrency(amount: BigDecimal, currency: String): BigDecimal =
                    if (currency == balanceCurrency) amount
                    else amount.multiply(childRates[currency] ?: BigDecimal.ONE)
                val mainInTotal = if (accountId == null) BigDecimal.ZERO else mainBalance
                val totalAvailable = mainInTotal + includedChildren.fold(BigDecimal.ZERO) { sum, a ->
                    sum + inBalanceCurrency(balances[a.id] ?: BigDecimal.ZERO, a.currency)
                }
                val cashTotal = includedChildren.filter { it.type == "CASH" }.fold(BigDecimal.ZERO) { sum, a ->
                    sum + inBalanceCurrency(balances[a.id] ?: BigDecimal.ZERO, a.currency)
                }

                HomeUiState(
                    balance = mainBalance,
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
                    accounts = accounts.filter { it.type == "MAIN" },
                    activeAccount = account,
                    balanceAccounts = accounts.filter { it.parentAccountId == accountId },
                    accountBalances = balances,
                    totalAvailable = totalAvailable,
                    cashTotal = cashTotal,
                    cardPayments = payments.filter { it.sourceAccountId == accountId },
                    message = _uiState.value.message
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

    private suspend fun runAutopay() {
        financeRepository.processAutopay().filter { it.status == "FAILED_INSUFFICIENT_FUNDS" }.forEach {
            notificationHelper.showAutopayFailure(it.cardId.toInt())
        }
    }

    fun createBalanceAccount(name: String, type: String, opening: String, currency: String = "") = action {
        financeRepository.createBalanceAccount(
            financeRepository.getActiveAccountId(),
            name,
            type,
            BigDecimal(opening),
            currency.ifBlank { null }
        )
    }

    fun fundCash(cashId: Long, amount: String) = action {
        val value = BigDecimal(amount)
        require(value > BigDecimal.ZERO) { "Amount must be positive" }
        financeRepository.insertTopUp(TopUp(accountId = cashId, amount = value.toPlainString(), date = System.currentTimeMillis(), note = "Cash income"))
    }

    /**
     * Withdraws from the active main balance into [toCashId]'s cash account,
     * converting between the two currencies with cached/fallback rates.
     */
    fun withdraw(toCashId: Long, amount: String, note: String? = null) = action {
        val value = try {
            BigDecimal(amount.trim().replace(',', '.'))
        } catch (e: Exception) {
            null
        } ?: error("Invalid amount")
        require(value > BigDecimal.ZERO) { "Amount must be positive" }
        val main = financeRepository.getAccountById(financeRepository.getActiveAccountId())
            ?: error("Main account not found")
        val cash = financeRepository.getAccountById(toCashId) ?: error("Cash account not found")
        require(cash.type == "CASH" && cash.parentAccountId == main.id) { "Choose a cash account of this balance" }
        val rate = rateRepository.getConversionRateOrFallback(main.currency, cash.currency)
        val withdrawal = financeRepository.withdraw(main.id, cash.id, value, rate, note)
        _uiState.update {
            it.copy(
                message = "Withdrew " + CurrencyFormatter.formatSimple(withdrawal.amountFromAsBigDecimal(), withdrawal.currencyFrom) +
                    " to ${cash.name} · " + CurrencyFormatter.formatSimple(withdrawal.amountToAsBigDecimal(), withdrawal.currencyTo)
            )
        }
    }

    /** Marks a cash account as set aside (excluded from totals) or included again. */
    fun setCashIncluded(cash: Account, included: Boolean) = action {
        financeRepository.updateAccount(cash.copy(includedInTotal = included))
    }

    /** Rate preview for the withdraw dialog: 1 [fromCurrency] = ? [toCurrency]. */
    suspend fun conversionRate(fromCurrency: String, toCurrency: String): BigDecimal =
        rateRepository.getConversionRateOrFallback(fromCurrency, toCurrency)
    fun payCard(cardId: Long, amount: String, overpayment: Boolean) = action {
        val result = financeRepository.payCard(cardId, BigDecimal(amount), overpayment)
        if (result.status != "COMPLETED") {
            notificationHelper.showAutopayFailure(cardId.toInt())
            error("Payment failed: insufficient main/debit funds")
        }
        _uiState.update { it.copy(message = "Credit card payment completed") }
    }
    fun configureAutopay(card: Account) = action { financeRepository.configureAutopay(card); runAutopay() }
    fun dismissPayment(payment: CreditCardPayment) = action { financeRepository.dismissPayment(payment) }
    fun clearMessage() { _uiState.update { it.copy(message = null) } }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _uiState.update { it.copy(message = e.message ?: "Unable to complete operation") } }
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
