package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
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
                financeRepository.observeCardPayments()
            ) { expenses, topUps, categories, wallet, payments ->
                val (account, accounts) = wallet
                val incomeAccountIds = accounts.filter { it.id == account?.id || it.parentAccountId == account?.id }.map { it.id }.toSet()
                val accountId = account?.id
                _uiState.value.copy(
                    cardPayments = payments.filter { it.sourceAccountId == accountId },
                    allExpenses = if (accountId == null) emptyList() else expenses.filter { it.accountId == accountId },
                    allTopUps = if (accountId == null) emptyList() else topUps.filter { it.accountId in incomeAccountIds },
                    categories = categories,
                    balanceCurrency = account?.currency ?: "USD",
                    isLoading = false
                )
            }.collect { baseState ->
                _uiState.value = baseState.copy(
                    filteredExpenses = filterExpenses(baseState.allExpenses, baseState),
                    filteredTopUps = filterTopUps(baseState.allTopUps, baseState)
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

    fun onSearchChanged(query: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            searchQuery = query,
            filteredExpenses = filterExpenses(current.allExpenses, current.copy(searchQuery = query)),
            filteredTopUps = filterTopUps(current.allTopUps, current.copy(searchQuery = query))
        )
    }

    fun onCategorySelected(id: Long?) {
        val current = _uiState.value
        val newState = current.copy(selectedCategoryId = id)
        _uiState.value = newState.copy(
            filteredExpenses = filterExpenses(current.allExpenses, newState),
            filteredTopUps = filterTopUps(current.allTopUps, newState)
        )
    }

    fun onCurrencySelected(currency: String?) {
        val current = _uiState.value
        val newState = current.copy(selectedCurrency = currency)
        _uiState.value = newState.copy(
            filteredExpenses = filterExpenses(current.allExpenses, newState),
            filteredTopUps = filterTopUps(current.allTopUps, newState)
        )
    }

    fun onTypeSelected(type: String?) {
        val current = _uiState.value
        val newState = current.copy(selectedType = type)
        _uiState.value = newState.copy(
            filteredExpenses = filterExpenses(current.allExpenses, newState),
            filteredTopUps = filterTopUps(current.allTopUps, newState)
        )
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { financeRepository.deleteExpense(expense) }
    }

    fun deleteTopUp(topUp: TopUp) {
        viewModelScope.launch { financeRepository.deleteTopUp(topUp) }
    }
}
