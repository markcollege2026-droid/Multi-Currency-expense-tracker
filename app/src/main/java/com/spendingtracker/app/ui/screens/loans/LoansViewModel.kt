package com.spendingtracker.app.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.domain.LoanCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

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
    /** Main + cash balances a settlement can move money through. */
    val settleAccounts: List<Account> = emptyList(),
    val accountBalances: Map<Long, BigDecimal> = emptyMap(),
    val message: String? = null,
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
                    settleAccounts = accounts.filter {
                        (account?.id != null && it.id == account.id) ||
                            (it.parentAccountId == account?.id && it.type == "CASH")
                    },
                    accountBalances = summaries.mapValues { it.value.balance },
                    message = _uiState.value.message,
                    isLoading = false
                )
            }
                // Aggregation + rate lookups run off the main thread
                .flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }
    }

    fun deleteLoan(loan: LoanDebt) {
        viewModelScope.launch {
            try {
                // Repayments are removed by the foreign-key cascade, which also
                // reverses any settlement money still sitting in a balance.
                loanRepository.deleteLoanDebt(loan)
                _uiState.update { it.copy(message = "\"${loan.personName}\" deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Unable to delete") }
            }
        }
    }

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
        viewModelScope.launch {
            try {
                val amount = amountLoanCurrency.trim().replace(',', '.').toBigDecimalOrNull()
                    ?: error("Enter a positive amount")
                val parsedRate = rate.trim().replace(',', '.').toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
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
                _uiState.update {
                    it.copy(
                        message = when {
                            amount.compareTo(BigDecimal.ZERO) == 0 ->
                                "\"${item.loan.personName}\" moved to the settled archive"
                            amount.compareTo(wasRemaining) == 0 ->
                                "\"${item.loan.personName}\" settled" +
                                    (accountName?.let { name ->
                                        if (item.loan.type == LoanDebt.TYPE_DEBT) " from $name" else " into $name"
                                    } ?: " (external)") + " · see History"
                            else -> "Payment recorded for \"${item.loan.personName}\""
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Unable to settle") }
            }
        }
    }

    /** Reopens an archived loan, reversing the transaction that settled it. */
    fun reopen(loan: LoanDebt) {
        viewModelScope.launch {
            try {
                financeRepository.reopenLoan(loan.id)
                _uiState.update { it.copy(message = "\"${loan.personName}\" reopened") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Unable to reopen") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Rate preview for the settle dialog: 1 [fromCurrency] = ? [toCurrency]. */
    suspend fun conversionRate(fromCurrency: String, toCurrency: String): BigDecimal =
        rateRepository.getConversionRateOrFallback(fromCurrency, toCurrency)
}
