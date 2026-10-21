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
    val createdAt: Long? = null,
    val accountId: Long? = null
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
                val account = financeRepository.getAccountById(financeRepository.getActiveAccountId())
                _uiState.value = _uiState.value.copy(
                    balanceCurrency = account?.currency ?: "USD",
                    accountId = account?.id
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val topUp = financeRepository.getTopUpById(topUpId)
                val account = financeRepository.getAccountById(
                    topUp?.accountId ?: financeRepository.getActiveAccountId()
                )
                if (topUp != null) {
                    _uiState.value = AddTopUpUiState(
                        amount = topUp.amount,
                        date = topUp.date,
                        note = topUp.note ?: "",
                        balanceCurrency = account?.currency ?: "USD",
                        isEditMode = true,
                        createdAt = topUp.createdAt,
                        accountId = topUp.accountId
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
                val amount = try { BigDecimal(state.amount.trim().replace(',', '.')) } catch (e: Exception) {
                    _uiState.value = state.copy(error = "Invalid amount", isLoading = false)
                    return@launch
                }
                if (amount <= BigDecimal.ZERO) {
                    _uiState.value = state.copy(error = "Amount must be positive", isLoading = false)
                    return@launch
                }
                val topUp = TopUp(
                    id = topUpId ?: 0,
                    accountId = state.accountId ?: financeRepository.getActiveAccountId(),
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

    /** Deletes the income entry being edited; the caller closes the screen on success. */
    fun deleteTopUp(topUpId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val existing = financeRepository.getTopUpById(topUpId)
                if (existing == null) {
                    _uiState.value = _uiState.value.copy(error = "Entry no longer exists", isLoading = false)
                    return@launch
                }
                financeRepository.deleteTopUp(existing)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onDeleted()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }
}
