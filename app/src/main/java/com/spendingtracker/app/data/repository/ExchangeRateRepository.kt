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
        // Try direct: from -> to
        // But our cached rates are base -> target where base is balanceCurrency.
        // So if toCurrency is balanceCurrency, we need rate from balance to from, then inverse.
        // Simplified: if we have base=toCurrency and target=fromCurrency, rate = 1 / storedRate
        val direct = exchangeRateDao.getRate(fromCurrency, toCurrency)
        if (direct != null) {
            return try { BigDecimal(direct.rate) } catch (e: Exception) { null }
        }
        val inverse = exchangeRateDao.getRate(toCurrency, fromCurrency)
        if (inverse != null) {
            return try {
                val r = BigDecimal(inverse.rate)
                if (r.compareTo(BigDecimal.ZERO) == 0) null
                else BigDecimal.ONE.divide(r, 10, java.math.RoundingMode.HALF_EVEN)
            } catch (e: Exception) { null }
        }
        return null
    }

    suspend fun insertRate(rate: ExchangeRate) {
        exchangeRateDao.insertRate(rate)
    }
}
