package com.spendingtracker.app.ui.screens.loans

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Discoverability of cross-currency repayment.
 *
 * The currency chips only exist once a balance in a different currency is chosen,
 * and the dialog's default source is "External revenue". So opening Settle and
 * reading only the top of the form made the feature look absent - the report was
 * "there is no option to pay loans in different currencies". The dialog now says
 * where the option is, in the same place, for exactly that case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class SettleDialogDiscoverabilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val ilsMain = Account(id = 1, name = "Everyday", currency = "ILS", startingBalance = "1000")
    private val eurCash = Account(
        id = 2, name = "Euro wallet", currency = "EUR", startingBalance = "100",
        type = "CASH", parentAccountId = 1
    )
    private val debt = LoanDebt(
        id = 1, type = LoanDebt.TYPE_DEBT, personName = "Dana", amountOriginal = "400",
        currencyOriginal = "ILS", date = 1, dueDate = null, note = null
    )

    private fun body(accountId: Long?, accounts: List<Account>) = compose.setContent {
        SpendingTrackerTheme(dynamicColor = false) {
            SettleLoanDialogBody(
                loan = debt,
                remaining = BigDecimal("400"),
                accounts = accounts,
                balances = accounts.associate { it.id to BigDecimal("500") },
                amount = "400",
                note = "",
                accountId = accountId,
                dropdownExpanded = false,
                form = settlementFormState(
                    amountText = "400",
                    remaining = BigDecimal("400"),
                    isDebt = true,
                    loanCurrency = "ILS",
                    account = accounts.firstOrNull { it.id == accountId },
                    accountBalance = BigDecimal("500"),
                    rate = BigDecimal("0.25")
                ),
                rate = BigDecimal("0.25"),
                rateReady = true
            )
        }
    }

    @Test
    fun theExternalDefaultExplainsWhereTheOtherCurrencyIs() {
        body(accountId = null, accounts = listOf(ilsMain, eurCash))
        compose.onNodeWithText("Paying in another currency? Choose a balance under PAY FROM", substring = true)
            .assertIsDisplayed()
        // It names a real balance, so the instruction can be followed immediately.
        compose.onNodeWithText("Euro wallet", substring = true).assertIsDisplayed()
    }

    @Test
    fun theHintIsGoneOnceSuchABalanceIsChosen() {
        body(accountId = 2, accounts = listOf(ilsMain, eurCash))
        // The hint is only for the no-balance case; the chips take over here.
        compose.onNodeWithText("Paying in another currency?", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Type in ILS").assertIsDisplayed()
        compose.onNodeWithText("Type in EUR").assertIsDisplayed()
    }

    @Test
    fun noHintWhenEveryBalanceUsesTheLoanCurrency() {
        val usdMain = Account(id = 5, name = "Travel", currency = "ILS", startingBalance = "0")
        body(accountId = null, accounts = listOf(ilsMain, usdMain))
        // Nothing to suggest: there is no balance in another currency anywhere.
        compose.onNodeWithText("Paying in another currency?", substring = true).assertDoesNotExist()
    }
}
