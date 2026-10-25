package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.ui.navigation.Screen
import com.spendingtracker.app.ui.screens.loans.LoanTypeChooserDialog
import com.spendingtracker.app.ui.screens.loans.SettleLoanDialogBody
import com.spendingtracker.app.ui.screens.loans.settlementConfirmLabel
import com.spendingtracker.app.ui.screens.loans.settlementFormState
import com.spendingtracker.app.ui.screens.loans.externalSourceLabel
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

private fun assertMoney(expected: String, actual: BigDecimal?) =
    assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(requireNotNull(actual)))

private val main = Account(id = 1, name = "Main", currency = "ILS", startingBalance = "1000")
private val cash = Account(id = 2, name = "Pocket", currency = "ILS", startingBalance = "100", type = "CASH", parentAccountId = 1)
private val aside = Account(id = 3, name = "Savings", currency = "ILS", startingBalance = "10", type = "CASH", parentAccountId = 1, includedInTotal = false)
private val eurCash = Account(id = 4, name = "Euro wallet", currency = "EUR", startingBalance = "100", type = "CASH", parentAccountId = 1)

/** Validation of the cash-to-cash transfer form, without a Compose window. */
class TransferFormStateTest {

    @Test fun validTransferCanBeConfirmed() {
        val form = transferFormState("40", BigDecimal("100"), BigDecimal.ONE, typedInTargetCurrency = false, hasPair = true)
        assertMoney("40", form.amountFrom)
        assertMoney("40", form.amountTo)
        assertNull(form.error)
        assertTrue(form.canConfirm)
    }

    @Test fun sourceBalanceIsTheLimit() {
        val form = transferFormState("101", BigDecimal("100"), BigDecimal.ONE, false, true)
        assertEquals("More than the source balance holds", form.error)
        assertFalse(form.canConfirm)
    }

    @Test fun noPairNoConfirm() {
        assertFalse(transferFormState("40", BigDecimal("100"), BigDecimal.ONE, false, hasPair = false).canConfirm)
        assertFalse(transferFormState("", BigDecimal("100"), BigDecimal.ONE, false, true).canConfirm)
        assertFalse(transferFormState("0", BigDecimal("100"), BigDecimal.ONE, false, true).canConfirm)
        assertFalse(transferFormState("-5", BigDecimal("100"), BigDecimal.ONE, false, true).canConfirm)
        assertEquals("Enter a positive amount", transferFormState("abc", BigDecimal("100"), BigDecimal.ONE, false, true).error)
    }

    @Test fun typedTargetSideDerivesTheSourceAndChecksIt() {
        // €25 arriving at 1 ILS = 0.25 EUR means ₪100 leaves the source.
        val ok = transferFormState("25", BigDecimal("100"), BigDecimal("0.25"), typedInTargetCurrency = true, hasPair = true)
        assertMoney("100", ok.amountFrom)
        assertMoney("25", ok.amountTo)
        assertTrue(ok.canConfirm)
        // €26 would need ₪104 the source does not have.
        val tooMuch = transferFormState("26", BigDecimal("100"), BigDecimal("0.25"), true, true)
        assertEquals("More than the source balance holds", tooMuch.error)
    }

    @Test fun totalEffectTextNamesTheSetAsideBalance() {
        assertNull(transferTotalEffectText(main, cash))
        val intoAside = transferTotalEffectText(cash, aside)!!
        assertTrue(intoAside.contains("Savings"))
        assertTrue(intoAside.contains("total falls"))
        val outOfAside = transferTotalEffectText(aside, cash)!!
        assertTrue(outOfAside.contains("raises total available"))
        val bothAside = transferTotalEffectText(aside, aside.copy(id = 9, name = "Other savings"))!!
        assertTrue(bothAside.contains("does not change"))
    }

    @Test fun labelsCarryBalanceAndSetAsideState() {
        assertTrue(transferAccountLabel(cash, BigDecimal("100")).startsWith("Pocket (ILS)"))
        assertFalse(transferAccountLabel(cash, BigDecimal("100")).contains("set aside"))
        assertTrue(transferAccountLabel(aside, BigDecimal("10")).contains("set aside"))
    }
}

