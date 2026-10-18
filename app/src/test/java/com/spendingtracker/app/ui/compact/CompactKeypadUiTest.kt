package com.spendingtracker.app.ui.compact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose checks for the 480x640 F21 window. These run on the JVM
 * in CI; they are not a substitute for a physical-keypad device test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w240dp-h320dp-mdpi")
class CompactKeypadUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun qinSizedWindowUsesCompactLayout() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                androidx.compose.material3.Text(
                    text = if (LocalCompactUi.current) "compact" else "regular"
                )
            }
        }
        compose.onNodeWithText("compact").assertExists()
    }

    @Test
    fun hardwareNumberKeysFillFocusedAmountField() {
        var value by mutableStateOf("")
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Amount",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput {
            pressKey(Key.One)
            pressKey(Key.Two)
            pressKey(Key.Period)
            pressKey(Key.Five)
            pressKey(Key.Six)
        }
        assertEquals("12.56", value)
    }

    @Test
    fun hardwareDecimalKeyCannotBeAddedTwice() {
        var value by mutableStateOf("1.5")
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Amount",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Period) }
        assertEquals("1.5", value)
    }
}
