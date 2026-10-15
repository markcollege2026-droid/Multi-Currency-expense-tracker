package com.spendingtracker.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendingtracker.app.data.repository.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Best-effort local autopay; persisted monthly keys prevent duplicate attempts. */
@HiltWorker
class CardAutopayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val financeRepository: FinanceRepository,
    private val notifications: com.spendingtracker.app.util.NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            financeRepository.processAutopay().filter { it.status == "FAILED_INSUFFICIENT_FUNDS" }.forEach { notifications.showAutopayFailure(it.cardId.toInt()) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