/** Settlement form: external default, balance limits, conversion, part payments. */
class SettlementFormStateTest {

    private fun form(
        amount: String,
        remaining: String = "80",
        isDebt: Boolean = true,
        account: Account? = null,
        balance: String? = null,
        rate: String = "1"
    ) = settlementFormState(
        amountText = amount,
        remaining = BigDecimal(remaining),
        isDebt = isDebt,
        loanCurrency = "ILS",
        account = account,
        accountBalance = balance?.let { BigDecimal(it) },
        rate = BigDecimal(rate)
    )

    @Test fun externalRevenueIsTheDefaultAndNeedsNoBalance() {
        assertEquals("External revenue (default)", externalSourceLabel(true))
        assertEquals("External (default)", externalSourceLabel(false))
        val state = form("80")
        assertTrue(state.canConfirm)
        assertTrue(state.settlesFully)
        assertNull(state.accountAmount)
        assertEquals("Settle debt", settlementConfirmLabel(true, state))
        assertEquals("Settle loan", settlementConfirmLabel(false, state))
    }

    @Test fun payingADebtFromCashIsLimitedByWhatTheCashHolds() {
        assertTrue(form("80", account = cash, balance = "100").canConfirm)
        val short = form("80", account = cash, balance = "50")
        assertFalse(short.canConfirm)
        assertNotNull(short.error)
        // Part-pay up to the balance instead.
        val part = form("50", account = cash, balance = "50")
        assertTrue(part.canConfirm)
        assertFalse(part.settlesFully)
        assertEquals("Record payment", settlementConfirmLabel(true, part))
    }

    @Test fun receivingIntoCashHasNoBalanceLimit() {
        val state = form("80", isDebt = false, account = cash, balance = "0")
        assertTrue(state.canConfirm)
        assertMoney("80", state.accountAmount)
    }

    @Test fun overpayingTheLoanIsRejected() {
        assertFalse(form("81").canConfirm)
        assertFalse(form("81", account = cash, balance = "1000").canConfirm)
    }

    @Test fun foreignCashSettlementConvertsAndChecksTheConvertedSide() {
        // ₪80 of debt at 1 ILS = 0.25 EUR costs €20.
        val ok = form("80", account = eurCash, balance = "20", rate = "0.25")
        assertMoney("20", ok.accountAmount)
        assertTrue(ok.canConfirm)
        assertFalse(form("80", account = eurCash, balance = "19.99", rate = "0.25").canConfirm)
    }

    @Test fun alreadyRepaidLoansOnlyNeedArchiving() {
        val state = form("", remaining = "0")
        assertTrue(state.alreadyRepaid)
        assertTrue(state.canConfirm)
        assertEquals("Move to archive", settlementConfirmLabel(true, state))
    }

    @Test fun invalidAmountsCannotConfirm() {
        assertFalse(form("").canConfirm)
        assertFalse(form("0").canConfirm)
        assertFalse(form("-3").canConfirm)
        assertFalse(form("abc").canConfirm)
    }
}

/** The loan type is chosen before the form opens and travels in the route. */
class LoanTypeRouteTest {
    @Test fun newLoanRoutesCarryTheChosenType() {
        assertEquals("add_loan?loanType=LOAN", Screen.AddLoan.createRoute(loanType = LoanDebt.TYPE_LOAN))
        assertEquals("add_loan?loanType=DEBT", Screen.AddLoan.createRoute(loanType = LoanDebt.TYPE_DEBT))
        assertEquals("add_loan?loanId=7", Screen.AddLoan.createRoute(7))
        assertEquals("add_loan", Screen.AddLoan.createRoute())
    }

