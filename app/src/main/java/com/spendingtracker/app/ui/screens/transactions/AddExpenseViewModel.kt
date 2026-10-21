package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.util.CurrencyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class AddExpenseUiState(
    val amount: String = "",
    val currency: String = "USD",
    val categoryId: Long? = null,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val paymentMethod: String = "",
    val manualRate: String = "",
    val useManualRate: Boolean = false,
    val categories: List<Category> = emptyList(),
    val balanceCurrency: String = "USD",
    /** The active main account currency; the payment target may differ (foreign cash). */
    val mainCurrency: String = "USD",
    val orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    val convertedAmount: BigDecimal? = null,
    val rateUsed: BigDecimal? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false,
    val createdAt: Long? = null,
    val accountId: Long? = null,
    val balanceAccountId: Long? = null,
    val paymentAccounts: List<com.spendingtracker.app.data.local.entity.Account> = emptyList(),
    val allowOverdraft: Boolean = false
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState

    // Only the latest conversion may write its result, otherwise fast typing can
    // race and overwrite newer input with stale state.
    private var conversionJob: Job? = null

    private fun computeOrdered(settings: com.spendingtracker.app.data.local.entity.AppSettings?): List<CurrencyProvider.CurrencyInfo> {
        return CurrencyProvider.getOrderedCurrencies(
            sortMode = settings?.currencySortMode ?: "ALPHABETICAL",
            customOrderJson = settings?.customCurrencyOrder,
            favoriteJson = settings?.favoriteCurrencies,
            recentJson = settings?.recentCurrencies,
            balanceCurrency = settings?.balanceCurrency
        )
    }

    fun loadExpense(expenseId: Long?) {
        if (expenseId == null) {
            viewModelScope.launch {
                val account = financeRepository.getAccountById(financeRepository.getActiveAccountId())
                val settings = financeRepository.getSettingsSync()
                val cats = financeRepository.getAllCategoriesSync()
                val ordered = computeOrdered(settings)
                _uiState.value = _uiState.value.copy(
                    balanceCurrency = account?.currency ?: "USD",
                    mainCurrency = account?.currency ?: "USD",
                    categories = cats,
                    currency = account?.currency ?: "USD",
                    orderedCurrencies = ordered,
                    accountId = account?.id,
                    paymentAccounts = financeRepository.getAllAccountsSync().filter { it.id == account?.id || it.parentAccountId == account?.id }
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val expense = financeRepository.getExpenseById(expenseId)
                val settings = financeRepository.getSettingsSync()
                val cats = financeRepository.getAllCategoriesSync()
                val ordered = computeOrdered(settings)
                if (expense != null) {
                    val expenseAccounts = financeRepository.getAllAccountsSync()
                        .filter { it.id == expense.accountId || it.parentAccountId == expense.accountId }
                    _uiState.value = AddExpenseUiState(
                        amount = expense.amountOriginal,
                        currency = expense.currencyOriginal,
                        categoryId = expense.categoryId,
                        date = expense.date,
                        note = expense.note ?: "",
                        paymentMethod = expense.paymentMethod ?: "",
                        manualRate = expense.exchangeRateUsed,
                        categories = cats,
                        balanceCurrency = expense.currencyConverted,
                        mainCurrency = expenseAccounts.firstOrNull { it.id == expense.accountId }?.currency
                            ?: expense.currencyConverted,
                        orderedCurrencies = ordered,
                        convertedAmount = expense.convertedAsBigDecimal(),
                        rateUsed = expense.rateAsBigDecimal(),
                        isEditMode = true,
                        createdAt = expense.createdAt,
                        accountId = expense.accountId,
                        balanceAccountId = expense.balanceAccountId,
                        paymentAccounts = expenseAccounts
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onAmountChanged(v: String) { _uiState.value = _uiState.value.copy(amount = v); calculateConversion() }
    fun onCurrencyChanged(v: String) {
        _uiState.value = _uiState.value.copy(currency = v)
        calculateConversion()
        // Persist recent currency
        viewModelScope.launch {
            try {
                val settings = financeRepository.getSettingsSync() ?: return@launch
                val newRecentJson = CurrencyProvider.addRecentCurrency(settings.recentCurrencies, v)
                financeRepository.saveSettings(settings.copy(recentCurrencies = newRecentJson))
                // Update ordered list
                val updatedSettings = settings.copy(recentCurrencies = newRecentJson)
                _uiState.value = _uiState.value.copy(orderedCurrencies = computeOrdered(updatedSettings))
            } catch (_: Exception) {}
        }
    }
    fun onCategoryChanged(id: Long?) { _uiState.value = _uiState.value.copy(categoryId = id) }

    /** Creates a custom category and selects it for the current expense. */
    fun createCategory(name: String, color: Int) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val id = financeRepository.insertCategory(
                Category(name = trimmed, iconName = "category", color = color)
            )
            val cats = financeRepository.getAllCategoriesSync()
            _uiState.update { it.copy(categories = cats, categoryId = id) }
        }
    }

    /** Deletes a custom category (expenses using it are kept, without category). */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            financeRepository.deleteCategory(category)
            val cats = financeRepository.getAllCategoriesSync()
            _uiState.update {
                it.copy(
                    categories = cats,
                    categoryId = if (it.categoryId == category.id) null else it.categoryId
                )
            }
        }
    }
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun selectPaymentAccount(id: Long) {
        val account = _uiState.value.paymentAccounts.firstOrNull { it.id == id } ?: return
        // The expense must convert into the currency of the account that pays it —
        // a cash balance can hold a different currency than main.
        _uiState.update {
            it.copy(balanceAccountId = id, paymentMethod = account.type, balanceCurrency = account.currency)
        }
        calculateConversion()
    }
    fun allowOverdraft(value: Boolean) { _uiState.update { it.copy(allowOverdraft = value) } }
    fun onManualRateChanged(v: String) { _uiState.value = _uiState.value.copy(manualRate = v); calculateConversion() }
    fun onUseManualRateChanged(v: Boolean) { _uiState.value = _uiState.value.copy(useManualRate = v); calculateConversion() }
    fun onDateChanged(v: Long) { _uiState.value = _uiState.value.copy(date = v) }

    private fun calculateConversion() {
        conversionJob?.cancel()
        conversionJob = viewModelScope.launch {
            val state = _uiState.value
            val amount = state.amount.trim().replace(',', '.').toBigDecimalOrNull()
            if (amount == null || state.currency.isBlank()) {
                _uiState.update { it.copy(convertedAmount = null, rateUsed = null) }
                return@launch
            }
            if (state.currency == state.balanceCurrency) {
                _uiState.update { it.copy(convertedAmount = amount, rateUsed = BigDecimal.ONE) }
                return@launch
            }
            val rate = if (state.useManualRate) {
                try { BigDecimal(state.manualRate.trim().replace(',', '.')) } catch (e: Exception) { null }
            } else {
                rateRepository.getConversionRate(state.currency, state.balanceCurrency)
            }
            if (rate != null) {
                val converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN)
                // Update only conversion fields so concurrently typed input is never reverted
                _uiState.update { it.copy(convertedAmount = converted, rateUsed = rate) }
            } else {
                _uiState.update { it.copy(convertedAmount = null, rateUsed = null) }
            }
        }
    }

    fun saveExpense(expenseId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value
                val amountOriginal = try { BigDecimal(state.amount.trim().replace(',', '.')) } catch (e: Exception) {
                    _uiState.value = state.copy(error = "Invalid amount", isLoading = false)
                    return@launch
                }
                if (amountOriginal <= BigDecimal.ZERO) {
                    _uiState.value = state.copy(error = "Amount must be positive", isLoading = false)
                    return@launch
                }
                val rate = if (state.currency == state.balanceCurrency) BigDecimal.ONE
                else if (state.useManualRate) {
                    try { BigDecimal(state.manualRate.trim().replace(',', '.')) } catch (e: Exception) {
                        _uiState.value = state.copy(error = "Invalid manual rate", isLoading = false)
                        return@launch
                    }
                } else {
                    rateRepository.getConversionRate(state.currency, state.balanceCurrency)
                        ?: run {
                            _uiState.value = state.copy(error = "No exchange rate available. Please enter manual rate.", isLoading = false)
                            return@launch
                        }
                }
                require(rate > BigDecimal.ZERO) { "Exchange rate must be positive" }
                val converted = amountOriginal.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)

                val expense = Expense(
                    id = expenseId ?: 0,
                    accountId = state.accountId ?: financeRepository.getActiveAccountId(),
                    amountOriginal = amountOriginal.toPlainString(),
                    currencyOriginal = state.currency,
                    amountConverted = converted.toPlainString(),
                    currencyConverted = state.balanceCurrency,
                    exchangeRateUsed = rate.toPlainString(),
                    categoryId = state.categoryId,
                    date = state.date,
                    note = state.note.ifBlank { null },
                    paymentMethod = state.paymentMethod.ifBlank { null },
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    balanceAccountId = state.balanceAccountId
                )
                if (state.isEditMode && expenseId != null) {
                    financeRepository.updateExpense(expense.copy(id = expenseId), state.allowOverdraft)
                } else {
                    financeRepository.insertExpense(expense, state.allowOverdraft)
                }
                _uiState.value = state.copy(isLoading = false)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
