package com.spendingtracker.app.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule the settle dialog enforces must hold for every path that records a
 * repayment. Both view models used to parse the rate themselves and fall back to
 * `BigDecimal.ONE`, which silently recorded a cross-currency payment 1:1 - the
 * exact behaviour the dialog refuses to allow.
 */
class SettlementRateTest {

    @Test
    fun noRateIsNeededWhenNothingConverts() {
        // External settlement: no balance, so the loan's currency is the only one.
        assertFalse(SettlementRate.required("ILS", null))
        assertEquals(BigDecimal.ONE, SettlementRate.resolve("ILS", null, null))
        assertEquals(BigDecimal.ONE, SettlementRate.resolve("ILS", null, "garbage"))

        // Same currency balance.
        assertFalse(SettlementRate.required("ILS", "ILS"))
        assertEquals(BigDecimal.ONE, SettlementRate.resolve("ILS", "ILS", null))
        // Currency codes arrive from the database and from settings; case must not
        // turn a same-currency payment into a "missing rate".
        assertEquals(BigDecimal.ONE, SettlementRate.resolve("ils", "ILS", null))
    }

    @Test
    fun aRealRateIsUsedAsGiven() {
        assertTrue(SettlementRate.required("ILS", "EUR"))
        assertEquals(BigDecimal("0.25"), SettlementRate.resolve("ILS", "EUR", "0.25"))
        assertEquals(BigDecimal("3.9"), SettlementRate.resolve("ILS", "EUR", " 3.9 "))
    }

    @Test
    fun aMissingOrNonsenseRateIsRefusedInsteadOfBecomingOne() {
        assertNull(SettlementRate.resolve("ILS", "EUR", null))
        assertNull(SettlementRate.resolve("ILS", "EUR", ""))
        assertNull(SettlementRate.resolve("ILS", "EUR", "   "))
        assertNull("zero is not a rate", SettlementRate.resolve("ILS", "EUR", "0"))
        assertNull("a negative rate would invert the payment", SettlementRate.resolve("ILS", "EUR", "-1"))
        assertNull(SettlementRate.resolve("ILS", "EUR", "not a number"))
    }

    @Test
    fun theRefusalNamesBothCurrencies() {
        val message = SettlementRate.missingRateMessage("ILS", "EUR")
        assertTrue(message, message.contains("ILS"))
        assertTrue(message, message.contains("EUR"))
        assertTrue("it must say where to fix it", message.contains("Refresh rates"))
    }
}
