package com.spendingtracker.app.util

import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON export/import schema. Version 3 adds [accounts] and [recurringPayments];
 * version 5 adds [withdrawals] and per-account `includedInTotal`. Every added
 * field has a default so files exported by older app versions (schema 1..4)
 * still import cleanly.
 */
@Serializable
data class ExportData(
    val schemaVersion: Int = 5,
    val settings: ExportSettings? = null,
    val categories: List<ExportCategory> = emptyList(),
    val expenses: List<ExportExpense> = emptyList(),
    val topUps: List<ExportTopUp> = emptyList(),
    val exchangeRates: List<ExportRate> = emptyList(),
    val loanDebts: List<ExportLoanDebt> = emptyList(),
    val repayments: List<ExportRepayment> = emptyList(),
    val accounts: List<ExportAccount> = emptyList(),
    val recurringPayments: List<ExportRecurringPayment> = emptyList(),
    val creditCardPayments: List<com.spendingtracker.app.data.local.entity.CreditCardPayment> = emptyList(),
    val withdrawals: List<com.spendingtracker.app.data.local.entity.Withdrawal> = emptyList()
)

@Serializable
data class ExportSettings(
    val balanceCurrency: String,
    val startingBalance: String,
    val lowBalanceThreshold: String?,
    val theme: String,
    val defaultCategoryId: Long? = null,
    val lastRatesUpdate: Long? = null,
    val isSetupComplete: Boolean = false,
    val currencySortMode: String = "ALPHABETICAL",
    val customCurrencyOrder: String? = null,
    val favoriteCurrencies: String? = null,
    val recentCurrencies: String? = null,
    val activeAccountId: Long = 1
)

@Serializable
data class ExportCategory(
    val id: Long,
    val name: String,
    val iconName: String,
    val color: Int,
    val isDefault: Boolean = false,
    val createdAt: Long = 0
)

@Serializable
data class ExportExpense(
    val id: Long,
    val amountOriginal: String,
    val currencyOriginal: String,
    val amountConverted: String,
    val currencyConverted: String,
    val exchangeRateUsed: String = "1",
    val categoryId: Long?,
    val date: Long,
    val note: String?,
    val paymentMethod: String?,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val accountId: Long = 1,
    val balanceAccountId: Long? = null
)

@Serializable
data class ExportTopUp(
    val id: Long,
    val amount: String,
    val date: Long,
    val note: String?,
    val createdAt: Long = 0,
    val accountId: Long = 1
)

@Serializable
data class ExportRate(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: String,
    val timestamp: Long
)

@Serializable
data class ExportLoanDebt(
    val id: Long,
    val type: String,
    val personName: String,
    val amountOriginal: String,
    val currencyOriginal: String,
    val date: Long,
    val dueDate: Long?,
    val note: String?,
    val isSettled: Boolean,
    val createdAt: Long = 0
)

@Serializable
data class ExportRepayment(
    val id: Long,
    val loanDebtId: Long,
    val amount: String,
    val date: Long,
    val note: String?,
    val createdAt: Long = 0
)

@Serializable
data class ExportAccount(
    val id: Long = 0,
    val name: String,
    val currency: String,
    val startingBalance: String,
    val lowBalanceThreshold: String? = null,
    val createdAt: Long = 0,
    val type: String = "MAIN",
    val parentAccountId: Long? = null,
    val creditLimit: String? = null,
    val autopayEnabled: Boolean = false,
    val autopayDay: Int = 1,
    val autopayMode: String = "FULL_BALANCE",
    val autopayFixedAmount: String? = null,
    val autopayStartDate: String? = null,
    val includedInTotal: Boolean = true
)

@Serializable
data class ExportRecurringPayment(
    val id: Long = 0,
    val accountId: Long = 1,
    val title: String,
    val amountOriginal: String,
    val currencyOriginal: String,
    val categoryId: Long? = null,
    val note: String? = null,
    val paymentMethod: String? = null,
    val frequency: String,
    val startDate: Long,
    val endDate: Long? = null,
    val nextDueAt: Long,
    val lastRunAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0
)

