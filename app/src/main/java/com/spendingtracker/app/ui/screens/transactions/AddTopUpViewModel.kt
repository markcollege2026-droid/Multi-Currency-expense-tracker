package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AddTopUpUiState(
    val amount: String = "",
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val balanceCurrency: String = "USD",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false,
    val createdAt: Long? = null
)

@HiltViewModel
class AddTopUpViewModel @Inject constructor(
    private val financeRepository: FinanceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddTopUpUiState())
    val uiState: StateFlow<AddTopUpUiState> = _uiState

    fun loadTopUp(topUpId: Long?) {
        if (topUpId == null) {
            viewModelScope.launch {
                val settings = financeRepository.getSettingsSync()
                _uiState.value = _uiState.value.copy(balanceCurrency = settings?.balanceCurrency ?: "USD")
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val topUp = financeRepository.getTopUpById(topUpId)
                val settings = financeRepository.getSettingsSync()
                if (topUp != null) {
                    _uiState.value = AddTopUpUiState(
                        amount = topUp.amount,
                        date = topUp.date,
                        note = topUp.note ?: "",
                        balanceCurrency = settings?.balanceCurrency ?: "USD",
                        isEditMode = true,
                        createdAt = topUp.createdAt
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onAmountChanged(v: String) { _uiState.value = _uiState.value.copy(amount = v) }
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onDateChanged(v: Long) { _uiState.value = _uiState.value.copy(date = v) }

    fun saveTopUp(topUpId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value
                val amount = try { BigDecimal(state.amount) } catch (e: Exception) {
                    _uiState.value = state.copy(error = "Invalid amount", isLoading = false)
                    return@launch
                }
                if (amount <= BigDecimal.ZERO) {
                    _uiState.value = state.copy(error = "Amount must be positive", isLoading = false)
                    return@launch
                }
                val topUp = TopUp(
                    id = topUpId ?: 0,
                    amount = amount.toPlainString(),
                    date = state.date,
                    note = state.note.ifBlank { null },
                    createdAt = state.createdAt ?: System.currentTimeMillis()
                )
                if (state.isEditMode && topUpId != null) {
                    financeRepository.updateTopUp(topUp.copy(id = topUpId))
                } else {
                    financeRepository.insertTopUp(topUp)
                }
                _uiState.value = state.copy(isLoading = false)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
