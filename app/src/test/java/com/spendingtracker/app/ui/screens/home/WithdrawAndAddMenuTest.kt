package com.spendingtracker.app.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Guards the feature pass that replaced the two "Expense"/"Income" buttons with a
 * single pop-out Add menu and added withdrawals from main into cash balances.
 *
 * These are reachability tests, not styling tests: the reported defect was a
 * dialog that only offered Cancel, so every assertion checks that an action
 * exists and that using it does something.
 */

/** Validation rules of the withdraw form, with no Compose window involved. */
class WithdrawFormTest {

    @Test
    fun validAmountCanBeConfirmed() {
        val form = withdrawFormState("100", BigDecimal("1000"), BigDecimal.ONE, inCashCurrency = false, hasTarget = true)
        assertEquals(0, BigDecimal("100").compareTo(requireNotNull(form.typed)))
        assertEquals(null, form.error)
        assertTrue(form.canConfirm)
    }

    @Test
    fun amountAboveTheMainBalanceIsRejected() {
        val form = withdrawFormState("100", BigDecimal("50"), BigDecimal.ONE, inCashCurrency = false, hasTarget = true)
        assertEquals("More than the main balance holds", form.error)
        assertFalse(form.canConfirm)
    }

    @Test
    fun blankZeroAndGarbageAmountsCannotBeConfirmed() {
        assertFalse(withdrawFormState("", BigDecimal("1000"), BigDecimal.ONE, false, true).canConfirm)
        // A blank field is not an error yet - it is just incomplete.
        assertEquals(null, withdrawFormState("", BigDecimal("1000"), BigDecimal.ONE, false, true).error)
        assertFalse(withdrawFormState("0", BigDecimal("1000"), BigDecimal.ONE, false, true).canConfirm)
        assertFalse(withdrawFormState("-5", BigDecimal("1000"), BigDecimal.ONE, false, true).canConfirm)
        assertEquals(
            "Enter a positive amount",
            withdrawFormState("abc", BigDecimal("1000"), BigDecimal.ONE, false, true).error
        )
        // Comma decimal separators are accepted (hardware keypad layouts).
        assertEquals(
            0,
            BigDecimal("12.50").compareTo(
                requireNotNull(withdrawFormState("12,50", BigDecimal("1000"), BigDecimal.ONE, false, true).typed)
            )
        )
    }

    @Test
    fun noDestinationMeansNothingCanBeConfirmed() {
        assertFalse(withdrawFormState("100", BigDecimal("1000"), BigDecimal.ONE, false, hasTarget = false).canConfirm)
    }

    @Test
    fun amountCanBeTypedInTheCashCurrency() {
        // 25 EUR at 0.25 EUR per ILS takes 100 ILS out of main.
        val form = withdrawFormState("25", BigDecimal("1000"), BigDecimal("0.25"), inCashCurrency = true, hasTarget = true)
        assertEquals(0, BigDecimal("100").compareTo(requireNotNull(form.amountFrom)))
        assertEquals(0, BigDecimal("25").compareTo(requireNotNull(form.amountTo)))
        assertEquals(null, form.error)
        assertTrue(form.canConfirm)
    }

    @Test
    fun cashCurrencyAmountIsStillLimitedByTheMainBalance() {
        // Typing the cash side must not let a withdrawal overdraw main.
        val form = withdrawFormState("100", BigDecimal("100"), BigDecimal("0.25"), inCashCurrency = true, hasTarget = true)
        assertEquals(0, BigDecimal("400").compareTo(requireNotNull(form.amountFrom)))
        assertEquals("More than the main balance holds", form.error)
        assertFalse(form.canConfirm)
    }

    @Test
    fun mainCurrencyAmountDerivesTheCashSide() {
        val form = withdrawFormState("400", BigDecimal("1000"), BigDecimal("0.25"), inCashCurrency = false, hasTarget = true)
        assertEquals(0, BigDecimal("400").compareTo(requireNotNull(form.amountFrom)))
        assertEquals(0, BigDecimal("100.00").compareTo(requireNotNull(form.amountTo)))
    }

