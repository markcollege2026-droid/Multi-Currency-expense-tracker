package com.spendingtracker.app.data.remote

import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Regression tests for genuine commodity historical data:
 *
 * - covered assets (gold, silver, platinum, palladium, bitcoin, ethereum) return
 *   the actual dated observations of the provider, in both directions, with
 *   per-date inverses and same-day cross rates;
 * - uncovered assets (WTI, Brent, natural gas, copper) are honestly
 *   unavailable - never estimated;
 * - invalid, mislabelled or missing observations are rejected, never repaired
 *   by interpolation, repetition of today's price, or hardcoded fallbacks;
 * - the cache serves repeats without refetching, refreshes when expired, stays
 *   distinct per commodity/period, is identified when served, and survives
 *   concurrent use; offline expiry falls back to the marked cached copy.
 *
 * All network responses are faked: no test needs internet access.
 */
class CommodityHistoryProviderTest {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val labelFmt = SimpleDateFormat("MMM d", Locale.US)

    /** 2026-11-11 local noon: midday avoids any date-boundary ambiguity. */
    private val now0: Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .parse("2026-11-11 12:00")!!.time

    private var now = now0

    private class FakeCommodityApi : ExchangeRateApi {
        /** Date ISO (or "latest") -> serving function. Absent key -> HTTP 404. */
        val snapshots = mutableMapOf<String, () -> JsonObject>()
        val calls = CopyOnWriteArrayList<String>()
        var gate: CompletableDeferred<Unit>? = null
        var failWith: Exception? = null

        override suspend fun getJsonUrl(url: String): JsonObject {
            calls += url
            gate?.await()
            failWith?.let { throw it }
            val key = when {
                "currency-api@" in url ->
                    url.substringAfter("currency-api@").substringBefore("/")
                ".currency-api.pages.dev" in url ->
                    url.removePrefix("https://").substringBefore(".currency-api.pages.dev")
                else -> error("unexpected url: $url")
            }
            val serve = snapshots[key]
                ?: throw HttpException(Response.error<JsonObject>(404, "".toResponseBody(null)))
            return serve()
        }

        override suspend fun getLatestRates(base: String): ExchangeRateResponse =
            error("getLatestRates must not be called for commodity history")

        override suspend fun getHistoricalRates(url: String): FrankfurterTimeSeriesResponse =
            error("Frankfurter must not be called for commodity history")
    }

    private lateinit var api: FakeCommodityApi

    private fun provider() = CommodityHistoryProvider(api, { now })

    private fun snapshot(date: String, rates: Map<String, Double>): JsonObject {
        val root = JsonObject()
        root.addProperty("date", date)
        val usd = JsonObject()
        usd.addProperty("usd", 1.0)
        for ((code, value) in rates) usd.addProperty(code.lowercase(), value)
        root.add("usd", usd)
        return root
    }

    private fun FakeCommodityApi.serve(date: String, rates: Map<String, Double>) {
        snapshots[date] = { snapshot(date, rates) }
    }

    private fun dateDaysAgo(days: Int): String = isoFmt.format(Date(now0 - days * 86_400_000L))

    private fun label(iso: String): String = labelFmt.format(isoFmt.parse(iso)!!)

    // ---- B: genuine commodity history, both directions ----

