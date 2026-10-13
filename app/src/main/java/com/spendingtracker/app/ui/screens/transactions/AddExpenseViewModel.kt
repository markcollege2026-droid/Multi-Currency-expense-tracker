package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.util.CurrencyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    val convertedAmount: BigDecimal? = null,
    val rateUsed: BigDecimal? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState

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
                val settings = financeRepository.getSettingsSync()
                val cats = financeRepository.getAllCategoriesSync()
                val ordered = computeOrdered(settings)
                _uiState.value = _uiState.value.copy(
                    balanceCurrency = settings?.balanceCurrency ?: "USD",
                    categories = cats,
                    currency = settings?.balanceCurrency ?: "USD",
                    orderedCurrencies = ordered
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
                    _uiState.value = AddExpenseUiState(
                        amount = expense.amountOriginal,
                        currency = expense.currencyOriginal,
                        categoryId = expense.categoryId,
                        date = expense.date,
                        note = expense.note ?: "",
                        paymentMethod = expense.paymentMethod ?: "",
                        manualRate = expense.exchangeRateUsed,
                        categories = cats,
                        balanceCurrency = settings?.balanceCurrency ?: expense.currencyConverted,
                        orderedCurrencies = ordered,
                        convertedAmount = expense.convertedAsBigDecimal(),
                        rateUsed = expense.rateAsBigDecimal(),
                        isEditMode = true
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
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onPaymentMethodChanged(v: String) { _uiState.value = _uiState.value.copy(paymentMethod = v) }
    fun onManualRateChanged(v: String) { _uiState.value = _uiState.value.copy(manualRate = v); calculateConversion() }
    fun onUseManualRateChanged(v: Boolean) { _uiState.value = _uiState.value.copy(useManualRate = v); calculateConversion() }
    fun onDateChanged(v: Long) { _uiState.value = _uiState.value.copy(date = v) }

    private fun calculateConversion() {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = try { BigDecimal(state.amount) } catch (e: Exception) { null }
            if (amount == null || state.currency.isBlank()) {
                _uiState.value = state.copy(convertedAmount = null, rateUsed = null)
                return@launch
            }
            if (state.currency == state.balanceCurrency) {
                _uiState.value = state.copy(convertedAmount = amount, rateUsed = BigDecimal.ONE)
                return@launch
            }
            val rate = if (state.useManualRate) {
                try { BigDecimal(state.manualRate) } catch (e: Exception) { null }
            } else {
                rateRepository.getConversionRate(state.currency, state.balanceCurrency)
            }
            if (rate != null) {
                val converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN)
                _uiState.value = state.copy(convertedAmount = converted, rateUsed = rate)
            } else {
                _uiState.value = state.copy(convertedAmount = null, rateUsed = null)
            }
        }
    }

    fun saveExpense(expenseId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value
                val amountOriginal = try { BigDecimal(state.amount) } catch (e: Exception) {
                    _uiState.value = state.copy(error = "Invalid amount", isLoading = false)
                    return@launch
                }
                if (amountOriginal <= BigDecimal.ZERO) {
                    _uiState.value = state.copy(error = "Amount must be positive", isLoading = false)
                    return@launch
                }
                val rate = if (state.currency == state.balanceCurrency) BigDecimal.ONE
                else if (state.useManualRate) {
                    try { BigDecimal(state.manualRate) } catch (e: Exception) {
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
                val converted = amountOriginal.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)

                val expense = Expense(
                    id = expenseId ?: 0,
                    amountOriginal = amountOriginal.toPlainString(),
                    currencyOriginal = state.currency,
                    amountConverted = converted.toPlainString(),
                    currencyConverted = state.balanceCurrency,
                    exchangeRateUsed = rate.toPlainString(),
                    categoryId = state.categoryId,
                    date = state.date,
                    note = state.note.ifBlank { null },
                    paymentMethod = state.paymentMethod.ifBlank { null },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (state.isEditMode && expenseId != null) {
                    financeRepository.updateExpense(expense.copy(id = expenseId))
                } else {
                    financeRepository.insertExpense(expense)
                }
                _uiState.value = state.copy(isLoading = false)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
