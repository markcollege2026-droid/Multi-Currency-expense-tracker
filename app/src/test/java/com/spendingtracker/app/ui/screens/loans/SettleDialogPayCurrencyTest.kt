package com.spendingtracker.app.ui.screens.loans

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import java.math.BigDecimal
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Pay in" on the external settle form.
 *
 * The currency used to be fixed to the loan's unless a balance in another currency
 * was chosen, so a loan paid in cash in a different currency could not be recorded
 * at all. The form now offers every currency, shows what is owed in it, and keeps
 * the loan's own balance in the loan's currency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class SettleDialogPayCurrencyTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var previousLocale: Locale

    @Before
    fun pinLocale() {
        // Money is rendered with java.text.NumberFormat, so pin the locale to make
        // the expected strings exact rather than depending on the build machine.
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private val ilsMain = Account(id = 1, name = "Everyday", currency = "ILS", startingBalance = "1000")
    private val eurCash = Account(
        id = 2, name = "Euro wallet", currency = "EUR", startingBalance = "100",
        type = "CASH", parentAccountId = 1
    )
    private val accounts = listOf(ilsMain, eurCash)
    private val balances = mapOf(1L to BigDecimal("1000"), 2L to BigDecimal("100"))
    private val debt = LoanDebt(
        id = 1, type = LoanDebt.TYPE_DEBT, personName = "Dana", amountOriginal = "9",
        currencyOriginal = "ILS", date = 1, dueDate = null, note = null
    )

    /** 3 ILS = $1, expressed as the rate the app stores: 1 ILS = 0.3333333 USD. */
    private val ilsToUsd = BigDecimal("0.3333333")

    private fun render(
        payCurrency: String,
        typedInPayCurrency: Boolean,
        amount: String,
        remaining: String = "9",
        accountId: Long? = null,
        onCurrencyMenuOpen: () -> Unit = {},
        onPayCurrencySelected: (String) -> Unit = {}
    ) = compose.setContent {
        SpendingTrackerTheme(dynamicColor = false) {
            SettleLoanDialogBody(
                loan = debt,
                remaining = BigDecimal(remaining),
                accounts = accounts,
                balances = balances,
                amount = amount,
                note = "",
                accountId = accountId,
                dropdownExpanded = false,
                form = settlementFormState(
                    amountText = amount,
                    remaining = BigDecimal(remaining),
                    isDebt = true,
                    loanCurrency = "ILS",
                    account = accounts.firstOrNull { it.id == accountId },
                    accountBalance = balances[accountId ?: 1L] ?: BigDecimal.ZERO,
                    rate = ilsToUsd,
                    payCurrency = payCurrency,
                    typedInPayCurrency = typedInPayCurrency
                ),
                rate = ilsToUsd,
                rateReady = true,
                typedInPayCurrency = typedInPayCurrency,
                payCurrency = payCurrency,
                currencies = listOf("USD", "EUR", "ILS"),
                currencyMenuOpen = false,
                onCurrencyMenuChange = { onCurrencyMenuOpen() },
                onPayCurrencySelected = onPayCurrencySelected
            )
        }
    }

    @Test
    fun choosingUsdShowsWhatIsStillOwedInUsd() {
        render(payCurrency = "USD", typedInPayCurrency = true, amount = "2")
        // The balance stays in the loan's currency...
        compose.onNodeWithText("₪9.00 of ₪9.00").assertIsDisplayed()
        // ...and the equivalent is spelled out, including the rate used.
        compose.onNodeWithText("= $3.00 at 1 ILS = 0.3333333 USD").assertIsDisplayed()
    }

    @Test
    fun payingTwoDollarsShowsTheLoanDroppingBySixShekels() {
        render(payCurrency = "USD", typedInPayCurrency = true, amount = "2")
        compose.onNodeWithText("Amount (USD)").assertIsDisplayed()
        compose.onNodeWithText("₪6.00 comes off the loan").assertIsDisplayed()
        compose.onNodeWithText("Part payment: ₪3.00 stays open afterwards.").assertIsDisplayed()
    }

    @Test
    fun theCurrencySelectorIsAvailableWithoutChoosingABalance() {
        render(payCurrency = "ILS", typedInPayCurrency = false, amount = "9")
        // No account is selected (external revenue), yet the paying currency can change.
        compose.onNodeWithText("PAY IN").assertIsDisplayed()
        // The selector names the currency and what is owed in it, and is a button.
        // (Opening it and picking a currency is covered end to end by
        // choosingUsdPrefillsTheEquivalentAndConfirmsInBothCurrencies.)
        val selector = compose.onNodeWithContentDescription("Currency:", substring = true)
        selector.assertIsDisplayed()
        selector.assertHasClickAction()
        compose.onNodeWithText("ILS · ₪9.00").assertIsDisplayed()
    }

    @Test
    fun typingInTheLoanCurrencyShowsWhatToHandOverInstead() {
        render(payCurrency = "USD", typedInPayCurrency = false, amount = "6")
        compose.onNodeWithText("Amount (ILS)").assertIsDisplayed()
        compose.onNodeWithText("Paying $2.00 covers it").assertIsDisplayed()
    }

    @Test
    fun eitherCurrencyCanBeTheOneTyped() {
        // External (no balance) in another currency: the chips choose which side of
        // the conversion the digits are in, so "pay $2" is expressible directly.
        render(payCurrency = "USD", typedInPayCurrency = true, amount = "2")
        compose.onNodeWithText("Type in ILS").assertIsDisplayed()
        compose.onNodeWithText("Type in USD").assertIsDisplayed()
    }

    @Test
    fun aBalanceInAnotherCurrencyOffersTheSameSwitch() {
        // With a balance chosen, the money leaves that balance, so its currency is
        // the paying one.
        render(payCurrency = "EUR", typedInPayCurrency = false, amount = "9", accountId = 2)
        compose.onNodeWithText("Type in ILS").assertIsDisplayed()
        compose.onNodeWithText("Type in EUR").assertIsDisplayed()
    }

    /**
     * The whole dialog, driving the "Pay in" selector the way a user does. This is
     * the reported example end to end: ₪9 owed at 3 ILS = $1, choose USD, confirm -
     * and the payment is recorded as $3 paying off ₪9.
     */
    @Test
    fun choosingUsdPrefillsTheEquivalentAndConfirmsInBothCurrencies() {
        var confirmed: List<String?>? = null
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                SettleLoanDialog(
                    loan = debt,
                    remaining = BigDecimal("9"),
                    accounts = accounts,
                    balances = balances,
                    rateLookup = { _, _ -> ilsToUsd },
                    onConfirm = { amountLoan, accountId, rate, note, payCurrency, payAmount ->
                        confirmed = listOf(amountLoan, accountId?.toString(), rate, note, payCurrency, payAmount)
                    },
                    onDismiss = {},
                    currencies = listOf("USD", "EUR", "ILS")
                )
            }
        }
        // External revenue is the default source, so the selector is available at once.
        compose.onNodeWithText("PAY IN").assertIsDisplayed()
        compose.onNodeWithContentDescription("Currency:", substring = true).performClick()
        // The loan's own currency is offered first and marked as such.
        compose.onNodeWithText("ILS (loan currency)").assertIsDisplayed()
        compose.onNodeWithText("USD").performClick()

        // The field switches to dollars and is filled with what is owed: $3 for ₪9.
        compose.onNodeWithText("Amount (USD)").assertIsDisplayed()
        compose.onNodeWithText("= $3.00 at 1 ILS = 0.3333333 USD").assertIsDisplayed()
        compose.onNodeWithText("Settle debt").performClick()

        assertEquals(
            listOf("9.00", null, "0.3333333", null, "USD", "3"),
            confirmed
        )
    }
}
