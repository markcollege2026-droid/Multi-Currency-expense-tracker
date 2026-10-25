package com.spendingtracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.local.entity.Withdrawal
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.domain.AccountBalances
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class TransactionsUiState(
    val allExpenses: List<Expense> = emptyList(),
    val cardPayments: List<com.spendingtracker.app.data.local.entity.CreditCardPayment> = emptyList(),
    val allTopUps: List<TopUp> = emptyList(),
    val allWithdrawals: List<Withdrawal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filteredExpenses: List<Expense> = emptyList(),
    val filteredTopUps: List<TopUp> = emptyList(),
    val filteredWithdrawals: List<Withdrawal> = emptyList(),
    /** Every account of the active main balance, so rows can show their own currency. */
    val accountsById: Map<Long, Account> = emptyMap(),
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val selectedCurrency: String? = null,
    val selectedType: String? = null, // EXPENSE, TOPUP, CARD_PAYMENT, WITHDRAWAL
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null,
    val balanceCurrency: String = "USD",
    /** Balance of the main account, so a withdrawal can be started from History. */
    val mainBalance: BigDecimal = BigDecimal.ZERO,
    val cashAccounts: List<Account> = emptyList(),
    /** Main + its cash balances: every balance a transfer can move money between. */
    val transferAccounts: List<Account> = emptyList(),
    /** Derived balance per account id, for transfer previews. */
    val accountBalances: Map<Long, BigDecimal> = emptyMap(),
    /** Loan repayments/settlements are transactions and belong in History. */
    val allRepayments: List<Repayment> = emptyList(),
    val filteredRepayments: List<Repayment> = emptyList(),
    val loansById: Map<Long, LoanDebt> = emptyMap(),
    val message: String? = null,
    /** Name of the ledger being shown, so an empty list can say which account it is. */
    val activeAccountName: String? = null,
    /**
     * Set when another main account holds entries while this one is empty. The data
     * is not missing, it is in a different account - the exact confusion that made a
     * healed ledger read as "history wiped".
     */
    val otherAccountId: Long? = null,
    val otherAccountName: String? = null,
    val otherAccountEntryCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                financeRepository.getAllExpenses(),
                financeRepository.getAllTopUps(),
                financeRepository.getCategories(),
                combine(financeRepository.observeActiveAccount(), financeRepository.observeBalanceAccounts()) { active, all -> active to all },
                combine(
                    financeRepository.observeCardPayments(),
                    financeRepository.observeWithdrawals(),
                    financeRepository.observeRepayments(),
                    financeRepository.observeLoans()
                ) { payments, withdrawals, repayments, loans ->
                    LedgerExtras(payments, withdrawals, repayments, loans)
                }
            ) { expenses, topUps, categories, wallet, extras ->
                val (account, accounts) = wallet
                val payments = extras.payments
                val withdrawals = extras.withdrawals
                val repayments = extras.repayments
                val loans = extras.loans
                val incomeAccountIds = accounts.filter { it.id == account?.id || it.parentAccountId == account?.id }.map { it.id }.toSet()
                val accountId = account?.id
                // Balances for the whole ledger (loan settlements included): the
                // withdraw/transfer dialogs need live figures, and History rows
                // never need a second query.
                val balances = AccountBalances.calculate(accounts, expenses, topUps, payments, withdrawals, repayments, loans)
                // Entries sitting in a *different* main account. Loaded from the raw
                // rows, before they are scoped down to the active ledger.
                val other = accounts
                    .filter { it.type == "MAIN" && it.id != accountId }
                    .map { candidate ->
                        val own = accounts.filter { it.id == candidate.id || it.parentAccountId == candidate.id }.map { it.id }.toSet()
                        val count = expenses.count { it.accountId == candidate.id } +
                            topUps.count { it.accountId in own } +
                            withdrawals.count { it.fromAccountId == candidate.id || it.toAccountId in own }
                        candidate to count
                    }
                    .filter { it.second > 0 }
                    .maxByOrNull { it.second }
                _uiState.value.copy(
                    cardPayments = payments.filter { it.sourceAccountId == accountId },
                    allExpenses = if (accountId == null) emptyList() else expenses.filter { it.accountId == accountId },
                    allTopUps = if (accountId == null) emptyList() else topUps.filter { it.accountId in incomeAccountIds },
                    // A transfer belongs to the main account it moves money inside
                    // (main <-> cash and cash <-> cash alike).
                    allWithdrawals = if (accountId == null) emptyList()
                    else withdrawals.filter { it.fromAccountId in incomeAccountIds && it.toAccountId in incomeAccountIds },
                    // Loans are shared: external settlements show in every ledger,
                    // account-linked ones in the ledger whose balance they moved.
                    allRepayments = if (accountId == null) emptyList()
                    else repayments.filter { it.accountId == null || it.accountId in incomeAccountIds },
                    loansById = loans.associateBy { it.id },
                    accountsById = accounts.associateBy { it.id },
                    categories = categories,
                    balanceCurrency = account?.currency ?: "USD",
                    mainBalance = if (accountId == null) BigDecimal.ZERO else balances[accountId] ?: BigDecimal.ZERO,
                    cashAccounts = accounts.filter { it.parentAccountId == accountId && it.type == "CASH" },
                    transferAccounts = accounts.filter {
                        (accountId != null && it.id == accountId) ||
                            (it.parentAccountId == accountId && it.type == "CASH")
                    },
                    accountBalances = balances,
                    activeAccountName = account?.name,
                    otherAccountId = other?.first?.id,
                    otherAccountName = other?.first?.name,
                    otherAccountEntryCount = other?.second ?: 0,
                    isLoading = false
                )
            }.collect { baseState ->
                _uiState.value = baseState.copy(
                    filteredExpenses = filterExpenses(baseState.allExpenses, baseState),
                    filteredTopUps = filterTopUps(baseState.allTopUps, baseState),
                    filteredWithdrawals = filterWithdrawals(baseState.allWithdrawals, baseState),
                    filteredRepayments = filterRepayments(baseState.allRepayments, baseState)
                )
            }
        }
    }

    private fun filterExpenses(expenses: List<Expense>, state: TransactionsUiState): List<Expense> {
        return expenses.filter { expense ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(expense.note?.lowercase()?.contains(q) == true || expense.currencyOriginal.lowercase().contains(q))) return@filter false
            }
            if (state.selectedCategoryId != null && expense.categoryId != state.selectedCategoryId) return@filter false
            if (state.selectedCurrency != null && expense.currencyOriginal != state.selectedCurrency) return@filter false
            if (state.selectedType != null && state.selectedType != "EXPENSE") return@filter false
            if (state.dateRangeStart != null && expense.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && expense.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun filterTopUps(topUps: List<TopUp>, state: TransactionsUiState): List<TopUp> {
        return topUps.filter { topUp ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(topUp.note?.lowercase()?.contains(q) == true)) return@filter false
            }
            if (state.selectedType != null && state.selectedType != "TOPUP") return@filter false
            if (state.dateRangeStart != null && topUp.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && topUp.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun filterWithdrawals(withdrawals: List<Withdrawal>, state: TransactionsUiState): List<Withdrawal> {
        return withdrawals.filter { withdrawal ->
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(withdrawal.note?.lowercase()?.contains(q) == true ||
                        withdrawal.currencyFrom.lowercase().contains(q) ||
                        withdrawal.currencyTo.lowercase().contains(q))
                ) return@filter false
            }
            // Transfers have no category, so a category filter hides them.
            if (state.selectedCategoryId != null) return@filter false
            if (state.selectedType != null && state.selectedType != "WITHDRAWAL") return@filter false
            if (state.selectedCurrency != null &&
                state.selectedCurrency != withdrawal.currencyFrom &&
                state.selectedCurrency != withdrawal.currencyTo
            ) return@filter false
            if (state.dateRangeStart != null && withdrawal.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && withdrawal.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    private fun filterRepayments(repayments: List<Repayment>, state: TransactionsUiState): List<Repayment> {
        return repayments.filter { repayment ->
            val loan = state.loansById[repayment.loanDebtId] ?: return@filter false
            if (state.searchQuery.isNotBlank()) {
                val q = state.searchQuery.lowercase()
                if (!(loan.personName.lowercase().contains(q) ||
                        repayment.note?.lowercase()?.contains(q) == true ||
                        loan.currencyOriginal.lowercase().contains(q))
                ) return@filter false
            }
            // Loan transactions have no category, so a category filter hides them.
            if (state.selectedCategoryId != null) return@filter false
            if (state.selectedType != null && state.selectedType != "LOAN") return@filter false
            if (state.selectedCurrency != null &&
                state.selectedCurrency != loan.currencyOriginal &&
                state.selectedCurrency != repayment.accountCurrency
            ) return@filter false
            if (state.dateRangeStart != null && repayment.date < state.dateRangeStart) return@filter false
            if (state.dateRangeEnd != null && repayment.date > state.dateRangeEnd) return@filter false
            true
        }
    }

    /** Re-runs all filters after one of them changed. */
    private fun refilter() {
        val current = _uiState.value
        _uiState.value = current.copy(
            filteredExpenses = filterExpenses(current.allExpenses, current),
            filteredTopUps = filterTopUps(current.allTopUps, current),
            filteredWithdrawals = filterWithdrawals(current.allWithdrawals, current),
            filteredRepayments = filterRepayments(current.allRepayments, current)
        )
    }

    fun onSearchChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refilter()
    }

    fun onCategorySelected(id: Long?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = id)
        refilter()
    }

    fun onCurrencySelected(currency: String?) {
        _uiState.value = _uiState.value.copy(selectedCurrency = currency)
        refilter()
    }

    fun onTypeSelected(type: String?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        refilter()
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { financeRepository.deleteExpense(expense) }
    }

    fun deleteTopUp(topUp: TopUp) {
        viewModelScope.launch { financeRepository.deleteTopUp(topUp) }
    }

    /** Removing a transfer moves the money back to main and off the cash balance. */
    fun deleteWithdrawal(withdrawal: Withdrawal) {
        viewModelScope.launch { financeRepository.deleteWithdrawal(withdrawal) }
    }

    /** Removing a loan settlement reopens its loan and reverses the money. */
    fun deleteRepayment(repayment: Repayment) {
        viewModelScope.launch {
            try {
                financeRepository.deleteRepayment(repayment)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message ?: "Unable to delete")
            }
        }
    }

    /**
     * Transfers between any two balances of the active main account (including
     * cash to cash). Both amounts come from the dialog, so the side the user
     * typed is stored exactly; the repository re-checks the source balance.
     */
    fun transfer(fromId: Long, toId: Long, amountFrom: String, amountTo: String, note: String?) {
        viewModelScope.launch {
            try {
                val from = amountFrom.trim().replace(',', '.').toBigDecimalOrNull()
                    ?: error("Enter a positive amount")
                val to = amountTo.trim().replace(',', '.').toBigDecimalOrNull()
                    ?: error("Enter a positive amount")
                val source = financeRepository.getAccountById(fromId) ?: error("Source balance not found")
                val target = financeRepository.getAccountById(toId) ?: error("Destination balance not found")
                val rate = rateRepository.getConversionRateOrFallback(source.currency, target.currency)
                financeRepository.transfer(fromId, toId, from, rate, note, amountTo = to)
                _uiState.value = _uiState.value.copy(
                    message = "Transferred " +
                        com.spendingtracker.app.util.CurrencyFormatter.formatSimple(from, source.currency) +
                        " from " + source.name + " to " + target.name
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message ?: "Unable to transfer")
            }
        }
    }

    /**
     * Withdraws from the active main balance into one of its cash balances. Both
     * amounts come from the dialog, so whichever currency the user typed is the
     * one that is stored exactly.
     */
    fun withdraw(cashId: Long, amountFrom: String, amountTo: String, note: String?) {
        viewModelScope.launch {
            try {
                val from = amountFrom.trim().replace(',', '.').toBigDecimalOrNull()
                    ?: error("Enter a positive amount")
                val to = amountTo.trim().replace(',', '.').toBigDecimalOrNull()
                    ?: error("Enter a positive amount")
                val mainId = financeRepository.getActiveAccountId()
                val cash = financeRepository.getAccountById(cashId) ?: error("Cash balance not found")
                val main = financeRepository.getAccountById(mainId) ?: error("Main account not found")
                val rate = rateRepository.getConversionRateOrFallback(main.currency, cash.currency)
                financeRepository.withdraw(mainId, cash.id, from, rate, note, amountTo = to)
                // Copy the *current* state: the ledger flow may have re-emitted while
                // the transaction above was running.
                _uiState.value = _uiState.value.copy(
                    message = "Withdrew " + com.spendingtracker.app.util.CurrencyFormatter.formatSimple(from, main.currency) +
                        " to " + cash.name
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message ?: "Unable to withdraw")
            }
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    /** Switches the active ledger from the History hint, without leaving the tab. */
    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            try {
                financeRepository.setActiveAccount(accountId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = e.message ?: "Could not switch account")
            }
        }
    }

    /** Rate preview for the withdraw dialog: 1 [fromCurrency] = ? [toCurrency]. */
    suspend fun conversionRate(fromCurrency: String, toCurrency: String): BigDecimal =
        rateRepository.getConversionRateOrFallback(fromCurrency, toCurrency)

    /** Applies a custom date range; null values clear that side of the range. */
    fun onDateRangeChanged(start: Long?, end: Long?) {
        _uiState.value = _uiState.value.copy(dateRangeStart = start, dateRangeEnd = end)
        refilter()
    }

    /** Resets every filter except the search query. */
    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = null,
            selectedCurrency = null,
            dateRangeStart = null,
            dateRangeEnd = null
        )
        refilter()
    }

}

/** Card payments, transfers and loan transactions feeding History in one combine. */
private data class LedgerExtras(
    val payments: List<com.spendingtracker.app.data.local.entity.CreditCardPayment>,
    val withdrawals: List<Withdrawal>,
    val repayments: List<Repayment>,
    val loans: List<LoanDebt>
)
