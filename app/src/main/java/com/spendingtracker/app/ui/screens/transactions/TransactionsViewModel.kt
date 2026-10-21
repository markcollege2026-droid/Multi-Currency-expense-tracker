package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.local.entity.Withdrawal
import com.spendingtracker.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class TransactionsUiState(
    val allExpenses: List<Expense> = emptyList(),
    val cardPayments: List<com.spendingtracker.app.data.local.entity.CreditCardPayment> = emptyList(),
    val allTopUps: List<TopUp> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val filteredTopUps: List<TopUp> = emptyList(),
    val allWithdrawals: List<Withdrawal> = emptyList(),
    val filteredWithdrawals: List<Withdrawal> = emptyList(),
    val accountsById: Map<Long, Account> = emptyMap(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val selectedCurrency: String? = null,
    val selectedType: String? = null, // EXPENSE, TOPUP
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null,
    val balanceCurrency: String = "USD",
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                financeRepository.getAllExpenses(),
                financeRepository.getAllTopUps(),
                financeRepository.getCategories(),
                combine(financeRepository.observeActiveAccount(), financeRepository.observeBalanceAccounts()) { active, all -> active to all },
                combine(financeRepository.observeCardPayments(), financeRepository.observeWithdrawals()) { payments, withdrawals -> payments to withdrawals }
            ) { expenses, topUps, categories, wallet, paymentAndWithdrawals ->
                val (payments, withdrawals) = paymentAndWithdrawals
                val (account, accounts) = wallet
                val incomeAccountIds = accounts.filter { it.id == account?.id || it.parentAccountId == account?.id }.map { it.id }.toSet()
                val accountId = account?.id
                _uiState.value.copy(
                    cardPayments = payments.filter { it.sourceAccountId == accountId },
                    allExpenses = if (accountId == null) emptyList() else expenses.filter { it.accountId == accountId },
                    allTopUps = if (accountId == null) emptyList() else topUps.filter { it.accountId in incomeAccountIds },
                    allWithdrawals = if (accountId == null) emptyList()
                    else withdrawals.filter { it.fromAccountId == accountId || incomeAccountIds.contains(it.toAccountId) },
                    accountsById = accounts.associateBy { it.id },
                    categories = categories,
                    balanceCurrency = account?.currency ?: "USD",
                    isLoading = false
                )
            }.collect { baseState ->
                _uiState.value = baseState.copy(
                    filteredExpenses = filterExpenses(baseState.allExpenses, baseState),
                    filteredTopUps = filterTopUps(baseState.allTopUps, baseState),
                    filteredWithdrawals = filterWithdrawals(baseState.allWithdrawals, baseState)
                )
            }
        }
    }

    private fun filterExpenses(expenses: List<Expense>, state: TransactionsUiState): List<Expense> {
        return expenses.filter { expense ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(expense.note?.lowercase()?.contains(q) == true || expense.currencyOriginal.lowercase().contains(q))) return@filter false
            }
            if (state.selectedCategoryId != null && expense.categoryId != state.selectedCategoryId) return@filter false
            if (state.selectedCurrency != null && expense.currencyOriginal != state.selectedCurrency) return@filter false
            if (state.selectedType != null && state.selectedType != "EXPENSE") return@filter false
            if (state.dateRangeStart != null && expense.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && expense.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun filterTopUps(topUps: List<TopUp>, state: TransactionsUiState): List<TopUp> {
        return topUps.filter { topUp ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(topUp.note?.lowercase()?.contains(q) == true)) return@filter false
            }
            if (state.selectedType != null && state.selectedType != "TOPUP") return@filter false
            if (state.dateRangeStart != null && topUp.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && topUp.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun filterWithdrawals(withdrawals: List<Withdrawal>, state: TransactionsUiState): List<Withdrawal> {
        return withdrawals.filter { withdrawal ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(withdrawal.note?.lowercase()?.contains(q) == true ||
                        withdrawal.currencyFrom.lowercase().contains(q) ||
                        withdrawal.currencyTo.lowercase().contains(q))
                ) return@filter false
            }
            if (state.selectedCategoryId != null) return@filter false
            if (state.selectedType != null && state.selectedType != "WITHDRAWAL") return@filter false
            if (state.selectedCurrency != null &&
                state.selectedCurrency != withdrawal.currencyFrom &&
                state.selectedCurrency != withdrawal.currencyTo
            ) return@filter false
            if (state.dateRangeStart != null && withdrawal.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && withdrawal.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun refilter() {
        val current = _uiState.value
        _uiState.value = current.copy(
            filteredExpenses = filterExpenses(current.allExpenses, current),
            filteredTopUps = filterTopUps(current.allTopUps, current),
            filteredWithdrawals = filterWithdrawals(current.allWithdrawals, current)
        )
    }

    fun onSearchChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refilter()
    }

    fun onCategorySelected(id: Long?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = id)
        refilter()
    }

    fun onCurrencySelected(currency: String?) {
        _uiState.value = _uiState.value.copy(selectedCurrency = currency)
        refilter()
    }

    fun onTypeSelected(type: String?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        refilter()
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { financeRepository.deleteExpense(expense) }
    }

    fun deleteTopUp(topUp: TopUp) {
        viewModelScope.launch { financeRepository.deleteTopUp(topUp) }
    }

    fun deleteWithdrawal(withdrawal: Withdrawal) {
        viewModelScope.launch { financeRepository.deleteWithdrawal(withdrawal) }
    }
}
