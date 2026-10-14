package com.spendingtracker.app.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val debts: List<LoanWithBalance> = emptyList(),
    val loans: List<LoanWithBalance> = emptyList(),
    val totalOwedByMe: BigDecimal = BigDecimal.ZERO,
    val totalOwedToMe: BigDecimal = BigDecimal.ZERO,
    val balanceCurrency: String = "USD",
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
                financeRepository.getSettings()
            ) { allLoans, settings ->
                val balanceCurrency = settings?.balanceCurrency ?: "USD"
                val debts = mutableListOf<LoanWithBalance>()
                val loans = mutableListOf<LoanWithBalance>()
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

                    if (loan.type == LoanDebt.TYPE_DEBT) {
                        totalDebtConverted += convertedRemaining
                        debts.add(
                            LoanWithBalance(
                                loan = loan,
                                remaining = calc.remaining,
                                totalRepaid = calc.totalRepaid,
                                progress = calc.progress,
                                isOverdue = loan.isOverdue(),
                                convertedAmount = convertedOriginal,
                                convertedRemaining = convertedRemaining
                            )
                        )
                    } else {
                        totalLoanConverted += convertedRemaining
                        loans.add(
                            LoanWithBalance(
                                loan = loan,
                                remaining = calc.remaining,
                                totalRepaid = calc.totalRepaid,
                                progress = calc.progress,
                                isOverdue = loan.isOverdue(),
                                convertedAmount = convertedOriginal,
                                convertedRemaining = convertedRemaining
                            )
                        )
                    }
                }

                LoansUiState(
                    debts = debts.sortedBy { it.loan.dueDate ?: Long.MAX_VALUE },
                    loans = loans.sortedBy { it.loan.dueDate ?: Long.MAX_VALUE },
                    totalOwedByMe = totalDebtConverted,
                    totalOwedToMe = totalLoanConverted,
                    balanceCurrency = balanceCurrency,
                    isLoading = false
                )
            }
                // Aggregation + rate lookups run off the main thread
                .flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }
    }

    fun deleteLoan(loan: LoanDebt) {
        viewModelScope.launch { loanRepository.deleteLoanDebt(loan) }
    }

    fun toggleSettled(loan: LoanDebt) {
        viewModelScope.launch {
            loanRepository.updateLoanDebt(loan.copy(isSettled = !loan.isSettled))
        }
    }
}
