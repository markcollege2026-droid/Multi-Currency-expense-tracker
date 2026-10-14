package com.spendingtracker.app.util

import android.content.Context
import com.spendingtracker.app.data.local.entity.*
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportData(
    val settings: ExportSettings?,
    val categories: List<ExportCategory>,
    val expenses: List<ExportExpense>,
    val topUps: List<ExportTopUp>,
    val exchangeRates: List<ExportRate>,
    val loanDebts: List<ExportLoanDebt>,
    val repayments: List<ExportRepayment>
)

@Serializable
data class ExportSettings(
    val balanceCurrency: String,
    val startingBalance: String,
    val lowBalanceThreshold: String?,
    val theme: String,
    val defaultCategoryId: Long?,
    val lastRatesUpdate: Long?,
    val isSetupComplete: Boolean
)

@Serializable
data class ExportCategory(val id: Long, val name: String, val iconName: String, val color: Int, val isDefault: Boolean, val createdAt: Long)
@Serializable
data class ExportExpense(val id: Long, val amountOriginal: String, val currencyOriginal: String, val amountConverted: String, val currencyConverted: String, val exchangeRateUsed: String, val categoryId: Long?, val date: Long, val note: String?, val paymentMethod: String?, val createdAt: Long, val updatedAt: Long)
@Serializable
data class ExportTopUp(val id: Long, val amount: String, val date: Long, val note: String?, val createdAt: Long)
@Serializable
data class ExportRate(val baseCurrency: String, val targetCurrency: String, val rate: String, val timestamp: Long)
@Serializable
data class ExportLoanDebt(val id: Long, val type: String, val personName: String, val amountOriginal: String, val currencyOriginal: String, val date: Long, val dueDate: Long?, val note: String?, val isSettled: Boolean, val createdAt: Long)
@Serializable
data class ExportRepayment(val id: Long, val loanDebtId: Long, val amount: String, val date: Long, val note: String?, val createdAt: Long)

@Singleton
class ExportImportManager @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val loanRepository: LoanRepository,
    private val rateRepository: ExchangeRateRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportAll(): String {
        val settings = financeRepository.getSettingsSync()
        val categories = financeRepository.getAllCategoriesSync()
        val expenses = financeRepository.getAllExpensesSync()
        val topUps = financeRepository.getAllTopUpsSync()
        val rates = rateRepository.getAllRates().first()
        val loanDebts = loanRepository.getAllLoanDebtsSync()
        // Need repayments for each loan
        val repayments = mutableListOf<com.spendingtracker.app.data.local.entity.Repayment>()
        for (loan in loanDebts) {
            repayments.addAll(loanRepository.getRepaymentsForLoanSync(loan.id))
        }

        val export = ExportData(
            settings = settings?.let {
                ExportSettings(
                    balanceCurrency = it.balanceCurrency,
                    startingBalance = it.startingBalance,
                    lowBalanceThreshold = it.lowBalanceThreshold,
                    theme = it.theme,
                    defaultCategoryId = it.defaultCategoryId,
                    lastRatesUpdate = it.lastRatesUpdate,
                    isSetupComplete = it.isSetupComplete
                )
            },
            categories = categories.map { ExportCategory(it.id, it.name, it.iconName, it.color, it.isDefault, it.createdAt) },
            expenses = expenses.map { ExportExpense(it.id, it.amountOriginal, it.currencyOriginal, it.amountConverted, it.currencyConverted, it.exchangeRateUsed, it.categoryId, it.date, it.note, it.paymentMethod, it.createdAt, it.updatedAt) },
            topUps = topUps.map { ExportTopUp(it.id, it.amount, it.date, it.note, it.createdAt) },
            exchangeRates = rates.map { ExportRate(it.baseCurrency, it.targetCurrency, it.rate, it.timestamp) },
            loanDebts = loanDebts.map { ExportLoanDebt(it.id, it.type, it.personName, it.amountOriginal, it.currencyOriginal, it.date, it.dueDate, it.note, it.isSettled, it.createdAt) },
            repayments = repayments.map { ExportRepayment(it.id, it.loanDebtId, it.amount, it.date, it.note, it.createdAt) }
        )
        return json.encodeToString(export)
    }

    suspend fun importAll(jsonString: String): Result<Unit> {
        return try {
            val data = json.decodeFromString<ExportData>(jsonString)
            // Clear existing
            financeRepository.eraseAll()
            loanRepository.eraseAll()
            rateRepository.clearRates()

            data.settings?.let {
                financeRepository.saveSettings(
                    AppSettings(
                        balanceCurrency = it.balanceCurrency,
                        startingBalance = it.startingBalance,
                        lowBalanceThreshold = it.lowBalanceThreshold,
                        theme = it.theme,
                        defaultCategoryId = it.defaultCategoryId,
                        lastRatesUpdate = it.lastRatesUpdate,
                        isSetupComplete = it.isSetupComplete
                    )
                )
            }
            data.categories.forEach {
                financeRepository.insertCategory(Category(it.id, it.name, it.iconName, it.color, it.isDefault, it.createdAt))
            }
            data.expenses.forEach {
                financeRepository.insertExpense(Expense(it.id, it.amountOriginal, it.currencyOriginal, it.amountConverted, it.currencyConverted, it.exchangeRateUsed, it.categoryId, it.date, it.note, it.paymentMethod, it.createdAt, it.updatedAt))
            }
            data.topUps.forEach {
                financeRepository.insertTopUp(TopUp(it.id, it.amount, it.date, it.note, it.createdAt))
            }
            data.exchangeRates.forEach {
                rateRepository.insertRate(ExchangeRate(baseCurrency = it.baseCurrency, targetCurrency = it.targetCurrency, rate = it.rate, timestamp = it.timestamp))
            }
            data.loanDebts.forEach {
                loanRepository.insertLoanDebt(LoanDebt(it.id, it.type, it.personName, it.amountOriginal, it.currencyOriginal, it.date, it.dueDate, it.note, it.isSettled, it.createdAt))
            }
            data.repayments.forEach {
                loanRepository.insertRepayment(Repayment(it.id, it.loanDebtId, it.amount, it.date, it.note, it.createdAt))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportTransactionsCsv(): String {
        val expenses = financeRepository.getAllExpensesSync()
        val topUps = financeRepository.getAllTopUpsSync()
        val balanceCurrency = financeRepository.getSettingsSync()?.balanceCurrency ?: "USD"
        fun csvEscape(value: String?): String {
            val v = value.orEmpty()
            return if (v.contains(',') || v.contains('"') || v.contains('\n')) {
                "\"${v.replace("\"", "\"\"")}\""
            } else v
        }
        val sb = StringBuilder()
        sb.appendLine("type,id,amount_original,currency_original,amount_converted,currency_converted,rate,categoryId,date,note,paymentMethod")
        expenses.forEach {
            sb.appendLine(
                "EXPENSE,${it.id},${it.amountOriginal},${it.currencyOriginal},${it.amountConverted},${it.currencyConverted},${it.exchangeRateUsed},${it.categoryId ?: ""},${it.date},${csvEscape(it.note)},${csvEscape(it.paymentMethod)}"
            )
        }
        topUps.forEach {
            // Top-ups are stored in the balance currency
            sb.appendLine(
                "TOPUP,${it.id},${it.amount},$balanceCurrency,${it.amount},$balanceCurrency,1,,${it.date},${csvEscape(it.note)},"
            )
        }
        return sb.toString()
    }
}
