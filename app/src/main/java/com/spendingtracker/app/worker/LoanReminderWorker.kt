package com.spendingtracker.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.util.CurrencyFormatter
import com.spendingtracker.app.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LoanReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val loanRepository: LoanRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val loans = loanRepository.getAllLoanDebtsSync()
            val now = System.currentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L
            loans.forEach { loan ->
                if (loan.isSettled) return@forEach
                val due = loan.dueDate ?: return@forEach
                // Notify if due within 1 day or overdue
                if (due - now <= oneDay) {
                    val amount = "${loan.amountOriginal} ${loan.currencyOriginal}"
                    notificationHelper.showLoanDueNotification(loan.personName, amount, loan.id.toInt())
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
