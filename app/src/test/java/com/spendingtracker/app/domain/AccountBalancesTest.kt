package com.spendingtracker.app.domain

import com.spendingtracker.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class AccountBalancesTest {
    private val main = Account(id = 1, name = "Main", currency = "ILS", startingBalance = "1000")
    private val cash = Account(id = 2, name = "Cash", currency = "ILS", startingBalance = "100", type = "CASH", parentAccountId = 1)
    private val card = Account(id = 3, name = "Visa", currency = "ILS", startingBalance = "0", type = "CREDIT_CARD", parentAccountId = 1)
    private fun expense(id: Long, target: Long?, amount: String, label: String? = null) = Expense(
        id = id, accountId = 1, balanceAccountId = target, amountOriginal = amount, currencyOriginal = "ILS",
        amountConverted = amount, currencyConverted = "ILS", exchangeRateUsed = "1", categoryId = 1, date = 0, note = "Test", paymentMethod = label)
    private fun assertMoney(expected: String, actual: BigDecimal?) = assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(actual)))
    @Test fun cashDoesNotReduceMain() {
        val result = AccountBalances.calculate(listOf(main, cash, card), listOf(expense(1, 2, "25")), emptyList(), emptyList())
        assertMoney("1000", result[1]); assertMoney("75", result[2]); assertMoney("0", result[3])
    }
    @Test fun creditExpenseIncreasesDebtOnly() {
        val result = AccountBalances.calculate(listOf(main, cash, card), listOf(expense(1, 3, "250")), emptyList(), emptyList())
        assertMoney("1000", result[1]); assertMoney("-250", result[3])
        assertMoney("850", result.values.fold(BigDecimal.ZERO, BigDecimal::add))
    }
    @Test fun paymentMovesFundsWithoutDoubleCountingSpending() {
        val payment = CreditCardPayment(cardId = 3, sourceAccountId = 1, amount = "100", date = 0, status = "COMPLETED")
        val result = AccountBalances.calculate(listOf(main, cash, card), listOf(expense(1, 3, "250")), emptyList(), listOf(payment))
        assertMoney("900", result[1]); assertMoney("-150", result[3]); assertMoney("850", result.values.fold(BigDecimal.ZERO, BigDecimal::add))
    }
    @Test fun failedPaymentDoesNotChangeBalances() {
        val payment = CreditCardPayment(cardId = 3, sourceAccountId = 1, amount = "250", date = 0, status = "FAILED_INSUFFICIENT_FUNDS")
        val result = AccountBalances.calculate(listOf(main, card), emptyList(), emptyList(), listOf(payment))
        assertMoney("1000", result[1]); assertMoney("0", result[3])
    }
    @Test fun legacyPaymentLabelsKeepOriginalEffect() {
        val result = AccountBalances.calculate(listOf(main, cash), listOf(expense(1, null, "20", "Cash")), emptyList(), emptyList())
        assertMoney("980", result[1]); assertMoney("100", result[2])
    }
    @Test fun cashIncomeIsIsolated() {
        val result = AccountBalances.calculate(listOf(main, cash), emptyList(), listOf(TopUp(accountId = 2, amount = "40", date = 0, note = null)), emptyList())
        assertMoney("1000", result[1]); assertMoney("140", result[2])
    }
    @Test fun fixedAutopayNeverOverpays() {
        assertMoney("20", AccountBalances.paymentAmount(card.copy(autopayMode = "FIXED_AMOUNT", autopayFixedAmount = "100"), BigDecimal("-20")))
        assertMoney("0", AccountBalances.paymentAmount(card, BigDecimal("30")))
    }
    @Test fun fullAutopayPaysDebt() { assertMoney("250", AccountBalances.paymentAmount(card, BigDecimal("-250"))) }
    @Test fun shortMonthClampsDueDay() {
        assertEquals("2026-02-28", AccountBalances.dueDate(card.copy(autopayDay = 31), YearMonth.of(2026, 2)).toString())
        assertEquals("2028-02-29", AccountBalances.dueDate(card.copy(autopayDay = 31), YearMonth.of(2028, 2)).toString())
    }
    @Test fun multipleCardsRemainIndependent() {
        val other = card.copy(id = 4, name = "Mastercard")
        val result = AccountBalances.calculate(listOf(main, card, other), listOf(expense(1, 3, "30"), expense(2, 4, "40")), emptyList(), emptyList())
        assertMoney("-30", result[3]); assertMoney("-40", result[4])
    }
    @Test fun withdrawalMovesFundsWithConversionBetweenCurrencies() {
        val eurCash = Account(id = 4, name = "EUR", currency = "EUR", startingBalance = "10", type = "CASH", parentAccountId = 1)
        val withdrawal = Withdrawal(
            fromAccountId = 1, toAccountId = 4, amountFrom = "100", amountTo = "25",
            currencyFrom = "ILS", currencyTo = "EUR", exchangeRate = "0.25", date = 0
        )
        val result = AccountBalances.calculate(listOf(main, eurCash), emptyList(), emptyList(), emptyList(), listOf(withdrawal))
        assertMoney("900", result[1])
        assertMoney("35", result[4])
        // Cash set aside or not, per-account balances are unchanged by the flag.
        assertMoney("35", AccountBalances.calculate(
            listOf(eurCash.copy(includedInTotal = false)), emptyList(), emptyList(), emptyList(), listOf(withdrawal)
        )[4])
    }
    @Test fun deletingOrEditingExpenseRecalculatesFromLedger() {
        val result = AccountBalances.calculate(listOf(main, card), listOf(expense(1, 3, "5")), emptyList(), emptyList())
        assertMoney("-5", result[3])
        assertMoney("0", AccountBalances.calculate(listOf(card), emptyList(), emptyList(), emptyList())[3])
    }
}
