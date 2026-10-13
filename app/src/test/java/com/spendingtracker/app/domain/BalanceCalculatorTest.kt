package com.spendingtracker.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()

    @Test
    fun testBalanceCalculation() {
        val starting = BigDecimal("1000")
        val topUps = listOf(BigDecimal("200"), BigDecimal("300"))
        val expenses = listOf(BigDecimal("150"), BigDecimal("50"))
        val result = calculator.calculate(starting, topUps, expenses)
        assertEquals(BigDecimal("1300"), result.balance) // 1000+500-200=1300
        assertEquals(BigDecimal("500"), result.totalTopUps)
        assertEquals(BigDecimal("200"), result.totalExpenses)
    }

    @Test
    fun testNegativeBalanceAllowed() {
        val starting = BigDecimal("100")
        val topUps = emptyList<BigDecimal>()
        val expenses = listOf(BigDecimal("200"))
        val result = calculator.calculate(starting, topUps, expenses)
        assertEquals(BigDecimal("-100"), result.balance)
        assertTrue(calculator.isNegative(result.balance))
    }

    @Test
    fun testEditExpenseRecalculates() {
        val starting = BigDecimal("1000")
        val topUps = listOf(BigDecimal("0"))
        val expensesOriginal = listOf(BigDecimal("100"), BigDecimal("200"))
        val resultOriginal = calculator.calculate(starting, topUps, expensesOriginal)
        assertEquals(BigDecimal("700"), resultOriginal.balance)

        // Edit second expense from 200 to 50
        val expensesEdited = listOf(BigDecimal("100"), BigDecimal("50"))
        val resultEdited = calculator.calculate(starting, topUps, expensesEdited)
        assertEquals(BigDecimal("850"), resultEdited.balance)
    }

    @Test
    fun testDeleteTopUp() {
        val starting = BigDecimal("500")
        val topUps = listOf(BigDecimal("200"), BigDecimal("100"))
        val expenses = listOf(BigDecimal("50"))
        val before = calculator.calculate(starting, topUps, expenses)
        assertEquals(BigDecimal("750"), before.balance)

        // Delete 200 topup
        val afterTopUps = listOf(BigDecimal("100"))
        val after = calculator.calculate(starting, afterTopUps, expenses)
        assertEquals(BigDecimal("550"), after.balance)
    }

    @Test
    fun testLowBalanceWarning() {
        val balance = BigDecimal("50")
        val threshold = BigDecimal("100")
        assertTrue(calculator.isLowBalance(balance, threshold))
        assertFalse(calculator.isLowBalance(BigDecimal("150"), threshold))
        assertFalse(calculator.isLowBalance(balance, null))
    }
}
