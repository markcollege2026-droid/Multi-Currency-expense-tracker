package com.spendingtracker.app.data.remote

import com.google.gson.JsonObject
import com.spendingtracker.app.data.repository.HistoricalRatePoint
import com.spendingtracker.app.data.repository.HistoricalRateSeries
import com.spendingtracker.app.data.repository.HistoricalUnavailableReason
import com.spendingtracker.app.util.CurrencyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genuine historical prices for commodities and crypto, fetched from dated
 * snapshots of the `@fawazahmed0/currency-api` feed (now `fawazahmed0/exchange-api`).
 *
 * This is the same provider the app already uses for *current* commodity
 * prices; each calendar day is published as an immutable release
 * (`.../currency-api@YYYY-MM-DD/v1/currencies/usd.min.json`), so historical
 * values are the actual observations recorded on those dates - never
 * interpolated, estimated, or derived from today's price. Verified against the
 * npm registry: daily versions from 2024-03-02 onward, CC0-1.0 licensed,
 * no API key and no documented rate limits.
 *
 * Snapshot shape (verified):
 * ```
 * { "date": "YYYY-MM-DD", "usd": { "usd": 1, "eur": 0.92, "xau": 0.0004295864, ... } }
 * ```
 * `usd.{code}` is "1 USD expressed in `code` units", so for any pair of codes
 * in the snapshot, `1 base = (usd[target] / usd[base]) target` and the inverse
 * direction uses the same day's actual price. Codes genuinely covered:
 * XAU, XAG, XPT, XPD (troy ounces), BTC, ETH. The provider has no data at all
 * for WTI, BRENT, NG and XCU - those pairs report an honest
 * [HistoricalUnavailableReason.UNSUPPORTED_ASSET] instead of invented prices.
 */
