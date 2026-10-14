package com.spendingtracker.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class RecurringDraft(
    val id: Long? = null,
    val title: String = "",
    val amount: String = "",
    val currency: String = "USD",
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: String = RecurringPayment.FREQ_MONTHLY,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null
)

data class RecurringUiState(
    val payments: List<RecurringPayment> = emptyList(),
    val account: Account? = null,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val editing: RecurringDraft? = null,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                financeRepository.observeActiveAccount(),
                recurringRepository.getAll()
            ) { account, all -> account to all.filter { it.accountId == account?.id } }
                .collect { (account, payments) ->
                    _uiState.value = _uiState.value.copy(
                        payments = payments,
                        account = account,
                        categories = financeRepository.getAllCategoriesSync(),
                        isLoading = false
                    )
                }
        }
    }

    fun openAdd() {
        val account = _uiState.value.account ?: return
        _uiState.value = _uiState.value.copy(
            editing = RecurringDraft(currency = account.currency),
            error = null
        )
    }

    fun openEdit(payment: RecurringPayment) {
        _uiState.value = _uiState.value.copy(
            editing = RecurringDraft(
                id = payment.id,
                title = payment.title,
                amount = payment.amountOriginal,
                currency = payment.currencyOriginal,
                categoryId = payment.categoryId,
                note = payment.note ?: "",
                frequency = payment.frequency,
                startDate = payment.startDate,
                endDate = payment.endDate
            ),
            error = null
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(editing = null, error = null)
    }

    fun onDraftChanged(draft: RecurringDraft) {
        _uiState.value = _uiState.value.copy(editing = draft)
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val draft = state.editing ?: return@launch
            val account = state.account ?: return@launch
            if (draft.title.isBlank()) {
                _uiState.value = state.copy(error = "Title required")
                return@launch
            }
            val amount = try { BigDecimal(draft.amount.trim().replace(',', '.')) } catch (e: Exception) {
                _uiState.value = state.copy(error = "Invalid amount")
                return@launch
            }
            if (amount <= BigDecimal.ZERO) {
                _uiState.value = state.copy(error = "Amount must be positive")
                return@launch
            }

            val existing = draft.id?.let { recurringRepository.getById(it) }
            val startDateChanged = existing == null || existing.startDate != draft.startDate
            val nextDueAt = when {
                existing == null -> draft.startDate
                startDateChanged -> draft.startDate // schedule restarts from the new start date
                else -> existing.nextDueAt
            }

            val payment = RecurringPayment(
                id = draft.id ?: 0,
                accountId = account.id,
                title = draft.title.trim(),
                amountOriginal = amount.toPlainString(),
                currencyOriginal = draft.currency,
                categoryId = draft.categoryId,
                note = draft.note.trim().ifBlank { null },
                paymentMethod = existing?.paymentMethod,
                frequency = draft.frequency,
                startDate = draft.startDate,
                endDate = draft.endDate,
                nextDueAt = nextDueAt,
                lastRunAt = existing?.lastRunAt,
                isActive = true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            if (existing != null) {
                recurringRepository.update(payment)
                _uiState.value = _uiState.value.copy(editing = null, message = "Recurring payment updated", error = null)
            } else {
                recurringRepository.insert(payment)
                _uiState.value = _uiState.value.copy(editing = null, message = "Recurring payment created", error = null)
            }
        }
    }

    fun delete(payment: RecurringPayment) {
        viewModelScope.launch {
            recurringRepository.delete(payment)
            _uiState.value = _uiState.value.copy(message = "Recurring payment deleted")
        }
    }

    fun toggleActive(payment: RecurringPayment) {
        viewModelScope.launch {
            recurringRepository.update(
                payment.copy(
                    isActive = !payment.isActive,
                    // Resuming restarts from the next future occurrence
                    nextDueAt = if (payment.isActive) {
                        payment.nextDueAt
                    } else {
                        maxOf(payment.nextDueAt, System.currentTimeMillis())
                    }
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
