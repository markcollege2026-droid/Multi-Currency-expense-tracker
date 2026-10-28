package com.spendingtracker.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amount typing, as the user experiences it. Regression coverage for the
 * decimal bug: with the old String-hoisted field the cursor jumped between
 * the edges of the field while typing after '.', and nothing stopped a
 * second separator from making the amount unparseable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class AmountTextFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private var captured: String = ""

    private fun setAmountField(rtl: Boolean = false) {
        captured = ""
        compose.setContent {
            var text by remember { mutableStateOf("") }
            val field: @androidx.compose.runtime.Composable () -> Unit = {
                AppTextField(
                    value = text,
                    onValueChange = { text = it; captured = it },
                    label = "Amount",
                    keyboardType = KeyboardType.Decimal
                )
            }
            SpendingTrackerTheme(dynamicColor = false) {
                if (rtl) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { field() }
                } else {
                    field()
                }
            }
        }
    }

    private fun type(vararg chunks: String) {
        chunks.forEach { compose.onNode(hasSetTextAction()).performTextInput(it) }
    }

    @Test
    fun typingADecimalKeepsTextAndCursorSteady() {
        setAmountField()
        type("1", "2", ".", "5")
        assertEquals("12.5", captured)
        val selection = compose.onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(4, 4), selection)
    }

    @Test
    fun secondDecimalPointIsRejectedNotAppended() {
        setAmountField()
        type("1", "2", ".", "5", ".", "6")
        assertEquals("12.56", captured)
    }

    @Test
    fun commaKeyBecomesTheDecimalPoint() {
        setAmountField()
        type("1", ",", "5")
        assertEquals("1.5", captured)
    }

    @Test
    fun pastedGroupedAmountIsNormalised() {
        setAmountField()
        type("1,234.56")
        assertEquals("1234.56", captured)
    }

    @Test
    fun decimalTypingStaysExactInRtlLayout() {
        setAmountField(rtl = true)
        type("1", "2", ".", "5", "0")
        assertEquals("12.50", captured)
        val selection = compose.onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange]
        assertEquals(TextRange(5, 5), selection)
    }
}
