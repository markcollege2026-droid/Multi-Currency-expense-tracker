package com.spendingtracker.app.ui.screens.converter

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonObject
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.data.remote.ExchangeRateResponse
import com.spendingtracker.app.data.remote.FrankfurterTimeSeriesResponse
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.domain.CurrencyConverter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal

/**
 * Regression tests for the Converter's asynchronous behaviour:
 *
 * - chart loading always terminates (success, failure, cancellation);
 * - a slow, stale chart response can never overwrite the pair/timeframe the
 *   user selected most recently (latest-request-wins);
 * - the refresh indicator can never stick on;
 * - no fabricated 24h change values are ever produced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {

    private lateinit var db: AppDatabase

    /** Historical responses arrive per target code, whenever the test decides. */
    private class GatedApi : ExchangeRateApi {
        val historicalGates =
            mutableMapOf<String, CompletableDeferred<FrankfurterTimeSeriesResponse>>()
        var historicalError: Exception? = null
        var latest: ExchangeRateResponse? = null
        var latestError: Exception? = IOException("offline in tests")

        override suspend fun getLatestRates(base: String): ExchangeRateResponse =
            latestError?.let { throw it } ?: latest ?: error("no latest response configured")

        override suspend fun getHistoricalRates(url: String): FrankfurterTimeSeriesResponse {
            historicalError?.let { throw it }
            val target = Regex("to=([A-Z]+)").find(url)?.groupValues?.get(1)
                ?: error("unexpected historical url $url")
            return historicalGates.getOrPut(target) { CompletableDeferred() }.await()
        }

        override suspend fun getJsonUrl(url: String): JsonObject =
            throw IOException("offline in tests")

        fun completeHistorical(target: String, rates: Map<String, Map<String, Double>>) {
            historicalGates.getOrPut(target) { CompletableDeferred() }
                .complete(FrankfurterTimeSeriesResponse(rates = rates))
        }
    }

    private lateinit var api: GatedApi

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = GatedApi()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): ConverterViewModel {
        val rates = ExchangeRateRepository(db.exchangeRateDao(), api)
        val finance = FinanceRepository(
            db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(),
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
            db.loanDebtDao(), db.repaymentDao(), db
        )
        return ConverterViewModel(rates, finance, CurrencyConverter(), RuntimeEnvironment.getApplication())
    }

    private suspend fun ConverterViewModel.awaitState(
        predicate: (ConverterUiState) -> Boolean
    ): ConverterUiState = withTimeout(15_000) {
        var state = uiState.value
        while (!predicate(state)) {
            delay(5)
            state = uiState.value
        }
        state
    }

    private fun series(target: String, vararg rates: Double) = mapOf(
        // dates run oldest -> newest so points.sortBy{date} keeps this order
        "2026-11-01" to mapOf(target to rates.first()),
        "2026-11-02" to mapOf(target to rates.getOrElse(1) { rates.first() }),
        "2026-11-03" to mapOf(target to rates.getOrElse(2) { rates.first() })
    )

    @Test
    fun staleChartResponseCannotOverwriteTheLatestSelection() = runBlocking {
        val vm = viewModel()
        // The initial chart request (USD/EUR from the defaults) is in flight.
        // The user switches to USD/GBP before it can complete.
        vm.setChartPair("USD", "GBP")
        // The NEW request finishes first and is applied...
        api.completeHistorical("GBP", series("GBP", 0.8, 0.81, 0.82))
        val applied = vm.awaitState { it.chartSeries?.target == "GBP" && !it.isChartLoading }
        assertNull(applied.chartError)
        assertEquals(3, applied.chartSeries!!.points.size)
        // ...and the OLD, slower response lands afterwards. Before the fix it
        // overwrote the user's latest selection with the stale pair's data.
        api.completeHistorical("EUR", series("EUR", 0.9, 0.95, 0.99))
        delay(200)
        val after = vm.uiState.value
        assertEquals("stale response must be ignored", "GBP", after.chartSeries?.target)
        assertFalse(after.isChartLoading)
    }

    @Test
    fun chartFailureEndsLoadingAndReportsAnUnavailableState() = runBlocking {
        api.historicalError = IOException("offline")
        val vm = viewModel()
        val state = vm.awaitState { !it.isChartLoading && it.chartError != null }
        // No fabricated series may appear in the failure state.
        assertTrue(state.chartSeries == null || state.chartSeries!!.points.isEmpty())
        assertNotNull(state.chartError)
    }

    @Test
    fun chartRetryAfterAFailureRecovers() = runBlocking {
        api.historicalError = IOException("offline")
        val vm = viewModel()
        vm.awaitState { !it.isChartLoading && it.chartError != null }
        // Network comes back: the retry must clear the error and load real data.
        api.historicalError = null
        vm.loadChartSeries()
        api.completeHistorical("EUR", series("EUR", 0.9, 0.91, 0.92))
        val state = vm.awaitState { it.chartSeries != null && !it.isChartLoading }
        assertNull(state.chartError)
        assertTrue(state.chartSeries!!.isLiveHistory)
    }

    @Test
    fun refreshNeverLeavesTheIndicatorOn() = runBlocking {
        val vm = viewModel()
        // Failing refresh: the indicator must clear and the user is told we are
        // on cached rates.
        vm.refreshRatesFromApi(showFeedback = true)
        val failed = vm.awaitState { !it.isRefreshing && it.statusMessage != null }
        assertEquals("Using cached / offline rates", failed.statusMessage)
        vm.clearStatusMessage()

        // Successful refresh: indicator clears again with a confirmation.
        api.latestError = null
        api.latest = ExchangeRateResponse("success", "USD", "", mapOf("USD" to 1.0, "EUR" to 0.9))
        vm.refreshRatesFromApi(showFeedback = true)
        val done = vm.awaitState { !it.isRefreshing && it.statusMessage == "Exchange rates updated" }
        assertFalse(done.isRefreshing)
    }

    @Test
    fun converterListNeverCarriesFabricated24hChange() = runBlocking {
        val vm = viewModel()
        vm.awaitState { it.items.isNotEmpty() }
        val state = vm.uiState.value
        assertTrue(state.items.isNotEmpty())
        state.items.forEach { item ->
            assertNull("24h change must be n/a without genuine history", item.changePercent24h)
        }
    }

    @Test
    fun sameCurrencyChartIsRealAndConstant() = runBlocking {
        val vm = viewModel()
        vm.setChartPair("USD", "USD")
        val state = vm.awaitState { it.chartSeries?.base == "USD" && it.chartSeries?.target == "USD" }
        assertNull(state.chartError)
        assertTrue(state.chartSeries!!.isLiveHistory)
        assertTrue(state.chartSeries!!.points.all { it.rate == 1.0 })
    }
}
