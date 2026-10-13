package com.spendingtracker.app.ui.screens.home

import com.spendingtracker.app.domain.BalanceCalculator
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class HomeViewModelTest {

    @Test
    fun testBalanceCalculatorIntegration() {
        val calculator = BalanceCalculator()
        val starting = BigDecimal("1000")
        val topUps = listOf(BigDecimal("200"))
        val expenses = listOf(BigDecimal("100"))
        val result = calculator.calculate(starting, topUps, expenses)
        assertEquals(BigDecimal("1100"), result.balance)
    }
}
