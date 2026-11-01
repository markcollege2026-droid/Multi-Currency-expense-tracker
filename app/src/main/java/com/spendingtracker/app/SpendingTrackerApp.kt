package com.spendingtracker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spendingtracker.app.util.NotificationHelper
import com.spendingtracker.app.worker.LoanReminderWorker
import com.spendingtracker.app.widget.SpendingWidgetProvider
import com.spendingtracker.app.widget.WidgetRefreshWorker
import com.spendingtracker.app.worker.RecurringPaymentWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SpendingTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        scheduleLoanReminders()
        scheduleRecurringPayments()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "credit_card_autopay", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.spendingtracker.app.worker.CardAutopayWorker>(1, TimeUnit.HOURS).build()
        )
        scheduleWidgetRefresh()
    }

    /**
     * The widget's snapshot is refreshed by unique work only while a widget is
     * actually placed, so removing it from the home screen stops the work.
     */
    private fun scheduleWidgetRefresh() {
        if (SpendingWidgetProvider.widgetIds(this).isNotEmpty()) {
            WidgetRefreshWorker.schedulePeriodic(this)
            WidgetRefreshWorker.refreshNow(this)
        }
    }

    private fun scheduleLoanReminders() {
        val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LOAN_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleRecurringPayments() {
        val request = PeriodicWorkRequestBuilder<RecurringPaymentWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RECURRING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val LOAN_REMINDER_WORK_NAME = "loan_due_reminders"
        private const val RECURRING_WORK_NAME = "recurring_payments"
    }
}
