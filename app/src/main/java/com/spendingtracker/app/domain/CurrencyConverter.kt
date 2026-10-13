package com.spendingtracker.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyConverter @Inject constructor() {
    private val mc = MathContext(20, RoundingMode.HALF_EVEN)

    fun convert(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        rate: BigDecimal
    ): BigDecimal {
        if (fromCurrency == toCurrency) return amount
        // rate is defined as: 1 unit of base = rate units of target?
        // For simplicity, we assume rate is conversion factor from original to balance:
        // converted = original * rate
        // If rate is from API where base is balanceCurrency, and target is original,
        // we need to handle accordingly in repository.
        return amount.multiply(rate, mc).setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros()
    }

    fun convertWithInverse(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        rateBaseToTarget: BigDecimal
    ): BigDecimal {
        if (fromCurrency == toCurrency) return amount
        // If rate is base->target, and we want from target->base, we need inverse
        // But our repository will store rate as fromOriginal to balance, so direct multiply
        return amount.multiply(rateBaseToTarget, mc)
    }

    fun calculateRate(
        amountOriginal: BigDecimal,
        amountConverted: BigDecimal
    ): BigDecimal {
        if (amountOriginal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE
        return amountConverted.divide(amountOriginal, mc)
    }
}
