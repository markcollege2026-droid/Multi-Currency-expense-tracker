package com.spendingtracker.app.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
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
 * dialog that only offered Cancel, so every assertion checks that an action is
 * displayed and that clicking it does something.
 *
 * The Add menu is exercised on both a regular phone window and the 240x320dp
 * Qin window; the dialogs are exercised on the roomy window only.
 */
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
                    onIncome = { income = true },
                    onWithdraw = { withdraw = true }
                )
            }
        }
        compose.onNodeWithText("Expense").assertIsDisplayed()
        compose.onNodeWithText("Income").assertIsDisplayed()
        compose.onNodeWithText("Withdraw", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Expense").performClick()
        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Withdraw", substring = true).performClick()
        assertTrue("Expense option did not navigate", expense)
        assertTrue("Income option did not navigate", income)
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
                    onIncome = {},
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class WithdrawDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val eurCash = Account(
        id = 7L, name = "Euro wallet", currency = "EUR", startingBalance = "0",
        type = "CASH", parentAccountId = 1L
    )

    private fun typeAmount(text: String) {
        // The first text field of the dialog is the amount.
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        compose.waitForIdle()
    }

    @Test
    fun withdrawConvertsIntoTheCashCurrency() {
        var confirmedId = -1L
        var confirmedAmount: String? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialog(
                    cashAccounts = listOf(eurCash),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    rateLookup = { _, _ -> BigDecimal("0.25") },
                    onConfirm = { id, amount, _ -> confirmedId = id; confirmedAmount = amount },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Withdraw to cash").assertIsDisplayed()
        typeAmount("100")
        // The rate preview is shown before committing (locale-independent assertion).
        compose.onNodeWithText("1 ILS = 0.25", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Withdraw").performClick()
        compose.waitForIdle()
        assertTrue("Withdraw action never confirmed", confirmedAmount != null)
        assertTrue("Wrong cash account", confirmedId == 7L)
        assertTrue("Wrong source amount: $confirmedAmount", confirmedAmount!!.startsWith("100"))
    }

    @Test
    fun withdrawRejectsMoreThanTheMainBalance() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialog(
                    cashAccounts = listOf(eurCash),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("50"),
                    rateLookup = { _, _ -> BigDecimal.ONE },
                    onConfirm = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        typeAmount("100")
        compose.onNodeWithText("More than the main balance holds").assertIsDisplayed()
    }

    @Test
    fun withdrawWithoutACashBalanceStillOffersAWayForward() {
        // The exact defect class reported: a dialog whose only action is Cancel.
        var created = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialog(
                    cashAccounts = emptyList(),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    rateLookup = { _, _ -> BigDecimal.ONE },
                    onConfirm = { _, _, _ -> },
                    onDismiss = {},
                    onCreateCash = { created = true }
                )
            }
        }
        compose.onNodeWithText("Add cash balance").assertIsDisplayed()
        compose.onNodeWithText("Add cash balance").performClick()
        assertTrue("Add cash balance did not open the create dialog", created)
    }

    @Test
    fun severalCashBalancesCanBeChosen() {
        val usdCash = eurCash.copy(id = 8L, name = "Dollar stash", currency = "USD")
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                WithdrawDialog(
                    cashAccounts = listOf(eurCash, usdCash),
                    mainCurrency = "ILS",
                    mainBalance = BigDecimal("1000"),
                    rateLookup = { _, _ -> BigDecimal.ONE },
                    onConfirm = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Euro wallet (EUR)").assertIsDisplayed()
        compose.onNodeWithText("Dollar stash (USD)").assertIsDisplayed()
    }
}
