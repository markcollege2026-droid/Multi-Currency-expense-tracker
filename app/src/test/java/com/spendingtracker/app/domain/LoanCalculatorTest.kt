package com.spendingtracker.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LoanCalculatorTest {

    private val calculator = LoanCalculator()

    @Test
    fun testRemainingCalculation() {
        val original = BigDecimal("1000")
        val repayments = listOf(BigDecimal("200"), BigDecimal("300"))
        val result = calculator.calculateRemaining(original, repayments)
        assertEquals(BigDecimal("500"), result.remaining)
        assertEquals(BigDecimal("500"), result.totalRepaid)
        assertEquals(0.5f, result.progress, 0.01f)
    }

    @Test
    fun testFullyRepaid() {
        val original = BigDecimal("500")
        val repayments = listOf(BigDecimal("500"))
        val result = calculator.calculateRemaining(original, repayments)
        assertEquals(BigDecimal.ZERO, result.remaining)
        assertTrue(calculator.isSettled(result.remaining))
    }

    @Test
    fun testPartialRepaymentProgress() {
        val original = BigDecimal("1000")
        val repayments = listOf(BigDecimal("250"))
        val result = calculator.calculateRemaining(original, repayments)
        assertEquals(0.25f, result.progress, 0.01f)
    }

    @Test
    fun testNoRepayments() {
        val original = BigDecimal("1000")
        val result = calculator.calculateRemaining(original, emptyList())
        assertEquals(BigDecimal("1000"), result.remaining)
        assertEquals(0f, result.progress, 0.01f)
    }
}
