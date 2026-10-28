package com.spendingtracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import com.spendingtracker.app.ui.screens.transactions.AddExpenseContent
import com.spendingtracker.app.ui.screens.transactions.AddExpenseUiState
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/**
 * On-device (emulator) check of decimal entry on the expense form: text goes
 * through the real input pipeline, so cursor behaviour is exercised for real.
 * Regression coverage for the cursor jumping between the field's edges while
 * typing digits after the decimal point.
 */
class AddExpenseUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var capturedAmount: String = ""

    /** The amount field is the first editable field on the form (note follows). */
    private fun amountField() = composeTestRule.onAllNodes(hasSetTextAction())[0]

    private fun setExpenseForm() {
        capturedAmount = ""
        composeTestRule.setContent {
            var amount by remember { mutableStateOf("") }
            val parsed = amount.toBigDecimalOrNull()
            SpendingTrackerTheme(dynamicColor = false) {
                AddExpenseContent(
                    state = AddExpenseUiState(
                        amount = amount,
                        currency = "ILS",
                        balanceCurrency = "ILS",
                        convertedAmount = parsed,
                        rateUsed = parsed?.let { BigDecimal.ONE }
                    ),
                    onBack = {},
                    onSave = {},
                    onAmountChanged = { amount = it; capturedAmount = it },
                    onNoteChanged = {},
                    onOpenCurrency = {},
                    onOpenCategory = {},
                    onOpenAccount = {},
                    onManualRateToggle = {},
                    onManualRateChanged = {},
                    onAllowOverdraft = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun typingDecimalAmountKeepsCursorAtTheEnd() {
        setExpenseForm()
        val field = amountField()
        field.performClick()
        listOf("1", "2", ".", "5").forEach { field.performTextInput(it) }
        composeTestRule.waitForIdle()
        assertEquals("12.5", capturedAmount)
        val selection = field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(4, 4), selection)
    }

    @Test
    fun secondDecimalPointDoesNotCorruptTheAmount() {
        setExpenseForm()
        val field = amountField()
        field.performClick()
        listOf("1", "2", ".", "5", ".", "6").forEach { field.performTextInput(it) }
        composeTestRule.waitForIdle()
        assertEquals("12.56", capturedAmount)
        // And no error is shown for the (now valid) amount.
        composeTestRule.onNodeWithText("Enter a valid amount").assertDoesNotExist()
    }
}
