package com.spendingtracker.app.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.util.CurrencyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val accountName: String = "",
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

    fun onAccountNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(accountName = value)
    }

    fun onSearchChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun completeSetup(onComplete: () -> Unit) {
        // Re-entrancy guard: the button only disables on the next frame, so a double
        // tap used to insert two "Main" accounts in the same instant.
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val state = _uiState.value
                // Accept both "1.5" and locale-style "1,5" input
                val normalized = state.startingBalance.trim().replace(',', '.')
                val balance = try {
                    if (normalized.isBlank()) BigDecimal.ZERO else BigDecimal(normalized)
                } catch (e: Exception) {
                    _uiState.value = state.copy(error = "Invalid starting balance", isLoading = false)
                    return@launch
                }
                if (balance < BigDecimal.ZERO) {
                    _uiState.value = state.copy(error = "Starting balance cannot be negative", isLoading = false)
                    return@launch
                }
                // One transaction (account + active pointer + setup flag + categories),
                // and NonCancellable: closing the app from Recents during setup used to
                // leave an account that the next launch refused to open.
                val accountId = withContext(NonCancellable) {
                    financeRepository.completeSetup(
                        accountName = state.accountName.trim().ifBlank { "Main" },
                        currency = state.selectedCurrency,
                        startingBalance = balance.toPlainString()
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (accountId > 0) onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
