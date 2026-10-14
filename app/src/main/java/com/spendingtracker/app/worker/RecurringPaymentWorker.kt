package com.spendingtracker.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendingtracker.app.data.repository.RecurringRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Posts due recurring payments as expenses once a day (also triggered on app start). */
@HiltWorker
class RecurringPaymentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurringRepository: RecurringRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            recurringRepository.processDuePayments()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
