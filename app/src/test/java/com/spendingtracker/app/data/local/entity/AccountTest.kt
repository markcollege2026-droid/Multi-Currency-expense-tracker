package com.spendingtracker.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AccountTest {

    @Test
    fun startingBalanceParsesValidInput() {
        val account = Account(name = "Main", currency = "USD", startingBalance = "123.45")
        assertEquals(BigDecimal("123.45"), account.startingBalanceAsBigDecimal())
    }

    @Test
    fun startingBalanceFallsBackToZeroOnGarbage() {
        val account = Account(name = "Main", currency = "USD", startingBalance = "oops")
        assertEquals(BigDecimal.ZERO, account.startingBalanceAsBigDecimal())
    }

    @Test
    fun thresholdIsNullWhenUnset() {
        val account = Account(name = "Main", currency = "USD", startingBalance = "0")
        assertNull(account.thresholdAsBigDecimal())
    }

    @Test
    fun thresholdIsNullOnGarbage() {
        val account = Account(
            name = "Main",
            currency = "USD",
            startingBalance = "0",
            lowBalanceThreshold = "oops"
        )
        assertNull(account.thresholdAsBigDecimal())
    }

    @Test
    fun thresholdParsesValidInput() {
        val account = Account(
            name = "Main",
            currency = "USD",
            startingBalance = "0",
            lowBalanceThreshold = "10"
        )
        assertEquals(BigDecimal("10"), account.thresholdAsBigDecimal())
    }
}
