package com.spendingtracker.app.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AddLoanUiState(
    val type: String = LoanDebt.TYPE_DEBT,
    val personName: String = "",
    val amount: String = "",
    val currency: String = "USD",
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val note: String = "",
    val isSettled: Boolean = false,
    val balanceCurrency: String = "USD",
    val repayments: List<Repayment> = emptyList(),
    val newRepaymentAmount: String = "",
    val newRepaymentNote: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class AddLoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLoanUiState())
    val uiState: StateFlow<AddLoanUiState> = _uiState

    fun loadLoan(loanId: Long?) {
        if (loanId == null) {
            viewModelScope.launch {
                val settings = financeRepository.getSettingsSync()
                _uiState.value = _uiState.value.copy(balanceCurrency = settings?.balanceCurrency ?: "USD", currency = settings?.balanceCurrency ?: "USD")
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val loan = loanRepository.getLoanDebtById(loanId)
                val settings = financeRepository.getSettingsSync()
                val reps = loanRepository.getRepaymentsForLoanSync(loanId)
                if (loan != null) {
                    _uiState.value = AddLoanUiState(
                        type = loan.type,
                        personName = loan.personName,
                        amount = loan.amountOriginal,
                        currency = loan.currencyOriginal,
                        date = loan.date,
                        dueDate = loan.dueDate,
                        note = loan.note ?: "",
                        isSettled = loan.isSettled,
                        balanceCurrency = settings?.balanceCurrency ?: "USD",
                        repayments = reps,
                        isEditMode = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onTypeChanged(t: String) { _uiState.value = _uiState.value.copy(type = t) }
    fun onPersonChanged(v: String) { _uiState.value = _uiState.value.copy(personName = v) }
    fun onAmountChanged(v: String) { _uiState.value = _uiState.value.copy(amount = v) }
    fun onCurrencyChanged(v: String) { _uiState.value = _uiState.value.copy(currency = v) }
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onDueDateChanged(v: Long?) { _uiState.value = _uiState.value.copy(dueDate = v) }
    fun onRepaymentAmountChanged(v: String) { _uiState.value = _uiState.value.copy(newRepaymentAmount = v) }
    fun onRepaymentNoteChanged(v: String) { _uiState.value = _uiState.value.copy(newRepaymentNote = v) }

    fun saveLoan(loanId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val s = _uiState.value
                if (s.personName.isBlank()) {
                    _uiState.value = s.copy(error = "Person name required", isLoading = false); return@launch
                }
                val amount = try { BigDecimal(s.amount) } catch (e: Exception) {
                    _uiState.value = s.copy(error = "Invalid amount", isLoading = false); return@launch
                }
                if (amount <= BigDecimal.ZERO) {
                    _uiState.value = s.copy(error = "Amount must be positive", isLoading = false); return@launch
                }
                val loan = LoanDebt(
                    id = loanId ?: 0,
                    type = s.type,
                    personName = s.personName,
                    amountOriginal = amount.toPlainString(),
                    currencyOriginal = s.currency,
                    date = s.date,
                    dueDate = s.dueDate,
                    note = s.note.ifBlank { null },
                    isSettled = s.isSettled
                )
                if (s.isEditMode && loanId != null) loanRepository.updateLoanDebt(loan.copy(id = loanId))
                else loanRepository.insertLoanDebt(loan)
                _uiState.value = s.copy(isLoading = false)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun addRepayment(loanId: Long) {
        viewModelScope.launch {
            val s = _uiState.value
            val amount = try { BigDecimal(s.newRepaymentAmount) } catch (e: Exception) {
                _uiState.value = s.copy(error = "Invalid repayment amount"); return@launch
            }
            if (amount <= BigDecimal.ZERO) {
                _uiState.value = s.copy(error = "Repayment must be positive"); return@launch
            }
            val repayment = Repayment(
                loanDebtId = loanId,
                amount = amount.toPlainString(),
                date = System.currentTimeMillis(),
                note = s.newRepaymentNote.ifBlank { null }
            )
            loanRepository.insertRepayment(repayment)
            // reload
            val reps = loanRepository.getRepaymentsForLoanSync(loanId)
            _uiState.value = s.copy(repayments = reps, newRepaymentAmount = "", newRepaymentNote = "", error = null)
        }
    }

    fun deleteRepayment(repayment: Repayment, loanId: Long) {
        viewModelScope.launch {
            loanRepository.deleteRepayment(repayment)
            val reps = loanRepository.getRepaymentsForLoanSync(loanId)
            _uiState.value = _uiState.value.copy(repayments = reps)
        }
    }
}
