package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.domain.TransferMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.spendingtracker.app.util.parseAmountOrNull

data class AddTopUpUiState(
    val amount: String = "",
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val balanceCurrency: String = "USD",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false,
    val createdAt: Long? = null,
    val accountId: Long? = null,
    /** Cash balances of the active main account: an optional funding source. */
    val cashAccounts: List<Account> = emptyList(),
    val cashBalances: Map<Long, BigDecimal> = emptyMap(),
    /** null (the default) means the deposit is external and does not touch cash. */
    val sourceCashId: Long? = null,
    /** 1 [cashCurrency] expressed in the main currency. */
    val rate: BigDecimal = BigDecimal.ONE
) {
    val sourceCash: Account? get() = cashAccounts.firstOrNull { it.id == sourceCashId }

    /** Cash needed for the typed deposit, when it is funded from a cash balance. */
    fun cashAmount(): BigDecimal? {
        val parsed = amount.parseAmountOrNull() ?: return null
        val cash = sourceCash ?: return null
        if (cash.currency == balanceCurrency) return parsed
        if (rate <= BigDecimal.ZERO) return null
        return parsed.divide(rate, TransferMath.SCALE, RoundingMode.HALF_EVEN)
    }
}

/** Result of validating the deposit form, kept pure so it can be unit-tested. */
data class DepositFormState(
    /** Amount added to main, in the main currency. */
    val amount: BigDecimal?,
    /** Amount that would leave the cash balance, in the cash currency. */
    val cashAmount: BigDecimal?,
    val error: String?,
    val canSave: Boolean
)

/**
 * A deposit is either external money (the default) or a transfer funded from one
 * of the cash balances. When a cash balance funds it, the cash side is derived
 * from the rate and may not exceed that balance.
 */
fun depositFormState(
    amount: String,
    rate: BigDecimal,
    cashBalance: BigDecimal?,
    cashCurrency: String?
): DepositFormState {
    val parsed = amount.parseAmountOrNull()?.takeIf { it > BigDecimal.ZERO }
    if (parsed == null) {
        return DepositFormState(
            amount = null,
            cashAmount = null,
            error = if (amount.isNotBlank()) "Enter a positive amount" else null,
            canSave = false
        )
    }
    if (cashBalance == null) {
        // External deposit: nothing leaves cash.
        return DepositFormState(parsed, null, null, true)
    }
    val cashAmount = if (rate > BigDecimal.ZERO) {
        parsed.divide(rate, TransferMath.SCALE, RoundingMode.HALF_EVEN)
    } else {
        parsed
    }
    val error = when {
        cashAmount <= BigDecimal.ZERO -> "Amount is too small after conversion"
        cashAmount > cashBalance ->
            "More than the ${cashCurrency ?: "cash"} balance holds"
        else -> null
    }
    return DepositFormState(
        amount = parsed,
        cashAmount = cashAmount,
        error = error,
        canSave = error == null
    )
}

@HiltViewModel
class AddTopUpViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddTopUpUiState())
    val uiState: StateFlow<AddTopUpUiState> = _uiState

    fun loadTopUp(topUpId: Long?) {
        if (topUpId == null) {
            viewModelScope.launch {
                val activeId = financeRepository.getActiveAccountId()
                val account = financeRepository.getAccountById(activeId)
                val accounts = financeRepository.getAllAccountsSync()
                val cash = accounts.filter { it.parentAccountId == activeId && it.type == "CASH" }
                // balances() is suspend; reading it inside copy() meant the state was
                // captured first and typed input could be overwritten afterwards.
                val cashBalances = financeRepository.balances()
                _uiState.update {
                    it.copy(
                        balanceCurrency = account?.currency ?: "USD",
                        accountId = account?.id,
                        cashAccounts = cash,
                        cashBalances = cashBalances,
                        // The default is a deposit that does not come from cash.
                        sourceCashId = null,
                        rate = BigDecimal.ONE
                    )
                }
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

    fun onAmountChanged(v: String) { _uiState.value = _uiState.value.copy(amount = v, error = null) }
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onDateChanged(v: Long) { _uiState.value = _uiState.value.copy(date = v) }

    /** Chooses the funding cash balance; null restores an external deposit. */
    fun selectSource(cashId: Long?) {
        val cash = _uiState.value.cashAccounts.firstOrNull { it.id == cashId }
        _uiState.value = _uiState.value.copy(sourceCashId = cash?.id, error = null)
        if (cash == null) {
            _uiState.value = _uiState.value.copy(rate = BigDecimal.ONE)
            return
        }
        if (cash.currency == _uiState.value.balanceCurrency) {
            _uiState.value = _uiState.value.copy(rate = BigDecimal.ONE)
            return
        }
        viewModelScope.launch {
            val rate = rateRepository.getConversionRateOrFallback(
                cash.currency,
                _uiState.value.balanceCurrency
            )
            _uiState.value = _uiState.value.copy(rate = if (rate > BigDecimal.ZERO) rate else BigDecimal.ONE)
        }
    }

    /** Whether [amount] can be saved right now. */
    fun formState(): DepositFormState {
        val state = _uiState.value
        val cash = state.sourceCash
        return depositFormState(
            amount = state.amount,
            rate = state.rate,
            cashBalance = if (cash == null) null else state.cashBalances[cash.id] ?: BigDecimal.ZERO,
            cashCurrency = cash?.currency
        )
    }

    fun saveTopUp(topUpId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val form = formState()
            if (form.amount == null || !form.canSave) {
                _uiState.value = state.copy(
                    error = form.error ?: "Enter a positive amount",
                    isLoading = false
                )
                return@launch
            }
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val mainId = state.accountId ?: financeRepository.getActiveAccountId()
                val cash = state.sourceCash
                val cashAmount = form.cashAmount
                if (cash != null && cashAmount == null) {
                    // Never record a transfer with nothing leaving the source.
                    _uiState.value = _uiState.value.copy(
                        error = "Enter a positive amount",
                        isLoading = false
                    )
                    return@launch
                }
                if (cash != null && cashAmount != null) {
                    // Funded from cash: this is a transfer into main, not new money.
                    financeRepository.withdraw(
                        fromAccountId = cash.id,
                        toAccountId = mainId,
                        amountFrom = cashAmount,
                        rate = state.rate,
                        note = state.note.ifBlank { null },
                        amountTo = requireNotNull(form.amount)
                    )
                } else {
                    val topUp = TopUp(
                        id = topUpId ?: 0,
                        accountId = mainId,
                        amount = requireNotNull(form.amount).toPlainString(),
                        date = state.date,
                        note = state.note.ifBlank { null },
                        createdAt = state.createdAt ?: System.currentTimeMillis()
                    )
                    if (state.isEditMode && topUpId != null) {
                        financeRepository.updateTopUp(topUp.copy(id = topUpId))
                    } else {
                        financeRepository.insertTopUp(topUp)
                    }
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSaved()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    /** Deletes the deposit being edited; the caller closes the screen on success. */
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
