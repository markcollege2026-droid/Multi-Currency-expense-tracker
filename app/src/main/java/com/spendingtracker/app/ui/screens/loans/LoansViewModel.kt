package com.spendingtracker.app.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.domain.LoanCalculator
import com.spendingtracker.app.domain.SettlementRate
import com.spendingtracker.app.domain.SettlementAccounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.spendingtracker.app.util.parseAmountOrNull

data class LoanWithBalance(
    val loan: LoanDebt,
    val remaining: BigDecimal,
    val totalRepaid: BigDecimal,
    val progress: Float,
    val isOverdue: Boolean,
    val convertedAmount: BigDecimal? = null,
    val convertedRemaining: BigDecimal? = null
)

data class LoansUiState(
    /** Active debts ("I owe") - settled loans never appear here. */
    val debts: List<LoanWithBalance> = emptyList(),
    /** Active loans ("owed to me"). */
    val loans: List<LoanWithBalance> = emptyList(),
    /** Settled loans of both kinds, most recently settled first. */
    val archived: List<LoanWithBalance> = emptyList(),
    val totalOwedByMe: BigDecimal = BigDecimal.ZERO,
    val totalOwedToMe: BigDecimal = BigDecimal.ZERO,
    val balanceCurrency: String = "USD",
    /** Main + cash balances a settlement can move money through, active ledger first. */
    val settleAccounts: List<Account> = emptyList(),
    /** Ledger the user is looking at; only used to shorten the dropdown labels. */
    val activeAccountId: Long? = null,
    val accountBalances: Map<Long, BigDecimal> = emptyMap(),
    val message: String? = null,
    /**
     * One-shot toast, cleared by the screen once shown. Refusals and results that
     * arrive too fast for a spinner used to be drawn only in the inline banner at
     * the top of the list, which is off-screen when the user has scrolled to the
     * loan they tapped - so the action looked like it did nothing.
     */
    val toast: String? = null,
    /** What is running right now, or null. Shown as an inline progress row. */
    val busy: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val loanCalculator: LoanCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                loanRepository.getAllLoanDebts(),
                financeRepository.observeActiveAccount(),
                financeRepository.observeBalanceAccounts(),
                financeRepository.observeAccountSummaries()
            ) { allLoans, account, accounts, summaries ->
                val balanceCurrency = account?.currency ?: "USD"
                val debts = mutableListOf<LoanWithBalance>()
                val loans = mutableListOf<LoanWithBalance>()
                val archived = mutableListOf<LoanWithBalance>()
                var totalDebtConverted = BigDecimal.ZERO
                var totalLoanConverted = BigDecimal.ZERO

                // One bulk query instead of one query per loan (N+1)
                val repaymentsByLoan = loanRepository.getAllRepaymentsSync().groupBy { it.loanDebtId }

                for (loan in allLoans) {
                    val repayments = repaymentsByLoan[loan.id].orEmpty()
                    val repaymentAmounts = repayments.map { try { BigDecimal(it.amount) } catch (e: Exception) { BigDecimal.ZERO } }
                    val calc = loanCalculator.calculateRemaining(loan.amountAsBigDecimal(), repaymentAmounts)

                    // Convert to balance currency for summary
                    val rate = rateRepository.getConversionRate(loan.currencyOriginal, balanceCurrency) ?: BigDecimal.ONE
                    val convertedOriginal = loan.amountAsBigDecimal().multiply(rate)
                    val convertedRemaining = calc.remaining.multiply(rate)
                    val item = LoanWithBalance(
                        loan = loan,
                        remaining = calc.remaining,
                        totalRepaid = calc.totalRepaid,
                        progress = calc.progress,
                        isOverdue = loan.isOverdue(),
                        convertedAmount = convertedOriginal,
                        convertedRemaining = convertedRemaining
                    )
                    when {
                        // Settled loans live in the archive and count toward nothing.
                        loan.isSettled -> archived.add(item)
                        loan.type == LoanDebt.TYPE_DEBT -> {
                            totalDebtConverted += convertedRemaining
                            debts.add(item)
                        }
                        else -> {
                            totalLoanConverted += convertedRemaining
                            loans.add(item)
                        }
                    }
                }

                LoansUiState(
                    debts = debts.sortedBy { it.loan.dueDate ?: Long.MAX_VALUE },
                    loans = loans.sortedBy { it.loan.dueDate ?: Long.MAX_VALUE },
                    archived = archived.sortedByDescending { it.loan.settledAt ?: it.loan.date },
                    totalOwedByMe = totalDebtConverted,
                    totalOwedToMe = totalLoanConverted,
                    balanceCurrency = balanceCurrency,
                    // Loans are shared, so every main account and every cash balance
                    // anywhere can settle one - not only the active ledger's own
                    // balances, which made the other accounts unreachable.
                    settleAccounts = SettlementAccounts.options(accounts, account?.id),
                    activeAccountId = account?.id,
                    accountBalances = summaries.mapValues { it.value.balance },
                    message = _uiState.value.message,
                    busy = _uiState.value.busy,
                    isLoading = false
                )
            }
                // Aggregation + rate lookups run off the main thread
                .flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }
    }

    fun deleteLoan(loan: LoanDebt) {
        if (busyStarting("Deleting \"${loan.personName}\"\u2026")) return
        viewModelScope.launch {
            try {
                // Repayments are removed by the foreign-key cascade, which also
                // reverses any settlement money still sitting in a balance.
                loanRepository.deleteLoanDebt(loan)
                _uiState.update { it.copy(message = "\"${loan.personName}\" deleted") }
            } catch (e: Exception) {
                val failure = e.message ?: "Unable to delete"
                _uiState.update { it.copy(message = failure, toast = failure) }
            } finally {
                busyDone()
            }
        }
    }

    /**
     * Marks work as started and returns false when something else is already
     * running, in which case the caller must not start. Refusals are reported
     * through [LoansUiState.message] so the screen can say why nothing happened.
     */
    private fun busyStarting(label: String): Boolean {
        val current = _uiState.value.busy
        if (current != null) {
            val refusal = "Still busy: $current"
            _uiState.update { it.copy(message = refusal, toast = refusal) }
            return true
        }
        _uiState.update { it.copy(busy = label, message = null) }
        return false
    }

    private fun busyDone() { _uiState.update { it.copy(busy = null) } }

    /** Called by the screen once [LoansUiState.toast] has been shown. */
    fun clearToast() { _uiState.update { it.copy(toast = null) } }

    /**
     * Records a settlement payment (or archives an already-repaid loan when
     * [amountLoanCurrency] is zero). [accountId] null means external revenue -
     * the default - so no balance changes.
     */
    fun settle(
        item: LoanWithBalance,
        amountLoanCurrency: String,
        accountId: Long?,
        rate: String,
        note: String?
    ) {
        // A cross-currency settlement without a real rate must be refused, not
        // quietly recorded 1:1: the old `?: BigDecimal.ONE` here re-introduced
        // exactly the silent conversion the dialog refuses to do.
        val targetCurrency = accountId?.let { id ->
            _uiState.value.settleAccounts.firstOrNull { it.id == id }?.currency
        }
        val appliedRate = SettlementRate.resolve(item.loan.currencyOriginal, targetCurrency, rate)
        if (appliedRate == null) {
            val refusal = SettlementRate.missingRateMessage(item.loan.currencyOriginal, targetCurrency)
            _uiState.update { it.copy(message = refusal, toast = refusal) }
            return
        }
        if (busyStarting(if (accountId == null) "Recording payment\u2026" else "Settling through a balance\u2026")) return
        viewModelScope.launch {
            try {
                val amount = amountLoanCurrency.parseAmountOrNull()
                    ?: error("Enter a positive amount")
                val parsedRate = appliedRate
                val wasRemaining = item.remaining
                financeRepository.recordRepayment(
                    loanId = item.loan.id,
                    amount = amount,
                    accountId = accountId,
                    rate = parsedRate,
                    note = note
                )
                val accountName = accountId?.let { id ->
                    _uiState.value.settleAccounts.firstOrNull { it.id == id }?.name
                }
                val result = when {
                    amount.compareTo(BigDecimal.ZERO) == 0 ->
                        "\"${item.loan.personName}\" moved to the settled archive"
                    amount.compareTo(wasRemaining) == 0 ->
                        "\"${item.loan.personName}\" settled" +
                            (accountName?.let { name ->
                                if (item.loan.type == LoanDebt.TYPE_DEBT) " from $name" else " into $name"
                            } ?: " (external)") + " · see History"
                    else -> "Payment recorded for \"${item.loan.personName}\""
                }
                _uiState.update { it.copy(message = result, toast = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Unable to settle") }
            } finally {
                busyDone()
            }
        }
    }

    /** Reopens an archived loan, reversing the transaction that settled it. */
    fun reopen(loan: LoanDebt) {
        if (busyStarting("Reopening \"${loan.personName}\"\u2026")) return
        viewModelScope.launch {
            try {
                financeRepository.reopenLoan(loan.id)
                _uiState.update { it.copy(message = "\"${loan.personName}\" reopened") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Unable to reopen") }
            } finally {
                busyDone()
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Rate for the settle dialog: 1 [fromCurrency] = ? [toCurrency].
     *
     * Strict on purpose - null means "no rate on this device", and the dialog then
     * refuses to confirm instead of converting at an invented 1:1.
     */
    suspend fun conversionRate(fromCurrency: String, toCurrency: String): BigDecimal? =
        rateRepository.getConversionRateStrict(fromCurrency, toCurrency)
}
