package com.spendingtracker.app.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.domain.SettlementAccounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.spendingtracker.app.domain.LoanCalculator
import com.spendingtracker.app.util.CurrencyProvider
import com.spendingtracker.app.util.parseAmountOrNull

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
    /** Still owed, in [currency]; drives the settle dialog's default amount. */
    val remaining: BigDecimal = BigDecimal.ZERO,
    /** Balances a payment can move through - every ledger, not just the active one. */
    val settleAccounts: List<Account> = emptyList(),
    val accountBalances: Map<Long, BigDecimal> = emptyMap(),
    /** Ledger being viewed, used to shorten the dropdown labels. */
    val activeAccountId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isEditMode: Boolean = false,
    val createdAt: Long? = null,
    // Settlement bookkeeping is carried through edits untouched - saving an
    // archived loan must never un-settle it or orphan its settlement transaction.
    val settledAt: Long? = null,
    val settlementRepaymentId: Long? = null,
    val orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies
)

@HiltViewModel
class AddLoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val loanCalculator: LoanCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLoanUiState())
    val uiState: StateFlow<AddLoanUiState> = _uiState

    /**
     * Loads a loan for editing, or starts a fresh form of [initialType]
     * ("DEBT" = I owe, "LOAN" = owed to me) chosen before the form opened.
     */
    fun loadLoan(loanId: Long?, initialType: String? = null) {
        if (loanId == null) {
            val chosenType = initialType?.takeIf { it == LoanDebt.TYPE_DEBT || it == LoanDebt.TYPE_LOAN }
            // Apply the chosen type immediately (and reset any stale form state:
            // reusing this view model for the other type must not carry fields over).
            _uiState.value = AddLoanUiState(type = chosenType ?: LoanDebt.TYPE_DEBT)
            viewModelScope.launch {
                val account = financeRepository.getAccountById(financeRepository.getActiveAccountId())
                _uiState.value = _uiState.value.copy(
                    balanceCurrency = account?.currency ?: "USD",
                    currency = account?.currency ?: "USD",
                    orderedCurrencies = financeRepository.orderedCurrencies(),
                    settleAccounts = SettlementAccounts.options(
                        financeRepository.getAllAccountsSync(),
                        account?.id
                    ),
                    accountBalances = financeRepository.balances(),
                    activeAccountId = account?.id
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val loan = loanRepository.getLoanDebtById(loanId)
                val settings = financeRepository.getSettingsSync()
                val account = financeRepository.getAccountById(financeRepository.getActiveAccountId())
                val reps = loanRepository.getRepaymentsForLoanSync(loanId)
                val remaining = loan?.let { loanCalculator.calculateRemaining(it.amountAsBigDecimal(), reps.map { r -> r.amountAsBigDecimal() }).remaining } ?: BigDecimal.ZERO
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
                        balanceCurrency = account?.currency ?: "USD",
                        repayments = reps,
                        remaining = remaining,
                        settleAccounts = SettlementAccounts.options(
                            financeRepository.getAllAccountsSync(),
                            account?.id
                        ),
                        accountBalances = financeRepository.balances(),
                        activeAccountId = account?.id,
                        isEditMode = true,
                        createdAt = loan.createdAt,
                        settledAt = loan.settledAt,
                        settlementRepaymentId = loan.settlementRepaymentId,
                        orderedCurrencies = financeRepository.orderedCurrencies()
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
    fun onCurrencyChanged(v: String) {
        _uiState.value = _uiState.value.copy(currency = v)
        viewModelScope.launch {
            financeRepository.recordRecentCurrency(v)
            _uiState.value = _uiState.value.copy(orderedCurrencies = financeRepository.orderedCurrencies())
        }
    }
    fun onNoteChanged(v: String) { _uiState.value = _uiState.value.copy(note = v) }
    fun onDueDateChanged(v: Long?) { _uiState.value = _uiState.value.copy(dueDate = v) }

    fun saveLoan(loanId: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val s = _uiState.value
                if (s.personName.isBlank()) {
                    _uiState.value = s.copy(error = "Person name required", isLoading = false); return@launch
                }
                val amount = try { BigDecimal(s.amount.trim().replace(',', '.')) } catch (e: Exception) {
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
                    isSettled = s.isSettled,
                    createdAt = s.createdAt ?: System.currentTimeMillis(),
                    settledAt = s.settledAt,
                    settlementRepaymentId = s.settlementRepaymentId
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

    /**
     * Records a payment on this loan through the same rules as the Loans tab.
     *
     * The old form here called [FinanceRepository.recordRepayment] with no account
     * and no rate, so a loan could only ever be repaid in its own currency from
     * outside every balance. [accountId] null still means external revenue;
     * otherwise the money moves through that balance, converted with [rate].
     */
    fun settle(
        loanId: Long,
        amountLoanCurrency: String,
        accountId: Long?,
        rate: String,
        note: String?
    ) {
        viewModelScope.launch {
            val amount = amountLoanCurrency.parseAmountOrNull()
            if (amount == null || amount <= BigDecimal.ZERO) {
                _uiState.value = _uiState.value.copy(error = "Enter a positive amount")
                return@launch
            }
            val parsedRate = rate.parseAmountOrNull()
                ?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
            try {
                financeRepository.recordRepayment(
                    loanId = loanId,
                    amount = amount,
                    accountId = accountId,
                    rate = parsedRate,
                    note = note
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Unable to record repayment")
                return@launch
            }
            _uiState.value = _uiState.value.copy(message = "Payment recorded")
            reloadLoan(loanId)
        }
    }

    /** Strict rate for the settle dialog: null when this device has no quote. */
    suspend fun conversionRate(fromCurrency: String, toCurrency: String): BigDecimal? =
        rateRepository.getConversionRateStrict(fromCurrency, toCurrency)

    fun deleteRepayment(repayment: Repayment, loanId: Long) {
        viewModelScope.launch {
            // Reopens the loan (and reverses its money) when this was the
            // transaction that settled it.
            financeRepository.deleteRepayment(repayment)
            reloadLoan(loanId)
        }
    }

    /** Refreshes repayments and settled state after money moved on this loan. */
    private suspend fun reloadLoan(loanId: Long) {
        val reps = loanRepository.getRepaymentsForLoanSync(loanId)
        val loan = loanRepository.getLoanDebtById(loanId)
        val active = financeRepository.getAccountById(financeRepository.getActiveAccountId())
        _uiState.value = _uiState.value.copy(
            repayments = reps,
            remaining = loan?.let {
                loanCalculator.calculateRemaining(it.amountAsBigDecimal(), reps.map { r -> r.amountAsBigDecimal() }).remaining
            } ?: BigDecimal.ZERO,
            isSettled = loan?.isSettled ?: _uiState.value.isSettled,
            settledAt = loan?.settledAt,
            settlementRepaymentId = loan?.settlementRepaymentId,
            settleAccounts = SettlementAccounts.options(financeRepository.getAllAccountsSync(), active?.id),
            accountBalances = financeRepository.balances(),
            error = null
        )
    }
}
