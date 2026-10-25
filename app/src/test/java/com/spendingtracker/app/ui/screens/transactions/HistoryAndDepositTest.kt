package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spendingtracker.app.ui.components.AddMenuOption
import com.spendingtracker.app.ui.components.AddMenuOptions
import com.spendingtracker.app.ui.components.AddMenuStyle
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

/** Row timestamps: time while it is today, the date once the day has passed. */
class HistoryTimestampTest {

    private val locale = Locale.US

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(locale).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = at(2026, 10, 22, 12, 0)

    @Test fun todayShowsTheTime() {
        assertEquals("08:12", historyTimestamp(at(2026, 10, 22, 8, 12), now, locale))
    }

    @Test fun yesterdayAndThisYearShowTheDate() {
        assertEquals("21 Oct", historyTimestamp(at(2026, 10, 21, 8, 12), now, locale))
        assertEquals("1 Jan", historyTimestamp(at(2026, 1, 1, 23, 59), now, locale))
    }

    @Test fun previousYearsIncludeTheYear() {
        assertEquals("31 Dec 2025", historyTimestamp(at(2025, 12, 31, 10, 0), now, locale))
    }

    @Test fun oneMinutePastMidnightCountsAsADifferentDay() {
        // Same day-number arithmetic must not depend on the clock time.
        val justAfterMidnight = at(2026, 10, 22, 0, 1)
        assertEquals("00:01", historyTimestamp(justAfterMidnight, now, locale))
        assertEquals("21 Oct", historyTimestamp(at(2026, 10, 21, 23, 59), now, locale))
    }
}

/**
 * An empty list must say *which* account is empty, and when rows live in another
 * account the screen has to say so. "No transactions yet" was read as data loss
 * after a healed ledger reopened on a different (empty) account.
 */
class AccountVisibilityMessageTest {

    @Test fun emptyStateNamesTheAccount() {
        assertEquals(
            "No entries in Main yet.\nAdd an expense or deposit to get started.",
            historyEmptyMessage("Main")
        )
    }

    @Test fun emptyStateFallsBackToThisAccount() {
        assertEquals(
            "No entries in this account yet.\nAdd an expense or deposit to get started.",
            historyEmptyMessage(null)
        )
    }

    @Test fun otherAccountHintIsSingularForOneEntry() {
        assertEquals("1 entry is in \"Old ledger\"", otherAccountHint(1, "Old ledger"))
    }

    @Test fun otherAccountHintIsPluralForMany() {
        assertEquals("12 entries are in \"Old ledger\"", otherAccountHint(12, "Old ledger"))
    }
}

/**
 * A deposit is external by default; funding it from a cash balance turns it into
 * a transfer that may not exceed that balance.
 */
class DepositFormStateTest {

    @Test fun defaultDepositIsExternal() {
        val form = depositFormState("500", BigDecimal.ONE, cashBalance = null, cashCurrency = null)
        assertEquals(0, BigDecimal("500").compareTo(requireNotNull(form.amount)))
        assertNull(form.cashAmount)
        assertNull(form.error)
        assertTrue(form.canSave)
    }

    @Test fun sameCurrencyCashSourceNeedsEnoughInCash() {
        val ok = depositFormState("40", BigDecimal.ONE, BigDecimal("100"), "ILS")
        assertEquals(0, BigDecimal("40").compareTo(requireNotNull(ok.cashAmount)))
        assertTrue(ok.canSave)

        val tooMuch = depositFormState("140", BigDecimal.ONE, BigDecimal("100"), "ILS")
        assertEquals("More than the ILS balance holds", tooMuch.error)
        assertFalse(tooMuch.canSave)
    }

    @Test fun foreignCashSourceConvertsFromTheRate() {
        // 1 EUR = 3.9473 ILS, so depositing ₪394.73 needs €100.
        val form = depositFormState("394.73", BigDecimal("3.9473"), BigDecimal("100"), "EUR")
        assertEquals(0, BigDecimal("100.00").compareTo(requireNotNull(form.cashAmount)))
        assertNull(form.error)
        assertTrue(form.canSave)
    }

    @Test fun invalidAmountsCannotBeSaved() {
        assertFalse(depositFormState("", BigDecimal.ONE, null, null).canSave)
        assertFalse(depositFormState("0", BigDecimal.ONE, null, null).canSave)
        assertFalse(depositFormState("abc", BigDecimal.ONE, null, null).canSave)
        assertEquals("Enter a positive amount", depositFormState("abc", BigDecimal.ONE, null, null).error)
    }
}

/** The History toolbar offers the same three actions as Home, as pills. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class HistoryAddMenuTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun collapsedMenuShowsNothingUntilOpened() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AddMenuOptions(
                    expanded = false,
                    style = AddMenuStyle.Pills,
                    options = listOf(
                        AddMenuOption(label = "Expense", onClick = {}),
                        AddMenuOption(label = "Deposit", onClick = {}),
                        AddMenuOption(label = "Withdraw", onClick = {})
                    )
                )
            }
        }
        compose.onNodeWithText("Expense").assertDoesNotExist()
    }

    @Test
    fun expandedMenuOffersExpenseDepositAndWithdraw() {
        var expense = false
        var deposit = false
        var withdraw = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AddMenuOptions(
                    expanded = true,
                    style = AddMenuStyle.Pills,
                    options = listOf(
                        AddMenuOption(label = "Expense", onClick = { expense = true }, primary = true),
                        AddMenuOption(label = "Deposit", onClick = { deposit = true }),
                        AddMenuOption(label = "Withdraw", onClick = { withdraw = true })
                    )
                )
            }
        }
        compose.onNodeWithText("Expense").assertIsDisplayed()
        compose.onNodeWithText("Deposit").assertIsDisplayed()
        compose.onNodeWithText("Withdraw").assertIsDisplayed()

        compose.onNodeWithText("Expense").performClick()
        compose.onNodeWithText("Deposit").performClick()
        compose.onNodeWithText("Withdraw").performClick()
        assertTrue(expense)
        assertTrue(deposit)
        assertTrue(withdraw)
    }
}
