package com.spendingtracker.app.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.util.CurrencyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    data class SetupUiState(
        val selectedCurrency: String = "USD",
        val startingBalance: String = "",
        val currencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    )

    fun onCurrencySelected(code: String) {
        _uiState.value = _uiState.value.copy(selectedCurrency = code)
    }

    fun onStartingBalanceChanged(value: String) {
        _uiState.value = _uiState.value.copy(startingBalance = value)
    }

    fun onSearchChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun completeSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Accept both "1.5" and locale-style "1,5" input
                val normalized = _uiState.value.startingBalance.trim().replace(',', '.')
                val balance = try {
                    if (normalized.isBlank()) BigDecimal.ZERO
                    else BigDecimal(normalized)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Invalid starting balance", isLoading = false)
                    return@launch
                }
                if (balance < BigDecimal.ZERO) {
                    _uiState.value = _uiState.value.copy(error = "Starting balance cannot be negative", isLoading = false)
                    return@launch
                }
                val settings = AppSettings(
                    balanceCurrency = _uiState.value.selectedCurrency,
                    startingBalance = balance.toPlainString(),
                    isSetupComplete = true,
                    theme = "SYSTEM"
                )
                financeRepository.saveSettings(settings)
                financeRepository.ensureDefaultCategories()
                _uiState.value = _uiState.value.copy(isLoading = false)
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
