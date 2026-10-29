package com.spendingtracker.app.ui.screens.loans

import com.spendingtracker.app.data.local.entity.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Cross-currency repayments.
 *
 * Regression coverage for "Lack of conversion by loans": a payment may be typed
 * in the loan's currency or in the balance's currency, and a missing rate must
 * refuse to confirm instead of quietly converting at 1:1.
 */
class SettlementConversionTest {

    private val ilsMain = Account(id = 1, name = "Everyday", currency = "ILS", startingBalance = "1000")
    private val eurCash = Account(id = 2, name = "Euro wallet", currency = "EUR", startingBalance = "100", type = "CASH", parentAccountId = 1)
    private val usdMain = Account(id = 5, name = "Travel", currency = "USD", startingBalance = "300")

    private fun form(
        amount: String,
        remaining: String = "400",
        isDebt: Boolean = true,
        loanCurrency: String = "ILS",
        account: Account? = null,
        balance: String? = null,
        rate: String = "0.25",
        typedInAccountCurrency: Boolean = false
    ) = settlementFormState(
        amountText = amount,
        remaining = BigDecimal(remaining),
        isDebt = isDebt,
        loanCurrency = loanCurrency,
        account = account,
        accountBalance = balance?.let { BigDecimal(it) },
        rate = BigDecimal(rate),
        typedInAccountCurrency = typedInAccountCurrency
    )

    @Test
    fun typingInTheLoanCurrencyConvertsTheAccountSide() {
        val state = form("400", account = eurCash, balance = "200", rate = "0.25")
        assertEquals(0, BigDecimal("400").compareTo(state.amount))
        assertEquals(0, BigDecimal("100").compareTo(state.accountAmount))
        assertTrue(state.canConfirm)
        assertTrue("the full remainder settles the loan", state.settlesFully)
    }

    @Test
    fun typingInTheAccountCurrencyDerivesTheLoanSide() {
        // €100 typed at 1 ILS = 0.25 EUR is ₪400 of the loan.
        val state = form("100", account = eurCash, balance = "150", rate = "0.25", typedInAccountCurrency = true)
        assertEquals(0, BigDecimal("400").compareTo(state.amount))
        assertEquals(0, BigDecimal("100").compareTo(state.accountAmount))
        assertTrue(state.canConfirm)
        assertTrue(state.settlesFully)
        assertTrue(state.typedInAccountCurrency)
    }

    @Test
    fun partPaymentsWorkFromEitherSide() {
        val fromLoanSide = form("200", account = eurCash, balance = "200", rate = "0.25")
        assertFalse(fromLoanSide.settlesFully)
        assertEquals(0, BigDecimal("50").compareTo(fromLoanSide.accountAmount))

        val fromAccountSide = form("25", account = eurCash, balance = "200", rate = "0.25", typedInAccountCurrency = true)
        assertFalse(fromAccountSide.settlesFully)
        assertEquals(0, BigDecimal("100").compareTo(fromAccountSide.amount))
    }

    @Test
    fun aLoanCannotBeOverpaidFromEitherSide() {
        assertFalse(form("401", account = eurCash, balance = "1000", rate = "0.25").canConfirm)
        // €101 is ₪404 - more than the ₪400 still owed.
        val overFromAccount = form("101", account = eurCash, balance = "1000", rate = "0.25", typedInAccountCurrency = true)
        assertFalse(overFromAccount.canConfirm)
        assertTrue(overFromAccount.error!!.contains("More than the remaining"))
    }

    @Test
    fun aMissingRateRefusesToConvertInsteadOfAssumingOneToOne() {
        val missing = form("400", account = eurCash, balance = "500", rate = "0")
        assertFalse("no rate must block the payment", missing.canConfirm)
        assertNull(missing.amount)
        assertTrue(missing.error!!.contains("No exchange rate"))
        // Same-currency payments never need a rate.
        assertTrue(form("400", account = ilsMain, balance = "1000", rate = "0").canConfirm)
    }

    @Test
    fun theBalanceLimitUsesTheConvertedAmount() {
        assertFalse(form("400", account = eurCash, balance = "99", rate = "0.25").canConfirm)
        assertTrue(form("400", account = eurCash, balance = "100", rate = "0.25").canConfirm)
        // Receiving a repayment into a balance has no such limit.
        assertTrue(form("400", isDebt = false, account = eurCash, balance = "0", rate = "0.25").canConfirm)
    }

    @Test
    fun anotherLedgersBalanceSettlesTheSameWay() {
        // Paying from a different main account (Travel, USD) is allowed and converts.
        val state = form("400", account = usdMain, balance = "500", rate = "0.27")
        assertTrue(state.canConfirm)
        assertEquals(0, BigDecimal("108.00").compareTo(state.accountAmount))
    }

    @Test
    fun amountFieldNamesTheCurrencyItIsTypedIn() {
        assertEquals("ILS", settlementAmountCurrency("ILS", null, false))
        assertEquals("ILS", settlementAmountCurrency("ILS", eurCash, false))
        assertEquals("EUR", settlementAmountCurrency("ILS", eurCash, true))
        // "typed in account currency" is meaningless without an account.
        assertEquals("ILS", settlementAmountCurrency("ILS", null, true))
    }

    @Test
    fun confirmVerbFollowsTheSameStateThatEnablesTheButton() {
        val part = form("100", account = eurCash, balance = "500", rate = "0.25")
        assertEquals("Record payment", settlementConfirmLabel(true, part))
        val full = form("400", account = eurCash, balance = "500", rate = "0.25")
        assertEquals("Settle debt", settlementConfirmLabel(true, full))
    }
}
