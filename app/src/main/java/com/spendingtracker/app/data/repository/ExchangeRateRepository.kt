package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.ExchangeRateDao
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.remote.ExchangeRateApi
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

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
            val response = api.getLatestRates(baseCurrency)
            if (response.result != "success") {
                return Result.failure(Exception("API returned ${response.result}"))
            }
            val timestamp = System.currentTimeMillis()
            val rates = response.rates.map { (target, rateDouble) ->
                ExchangeRate(
                    baseCurrency = baseCurrency,
                    targetCurrency = target,
                    rate = BigDecimal.valueOf(rateDouble).toPlainString(),
                    timestamp = timestamp
                )
            }
            // For conversion from any currency to base, we need inverse?
            // API gives base -> target rate. So if base is USD, target EUR rate 0.9 means 1 USD = 0.9 EUR.
            // To convert EUR expense to USD, we need 1 EUR = 1/0.9 USD.
            // We'll store both directions for convenience, but we can compute inverse when needed.
            // Here we store as API gives, and repository will handle inversion.
            exchangeRateDao.deleteByBase(baseCurrency)
            exchangeRateDao.insertRates(rates)
            Result.success(timestamp)
        } catch (e: Exception) {
            Result.failure(e)
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
                return BigDecimal.ONE.divide(it.third, 10, java.math.RoundingMode.HALF_EVEN)
            }
            return null
        }

        leg(fromCurrency, toCurrency)?.let { return it }

        val currencies = table.flatMap { listOf(it.first, it.second) }.distinct()
        for (pivot in currencies) {
            if (pivot == fromCurrency || pivot == toCurrency) continue
            val first = leg(fromCurrency, pivot) ?: continue
            val second = leg(pivot, toCurrency) ?: continue
            return first.multiply(second).setScale(10, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros()
        }
        return null
    }

    suspend fun insertRate(rate: ExchangeRate) {
        exchangeRateDao.insertRate(rate)
    }
}
