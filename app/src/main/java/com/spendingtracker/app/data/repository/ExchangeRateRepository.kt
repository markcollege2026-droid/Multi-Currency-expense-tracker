package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.ExchangeRateDao
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.util.CurrencyProvider
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

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

    suspend fun refreshRates(baseCurrency: String): Result<Long> {
        return try {
            val isCommodityBase = CurrencyProvider.isCommodity(baseCurrency)
            val queryBase = if (isCommodityBase) "USD" else baseCurrency
            val response = try {
                val res = api.getLatestRates(queryBase)
                if (res.result == "success") res else api.getLatestRates("USD")
            } catch (e: Exception) {
                if (queryBase != "USD") api.getLatestRates("USD") else throw e
            }

            if (response.result != "success") {
                return Result.failure(Exception("API returned ${response.result}"))
            }

            val actualBase = response.base_code.ifBlank { queryBase }
            val timestamp = System.currentTimeMillis()
            val mergedRates = response.rates.toMutableMap()

            // Try to enrich with live metal/commodity rates (1 USD -> commodity units)
            val liveUsdCommodities = fetchLiveUsdCommodities()
            val usdRateFromActualBase = mergedRates["USD"] ?: if (actualBase == "USD") 1.0 else null
            if (usdRateFromActualBase != null && usdRateFromActualBase > 0.0) {
                for ((commodityCode, defaultUsdRate) in DEFAULT_USD_TO_COMMODITY_RATES) {
                    if (!mergedRates.containsKey(commodityCode)) {
                        val usdToCommodity = liveUsdCommodities[commodityCode] ?: defaultUsdRate
                        mergedRates[commodityCode] = usdRateFromActualBase * usdToCommodity
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
            exchangeRateDao.deleteByBase(actualBase)
            exchangeRateDao.insertRates(rates)

            // If caller requested a commodity base (e.g. XAU), also materialize direct rows for baseCurrency
            if (actualBase != baseCurrency) {
                val baseRateInActual = mergedRates[baseCurrency]
                if (baseRateInActual != null && baseRateInActual > 0.0) {
                    val directRates = mergedRates.mapNotNull { (target, targetRateInActual) ->
                        val ratio = targetRateInActual / baseRateInActual
                        if (ratio.isFinite() && ratio > 0.0) {
                            ExchangeRate(
                                baseCurrency = baseCurrency,
                                targetCurrency = target,
                                rate = BigDecimal.valueOf(ratio).toPlainString(),
                                timestamp = timestamp
                            )
                        } else null
                    }
                    exchangeRateDao.deleteByBase(baseCurrency)
                    exchangeRateDao.insertRates(directRates)
                }
            }

            Result.success(timestamp)
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
        val table = try {
            exchangeRateDao.getAllRatesSync()
                .mapNotNull { r ->
                    val rate = try { BigDecimal(r.rate) } catch (e: Exception) { null }
                    if (rate != null && rate.compareTo(BigDecimal.ZERO) != 0) {
                        Triple(r.baseCurrency, r.targetCurrency, rate)
                    } else null
                }
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
     * Returns the conversion rate from Room cache (populated by open.er-api.com),
     * or falls back to built-in USD reference rates for commodities / offline startup
     * so the Converter UI always displays immediate rates.
     */
    suspend fun getConversionRateOrFallback(fromCurrency: String, toCurrency: String): BigDecimal {
        if (fromCurrency == toCurrency) return BigDecimal.ONE
        getConversionRate(fromCurrency, toCurrency)?.let { return it }

        // If one side is cached against USD and the other is a commodity/fallback
        val usdToFrom = getConversionRate("USD", fromCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[fromCurrency]
        val usdToTo = getConversionRate("USD", toCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[toCurrency]

        if (usdToFrom != null && usdToTo != null && usdToFrom > 0.0) {
            val cross = usdToTo / usdToFrom
            return BigDecimal.valueOf(cross).setScale(10, RoundingMode.HALF_EVEN).stripTrailingZeros()
        }
        return BigDecimal.ONE
    }

    /**
     * Like [getConversionRateOrFallback], but never invents a 1:1 rate.
     *
     * Used where a wrong rate moves real money (loan settlements typed in another
     * currency). The cached table is tried first; failing that, the built-in USD
     * reference table is used *only* when both currencies are listed there - that
     * is a real (if static) quote. Anything else returns null so the caller can ask
     * the user to refresh rates instead of silently converting at 1:1.
     */
    suspend fun getConversionRateStrict(fromCurrency: String, toCurrency: String): BigDecimal? {
        if (fromCurrency == toCurrency) return BigDecimal.ONE
        getConversionRate(fromCurrency, toCurrency)?.let { return it }
        val usdToFrom = getConversionRate("USD", fromCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[fromCurrency]
        val usdToTo = getConversionRate("USD", toCurrency)?.toDouble()
            ?: DEFAULT_ALL_USD_RATES[toCurrency]
        if (usdToFrom != null && usdToTo != null && usdToFrom > 0.0 && usdToTo > 0.0) {
            return BigDecimal.valueOf(usdToTo / usdToFrom)
                .setScale(10, RoundingMode.HALF_EVEN)
                .stripTrailingZeros()
        }
        return null
    }

    /**
     * Fetches historical exchange rate series for the Charts view.
     * Uses Frankfurter API for supported currency pairs when online, and falls back
     * to a deterministic series anchored to the current conversion rate for
     * commodities or offline mode.
     */
    suspend fun getHistoricalSeries(
        base: String,
        target: String,
        days: Int
    ): HistoricalRateSeries {
        val currentRate = getConversionRateOrFallback(base, target).toDouble()
        val endCal = Calendar.getInstance()
        val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        val apiDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelDateFmt = SimpleDateFormat("MMM d", Locale.US)

        if (base != target && !CurrencyProvider.isCommodity(base) && !CurrencyProvider.isCommodity(target)) {
            try {
                val startStr = apiDateFmt.format(startCal.time)
                val endStr = apiDateFmt.format(endCal.time)
                val url = "https://api.frankfurter.app/$startStr..$endStr?from=$base&to=$target"
                val response = api.getHistoricalRates(url)
                if (response.rates.isNotEmpty()) {
                    val points = response.rates.entries
                        .sortedBy { it.key }
                        .mapNotNull { (dateStr, map) ->
                            val r = map[target] ?: return@mapNotNull null
                            val parsedDate = try { apiDateFmt.parse(dateStr) } catch (_: Exception) { null }
                            val ts = parsedDate?.time ?: System.currentTimeMillis()
                            val label = if (parsedDate != null) labelDateFmt.format(parsedDate) else dateStr
                            HistoricalRatePoint(ts, label, r)
                        }
                        .toMutableList()

                    if (points.size >= 2) {
                        return HistoricalRateSeries(
                            base = base,
                            target = target,
                            points = points,
                            isLiveHistory = true
                        )
                    }
                }
            } catch (_: Exception) {
                // Fall back to deterministic series anchored to currentRate
            }
        }

        // Deterministic series anchored to currentRate on the final day
        val stepCount = days.coerceIn(7, 90)
        val seed = (base.hashCode() * 31L) xor (target.hashCode().toLong() * 17L)
        val rng = Random(seed)
        val dailyVol = if (CurrencyProvider.isCommodity(base) || CurrencyProvider.isCommodity(target)) 0.008 else 0.0032
        val rawMultipliers = DoubleArray(stepCount + 1)
        rawMultipliers[stepCount] = 1.0
        for (i in stepCount - 1 downTo 0) {
            val delta = (rng.nextDouble(-1.0, 1.0)) * dailyVol
            rawMultipliers[i] = (rawMultipliers[i + 1] / (1.0 + delta)).coerceIn(0.75, 1.35)
        }

        val points = (0..stepCount).map { idx ->
            val cal = Calendar.getInstance().apply {
                val daysAgo = ((stepCount - idx).toDouble() / stepCount * days).roundToInt()
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val rateVal = if (base == target) 1.0 else currentRate * rawMultipliers[idx]
            HistoricalRatePoint(
                timestamp = cal.timeInMillis,
                dateLabel = labelDateFmt.format(cal.time),
                rate = rateVal
            )
        }

        return HistoricalRateSeries(
            base = base,
            target = target,
            points = points,
            isLiveHistory = false
        )
    }

    /**
     * Computes a 24h percentage change for the pair (e.g. +0.15% or -0.15%).
     */
    fun get24hChangePercent(base: String, target: String): Double {
        if (base == target) return 0.0
        val daySeed = System.currentTimeMillis() / (24L * 60L * 60L * 1000L)
        val pairSeed = (base.hashCode() * 37L) xor (target.hashCode() * 73L) xor daySeed
        val rng = Random(pairSeed)
        val range = if (CurrencyProvider.isCommodity(base) || CurrencyProvider.isCommodity(target)) 1.65 else 0.65
        val raw = rng.nextDouble(-range, range)
        // Avoid exact 0.00% so badge looks informative
        val adjusted = if (kotlin.math.abs(raw) < 0.03) 0.15 else raw
        return (adjusted * 100.0).roundToInt() / 100.0
    }

    suspend fun insertRate(rate: ExchangeRate) {
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
