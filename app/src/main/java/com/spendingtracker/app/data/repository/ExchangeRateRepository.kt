package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.ExchangeRateDao
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.util.CurrencyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class HistoricalRatePoint(
    val timestamp: Long,
    val dateLabel: String,
    val rate: Double
)

data class HistoricalRateSeries(
    val base: String,
    val target: String,
    val points: List<HistoricalRatePoint>,
    val isLiveHistory: Boolean
)

@Singleton
class ExchangeRateRepository @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
    private val api: ExchangeRateApi
) {
    fun getAllRates(): Flow<List<ExchangeRate>> = exchangeRateDao.getAllRates()

    suspend fun getRate(base: String, target: String): ExchangeRate? {
        if (base == target) {
            return ExchangeRate(
                baseCurrency = base,
                targetCurrency = target,
                rate = "1",
                timestamp = System.currentTimeMillis()
            )
        }
        return exchangeRateDao.getRate(base, target)
    }

    suspend fun getRatesForBase(base: String): List<ExchangeRate> {
        return exchangeRateDao.getRatesForBase(base)
    }

    suspend fun getAllRatesSync(): List<ExchangeRate> = exchangeRateDao.getAllRatesSync()

    suspend fun clearRates() = exchangeRateDao.deleteAll()

    /**
     * A rate is usable only when it parses as a positive, finite number.
     * Zero, negative, NaN, infinite and malformed values are never stored and
     * never used in a financial calculation.
     */
    fun isUsableRate(rate: BigDecimal): Boolean =
        rate > BigDecimal.ZERO && runCatching { rate.toPlainString().toDouble() }.getOrNull()?.isFinite() == true

    private fun usableOrNull(rate: Double): Double? =
        rate.takeIf { it.isFinite() && it > 0.0 }

    /** Parses a stored rate string, rejecting malformed or non-positive values. */
    fun parseUsableRate(raw: String): BigDecimal? =
        try { BigDecimal(raw) } catch (e: NumberFormatException) { null }?.takeIf { isUsableRate(it) }

    suspend fun refreshRates(baseCurrency: String): Result<Long> {
        return try {
            val isCommodityBase = CurrencyProvider.isCommodity(baseCurrency)
            val queryBase = if (isCommodityBase) "USD" else baseCurrency
            val response = try {
                val res = api.getLatestRates(queryBase)
                if (res.result == "success") res else api.getLatestRates("USD")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (queryBase != "USD") api.getLatestRates("USD") else throw e
            }

            if (response.result != "success") {
                return Result.failure(Exception("API returned ${response.result}"))
            }

            val actualBase = response.base_code.ifBlank { queryBase }
            val timestamp = System.currentTimeMillis()
            // Reject unusable API values outright: a zero/negative/NaN rate must
            // never overwrite a previously cached good rate for the same pair.
            val mergedRates = response.rates.mapNotNull { (target, rateDouble) ->
                usableOrNull(rateDouble)?.let { target to it }
            }.toMap().toMutableMap()
            if (mergedRates.isEmpty()) {
                return Result.failure(Exception("API returned no valid exchange rates"))
            }

            // Try to enrich with live metal/commodity rates (1 USD -> commodity units)
            val liveUsdCommodities = fetchLiveUsdCommodities()
            val usdRateFromActualBase = mergedRates["USD"]
                ?: if (actualBase == "USD") 1.0 else null
            if (usdRateFromActualBase != null) {
                for ((commodityCode, defaultUsdRate) in DEFAULT_USD_TO_COMMODITY_RATES) {
                    if (!mergedRates.containsKey(commodityCode)) {
                    val usdToCommodity = usableOrNull(liveUsdCommodities[commodityCode] ?: defaultUsdRate)
                    val cross = usdToCommodity?.let { usdRateFromActualBase * it }
                    if (cross != null) {
                        usableOrNull(cross)?.let { mergedRates[commodityCode] = it }
                    }
                    }
                }
            }

            val rates = mergedRates.map { (target, rateDouble) ->
                ExchangeRate(
                    baseCurrency = actualBase,
                    targetCurrency = target,
                    rate = BigDecimal.valueOf(rateDouble).toPlainString(),
                    timestamp = timestamp
                )
            }
            // Delete + insert atomically: a crash between them used to leave the
            // base with no rates at all.
            exchangeRateDao.replaceRatesForBase(actualBase, rates)

            // If caller requested a commodity base (e.g. XAU), also materialize direct rows for baseCurrency
            if (actualBase != baseCurrency) {
                val baseRateInActual = mergedRates[baseCurrency]
                if (baseRateInActual != null) {
                    val directRates = mergedRates.mapNotNull { (target, targetRateInActual) ->
                        val ratio = targetRateInActual / baseRateInActual
                        usableOrNull(ratio)?.let { ratioValue ->
                            ExchangeRate(
                                baseCurrency = baseCurrency,
                                targetCurrency = target,
                                rate = BigDecimal.valueOf(ratioValue).toPlainString(),
                                timestamp = timestamp
                            )
                        }
                    }
                    exchangeRateDao.replaceRatesForBase(baseCurrency, directRates)
                }
            }

            Result.success(timestamp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchLiveUsdCommodities(): Map<String, Double> {
        return try {
            val json = api.getJsonUrl("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")
            val usdObj = json.getAsJsonObject("usd") ?: return emptyMap()
            val map = mutableMapOf<String, Double>()
            listOf("xau", "xag", "xpt", "xpd", "btc", "eth").forEach { key ->
                if (usdObj.has(key)) {
                    val v = usdObj.get(key).asDouble
                    if (v > 0.0 && v.isFinite()) {
                        map[key.uppercase(Locale.US)] = v
                    }
                }
            }
            map
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun getConversionRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        if (fromCurrency == toCurrency) return BigDecimal.ONE
        // Load the (small) rate table once and look for a conversion path:
        // 1) direct: from -> to
        // 2) inverse: to -> from
        // 3) cross-rate through any pivot currency we have a quote for,
        //    e.g. EUR -> USD -> ILS when only USD-based rates are cached.
        // Malformed or non-positive stored rows are ignored: a bad rate must
        // never take part in a financial calculation.
        val table = try {
            exchangeRateDao.getAllRatesSync()
                .mapNotNull { r ->
                    parseUsableRate(r.rate)?.let { rate ->
                        Triple(r.baseCurrency, r.targetCurrency, rate)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        fun leg(from: String, to: String): BigDecimal? {
            table.firstOrNull { it.first == from && it.second == to }?.let { return it.third }
            table.firstOrNull { it.first == to && it.second == from }?.let {
                return BigDecimal.ONE.divide(it.third, 10, RoundingMode.HALF_EVEN)
            }
            return null
        }

        leg(fromCurrency, toCurrency)?.let { return it }

        val currencies = table.flatMap { listOf(it.first, it.second) }.distinct()
        for (pivot in currencies) {
            if (pivot == fromCurrency || pivot == toCurrency) continue
            val first = leg(fromCurrency, pivot) ?: continue
            val second = leg(pivot, toCurrency) ?: continue
            return first.multiply(second).setScale(10, RoundingMode.HALF_EVEN).stripTrailingZeros()
        }
        return null
    }

    /**
     * Conversion rate from the Room cache (populated by open.er-api.com), falling
     * back to the built-in USD reference table for offline startup so the
     * Converter UI can display immediate rates.
     *
     * Returns **null** when no real quote exists for the pair. It deliberately
     * never invents a 1:1 rate: an unknown pair converted at parity silently
     * moves real money to the wrong value. Callers must surface "rate
     * unavailable" and refuse the operation (or mark derived totals as
     * incomplete) instead of substituting a guess.
     */
    suspend fun getConversionRateStrict(fromCurrency: String, toCurrency: String): BigDecimal? {
        if (fromCurrency == toCurrency) return BigDecimal.ONE
        getConversionRate(fromCurrency, toCurrency)?.let { return it }
        val usdToFrom = getConversionRate("USD", fromCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[fromCurrency]
        val usdToTo = getConversionRate("USD", toCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[toCurrency]
        if (usdToFrom != null && usdToTo != null &&
            usdToFrom.isFinite() && usdToTo.isFinite() && usdToFrom > 0.0 && usdToTo > 0.0
        ) {
            return BigDecimal.valueOf(usdToTo / usdToFrom)
                .setScale(10, RoundingMode.HALF_EVEN)
                .stripTrailingZeros()
        }
        return null
    }

    /**
     * Fetches historical exchange rate series for the Charts view from the
     * Frankfurter API. Historical values are only ever shown when they come from
     * that legitimate source; when it is unavailable (offline, unsupported pair
     * such as commodities, or an API failure) the returned series has **no
     * points** and `isLiveHistory = false`, so the UI can say "unavailable"
     * instead of drawing invented market data.
     */
    suspend fun getHistoricalSeries(
        base: String,
        target: String,
        days: Int
    ): HistoricalRateSeries {
        // Same currency: the rate is definitionally constant - not market data.
        if (base == target) {
            val endCal = Calendar.getInstance()
            val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
            val labelDateFmt = SimpleDateFormat("MMM d", Locale.US)
            return HistoricalRateSeries(
                base = base,
                target = target,
                points = listOf(
                    HistoricalRatePoint(startCal.timeInMillis, labelDateFmt.format(startCal.time), 1.0),
                    HistoricalRatePoint(endCal.timeInMillis, labelDateFmt.format(endCal.time), 1.0)
                ),
                isLiveHistory = true
            )
        }
        if (!CurrencyProvider.isCommodity(base) && !CurrencyProvider.isCommodity(target)) {
            try {
                val endCal = Calendar.getInstance()
                val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
                val apiDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val labelDateFmt = SimpleDateFormat("MMM d", Locale.US)
                val startStr = apiDateFmt.format(startCal.time)
                val endStr = apiDateFmt.format(endCal.time)
                val url = "https://api.frankfurter.app/$startStr..$endStr?from=$base&to=$target"
                val response = api.getHistoricalRates(url)
                if (response.rates.isNotEmpty()) {
                    val points = response.rates.entries
                        .sortedBy { it.key }
                        .mapNotNull { (dateStr, map) ->
                            val r = map[target] ?: return@mapNotNull null
                            // Unusable values are not charted either.
                            if (!r.isFinite() || r <= 0.0) return@mapNotNull null
                            val parsedDate = try { apiDateFmt.parse(dateStr) } catch (_: Exception) { null }
                            val ts = parsedDate?.time ?: System.currentTimeMillis()
                            val label = if (parsedDate != null) labelDateFmt.format(parsedDate) else dateStr
                            HistoricalRatePoint(ts, label, r)
                        }

                    if (points.size >= 2) {
                        return HistoricalRateSeries(
                            base = base,
                            target = target,
                            points = points,
                            isLiveHistory = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No genuine history available: report unavailable below.
            }
        }
        return HistoricalRateSeries(
            base = base,
            target = target,
            points = emptyList(),
            isLiveHistory = false
        )
    }

    suspend fun insertRate(rate: ExchangeRate) {
        // A stored rate is money-relevant data: malformed or non-positive values
        // (e.g. from a tampered backup) are rejected outright.
        require(parseUsableRate(rate.rate) != null) {
            "Invalid exchange rate ${rate.rate} for ${rate.baseCurrency}/${rate.targetCurrency}"
        }
        exchangeRateDao.insertRate(rate)
    }

    companion object {
        // 1 USD in each commodity unit (inverse of USD price per unit)
        val DEFAULT_USD_TO_COMMODITY_RATES = mapOf(
            "XAU" to (1.0 / 2720.0),   // Gold (Troy Ounce) ~$2,720/oz
            "XAG" to (1.0 / 32.50),    // Silver (Troy Ounce) ~$32.50/oz
            "XPT" to (1.0 / 1010.0),   // Platinum (Troy Ounce) ~$1,010/oz
            "XPD" to (1.0 / 1060.0),   // Palladium (Troy Ounce) ~$1,060/oz
            "WTI" to (1.0 / 71.50),    // Crude Oil WTI (Barrel) ~$71.50/bbl
            "BRENT" to (1.0 / 75.20),  // Brent Crude Oil (Barrel) ~$75.20/bbl
            "NG" to (1.0 / 2.65),      // Natural Gas (MMBtu) ~$2.65/MMBtu
            "XCU" to (1.0 / 4.35),     // Copper (Pound) ~$4.35/lb
            "BTC" to (1.0 / 67500.0),  // Bitcoin ~$67,500
            "ETH" to (1.0 / 2620.0)    // Ethereum ~$2,620
        )

        // Baseline 1 USD -> currency rates for offline fallback before first API sync
        val DEFAULT_USD_CURRENCY_RATES = mapOf(
            "USD" to 1.0,
            "EUR" to 0.8790,
            "GBP" to 0.7539,
            "ILS" to 3.7250,
            "JPY" to 149.80,
            "AUD" to 1.5120,
            "CAD" to 1.3820,
            "CHF" to 0.8640,
            "CNY" to 7.1150,
            "SEK" to 10.420,
            "NZD" to 1.6580,
            "MXN" to 19.850,
            "SGD" to 1.3140,
            "HKD" to 7.7720,
            "NOK" to 10.890,
            "KRW" to 1368.0,
            "TRY" to 34.250,
            "INR" to 84.050,
            "BRL" to 5.6800,
            "ZAR" to 17.620,
            "RUB" to 96.500,
            "PLN" to 3.9800,
            "DKK" to 6.8600,
            "THB" to 33.450,
            "AED" to 3.6725,
            "SAR" to 3.7500,
            "EGP" to 48.650,
            "CZK" to 23.250,
            "HUF" to 368.50,
            "RON" to 4.5800,
            "UAH" to 41.250,
            "PHP" to 57.800,
            "IDR" to 15580.0,
            "MYR" to 4.3100,
            "VND" to 25150.0,
            "ARS" to 985.00,
            "CLP" to 948.00,
            "COP" to 4260.0,
            "PEN" to 3.7600,
            "NGN" to 1640.0,
            "KES" to 129.00,
            "PKR" to 277.80,
            "BDT" to 119.50,
            "TWD" to 32.100,
            "QAR" to 3.6400,
            "KWD" to 0.3065,
            "BHD" to 0.3760,
            "OMR" to 0.3850,
            "JOD" to 0.7090
        )

        val DEFAULT_ALL_USD_RATES: Map<String, Double> =
            DEFAULT_USD_CURRENCY_RATES + DEFAULT_USD_TO_COMMODITY_RATES
    }
}
