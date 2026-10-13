package com.spendingtracker.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CurrencyConverterTest {

    private val converter = CurrencyConverter()

    @Test
    fun testSameCurrencyReturnsSameAmount() {
        val amount = BigDecimal("100.00")
        val result = converter.convert(amount, "USD", "USD", BigDecimal.ONE)
        assertEquals(amount, result)
    }

    @Test
    fun testConversionWithRate() {
        val amount = BigDecimal("20.00")
        val rate = BigDecimal("4.02") // e.g. 1 EUR = 4.02 ILS
        val result = converter.convert(amount, "EUR", "ILS", rate)
        assertEquals(BigDecimal("80.40"), result.setScale(2, java.math.RoundingMode.HALF_EVEN))
    }

    @Test
    fun testInverseConversion() {
        val amount = BigDecimal("100")
        val rate = BigDecimal("0.9") // 1 USD = 0.9 EUR
        // To convert EUR to USD, need inverse
        val inverse = BigDecimal.ONE.divide(rate, 10, java.math.RoundingMode.HALF_EVEN)
        assertEquals(BigDecimal("1.1111111111").setScale(10, java.math.RoundingMode.HALF_EVEN), inverse.setScale(10, java.math.RoundingMode.HALF_EVEN))
    }

    @Test
    fun testCalculateRate() {
        val original = BigDecimal("20")
        val converted = BigDecimal("80.40")
        val rate = converter.calculateRate(original, converted)
        assertEquals(BigDecimal("4.02"), rate.setScale(2, java.math.RoundingMode.HALF_EVEN))
    }
}