@Singleton
class ExportImportManager @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val loanRepository: LoanRepository,
    private val rateRepository: ExchangeRateRepository,
    private val recurringRepository: RecurringRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportAll(): String = financeRepository.atomic {
        val settings = financeRepository.getSettingsSync()
        val categories = financeRepository.getAllCategoriesSync()
        val expenses = financeRepository.getAllExpensesSync()
        val topUps = financeRepository.getAllTopUpsSync()
        val rates = rateRepository.getAllRatesSync()
        val loanDebts = loanRepository.getAllLoanDebtsSync()
        val repayments = mutableListOf<Repayment>()
        for (loan in loanDebts) {
            repayments.addAll(loanRepository.getRepaymentsForLoanSync(loan.id))
        }
        val accounts = financeRepository.getAllAccountsSync()
        val recurring = recurringRepository.getAllSync()

        val export = ExportData(
            creditCardPayments = financeRepository.getCardPayments(),
            withdrawals = financeRepository.getWithdrawalsSync(),
            settings = settings?.let {
                ExportSettings(
                    balanceCurrency = it.balanceCurrency,
                    startingBalance = it.startingBalance,
                    lowBalanceThreshold = it.lowBalanceThreshold,
                    theme = it.theme,
                    defaultCategoryId = it.defaultCategoryId,
                    lastRatesUpdate = it.lastRatesUpdate,
                    isSetupComplete = it.isSetupComplete,
                    currencySortMode = it.currencySortMode,
                    customCurrencyOrder = it.customCurrencyOrder,
                    favoriteCurrencies = it.favoriteCurrencies,
                    recentCurrencies = it.recentCurrencies,
                    activeAccountId = it.activeAccountId
                )
            },
            categories = categories.map {
                ExportCategory(it.id, it.name, it.iconName, it.color, it.isDefault, it.createdAt)
            },
            expenses = expenses.map {
                ExportExpense(
                    id = it.id,
                    amountOriginal = it.amountOriginal,
                    currencyOriginal = it.currencyOriginal,
                    amountConverted = it.amountConverted,
                    currencyConverted = it.currencyConverted,
                    exchangeRateUsed = it.exchangeRateUsed,
                    categoryId = it.categoryId,
                    date = it.date,
                    note = it.note,
                    paymentMethod = it.paymentMethod,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    accountId = it.accountId,
                    balanceAccountId = it.balanceAccountId
                )
            },
            topUps = topUps.map {
                ExportTopUp(
                    id = it.id,
                    amount = it.amount,
                    date = it.date,
                    note = it.note,
                    createdAt = it.createdAt,
                    accountId = it.accountId
                )
            },
            exchangeRates = rates.map {
                ExportRate(it.baseCurrency, it.targetCurrency, it.rate, it.timestamp)
            },
            loanDebts = loanDebts.map {
                ExportLoanDebt(
                    it.id, it.type, it.personName, it.amountOriginal, it.currencyOriginal,
                    it.date, it.dueDate, it.note, it.isSettled, it.createdAt
                )
            },
            repayments = repayments.map {
                ExportRepayment(it.id, it.loanDebtId, it.amount, it.date, it.note, it.createdAt)
            },
            accounts = accounts.map {
                ExportAccount(
                    id = it.id,
                    name = it.name,
                    currency = it.currency,
                    startingBalance = it.startingBalance,
                    lowBalanceThreshold = it.lowBalanceThreshold,
                    createdAt = it.createdAt,
                    type = it.type, parentAccountId = it.parentAccountId, creditLimit = it.creditLimit,
                    autopayEnabled = it.autopayEnabled, autopayDay = it.autopayDay, autopayMode = it.autopayMode,
                    autopayFixedAmount = it.autopayFixedAmount, autopayStartDate = it.autopayStartDate,
                    includedInTotal = it.includedInTotal
                )
            },
            recurringPayments = recurring.map {
                ExportRecurringPayment(
                    id = it.id,
                    accountId = it.accountId,
                    title = it.title,
                    amountOriginal = it.amountOriginal,
                    currencyOriginal = it.currencyOriginal,
                    categoryId = it.categoryId,
                    note = it.note,
                    paymentMethod = it.paymentMethod,
                    frequency = it.frequency,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    nextDueAt = it.nextDueAt,
                    lastRunAt = it.lastRunAt,
                    isActive = it.isActive,
                    createdAt = it.createdAt
                )
            }
        )
        json.encodeToString(export)
    }

    /**
     * Replace-all import. Accepts files from every previous app version:
     * older exports simply lack the accounts / recurringPayments blocks, in
     * which case a single "Main" account is created to hold the imported data.
     * Loans & debts stay shared across accounts.
     */
    suspend fun importAll(jsonString: String): Result<Unit> {
        return try {
            val data = json.decodeFromString<ExportData>(jsonString)
            require(data.schemaVersion in 1..5) { "Unsupported backup version" }
            require(data.accounts.all { it.id > 0 }) { "Invalid backup account ID" }
            financeRepository.atomic {
                financeRepository.eraseAll()
                loanRepository.eraseAll()
                financeRepository.clearCategoriesForRestore()
                rateRepository.clearRates()

                // Accounts: use exported ones, or synthesize a default account for legacy exports.
                val accountsToInsert = if (data.accounts.isNotEmpty()) {
                    data.accounts
                } else {
                    listOf(
                        ExportAccount(
                            id = 1,
                            name = "Main",
                            currency = data.settings?.balanceCurrency ?: "USD",
                            startingBalance = data.settings?.startingBalance ?: "0",
                            lowBalanceThreshold = data.settings?.lowBalanceThreshold
                        )
                    )
                }
                accountsToInsert.forEach {
                    financeRepository.insertAccount(
                        Account(
                            id = it.id,
                            name = it.name,
                            currency = it.currency,
                            startingBalance = it.startingBalance,
                            lowBalanceThreshold = it.lowBalanceThreshold,
                            createdAt = if (it.createdAt == 0L) System.currentTimeMillis() else it.createdAt,
                            type = it.type, parentAccountId = it.parentAccountId, creditLimit = it.creditLimit,
                            autopayEnabled = it.autopayEnabled, autopayDay = it.autopayDay, autopayMode = it.autopayMode,
                            autopayFixedAmount = it.autopayFixedAmount, autopayStartDate = it.autopayStartDate,
                            includedInTotal = it.includedInTotal
                        )
                    )
                }
                val fallbackAccountId = accountsToInsert.first().id
                val validAccountIds = accountsToInsert.map { it.id }.toSet()

                val s = data.settings
                financeRepository.saveSettings(
                    AppSettings(
                        balanceCurrency = s?.balanceCurrency ?: accountsToInsert.first().currency,
                        startingBalance = s?.startingBalance ?: accountsToInsert.first().startingBalance,
                        lowBalanceThreshold = s?.lowBalanceThreshold,
                        theme = s?.theme ?: "SYSTEM",
                        defaultCategoryId = s?.defaultCategoryId,
                        lastRatesUpdate = s?.lastRatesUpdate,
                        isSetupComplete = s?.isSetupComplete ?: true,
                        currencySortMode = s?.currencySortMode ?: "ALPHABETICAL",
                        customCurrencyOrder = s?.customCurrencyOrder,
                        favoriteCurrencies = s?.favoriteCurrencies,
                        recentCurrencies = s?.recentCurrencies,
                        activeAccountId = s?.activeAccountId?.takeIf { it in validAccountIds }
                            ?: fallbackAccountId
                    )
                )

                data.categories.forEach {
                    financeRepository.insertCategory(
                        Category(it.id, it.name, it.iconName, it.color, it.isDefault, it.createdAt)
                    )
                }
                data.expenses.forEach {
                    financeRepository.restoreExpense(
                        Expense(
                            id = it.id,
                            accountId = it.accountId.takeIf { id -> id in validAccountIds }
                                ?: fallbackAccountId,
                            amountOriginal = it.amountOriginal,
                            currencyOriginal = it.currencyOriginal,
                            amountConverted = it.amountConverted,
                            currencyConverted = it.currencyConverted,
                            exchangeRateUsed = it.exchangeRateUsed,
                            categoryId = it.categoryId,
                            date = it.date,
                            note = it.note,
                            paymentMethod = it.paymentMethod,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            balanceAccountId = it.balanceAccountId
                        )
                    )
                }
                data.topUps.forEach {
                    financeRepository.insertTopUp(
                        TopUp(
                            id = it.id,
                            accountId = it.accountId.takeIf { id -> id in validAccountIds }
                                ?: fallbackAccountId,
                            amount = it.amount,
                            date = it.date,
                            note = it.note,
                            createdAt = it.createdAt
                        )
                    )
                }
                data.exchangeRates.forEach {
                    rateRepository.insertRate(
                        ExchangeRate(
                            baseCurrency = it.baseCurrency,
                            targetCurrency = it.targetCurrency,
                            rate = it.rate,
                            timestamp = it.timestamp
                        )
                    )
                }
                data.loanDebts.forEach {
                    loanRepository.insertLoanDebt(
                        LoanDebt(
                            id = it.id,
                            type = it.type,
                            personName = it.personName,
                            amountOriginal = it.amountOriginal,
                            currencyOriginal = it.currencyOriginal,
                            date = it.date,
                            dueDate = it.dueDate,
                            note = it.note,
                            isSettled = it.isSettled,
                            createdAt = it.createdAt
                        )
                    )
                }
                data.repayments.forEach {
                    loanRepository.insertRepayment(
                        Repayment(
                            id = it.id,
                            loanDebtId = it.loanDebtId,
                            amount = it.amount,
                            date = it.date,
                            note = it.note,
                            createdAt = it.createdAt
                        )
                    )
                }
                data.recurringPayments.forEach {
                    recurringRepository.insert(
                        RecurringPayment(
                            id = it.id,
                            accountId = it.accountId.takeIf { id -> id in validAccountIds }
                                ?: fallbackAccountId,
                            title = it.title,
                            amountOriginal = it.amountOriginal,
                            currencyOriginal = it.currencyOriginal,
                            categoryId = it.categoryId,
                            note = it.note,
                            paymentMethod = it.paymentMethod,
                            frequency = it.frequency,
                            startDate = it.startDate,
                            endDate = it.endDate,
                            nextDueAt = it.nextDueAt,
                            lastRunAt = it.lastRunAt,
                            isActive = it.isActive,
                            createdAt = if (it.createdAt == 0L) System.currentTimeMillis() else it.createdAt
                        )
                    )
                }

                data.creditCardPayments.forEach { financeRepository.restoreCardPayment(it) }
                data.withdrawals.forEach { w ->
                    // Skip rows whose accounts did not survive the import instead of
                    // failing the whole restore.
                    if (w.fromAccountId in validAccountIds && w.toAccountId in validAccountIds) {
                        financeRepository.restoreWithdrawal(w)
                    }
                }
                financeRepository.validateRestoredLedger()
            } // Transaction rolls back the entire replace-all import on any failure.
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportTransactionsCsv(): String = financeRepository.atomic {
        val accounts = financeRepository.getAllAccountsSync().associateBy { it.id }
        val output = StringBuilder()
        fun row(vararg values: Any?) {
            output.appendLine(values.joinToString(",") { value -> "\"${(value?.toString() ?: "").replace("\"", "\"\"")}\"" })
        }
        row("type", "id", "amount_original", "currency_original", "amount_converted", "currency_converted", "rate", "categoryId", "date", "note", "paymentMethod", "account_id", "balance_account_id", "status")
        financeRepository.getAllExpensesSync().forEach {
            row("EXPENSE", it.id, it.amountOriginal, it.currencyOriginal, it.amountConverted, it.currencyConverted,
                it.exchangeRateUsed, it.categoryId, it.date, it.note, it.paymentMethod, it.accountId, it.balanceAccountId ?: it.accountId, "COMPLETED")
        }
        financeRepository.getAllTopUpsSync().forEach {
            val currency = accounts[it.accountId]?.currency ?: ""
            row("INCOME", it.id, it.amount, currency, it.amount, currency, "1", null, it.date, it.note, accounts[it.accountId]?.type, it.accountId, it.accountId, "COMPLETED")
        }
        financeRepository.getCardPayments().forEach {
            val currency = accounts[it.sourceAccountId]?.currency ?: ""
            row("CARD_PAYMENT", it.id, it.amount, currency, it.amount, currency, "1", null, it.date,
                "Credit card payment", "MAIN", it.sourceAccountId, it.cardId, it.status)
        }
        // Transfers between balances are exported too so a CSV tells the whole story.
        financeRepository.getWithdrawalsSync().forEach {
            row("WITHDRAWAL", it.id, it.amountFrom, it.currencyFrom, it.amountTo, it.currencyTo,
                it.exchangeRate, null, it.date, it.note ?: "Withdrawal to cash", "MAIN",
                it.fromAccountId, it.toAccountId, "COMPLETED")
        }
        output.toString()
    }
}
