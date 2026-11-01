package com.spendingtracker.app.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.domain.AccountBalances
import com.spendingtracker.app.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Keeps the widget's snapshot fresh without the widget touching the database.
 *
 * Runs as unique work: one immediate refresh when a widget is added or tapped,
 * and a 12-hour periodic refresh (matching [Constants.RATE_REFRESH_INTERVAL_HOURS])
 * that only fires when there is a network. Both are cancelled when the last
 * widget is removed, so a user without a widget pays nothing.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository
) : CoroutineWorker(context, params) {

    // Block body on purpose: Kotlin forbids `return` inside an expression body,
    // and the early exit for "no ledger yet" needs one.
    override suspend fun doWork(): Result {
        return try {
            val account = financeRepository.observeActiveAccount().first() ?: return Result.success()
            val base = account.currency

            // Refresh the cached table only when it is actually stale, so the widget
            // does not turn into a 12-hourly network poll for no reason.
            val settings = financeRepository.getSettingsSync()
            val stale = settings?.lastRatesUpdate?.let {
                System.currentTimeMillis() - it > Constants.RATE_REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L
            } ?: true
            if (stale) {
                rateRepository.refreshRates(base).onSuccess { financeRepository.saveRatesTimestamp(it) }
            }

            val accounts = financeRepository.getAllAccountsSync()
            val balances = financeRepository.balances()
            // Same conversion rule Home uses for "total available".
            val childCurrencies = accounts
                .filter { it.parentAccountId == account.id }
                .map { it.currency }
                .distinct()
                .filter { it != base }
            val childRates: Map<String, BigDecimal> = childCurrencies.associateWith { currency ->
                rateRepository.getConversionRateOrFallback(currency, base)
            }
            val total = AccountBalances.totalAvailable(accounts, balances, account.id) { currency ->
                childRates[currency] ?: BigDecimal.ONE
            }

            val favorites = settings?.favoriteCurrencies
                ?.split(",")
                ?.map { it.trim().trim('"', '\'', '[', ']', ' ') }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val target = WidgetSnapshotStore.savedTarget(applicationContext)
                ?.takeIf { it != base }
                ?: WidgetText.targetCurrency(favorites, base)
            WidgetSnapshotStore.rememberTarget(applicationContext, target)
            val rate = rateRepository.getConversionRateStrict(base, target)

            WidgetSnapshotStore.save(
                applicationContext,
                WidgetSnapshot(
                    balance = total.toPlainString(),
                    balanceCurrency = base,
                    rateBase = base,
                    rateTarget = target,
                    rate = rate?.toPlainString(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            SpendingWidgetProvider.requestUpdate(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "widget_refresh_periodic"
        private const val ONCE_WORK = "widget_refresh_now"

        /** One immediate refresh; replaces any pending one. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(networkRequired())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                Constants.RATE_REFRESH_INTERVAL_HOURS.toLong(), TimeUnit.HOURS
            )
                .setConstraints(networkRequired())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(ONCE_WORK)
                cancelUniqueWork(PERIODIC_WORK)
            }
        }

        private fun networkRequired() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
