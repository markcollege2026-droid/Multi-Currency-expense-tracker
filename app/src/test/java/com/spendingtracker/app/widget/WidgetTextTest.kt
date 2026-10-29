package com.spendingtracker.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's wording is pure, so it is tested without Android. The rendering
 * itself (RemoteViews compatibility) is covered by the emulator suite.
 */
class WidgetTextTest {

    private fun snapshot(
        balance: String = "1234.5",
        currency: String = "ILS",
        base: String = "ILS",
        target: String = "EUR",
        rate: String? = "0.25",
        updatedAt: Long = 0
    ) = WidgetSnapshot(balance, currency, base, target, rate, updatedAt)

    @Test
    fun balanceLineUsesTheLedgerCurrency() {
        assertEquals("\u20aa1234.50 available", WidgetText.balanceLine(snapshot()))
        assertEquals("\$300.00 available", WidgetText.balanceLine(snapshot(balance = "300", currency = "USD")))
    }

    @Test
    fun rateLineShowsBothCurrencies() {
        assertEquals("1 ILS = 0.25 EUR", WidgetText.rateLine(snapshot()))
        assertEquals("1 USD = 3.7 ILS", WidgetText.rateLine(snapshot(base = "USD", target = "ILS", rate = "3.700")))
    }

    @Test
    fun aMissingRateSaysSoInsteadOfShowingOne() {
        val line = WidgetText.rateLine(snapshot(rate = null))
        assertTrue(line, line.contains("No ILS"))
        assertFalse(line.contains("= 1"))
        assertTrue(WidgetText.rateLine(snapshot(rate = "0")).contains("No ILS"))
        assertTrue(WidgetText.rateLine(snapshot(rate = "not-a-number")).contains("No ILS"))
    }

    @Test
    fun updatedLineIsCoarseAndNeverNegative() {
        val now = 10_000_000L
        assertEquals("Updated just now", WidgetText.updatedLine(snapshot(updatedAt = now), now))
        assertEquals("Updated 5m ago", WidgetText.updatedLine(snapshot(updatedAt = now - 5 * 60_000), now))
        assertEquals("Updated 3h ago", WidgetText.updatedLine(snapshot(updatedAt = now - 3 * 3_600_000), now))
        assertEquals("Updated 2d ago", WidgetText.updatedLine(snapshot(updatedAt = now - 2 * 86_400_000), now))
        // A clock that jumped backwards must not print a negative age.
        assertEquals("Updated just now", WidgetText.updatedLine(snapshot(updatedAt = now + 60_000), now))
    }

    @Test
    fun targetCurrencyPrefersAFavouriteThenAFallback() {
        assertEquals("USD", WidgetText.targetCurrency(listOf("USD", "EUR"), "ILS"))
        assertEquals("EUR", WidgetText.targetCurrency(listOf("ILS", "EUR"), "ILS"))
        // No favourites: never the same currency as the ledger.
        assertEquals("USD", WidgetText.targetCurrency(emptyList(), "ILS"))
        assertEquals("EUR", WidgetText.targetCurrency(emptyList(), "USD"))
        assertEquals("USD", WidgetText.targetCurrency(listOf("  ", "ILS"), "ILS"))
    }
}
