package com.spendingtracker.app.domain

import com.spendingtracker.app.data.local.entity.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Which balances a settlement may use, and how they read.
 *
 * Regression coverage for "Loan Settle into accounts availability failure": the
 * dropdown only offered the active ledger, so a second main account and its cash
 * balances could never settle a loan.
 */
class SettlementAccountsTest {

    private val mainA = Account(id = 1, name = "Everyday", currency = "ILS", startingBalance = "1000", createdAt = 10)
    private val cashA1 = Account(id = 2, name = "Pocket", currency = "ILS", startingBalance = "100", type = "CASH", parentAccountId = 1, createdAt = 11)
    private val cashA2 = Account(id = 3, name = "Savings", currency = "EUR", startingBalance = "10", type = "CASH", parentAccountId = 1, includedInTotal = false, createdAt = 12)
    private val cardA = Account(id = 4, name = "Visa", currency = "ILS", startingBalance = "-250", type = "CREDIT_CARD", parentAccountId = 1, createdAt = 13)
    private val mainB = Account(id = 5, name = "Travel", currency = "EUR", startingBalance = "200", createdAt = 20)
    private val cashB = Account(id = 6, name = "Cash", currency = "EUR", startingBalance = "50", type = "CASH", parentAccountId = 5, createdAt = 21)

    private val all = listOf(mainA, cashA1, cashA2, cardA, mainB, cashB)

    @Test
    fun everyLedgerIsOfferedNotOnlyTheActiveOne() {
        val options = SettlementAccounts.options(all, activeAccountId = mainA.id)
        assertTrue("the other main account must be offered", options.contains(mainB))
        assertTrue("its cash balance must be offered", options.contains(cashB))
        assertEquals(5, options.size)
    }

    @Test
    fun creditCardsAreNeverASettlementTarget() {
        val options = SettlementAccounts.options(all, activeAccountId = mainA.id)
        assertFalse(options.contains(cardA))
        assertTrue(options.none { it.type == "CREDIT_CARD" })
    }

    @Test
    fun activeLedgerComesFirstAndTheOrderIsStable() {
        val options = SettlementAccounts.options(all, activeAccountId = mainA.id)
        assertEquals(
            listOf(mainA.id, cashA1.id, cashA2.id, mainB.id, cashB.id),
            options.map { it.id }
        )
        // Same input, same output - the dropdown must not shuffle between renders.
        assertEquals(options.map { it.id }, SettlementAccounts.options(all.shuffled(), mainA.id).map { it.id })
    }

    @Test
    fun withNoActiveAccountEveryMainAndCashBalanceIsStillOffered() {
        val options = SettlementAccounts.options(all, activeAccountId = null)
        assertEquals(5, options.size)
        assertFalse(options.contains(cardA))
    }

    @Test
    fun foreignLedgerCashNamesItsParentSoTwoCashRowsStayDistinct() {
        val label = SettlementAccounts.label(cashB, all, BigDecimal("50"), activeAccountId = mainA.id)
        assertTrue("must say which ledger it belongs to: $label", label.startsWith("Travel \u00b7 "))
        assertTrue(label.contains("EUR"))
        assertTrue(label.contains("50.00"))
    }

    @Test
    fun ownCashKeepsTheShortNameAndSetAsideState() {
        val own = SettlementAccounts.label(cashA1, all, BigDecimal("100"), activeAccountId = mainA.id)
        assertEquals("Pocket (ILS) \u00b7 \u20aa100.00", own)
        val aside = SettlementAccounts.label(cashA2, all, BigDecimal("10"), activeAccountId = mainA.id)
        assertTrue(aside.contains("set aside"))
        assertFalse(aside.startsWith("Everyday"))
        val main = SettlementAccounts.label(mainB, all, BigDecimal("200"), activeAccountId = mainA.id)
        assertEquals("Travel (EUR) \u00b7 \u20ac200.00", main)
    }
}