    @Test
    fun withoutCashTheActionBecomesAddCashBalance() {
        // The defect class reported by the user: a dialog offering only Cancel.
        assertEquals("Add cash balance", withdrawConfirmLabel(cashCount = 0))
        assertEquals("Withdraw", withdrawConfirmLabel(cashCount = 1))
        assertEquals("Withdraw", withdrawConfirmLabel(cashCount = 3))

        val emptyForm = withdrawFormState("", BigDecimal("1000"), BigDecimal.ONE, false, hasTarget = false)
        // There is a way forward even when no cash balance exists...
        assertTrue(withdrawConfirmEnabled(cashCount = 0, hasCreatePath = true, form = emptyForm))
        // ...and no confirm button at all if the host offers no create path.
        assertFalse(withdrawConfirmEnabled(cashCount = 0, hasCreatePath = false, form = emptyForm))
        // With cash present the amount rules decide.
        assertFalse(withdrawConfirmEnabled(cashCount = 1, hasCreatePath = true, form = emptyForm))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class WithdrawDialogBodyTest {

    @get:Rule
    val compose = createComposeRule()

    private val eurCash = Account(
        id = 7L, name = "Euro wallet", currency = "EUR", startingBalance = "0",
        type = "CASH", parentAccountId = 1L
    )

    @Test
    fun convertedAmountIsPreviewedBeforeConfirming() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialogBody(
                    cashAccounts = listOf(eurCash),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    targetId = 7L,
                    amount = "100",
                    note = "",
                    form = withdrawFormState("100", BigDecimal("1000"), BigDecimal("0.25"), false, true),
                    rate = BigDecimal("0.25"),
                    rateReady = true,
                    side = AmountSide.MAIN
                )
            }
        }
        compose.onNodeWithText("Euro wallet · EUR").assertIsDisplayed()
        // Locale-independent: the rate line is built from toPlainString.
        compose.onNodeWithText("1 ILS = 0.25 EUR", substring = true).assertIsDisplayed()
        // Both currencies can be chosen for the typed amount.
        compose.onNodeWithText("ILS").assertIsDisplayed()
        compose.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun overBalanceMessageIsShown() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialogBody(
                    cashAccounts = listOf(eurCash),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("50"),
                    targetId = 7L,
                    amount = "100",
                    note = "",
                    form = withdrawFormState("100", BigDecimal("50"), BigDecimal.ONE, false, true),
                    rate = BigDecimal.ONE,
                    rateReady = true
                )
            }
        }
        compose.onNodeWithText("More than the main balance holds").assertIsDisplayed()
    }

    @Test
    fun everyCashBalanceCanBeChosen() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialogBody(
                    cashAccounts = listOf(eurCash, eurCash.copy(id = 8L, name = "Dollar stash", currency = "USD")),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    targetId = 7L,
                    amount = "",
                    note = "",
                    form = withdrawFormState("", BigDecimal("1000"), BigDecimal.ONE, false, true),
                    rate = BigDecimal.ONE,
                    rateReady = true
                )
            }
        }
        compose.onNodeWithText("Euro wallet (EUR)").assertIsDisplayed()
        compose.onNodeWithText("Dollar stash (USD)").assertIsDisplayed()
    }

    @Test
    fun emptyStateExplainsHowToGetStarted() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialogBody(
                    cashAccounts = emptyList(),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    targetId = null,
                    amount = "",
                    note = "",
                    form = withdrawFormState("", BigDecimal("1000"), BigDecimal.ONE, false, false),
                    rate = BigDecimal.ONE,
                    rateReady = false
                )
            }
        }
        compose.onNodeWithText("No cash balance yet", substring = true).assertIsDisplayed()
    }
}

/** The Add menu is exercised on both a regular phone window and the Qin window. */
abstract class AddMenuTestBase {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addMenuOffersExpenseIncomeAndWithdraw() {
        var expense = false
        var income = false
        var withdraw = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AddMenu(
                    expanded = true,
                    onToggle = {},
                    onExpense = { expense = true },
                    onDeposit = { income = true },
                    onWithdraw = { withdraw = true }
                )
            }
        }
        compose.onNodeWithText("Expense").assertIsDisplayed()
        // The main balance's entry point is a deposit, not "income".
        compose.onNodeWithText("Deposit").assertIsDisplayed()
        compose.onNodeWithText("Withdraw", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Expense").performClick()
        compose.onNodeWithText("Deposit").performClick()
        compose.onNodeWithText("Withdraw", substring = true).performClick()
        assertTrue("Expense option did not navigate", expense)
        assertTrue("Deposit option did not navigate", income)
        assertTrue("Withdraw option did not open the withdraw dialog", withdraw)
    }

    @Test
    fun addMenuIsCollapsedUntilTapped() {
        var toggled = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AddMenu(
                    expanded = false,
                    onToggle = { toggled = true },
                    onExpense = {},
                    onDeposit = {},
                    onWithdraw = {}
                )
            }
        }
        compose.onNodeWithText("+ Add").assertIsDisplayed()
        compose.onNodeWithText("Withdraw", substring = true).assertDoesNotExist()
        compose.onNodeWithText("+ Add").performClick()
        assertTrue("+ Add did not toggle the menu", toggled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class AddMenuRegularTest : AddMenuTestBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w240dp-h320dp-mdpi")
class AddMenuCompactTest : AddMenuTestBase()

/**
 * End-to-end check of the real dialog: with no cash balance the only way out is
 * not Cancel - "Add cash balance" is present and wired to the create sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class WithdrawDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun withoutCashTheDialogOffersAWayForward() {
        var created = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialog(
                    cashAccounts = emptyList(),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    rateLookup = { _, _ -> BigDecimal.ONE },
                    onConfirm = { _, _, _, _ -> },
                    onDismiss = {},
                    onCreateCash = { created = true }
                )
            }
        }
        compose.onNodeWithText("Withdraw to cash").assertIsDisplayed()
        compose.onNodeWithText("Add cash balance").assertIsDisplayed()
        compose.onNodeWithText("Add cash balance").performClick()
        assertTrue("Add cash balance did not open the create dialog", created)
    }
}