@Singleton
class CommodityHistoryProvider internal constructor(
    private val api: ExchangeRateApi,
    private val clock: () -> Long
) {
    @Inject
    constructor(api: ExchangeRateApi) : this(api, System::currentTimeMillis)

    private data class CachedSnapshot(
        val usdRates: Map<String, Double>,
        val fetchedAtMillis: Long
    )

    private data class CachedSeries(
        val series: HistoricalRateSeries,
        val fetchedAtMillis: Long
    )

    private sealed class SnapshotOutcome {
        data class Found(val date: String, val usdRates: Map<String, Double>) : SnapshotOutcome()
        /** The provider has no snapshot for that date (HTTP 404). */
        object Missing : SnapshotOutcome()
        /** Connectivity or response-integrity failure. */
        object NetworkError : SnapshotOutcome()
    }

    private sealed class FetchOutcome {
        data class Ok(val json: JsonObject) : FetchOutcome()
        object NotFound : FetchOutcome()
        object Failed : FetchOutcome()
    }

    // Immutable, keyed by ISO date. Past dates never expire (the npm releases
    // are immutable); the in-progress "today" snapshot expires with the series
    // TTL. ConcurrentHashMap keeps concurrent loads from corrupting the cache.
    private val snapshotCache = ConcurrentHashMap<String, CachedSnapshot>()
    private val seriesCache = ConcurrentHashMap<String, CachedSeries>()

    /** True when this commodity has genuine historical data from the provider. */
    fun supportsHistorical(code: String): Boolean = code in SUPPORTED_COMMODITIES

    /**
     * Historical series for [base]/[target] covering the last [days] days.
     *
     * Returns genuine dated observations ([HistoricalRateSeries.isLiveHistory]
     * true) or an empty, honestly-labelled unavailable series. Cached results
     * are marked [HistoricalRateSeries.isFromCache] and keep their original
     * observation dates.
     */
    suspend fun getSeries(base: String, target: String, days: Int): HistoricalRateSeries {
        val cacheKey = "$base|$target|$days"
        cachedFresh(cacheKey)?.let { return it.copy(isFromCache = true) }

        if (!isSupportedPair(base, target)) {
            return unavailableSeries(base, target, HistoricalUnavailableReason.UNSUPPORTED_ASSET)
        }

        val now = clock()
        val nowIso = isoDate(now)
        val networked = fetchObservations(base, target, days, now, nowIso)

        if (networked.points.size >= 2) {
            val series = HistoricalRateSeries(
                base = base,
                target = target,
                points = networked.points,
                isLiveHistory = true
            )
            seriesCache[cacheKey] = CachedSeries(series, now)
            return series
        }

        // Nothing usable came back this time. A previously cached series (even
        // an expired one) is still genuine data with real observation dates -
        // offer it, clearly marked as cached, before declaring unavailable.
        seriesCache[cacheKey]?.let { return it.series.copy(isFromCache = true) }

        val reason = when {
            networked.hadNetworkError && networked.foundSnapshots == 0 ->
                HistoricalUnavailableReason.OFFLINE_OR_UNAVAILABLE
            else -> HistoricalUnavailableReason.NO_DATA_IN_RANGE
        }
        return unavailableSeries(base, target, reason)
    }

    // ---------------------------------------------------------------- internal

    private class NetworkResult(
        val points: List<HistoricalRatePoint>,
        val foundSnapshots: Int,
        val hadNetworkError: Boolean
    )

    private suspend fun fetchObservations(
        base: String,
        target: String,
        days: Int,
        now: Long,
        nowIso: String
    ): NetworkResult {
        val labelFmt = SimpleDateFormat("MMM d", Locale.US)
        val dates = sampleDates(now, days)
        val outcomes = coroutineScope {
            dates.map { dateIso -> async { dateIso to fetchSnapshot(dateIso) } }.awaitAll()
        }
        val points = mutableListOf<HistoricalRatePoint>()
        var found = 0
        var hadNetworkError = false
        for ((dateIso, outcome) in outcomes) {
            when (outcome) {
                is SnapshotOutcome.Found -> {
                    found++
                    // Strict date integrity: only accept the observation whose
                    // own reported date is the one we asked for.
                    if (outcome.date != dateIso) continue
                    addPoint(points, labelFmt, outcome, base, target)
                }
                SnapshotOutcome.Missing -> {
                    // The daily release for "today" can lag behind the wall
                    // clock. The `latest` snapshot is still a genuine dated
                    // observation: use it only under its own reported date.
                    if (dateIso == nowIso) {
                        when (val latest = fetchLatestSnapshot()) {
                            is SnapshotOutcome.Found -> {
                                found++
                                if (latest.date != nowIso &&
                                    dates.none { it == latest.date } &&
                                    latest.date in dates.first()..nowIso
                                ) {
                                    addPoint(points, labelFmt, latest, base, target)
                                } else if (latest.date == nowIso) {
                                    addPoint(points, labelFmt, latest, base, target)
                                }
                            }
                            SnapshotOutcome.NetworkError -> hadNetworkError = true
                            SnapshotOutcome.Missing -> Unit
                        }
                    }
                }
                SnapshotOutcome.NetworkError -> hadNetworkError = true
            }
        }
        return NetworkResult(
            points = points.sortedBy { it.timestamp },
            foundSnapshots = found,
            hadNetworkError = hadNetworkError
        )
    }

    private fun addPoint(
        points: MutableList<HistoricalRatePoint>,
        labelFmt: SimpleDateFormat,
        outcome: SnapshotOutcome.Found,
        base: String,
        target: String
    ) {
        val rate = rateFor(base, target, outcome.usdRates) ?: return
        val ts = parseIsoDate(outcome.date)?.time ?: return
        if (points.any { it.timestamp == ts }) return
        points.add(HistoricalRatePoint(ts, labelFmt.format(Date(ts)), rate))
    }

    /**
     * `1 base = ? target`, computed only from a single snapshot so both sides
     * of the ratio are the same day's genuine observation. Null when either
     * code is missing from the snapshot or the value is not positive/finite.
     */
    private fun rateFor(base: String, target: String, usdRates: Map<String, Double>): Double? {
        if (base == target) return 1.0
        val baseInUsd = usdRates[base] ?: return null
        val targetInUsd = usdRates[target] ?: return null
        val rate = targetInUsd / baseInUsd
        return rate.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * A pair is supported only when it involves at least one commodity this
     * provider genuinely covers and no commodity it does not cover. Anything
     * else is answered honestly as unsupported - no estimates.
     */
    private fun isSupportedPair(base: String, target: String): Boolean {
        val commodities = listOf(base, target).filter { CurrencyProvider.isCommodity(it) }
        if (commodities.isEmpty()) return false
        return commodities.all { it in SUPPORTED_COMMODITIES }
    }

    private fun cachedFresh(cacheKey: String): HistoricalRateSeries? {
        val cached = seriesCache[cacheKey] ?: return null
        return if (clock() - cached.fetchedAtMillis <= CACHE_TTL_MILLIS) cached.series else null
    }

    private suspend fun fetchSnapshot(dateIso: String): SnapshotOutcome {
        snapshotCache[dateIso]?.let { cached ->
            val expiresImmediately = dateIso == isoDate(clock())
            if (!expiresImmediately || clock() - cached.fetchedAtMillis <= CACHE_TTL_MILLIS) {
                return SnapshotOutcome.Found(dateIso, cached.usdRates)
            }
        }
        return when (
            val fetched = fetchWithFallback(
                primary = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$dateIso/v1/currencies/usd.min.json",
                fallback = "https://$dateIso.currency-api.pages.dev/v1/currencies/usd.min.json"
            )
        ) {
            FetchOutcome.NotFound -> SnapshotOutcome.Missing
            FetchOutcome.Failed -> SnapshotOutcome.NetworkError
            is FetchOutcome.Ok -> {
                val parsed = parseSnapshot(fetched.json, expectedDate = dateIso)
                    ?: return SnapshotOutcome.NetworkError
                snapshotCache[dateIso] = CachedSnapshot(parsed.second, clock())
                SnapshotOutcome.Found(parsed.first, parsed.second)
            }
        }
    }

    private suspend fun fetchLatestSnapshot(): SnapshotOutcome {
        return when (
            val fetched = fetchWithFallback(
                primary = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
                fallback = "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json"
            )
        ) {
            FetchOutcome.NotFound -> SnapshotOutcome.Missing
            FetchOutcome.Failed -> SnapshotOutcome.NetworkError
            is FetchOutcome.Ok -> {
                // The `latest` payload's own date is authoritative; there is
                // no requested date to match against.
                val parsed = parseSnapshot(fetched.json, expectedDate = null)
                    ?: return SnapshotOutcome.NetworkError
                SnapshotOutcome.Found(parsed.first, parsed.second)
            }
        }
    }

    /**
     * Tries the primary CDN, then the provider-documented fallback host (the
     * upstream explicitly requires a fallback mechanism). Confirmed 404 on
     * every host means "no snapshot for that date"; any other failure is a
     * network or integrity problem. [CancellationException] always propagates.
     */
    private suspend fun fetchWithFallback(primary: String, fallback: String): FetchOutcome {
        var anyNonNotFound = false
        for (url in listOf(primary, fallback)) {
            try {
                return FetchOutcome.Ok(api.getJsonUrl(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() != 404) anyNonNotFound = true
            } catch (_: Exception) {
                anyNonNotFound = true
            }
        }
        return if (anyNonNotFound) FetchOutcome.Failed else FetchOutcome.NotFound
    }

    /**
     * Validates a snapshot before it can become chart data: the payload must
     * carry a parseable date (matching [expectedDate] when one is required) and
     * only positive, finite values are taken. A snapshot that fails validation
     * is discarded entirely - bad data is never partially trusted.
     */
    private fun parseSnapshot(json: JsonObject, expectedDate: String?): Pair<String, Map<String, Double>>? {
        val date = json.get("date")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (expectedDate != null && date != expectedDate) return null
        if (parseIsoDate(date) == null) return null
        val usdObj = json.getAsJsonObject("usd") ?: return null
        val rates = mutableMapOf<String, Double>()
        for ((key, value) in usdObj.entrySet()) {
            val v = runCatching { value.asDouble }.getOrNull() ?: continue
            if (v.isFinite() && v > 0.0) {
                rates[key.uppercase(Locale.US)] = v
            }
        }
        return if (rates.isEmpty()) null else date to rates
    }

    private fun sampleDates(nowMillis: Long, days: Int): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val span = days.coerceAtLeast(1) + 1 // inclusive of both endpoints
        val stride = maxOf(1, (span + MAX_SAMPLES_PER_LOAD - 1) / MAX_SAMPLES_PER_LOAD)
        val dates = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -days.coerceAtLeast(1))
        }
        while (cal.timeInMillis <= nowMillis) {
            dates.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, stride)
        }
        val today = fmt.format(Date(nowMillis))
        if (dates.lastOrNull() != today) dates.add(today)
        return dates
    }

    private fun isoDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun parseIsoDate(date: String): Date? =
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        } catch (_: Exception) {
            null
        }

    private fun unavailableSeries(
        base: String,
        target: String,
        reason: HistoricalUnavailableReason
    ) = HistoricalRateSeries(
        base = base,
        target = target,
        points = emptyList(),
        isLiveHistory = false,
        unavailableReason = reason
    )

    companion object {
        /**
         * Commodities this provider genuinely has historical observations for
         * (verified against published snapshots: xau/xag/xpt/xpd/btc/eth are
         * present; wti/brent/ng/xcu are not published at all).
         */
        val SUPPORTED_COMMODITIES = setOf("XAU", "XAG", "XPT", "XPD", "BTC", "ETH")

        /** Cached data older than this is refreshed before it is served. */
        internal const val CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L

        /** Hard cap on snapshot fetches per chart load; older windows sample a coarser stride of real dates. */
        internal const val MAX_SAMPLES_PER_LOAD = 32
    }
}