    @Test fun deleteMessageExplainsReopenAndMoneyReversal() {
        val loan = LoanDebt(
            id = 1, type = LoanDebt.TYPE_DEBT, personName = "Dana", amountOriginal = "80",
            currencyOriginal = "ILS", date = 0, dueDate = null, note = null,
            isSettled = true, settlementRepaymentId = 5
        )
        val settlement = Repayment(
            id = 5, loanDebtId = 1, amount = "80", date = 0, note = null,
            accountId = 2, accountAmount = "80", accountCurrency = "ILS", exchangeRate = "1"
        )
        val message = loanDeleteMessage(settlement, loan, settlesLoan = true)
        assertTrue(message.contains("reopens"))
        assertTrue(message.contains("returns to the account"))
        val external = loanDeleteMessage(settlement.copy(accountId = null, accountAmount = null), loan, settlesLoan = false)
        assertTrue(external.contains("No balance was involved"))
    }
}

/** The transfer dialog body: source, destination, previews, set-aside effect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class TransferDialogBodyTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cashToSetAsideCashExplainsTheTotalEffect() {
        val balances = mapOf(2L to BigDecimal("100"), 3L to BigDecimal("10"))
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                TransferDialogBody(
                    from = cash,
                    to = aside,
                    balances = balances,
                    amount = "40",
                    note = "",
                    form = transferFormState("40", BigDecimal("100"), BigDecimal.ONE, false, true),
                    rate = BigDecimal.ONE,
                    rateReady = true
                )
            }
        }
        compose.onNodeWithText("Source balance").assertIsDisplayed()
        compose.onNodeWithText("Destination balance").assertIsDisplayed()
        compose.onNodeWithText("Pocket (ILS)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Savings (ILS)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("total falls", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Swap", substring = true).assertIsDisplayed()
    }

    @Test
    fun overBalanceAmountIsShownAsAnError() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                TransferDialogBody(
                    from = cash,
                    to = eurCash,
                    balances = mapOf(2L to BigDecimal("100"), 4L to BigDecimal("0")),
                    amount = "500",
                    note = "",
                    form = transferFormState("500", BigDecimal("100"), BigDecimal("0.25"), false, true),
                    rate = BigDecimal("0.25"),
                    rateReady = true
                )
            }
        }
        compose.onNodeWithText("More than the source balance holds").assertIsDisplayed()
    }
}

/** The settle dialog defaults to external revenue in its dropdown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class SettleLoanDialogBodyTest {

    @get:Rule
    val compose = createComposeRule()

    private val debt = LoanDebt(
        id = 1, type = LoanDebt.TYPE_DEBT, personName = "Dana", amountOriginal = "100",
        currencyOriginal = "ILS", date = 0, dueDate = null, note = null
    )

    @Test
    fun externalRevenueIsSelectedByDefault() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                SettleLoanDialogBody(
                    loan = debt,
                    remaining = BigDecimal("80"),
                    accounts = listOf(main, cash),
                    balances = mapOf(1L to BigDecimal("1000"), 2L to BigDecimal("100")),
                    amount = "80",
                    note = "",
                    accountId = null,
                    dropdownExpanded = false,
                    form = settlementFormState("80", BigDecimal("80"), true, "ILS", null, null, BigDecimal.ONE),
                    rate = BigDecimal.ONE,
                    rateReady = true
                )
            }
        }
        compose.onNodeWithText("PAY FROM").assertIsDisplayed()
        compose.onNodeWithText("External revenue (default)").assertIsDisplayed()
        compose.onNodeWithText("without touching a balance", substring = true).assertIsDisplayed()
    }

    @Test
    fun typeChooserOffersBothLoanTypes() {
        var chosen: String? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                LoanTypeChooserDialog(onDismiss = {}, onChoose = { chosen = it })
            }
        }
        compose.onNodeWithText("I owe").assertIsDisplayed()
        compose.onNodeWithText("Owed to me").assertIsDisplayed()
        assertNull(chosen)
        // Choosing decides the type before any form opens.
        compose.onNodeWithContentDescription("Owed to me: Money someone will pay me back").performClick()
        assertEquals(LoanDebt.TYPE_LOAN, chosen)
    }
}
