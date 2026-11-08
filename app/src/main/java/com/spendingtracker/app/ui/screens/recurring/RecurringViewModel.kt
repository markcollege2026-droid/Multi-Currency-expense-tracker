package com.spendingtracker.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.spendingtracker.app.util.CurrencyProvider

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
    val orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    val error: String? = null,
    val message: String? = null,
    /** What is running right now, or null. Guards against repeated taps. */
    val busy: String? = null,
    /**
     * Set when the initial flows failed. Shown with a retry instead of an
     * infinite spinner.
     */
    val loadError: String? = null
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _uiState

    init {
        observeData()
    }

    private var dataJob: kotlinx.coroutines.Job? = null

    private fun observeData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                financeRepository.observeActiveAccount(),
                recurringRepository.getAll()
            ) { account, all -> account to all.filter { it.accountId == account?.id } }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "Recurring payments could not load (" +
                                (failure.message ?: "unknown error") + "). Tap to retry."
                        )
                    }
                }
                .collect { (account, payments) ->
                    // Fetch first, assign second. A suspend call inside the copy()
                    // argument list captured the state before suspending, so an
                    // emission could put back `editing = null` and close the
                    // add/edit dialog while the user was typing in it.
                    val categories = financeRepository.getAllCategoriesSync()
                    val ordered = financeRepository.orderedCurrencies()
                    _uiState.update {
                        it.copy(
                            payments = payments,
                            account = account,
                            categories = categories,
                            orderedCurrencies = ordered,
                            isLoading = false,
                            loadError = null
                        )
                    }
                }
        }
    }

    /** Re-runs the collectors after a load failure. */
    fun retry() {
        _uiState.update { it.copy(loadError = null, isLoading = true) }
        observeData()
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
        val previous = _uiState.value.editing
        _uiState.value = _uiState.value.copy(editing = draft)
        if (previous != null && previous.currency != draft.currency) {
            viewModelScope.launch {
                financeRepository.recordRecentCurrency(draft.currency)
                val ordered = financeRepository.orderedCurrencies()
                _uiState.update { it.copy(orderedCurrencies = ordered) }
            }
        }
    }

    /**
     * Starts one repository operation with a busy guard: while anything is
     * running, further taps are refused instead of queueing duplicate work
     * (a double tap on Save used to create the payment twice).
     *
     * @return true when the operation started, false when it was refused.
     */
    private fun startBusy(label: String): Boolean {
        val current = _uiState.value.busy
        if (current != null) {
            _uiState.update { it.copy(message = "Still busy: $current") }
            return false
        }
        _uiState.update { it.copy(busy = label, error = null) }
        return true
    }

    private fun endBusy() {
        _uiState.update { it.copy(busy = null) }
    }

    fun save() {
        val state = _uiState.value
        val draft = state.editing ?: return
        val account = state.account ?: return
        if (draft.title.isBlank()) {
            _uiState.value = state.copy(error = "Title required")
            return
        }
        val amount = try { BigDecimal(draft.amount.trim().replace(',', '.')) } catch (e: NumberFormatException) {
            _uiState.value = state.copy(error = "Invalid amount")
            return
        }
        if (amount <= BigDecimal.ZERO) {
            _uiState.value = state.copy(error = "Amount must be positive")
            return
        }
        if (!startBusy(if (draft.id == null) "Creating recurring payment…" else "Saving recurring payment…")) return

        viewModelScope.launch {
            try {
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
                    _uiState.update {
                        it.copy(editing = null, message = "Recurring payment updated", error = null)
                    }
                } else {
                    recurringRepository.insert(payment)
                    _uiState.update {
                        it.copy(editing = null, message = "Recurring payment created", error = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The dialog stays open with the typed values; the error shows in it.
                _uiState.update {
                    it.copy(error = e.message ?: "Could not save the recurring payment")
                }
            } finally {
                endBusy()
            }
        }
    }

    fun delete(payment: RecurringPayment) {
        if (!startBusy("Deleting \"${payment.title}\"…")) return
        viewModelScope.launch {
            try {
                recurringRepository.delete(payment)
                _uiState.update { it.copy(message = "Recurring payment deleted", error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = e.message ?: "Unable to delete", error = null)
                }
            } finally {
                endBusy()
            }
        }
    }

    fun toggleActive(payment: RecurringPayment) {
        if (!startBusy(if (payment.isActive) "Pausing \"${payment.title}\"…" else "Resuming \"${payment.title}\"…")) return
        viewModelScope.launch {
            try {
                val resuming = !payment.isActive
                recurringRepository.update(
                    payment.copy(
                        isActive = resuming,
                        // Resuming restarts from the next future occurrence
                        nextDueAt = if (resuming) {
                            maxOf(payment.nextDueAt, System.currentTimeMillis())
                        } else {
                            payment.nextDueAt
                        }
                    )
                )
                _uiState.update {
                    it.copy(
                        message = if (resuming) "\"${payment.title}\" resumes posting" else "\"${payment.title}\" paused",
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = e.message ?: "Unable to update", error = null)
                }
            } finally {
                endBusy()
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}
