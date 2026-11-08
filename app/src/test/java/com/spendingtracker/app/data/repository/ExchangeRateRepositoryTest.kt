package com.spendingtracker.app.data.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonObject
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.data.remote.ExchangeRateResponse
import com.spendingtracker.app.data.remote.FrankfurterTimeSeriesResponse
import kotlinx.coroutines.runBlocking
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
 * Regression tests for exchange-rate handling:
 *
 * - an unknown currency pair must NEVER convert at a silent 1:1 rate
 *   ("getConversionRateOrFallback() may return BigDecimal.ONE when no exchange
 *   rate is found" - it now returns null and callers refuse);
 * - zero, negative, malformed and non-finite rates are rejected before storage
 *   and ignored when already stored;
 * - an invalid API response can never wipe or overwrite a valid cache;
 * - historical charts must never fabricate data: without a genuine source the
 *   series is empty and marked not-live, and there is no invented 24h change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class ExchangeRateRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var api: FakeRateApi
    private lateinit var repository: ExchangeRateRepository

    /** Controllable stand-in for the network: nothing is fetched implicitly. */
    private class FakeRateApi : ExchangeRateApi {
        var latest: ExchangeRateResponse? = null
        var latestError: Exception? = null
        var historical: FrankfurterTimeSeriesResponse? = null
        var historicalError: Exception? = null
        var historicalCalls = 0

        override suspend fun getLatestRates(base: String): ExchangeRateResponse =
            latestError?.let { throw it } ?: latest ?: error("no latest response configured")

        override suspend fun getHistoricalRates(url: String): FrankfurterTimeSeriesResponse {
            historicalCalls++
            return historicalError?.let { throw it } ?: historical ?: error("no historical response configured")
        }

        override suspend fun getJsonUrl(url: String): JsonObject =
            throw IOException("offline in tests")
    }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeRateApi()
        repository = ExchangeRateRepository(db.exchangeRateDao(), api)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun store(base: String, target: String, rate: String) {
        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = base, targetCurrency = target, rate = rate, timestamp = 1)
        )
    }

    private fun assertRate(expected: String, actual: BigDecimal?) {
        assertNotNull("expected a rate of $expected but got null", actual)
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    // ---- Unknown pairs must never fall back to 1:1 ----

    @Test
    fun unknownPairReturnsNullNeverOne() = runBlocking {
        // No cached rates at all, and both codes are unknown to the built-in
        // USD reference table: the old code returned BigDecimal.ONE here, which
        // silently converted real money at a wrong 1:1 rate.
        assertNull(repository.getConversionRateStrict("ZZZ", "QQQ"))
        assertNull(repository.getConversionRate("ZZZ", "QQQ"))
    }

    @Test
    fun unknownSideOfPairReturnsNull() = runBlocking {
        store("USD", "EUR", "0.9")
        // EUR is known but ZZZ has no quote anywhere: still null, not 1:1.
        assertNull(repository.getConversionRateStrict("EUR", "ZZZ"))
        assertNull(repository.getConversionRateStrict("ZZZ", "EUR"))
    }

    @Test
    fun sameCurrencyIsExactlyOne() = runBlocking {
        assertRate("1", repository.getConversionRateStrict("ILS", "ILS"))
    }

    @Test
    fun cachedDirectInverseAndPivotPathsResolve() = runBlocking {
        store("USD", "ILS", "3.72")
        store("USD", "EUR", "0.92")
        assertRate("3.72", repository.getConversionRateStrict("USD", "ILS"))
        // Inverse: 1 / 3.72
        val inverse = repository.getConversionRateStrict("ILS", "USD")
        assertNotNull(inverse)
        assertEquals(0, BigDecimal("1").divide(BigDecimal("3.72"), 10, java.math.RoundingMode.HALF_EVEN).compareTo(inverse))
        // Pivot: EUR -> USD -> ILS
        val cross = repository.getConversionRateStrict("EUR", "ILS")
        assertNotNull(cross)
    }

    @Test
    fun builtInUsdReferenceTableCrossesKnownCurrencies() = runBlocking {
        // Offline startup: both currencies are in the built-in USD table, which
        // is a real (if static) quote - the cross must be computed, not faked.
        val rate = repository.getConversionRateStrict("EUR", "GBP")
        assertNotNull(rate)
        // EUR -> GBP via USD: (USD->GBP) / (USD->EUR) = 0.7539 / 0.8790
        val expected = BigDecimal.valueOf(0.7539 / 0.8790)
            .setScale(10, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros()
        assertEquals(0, expected.compareTo(rate))
    }

    // ---- Invalid rates are rejected before storage and ignored when stored ----

    @Test
    fun insertRateRejectsZeroNegativeAndMalformed() = runBlocking {
        for (bad in listOf("0", "-1", "0.0", "abc", "", "1e400")) {
            try {
                repository.insertRate(
                    ExchangeRate(baseCurrency = "USD", targetCurrency = "EUR", rate = bad)
                )
                throw AssertionError("insertRate accepted invalid rate '$bad'")
            } catch (expected: IllegalArgumentException) {
                // rejected - as required
            }
        }
        assertNull(db.exchangeRateDao().getRate("USD", "EUR"))
    }

    @Test
    fun storedInvalidRowsAreIgnoredInCalculations() = runBlocking {
        // Simulate legacy/corrupt rows reaching the table. Codes deliberately
        // outside the built-in USD reference table: the stored row is the only
        // possible source, and if it is invalid there is NO rate at all - never
        // a silent 1:1.
        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = "USD", targetCurrency = "ZZZ", rate = "0", timestamp = 1)
        )
        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = "USD", targetCurrency = "QQQ", rate = "-2.5", timestamp = 1)
        )
        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = "USD", targetCurrency = "XXX", rate = "not-a-number", timestamp = 1)
        )
        assertNull(repository.getConversionRate("USD", "ZZZ"))
        assertNull(repository.getConversionRate("USD", "QQQ"))
        assertNull(repository.getConversionRate("USD", "XXX"))
        assertNull(repository.getConversionRateStrict("USD", "ZZZ"))

        // For a currency the built-in USD table does quote, the corrupt row is
        // skipped: the cache-only lookup refuses it (null) and the strict
        // lookup falls through to the genuine built-in quote (0.8790 EUR per
        // USD) - real data, still never a silent 1:1.
        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = "USD", targetCurrency = "EUR", rate = "0", timestamp = 1)
        )
        assertNull(repository.getConversionRate("USD", "EUR"))
        assertRate("0.8790", repository.getConversionRateStrict("USD", "EUR"))
    }

    // ---- API validation and cache preservation ----

    @Test
    fun refreshDropsInvalidApiValuesAndKeepsValidOnes() = runBlocking {
        api.latest = ExchangeRateResponse(
            result = "success",
            base_code = "USD",
            time_last_update_utc = "",
            rates = mapOf(
                "USD" to 1.0,
                "EUR" to 0.9,
                "GBP" to 0.0,        // zero: unusable
                "JPY" to -5.0,       // negative: unusable
                "CHF" to Double.NaN  // non-finite: unusable
            )
        )
        val result = repository.refreshRates("USD")
        assertTrue("refresh should succeed with the valid rows", result.isSuccess)
        assertRate("0.9", repository.getConversionRate("USD", "EUR"))
        assertNull(db.exchangeRateDao().getRate("USD", "GBP"))
        assertNull(db.exchangeRateDao().getRate("USD", "JPY"))
        assertNull(db.exchangeRateDao().getRate("USD", "CHF"))
    }

    @Test
    fun refreshWithoutAnyValidRateFailsAndNeverTouchesTheCache() = runBlocking {
        store("USD", "EUR", "0.9")
        api.latest = ExchangeRateResponse(
            result = "success",
            base_code = "USD",
            time_last_update_utc = "",
            rates = mapOf("EUR" to 0.0, "GBP" to -1.0)
        )
        val result = repository.refreshRates("USD")
        assertTrue("an all-invalid response must fail", result.isFailure)
        // The valid cached rate must survive - invalid API data can never
        // overwrite or erase it.
        assertRate("0.9", repository.getConversionRate("USD", "EUR"))
    }

    @Test
    fun refreshReplacesStaleRowsWithTheNewValues() = runBlocking {
        store("USD", "EUR", "0.5")
        api.latest = ExchangeRateResponse(
            result = "success",
            base_code = "USD",
            time_last_update_utc = "",
            rates = mapOf("USD" to 1.0, "EUR" to 0.9)
        )
        assertTrue(repository.refreshRates("USD").isSuccess)
        assertRate("0.9", repository.getConversionRate("USD", "EUR"))
    }

    // ---- Historical data is only shown when genuine ----

    @Test
    fun historicalSeriesUsesRealApiDataOnly() = runBlocking {
        api.historical = FrankfurterTimeSeriesResponse(
            rates = mapOf(
                "2026-11-01" to mapOf("EUR" to 0.90),
                "2026-11-02" to mapOf("EUR" to 0.91),
                "2026-11-03" to mapOf("EUR" to 0.92)
            )
        )
        val series = repository.getHistoricalSeries("USD", "EUR", 7)
        assertTrue(series.isLiveHistory)
        assertEquals(3, series.points.size)
    }

    @Test
    fun historicalFailureReturnsNoFabricatedData() = runBlocking {
        api.historicalError = IOException("offline")
        val series = repository.getHistoricalSeries("USD", "EUR", 30)
        // The old code generated a deterministic pseudo-random series here and
        // presented it as market data. It must be empty and marked not-live.
        assertFalse(series.isLiveHistory)
        assertTrue("fabricated history must not exist", series.points.isEmpty())
    }

    @Test
    fun historicalSeriesRejectsUnusablePoints() = runBlocking {
        api.historical = FrankfurterTimeSeriesResponse(
            rates = mapOf(
                "2026-11-01" to mapOf("EUR" to 0.90),
                "2026-11-02" to mapOf("EUR" to 0.0),
                "2026-11-03" to mapOf("EUR" to -1.0)
            )
        )
        val series = repository.getHistoricalSeries("USD", "EUR", 7)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
    }

    @Test
    fun commodityHistoryIsUnavailableNotFabricated() = runBlocking {
        val series = repository.getHistoricalSeries("USD", "XAU", 30)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        // Frankfurter cannot chart commodities: the API must not even be asked.
        assertEquals(0, api.historicalCalls)
    }

    @Test
    fun sameCurrencyHistoryIsDefinitionallyConstant() = runBlocking {
        val series = repository.getHistoricalSeries("EUR", "EUR", 30)
        assertTrue(series.isLiveHistory)
        assertTrue(series.points.all { it.rate == 1.0 })
    }
}