    @Test
    fun usdToGoldReturnsGenuineDatedObservations() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))

        val series = provider().getSeries("USD", "XAU", days = 2)

        assertTrue(series.isLiveHistory)
        assertFalse(series.isFromCache)
        assertEquals(3, series.points.size)
        // The charted values are exactly the provider's observations.
        assertEquals(0.00040, series.points[0].rate, 0.0)
        assertEquals(0.00045, series.points[1].rate, 0.0)
        assertEquals(0.00050, series.points[2].rate, 0.0)
        // Observations keep their real dates, oldest first.
        assertEquals(label(dateDaysAgo(2)), series.points[0].dateLabel)
        assertEquals(label(dateDaysAgo(1)), series.points[1].dateLabel)
        assertEquals(label(dateDaysAgo(0)), series.points[2].dateLabel)
        assertTrue(series.points.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        // Endpoint contract: dated snapshots of the existing commodity feed.
        assertTrue(api.calls.first().contains("currency-api@${dateDaysAgo(2)}/v1/currencies/usd.min.json"))
    }

    @Test
    fun goldToUsdUsesTheInverseOfEachDateNotTodaysRate() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))

        val series = provider().getSeries("XAU", "USD", days = 2)

        assertTrue(series.isLiveHistory)
        assertEquals(3, series.points.size)
        // Each point is that date's own price, not one global inverse.
        assertEquals(1.0 / 0.00040, series.points[0].rate, 0.0)
        assertEquals(1.0 / 0.00045, series.points[1].rate, 0.0)
        assertEquals(1.0 / 0.00050, series.points[2].rate, 0.0)
    }

    @Test
    fun silverAndPlatinumHaveGenuineHistory() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xag" to 0.02, "xpt" to 0.001))
        api.serve(dateDaysAgo(0), mapOf("xag" to 0.025, "xpt" to 0.0012))

        val silver = provider().getSeries("XAG", "USD", days = 1)
        assertTrue(silver.isLiveHistory)
        assertEquals(2, silver.points.size)
        assertEquals(1.0 / 0.02, silver.points[0].rate, 0.0)
        assertEquals(1.0 / 0.025, silver.points[1].rate, 0.0)

        val platinum = provider().getSeries("XPT", "USD", days = 1)
        assertTrue(platinum.isLiveHistory)
        assertEquals(2, platinum.points.size)
        assertEquals(1.0 / 0.001, platinum.points[0].rate, 0.0)
        assertEquals(1.0 / 0.0012, platinum.points[1].rate, 0.0)
    }

    @Test
    fun palladiumBitcoinAndEthereumHaveGenuineHistory() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xpd" to 0.001, "btc" to 1e-5, "eth" to 2e-4))
        api.serve(dateDaysAgo(0), mapOf("xpd" to 0.0011, "btc" to 1.1e-5, "eth" to 2.2e-4))

        for ((base, target, first) in listOf(
            Triple("XPD", "USD", 1.0 / 0.001),
            Triple("BTC", "USD", 1.0 / 1e-5),
            Triple("ETH", "USD", 1.0 / 2e-4)
        )) {
            val series = provider().getSeries(base, target, days = 1)
            assertTrue("$base/$target must have genuine history", series.isLiveHistory)
            assertEquals(2, series.points.size)
            assertEquals(first, series.points[0].rate, 0.0)
        }
    }

    @Test
    fun crossRatesUseSameDayPricesFromASingleSnapshot() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00040, "xag" to 0.02, "eur" to 0.90))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050, "xag" to 0.025, "eur" to 0.95))

        // Commodity vs commodity: gold priced in silver, each day's own ratio.
        val goldInSilver = provider().getSeries("XAU", "XAG", days = 1)
        assertTrue(goldInSilver.isLiveHistory)
        assertEquals(0.02 / 0.00040, goldInSilver.points[0].rate, 0.0)
        assertEquals(0.025 / 0.00050, goldInSilver.points[1].rate, 0.0)

        // Currency vs commodity: same-day cross through USD, never a mix of days.
        val eurToGold = provider().getSeries("EUR", "XAU", days = 1)
        assertTrue(eurToGold.isLiveHistory)
        assertEquals(0.00040 / 0.90, eurToGold.points[0].rate, 0.0)
        assertEquals(0.00050 / 0.95, eurToGold.points[1].rate, 0.0)
    }

    @Test
    fun datesAreChronologicallyOrderedEvenWhenServedOutOfOrder() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))

        val series = provider().getSeries("USD", "XAU", days = 2)
        assertEquals(3, series.points.size)
        assertTrue(series.points.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        assertEquals(0.00040, series.points.first().rate, 0.0)
        assertEquals(0.00050, series.points.last().rate, 0.0)
    }

    // ---- honest unavailability for uncovered commodities ----

    @Test
    fun wtiBrentNaturalGasAndCopperAreHonestUnavailable() = runBlocking {
        api = FakeCommodityApi()
        for (code in listOf("WTI", "BRENT", "NG", "XCU")) {
            val series = provider().getSeries("USD", code, days = 7)
            assertFalse("$code must not claim live history", series.isLiveHistory)
            assertTrue("$code must have no points", series.points.isEmpty())
            assertEquals(
                com.spendingtracker.app.data.repository.HistoricalUnavailableReason.UNSUPPORTED_ASSET,
                series.unavailableReason
            )
        }
        // Honest unavailability is answered without touching the network.
        assertTrue("no fetches may happen for unsupported assets", api.calls.isEmpty())
    }

    @Test
    fun mixingSupportedAndUnsupportedCommoditiesIsUnsupported() = runBlocking {
        api = FakeCommodityApi()
        val series = provider().getSeries("XAU", "WTI", days = 7)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        assertEquals(
            com.spendingtracker.app.data.repository.HistoricalUnavailableReason.UNSUPPORTED_ASSET,
            series.unavailableReason
        )
        assertTrue(api.calls.isEmpty())
    }

    // ---- C: data integrity ----

    @Test
    fun invalidSnapshotValuesAreRejectedNeverCharted() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.0))       // zero: unusable
        api.serve(dateDaysAgo(0), mapOf("xau" to -1.0))      // negative: unusable

        val series = provider().getSeries("USD", "XAU", days = 2)
        // Only the one genuine, usable observation survives - and a single
        // point is not charted as a trend. Nothing is inserted in its place.
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        assertEquals(
            com.spendingtracker.app.data.repository.HistoricalUnavailableReason.NO_DATA_IN_RANGE,
            series.unavailableReason
        )
    }

    @Test
    fun nonFiniteSnapshotValuesAreRejected() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to Double.NaN, "xag" to 0.02))
        api.serve(dateDaysAgo(0), mapOf("xau" to Double.POSITIVE_INFINITY, "xag" to 0.025))

        val gold = provider().getSeries("USD", "XAU", days = 1)
        assertFalse(gold.isLiveHistory)
        assertTrue(gold.points.isEmpty())

        // Silver in the same snapshots is fine and unaffected.
        val silver = provider().getSeries("USD", "XAG", days = 1)
        assertTrue(silver.isLiveHistory)
        assertEquals(2, silver.points.size)
    }

    @Test
    fun mislabeledSnapshotsAreRejectedEntirely() = runBlocking {
        api = FakeCommodityApi()
        // Claims to be from another day: it must not be charted under either date.
        api.snapshots[dateDaysAgo(1)] = { snapshot(dateDaysAgo(0), mapOf("xau" to 0.00040)) }
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
    }

    @Test
    fun missingDatesAreNotFabricated() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        // dateDaysAgo(1) intentionally absent (404)
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))

        val series = provider().getSeries("USD", "XAU", days = 2)
        assertTrue(series.isLiveHistory)
        // Two real observations, with a visible gap - never an interpolated third.
        assertEquals(2, series.points.size)
        assertEquals(0.00040, series.points[0].rate, 0.0)
        assertEquals(0.00050, series.points[1].rate, 0.0)
        assertEquals(label(dateDaysAgo(2)), series.points[0].dateLabel)
        assertEquals(label(dateDaysAgo(0)), series.points[1].dateLabel)
    }

    @Test
    fun singleObservationIsNotAChartAndNeverPaddedWithTodaysPrice() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00040))
        // today absent: only one real observation exists.

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        assertEquals(
            com.spendingtracker.app.data.repository.HistoricalUnavailableReason.NO_DATA_IN_RANGE,
            series.unavailableReason
        )
    }

    @Test
    fun noHardcodedFallbackPricesEverEnterTheSeries() = runBlocking {
        api = FakeCommodityApi()
        // Far from every DEFAULT_USD_TO_COMMODITY_RATES value: only these exact
        // observations may appear in the series.
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00011))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00013))

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertEquals(2, series.points.size)
        assertTrue(series.points.all { it.rate == 0.00011 || it.rate == 0.00013 })
    }

    // ---- today's lagging snapshot ----

    @Test
    fun todaysMissingSnapshotFallsBackToLatestUnderItsOwnRealDate() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        // "today" 404s on both hosts; `latest` really holds today's observation.
        api.snapshots["latest"] = { snapshot(dateDaysAgo(0), mapOf("xau" to 0.00050)) }

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertTrue(series.isLiveHistory)
        assertEquals(2, series.points.size)
        assertEquals(0.00050, series.points[1].rate, 0.0)
        assertEquals(label(dateDaysAgo(0)), series.points[1].dateLabel)
    }

    @Test
    fun latestCarryingAnOlderDateIsNeverClaimedAsToday() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(2), mapOf("xau" to 0.00040))
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        // "today" 404s and `latest` still points at yesterday's file.
        api.snapshots["latest"] = { snapshot(dateDaysAgo(1), mapOf("xau" to 0.00045)) }

        val series = provider().getSeries("USD", "XAU", days = 2)
        assertTrue(series.isLiveHistory)
        assertEquals(2, series.points.size)
        // The newest charted point is yesterday - its real observation date.
        // Today is a visible gap, never filled by repeating the latest price.
        assertEquals(label(dateDaysAgo(1)), series.points.last().dateLabel)
    }

    // ---- D: network and loading ----

    @Test
    fun networkFailureEndsAsUnavailableNotFabrication() = runBlocking {
        api = FakeCommodityApi()
        api.failWith = IOException("offline")

        val series = provider().getSeries("USD", "XAU", days = 7)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        assertEquals(
            com.spendingtracker.app.data.repository.HistoricalUnavailableReason.OFFLINE_OR_UNAVAILABLE,
            series.unavailableReason
        )
    }

    @Test
    fun fallbackHostIsUsedWhenPrimaryFails() = runBlocking {
        api = FakeCommodityApi()
        var attempts = 0
        api.snapshots[dateDaysAgo(1)] = {
            attempts++
            if (attempts == 1) throw IOException("cdn down") else snapshot(dateDaysAgo(1), mapOf("xau" to 0.00045))
        }
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertTrue(series.isLiveHistory)
        assertEquals(2, series.points.size)
        // The provider-documented fallback host was exercised for the failed date.
        assertTrue(api.calls.any { it.startsWith("https://${dateDaysAgo(1)}.currency-api.pages.dev") })
    }

    @Test
    fun emptySnapshotIsAnUnavailableResult() = runBlocking {
        api = FakeCommodityApi()
        // Snapshots exist but hold no usable rates at all.
        api.snapshots[dateDaysAgo(1)] = { JsonObject() }
        api.snapshots[dateDaysAgo(0)] = { JsonObject() }

        val series = provider().getSeries("USD", "XAU", days = 1)
        assertFalse(series.isLiveHistory)
        assertTrue(series.points.isEmpty())
        assertNull(series.points.firstOrNull())
    }

    @Test
    fun cancellationPropagatesToTheCaller() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))
        api.gate = CompletableDeferred() // never completes

        val job = launch { provider().getSeries("USD", "XAU", days = 1) }
        while (api.calls.isEmpty()) kotlinx.coroutines.yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    // ---- E: caching ----

    @Test
    fun repeatedRequestsAreServedFromCacheWithoutRefetching() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))
        val repo = provider()

        val first = repo.getSeries("USD", "XAU", days = 1)
        val fetchesAfterFirst = api.calls.size
        val second = repo.getSeries("USD", "XAU", days = 1)

        assertTrue(first.isLiveHistory)
        assertFalse("freshly fetched data is not marked cached", first.isFromCache)
        assertTrue("cached data must be identified as such", second.isFromCache)
        assertEquals("a valid cache entry must not refetch", fetchesAfterFirst, api.calls.size)
        // Cached data keeps its genuine observation dates and values.
        assertEquals(first.points.map { it.dateLabel }, second.points.map { it.dateLabel })
        assertEquals(first.points.map { it.rate }, second.points.map { it.rate })
    }

    @Test
    fun expiredCacheTriggersARefresh() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))
        val repo = provider()
        repo.getSeries("USD", "XAU", days = 1)

        // Provider data updates; the cache is now stale.
        now += CommodityHistoryProvider.CACHE_TTL_MILLIS + 1
        api.snapshots.clear()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00075))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00080))

        val refreshed = repo.getSeries("USD", "XAU", days = 1)
        assertFalse("stale data must not masquerade as fresh", refreshed.isFromCache)
        assertTrue(refreshed.isLiveHistory)
        assertTrue(refreshed.points.any { it.rate == 0.00075 })
        assertTrue(refreshed.points.any { it.rate == 0.00080 })
    }

    @Test
    fun offlineRequestServesTheExpiredCacheMarkedAsCached() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050))
        val repo = provider()
        val fresh = repo.getSeries("USD", "XAU", days = 1)

        now += CommodityHistoryProvider.CACHE_TTL_MILLIS + 1
        api.snapshots.clear()
        api.failWith = IOException("offline")

        val offline = repo.getSeries("USD", "XAU", days = 1)
        assertTrue("offline cache fallback is still genuine history", offline.isLiveHistory)
        assertTrue("it must be identified as cached, not freshly fetched", offline.isFromCache)
        // The original observation dates are preserved exactly.
        assertEquals(fresh.points.map { it.dateLabel }, offline.points.map { it.dateLabel })
        assertEquals(fresh.points.map { it.rate }, offline.points.map { it.rate })
    }

    @Test
    fun differentCommoditiesAndTimeframesNeverShareCacheEntries() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045, "xag" to 0.02))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050, "xag" to 0.025))
        val repo = provider()

        val gold = repo.getSeries("USD", "XAU", days = 1)
        val fetchesAfterGold = api.calls.size
        val silver = repo.getSeries("USD", "XAG", days = 1)
        val goldWeek = repo.getSeries("USD", "XAU", days = 7)

        assertFalse(silver.isFromCache)
        assertFalse(goldWeek.isFromCache)
        assertTrue("a different commodity must not hit gold's cache", api.calls.size > fetchesAfterGold)
        assertTrue(gold.points.all { it.rate == 0.00045 || it.rate == 0.00050 })
        assertTrue(silver.points.all { it.rate == 0.02 || it.rate == 0.025 })
    }

    @Test
    fun concurrentLoadsDoNotCorruptTheCache() = runBlocking {
        api = FakeCommodityApi()
        api.serve(dateDaysAgo(1), mapOf("xau" to 0.00045, "xag" to 0.02, "xpt" to 0.001, "btc" to 1e-5))
        api.serve(dateDaysAgo(0), mapOf("xau" to 0.00050, "xag" to 0.025, "xpt" to 0.0012, "btc" to 1.1e-5))
        val repo = provider()

        val pairs = listOf("XAU" to 0.00045, "XAG" to 0.02, "XPT" to 0.001, "BTC" to 1e-5)
        val jobs = pairs.map { (code, expected) ->
            launch {
                val series = repo.getSeries("USD", code, days = 1)
                assertTrue(series.isLiveHistory)
                assertTrue("$code cache crossed wires", series.points.any { it.rate == expected })
            }
        }
        jobs.forEach { it.join() }

        // Every key now serves its own cached copy, without cross-talk.
        for ((code, expected) in pairs) {
            val cached = repo.getSeries("USD", code, days = 1)
            assertTrue(cached.isFromCache)
            assertTrue("$code cached entry lost its data", cached.points.any { it.rate == expected })
        }
    }

    @Test
    fun supportedCoversExactlyTheProviderBackedCommodities() {
        assertTrue(CommodityHistoryProvider.SUPPORTED_COMMODITIES.containsAll(
            listOf("XAU", "XAG", "XPT", "XPD", "BTC", "ETH")
        ))
        assertFalse(CommodityHistoryProvider.SUPPORTED_COMMODITIES.contains("WTI"))
        assertFalse(CommodityHistoryProvider.SUPPORTED_COMMODITIES.contains("BRENT"))
        assertFalse(CommodityHistoryProvider.SUPPORTED_COMMODITIES.contains("NG"))
        assertFalse(CommodityHistoryProvider.SUPPORTED_COMMODITIES.contains("XCU"))
    }
}
