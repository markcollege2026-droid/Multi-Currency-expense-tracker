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
private fun assertMoney(expected: String, actual: java.math.BigDecimal?) =
    assertEquals("expected $expected but was $actual", 0, java.math.BigDecimal(expected).compareTo(requireNotNull(actual)))

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
        typedInAccountCurrency: Boolean = false,
        payCurrency: String? = null
    ) = settlementFormState(
        amountText = amount,
        remaining = BigDecimal(remaining),
        isDebt = isDebt,
        loanCurrency = loanCurrency,
        account = account,
        accountBalance = balance?.let { BigDecimal(it) },
        rate = BigDecimal(rate),
        // With a balance the payment currency is the balance's; without one it is
        // whatever the user picked in "Pay in".
        payCurrency = payCurrency ?: account?.currency ?: loanCurrency,
        typedInPayCurrency = typedInAccountCurrency
    )

    @Test
    fun typingInTheLoanCurrencyConvertsTheAccountSide() {
        val state = form("400", account = eurCash, balance = "200", rate = "0.25")
        assertMoney("400", state.amount)
        assertMoney("100", state.payAmount)
        assertTrue(state.canConfirm)
        assertTrue("the full remainder settles the loan", state.settlesFully)
    }

    @Test
    fun typingInTheAccountCurrencyDerivesTheLoanSide() {
        // €100 typed at 1 ILS = 0.25 EUR is ₪400 of the loan.
        val state = form("100", account = eurCash, balance = "150", rate = "0.25", typedInAccountCurrency = true)
        assertMoney("400", state.amount)
        assertMoney("100", state.payAmount)
        assertTrue(state.canConfirm)
        assertTrue(state.settlesFully)
        assertTrue(state.typedInPayCurrency)
    }

    @Test
    fun partPaymentsWorkFromEitherSide() {
        val fromLoanSide = form("200", account = eurCash, balance = "200", rate = "0.25")
        assertFalse(fromLoanSide.settlesFully)
        assertMoney("50", fromLoanSide.payAmount)

        val fromAccountSide = form("25", account = eurCash, balance = "200", rate = "0.25", typedInAccountCurrency = true)
        assertFalse(fromAccountSide.settlesFully)
        assertMoney("100", fromAccountSide.amount)
    }

    @Test
    fun aLoanCannotBeOverpaidFromEitherSide() {
        assertFalse(form("401", account = eurCash, balance = "1000", rate = "0.25").canConfirm)
        // €101 is ₪404 - more than the ₪400 still owed.
        val overFromAccount = form("101", account = eurCash, balance = "1000", rate = "0.25", typedInAccountCurrency = true)
        assertFalse(overFromAccount.canConfirm)
        assertTrue(overFromAccount.error!!.contains("more than the remaining"))
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
        assertMoney("108.00", state.payAmount)
    }

    @Test
    fun amountFieldNamesTheCurrencyItIsTypedIn() {
        assertEquals("ILS", settlementAmountCurrency("ILS", "ILS", false))
        assertEquals("ILS", settlementAmountCurrency("ILS", "EUR", false))
        assertEquals("EUR", settlementAmountCurrency("ILS", "EUR", true))
        // Typing in the loan's currency is the same field whichever way round.
        assertEquals("ILS", settlementAmountCurrency("ILS", "ILS", true))
    }

    // ---- external payments in another currency (no balance involved) ----

    @Test
    fun anExternalPaymentCanBeMadeInAnotherCurrency() {
        // The reported example: ₪9 owed, 3 ILS = $1, so $3 clears it. Paying $2
        // must apply ₪6 and leave ₪3.
        val owedInUsd = owedIn(BigDecimal("9"), BigDecimal("0.3333333"), "USD", "ILS")
        assertMoney("3.00", owedInUsd)

        val paysTwoDollars = form(
            "2", remaining = "9", account = null, rate = "0.3333333",
            typedInAccountCurrency = true, payCurrency = "USD"
        )
        assertMoney("6.00", paysTwoDollars.amount)
        assertMoney("2", paysTwoDollars.payAmount)
        assertTrue(paysTwoDollars.canConfirm)
        assertFalse("a $2 payment of $3 owed is a part payment", paysTwoDollars.settlesFully)
        assertMoney("3.00", paysTwoDollars.remainingAfter(BigDecimal("9")))
    }

    @Test
    fun theWholeBalanceInAnotherCurrencySettlesTheLoan() {
        val full = form(
            "3", remaining = "9", account = null, rate = "0.3333333",
            typedInAccountCurrency = true, payCurrency = "USD"
        )
        assertMoney("9.00", full.amount)
        assertTrue(full.settlesFully)
        assertEquals("Settle debt", settlementConfirmLabel(true, full))
    }

    @Test
    fun typingInTheLoanCurrencyShowsWhatToHandOver() {
        // ₪6 typed with USD selected: the user hands over $2.
        val state = form("6", remaining = "9", account = null, rate = "0.3333333", payCurrency = "USD")
        assertMoney("6", state.amount)
        assertMoney("2.00", state.payAmount)
        assertFalse(state.typedInPayCurrency)
    }

    @Test
    fun anExternalPaymentCannotExceedTheBalanceInEitherCurrency() {
        // $4 is ₪12 - more than ₪9 owed.
        val over = form(
            "4", remaining = "9", account = null, rate = "0.3333333",
            typedInAccountCurrency = true, payCurrency = "USD"
        )
        assertFalse(over.canConfirm)
        assertTrue(over.error!!.contains("more than the remaining"))
        // The message gives the ceiling in the currency being paid, not only the loan's.
        assertTrue(over.error!!.contains("$3.00"))
    }

    @Test
    fun anExternalPaymentWithoutARateIsRefused() {
        val missing = form(
            "2", remaining = "9", account = null, rate = "0",
            typedInAccountCurrency = true, payCurrency = "USD"
        )
        assertFalse(missing.canConfirm)
        assertNull(missing.amount)
        assertTrue(missing.error!!.contains("No exchange rate"))
        assertNull("no rate means no equivalent to show", owedIn(BigDecimal("9"), BigDecimal.ZERO, "USD", "ILS"))
        // Paying in the loan's own currency never needs a rate.
        assertTrue(form("2", remaining = "9", account = null, rate = "0").canConfirm)
    }

    @Test
    fun theLoanCurrencyStaysTheBasisOfTheBalance() {
        // Whatever currency is paid in, the reduction and the remainder are in the
        // loan's currency - the balance is never re-denominated.
        val paid = form(
            "2", remaining = "9", account = null, rate = "0.3333333",
            typedInAccountCurrency = true, payCurrency = "USD"
        )
        assertEquals("USD", paid.payCurrency)
        assertMoney("6.00", paid.amount)
        assertMoney("3.00", paid.remainingAfter(BigDecimal("9")))
        // And the equivalent owed is only meaningful while the currencies differ.
        assertNull(owedIn(BigDecimal("9"), BigDecimal("0.3333333"), "ILS", "ILS"))
    }

    @Test
    fun confirmVerbFollowsTheSameStateThatEnablesTheButton() {
        val part = form("100", account = eurCash, balance = "500", rate = "0.25")
        assertEquals("Record payment", settlementConfirmLabel(true, part))
        val full = form("400", account = eurCash, balance = "500", rate = "0.25")
        assertEquals("Settle debt", settlementConfirmLabel(true, full))
    }
}
