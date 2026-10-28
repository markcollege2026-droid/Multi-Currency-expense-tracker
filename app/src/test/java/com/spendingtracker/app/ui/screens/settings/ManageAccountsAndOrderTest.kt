package com.spendingtracker.app.ui.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.repository.AccountSummary
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import com.spendingtracker.app.util.CurrencyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Settings -> Manage accounts drill-down and the currency-order dialog's
 * local-only editing (reordering used to persist on every tap and reseed the
 * list mid-edit; now only Save persists and Cancel truly cancels).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class ManageAccountsAndOrderTest {

    @get:Rule
    val compose = createComposeRule()

    private val main = Account(id = 1, name = "Everyday", currency = "ILS", startingBalance = "1000")
    private val other = Account(id = 5, name = "Travel", currency = "EUR", startingBalance = "200")
    private val pocket = Account(id = 2, name = "Pocket", currency = "ILS", startingBalance = "100", type = "CASH", parentAccountId = 1)
    private val visa = Account(id = 3, name = "Visa", currency = "ILS", startingBalance = "-250", type = "CREDIT_CARD", parentAccountId = 1)
    private val summaries = mapOf(
        1L to AccountSummary(entries = 9, balance = BigDecimal("900")),
        2L to AccountSummary(entries = 3, balance = BigDecimal("80")),
        3L to AccountSummary(entries = 4, balance = BigDecimal("-250")),
        5L to AccountSummary(entries = 1, balance = BigDecimal("200"))
    )

    @Test
    fun manageListShowsAccountsAndOpensOne() {
        var opened: Long? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                ManageAccountsList(
                    accounts = listOf(main, other),
                    balanceAccounts = listOf(main, pocket, visa, other),
                    summaries = summaries,
                    activeAccountId = 1,
                    message = null,
                    error = null,
                    onDismissMessage = {},
                    onOpenAccount = { opened = it.id },
                    onUseAccount = {},
                    onAddAccount = {}
                )
            }
        }
        compose.onNodeWithText("Everyday").assertIsDisplayed()
        compose.onNodeWithText("Travel").assertIsDisplayed()
        compose.onNodeWithText("ACTIVE").assertIsDisplayed()
        compose.onNodeWithText("1 cash balance · 1 card", substring = true).assertIsDisplayed()
        compose.onNodeWithText("+ Add account").assertIsDisplayed()
        assertNull(opened)
        compose.onNodeWithText("Travel").performClick()
        assertEquals(5L, opened)
    }

    @Test
    fun accountDetailShowsCashAndCards() {
        var addCash = false
        var deletedChild: Long? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AccountDetailContent(
                    account = main,
                    children = listOf(pocket, visa),
                    summaries = summaries,
                    isActive = true,
                    canDeleteAccount = true,
                    message = null,
                    error = null,
                    onDismissMessage = {},
                    onUseAccount = {},
                    onRename = {},
                    onDeleteAccount = {},
                    onDeleteChild = { deletedChild = it.id },
                    onAddCash = { addCash = true }
                )
            }
        }
        compose.onNodeWithText("Cash balances", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Pocket").assertIsDisplayed()
        compose.onNodeWithText("Credit cards", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Visa").assertIsDisplayed()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Delete this account").assertIsDisplayed()
        compose.onNodeWithText("+ Add cash balance").performClick()
        assertEquals(true, addCash)
        assertNull(deletedChild)
    }

    @Test
    fun currencyOrderDialogSavesOnlyOnSave() {
        val codes = listOf("USD", "EUR", "ILS")
        val infos = CurrencyProvider.allCurrencies.filter { it.code in codes }
        var saved: List<String>? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                CurrencyOrderDialog(
                    orderedCurrencies = infos,
                    favoriteCurrencies = emptyList(),
                    customOrder = codes,
                    sortMode = "ALPHABETICAL",
                    onDismiss = {},
                    onToggleFavorite = {},
                    onSaveCustomOrder = { saved = it },
                    onSortModeChanged = {}
                )
            }
        }
        // Arrows exist even before CUSTOM is selected; move USD down one.
        compose.onAllNodesWithText("↓")[0].performClick()
        // Nothing is persisted until Save is pressed.
        assertNull(saved)
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("EUR", "USD", "ILS"), saved)
    }
}
