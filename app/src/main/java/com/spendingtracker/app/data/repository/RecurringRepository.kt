package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.AccountDao
import com.spendingtracker.app.data.local.dao.ExpenseDao
import com.spendingtracker.app.data.local.dao.RecurringPaymentDao
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.RecurringPayment
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(
    private val recurringPaymentDao: RecurringPaymentDao,
    private val accountDao: AccountDao,
    private val expenseDao: ExpenseDao,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    fun getForAccount(accountId: Long): Flow<List<RecurringPayment>> =
        recurringPaymentDao.getForAccount(accountId)

    fun getAll(): Flow<List<RecurringPayment>> = recurringPaymentDao.getAll()

    suspend fun getById(id: Long): RecurringPayment? = recurringPaymentDao.getById(id)

    suspend fun getAllSync(): List<RecurringPayment> = recurringPaymentDao.getAllSync()

    suspend fun insert(payment: RecurringPayment): Long = recurringPaymentDao.insert(payment)

    suspend fun update(payment: RecurringPayment) = recurringPaymentDao.update(payment)

    suspend fun delete(payment: RecurringPayment) = recurringPaymentDao.delete(payment)

    suspend fun deleteForAccount(accountId: Long) = recurringPaymentDao.deleteForAccount(accountId)

    /**
     * Posts every due recurring payment as an expense in its account and advances
     * its schedule. Catches up missed occurrences (capped per run).
     *
     * If an exchange rate is not available yet, the payment stays due and is
     * retried on the next run - it is never posted with a wrong amount.
     *
     * @return number of expenses created
     */
    suspend fun processDuePayments(now: Long = System.currentTimeMillis()): Int {
        var created = 0
        for (payment in recurringPaymentDao.getAllSync()) {
            if (!payment.isActive) continue
            val account = accountDao.getAccountById(payment.accountId)
            if (account == null) {
                // Orphaned payment - its account is gone
                recurringPaymentDao.update(payment.copy(isActive = false))
                continue
            }
            var next = payment.nextDueAt
            var lastRun = payment.lastRunAt
            var madeProgress = false
            var completed = false // deactivated at end date - nothing more to save
            var safety = 0
            while (next <= now && safety < MAX_CATCH_UP_PER_RUN && !completed) {
                safety++
                val end = payment.endDate
                if (end != null && next > end) {
                    // Schedule is over - keep the record but stop firing
                    recurringPaymentDao.update(
                        payment.copy(nextDueAt = next, lastRunAt = lastRun, isActive = false)
                    )
                    completed = true
                    break
                }
                // Convert at execution time with the currently cached rate
                val rate = if (payment.currencyOriginal == account.currency) {
                    BigDecimal.ONE
                } else {
                    exchangeRateRepository.getConversionRate(payment.currencyOriginal, account.currency)
                }
                if (rate == null) {
                    // No rate yet - leave the remaining occurrences due and retry
                    // later (never guess the amount). Other payments still process.
                    break
                }
                val original = payment.amountAsBigDecimal()
                val converted = original.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)
                expenseDao.insertExpense(
                    Expense(
                        accountId = account.id,
                        amountOriginal = original.toPlainString(),
                        currencyOriginal = payment.currencyOriginal,
                        amountConverted = converted.toPlainString(),
                        currencyConverted = account.currency,
                        exchangeRateUsed = rate.toPlainString(),
                        categoryId = payment.categoryId,
                        date = next,
                        note = payment.note ?: payment.title,
                        paymentMethod = payment.paymentMethod
                    )
                )
                created++
                lastRun = next
                next = RecurringPayment.nextAfter(next, payment.frequency)
                madeProgress = true
            }
            if (madeProgress && !completed) {
                recurringPaymentDao.update(
                    payment.copy(nextDueAt = next, lastRunAt = lastRun)
                )
            }
        }
        return created
    }

    companion object {
        private const val MAX_CATCH_UP_PER_RUN = 50
    }
}
