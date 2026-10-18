package com.spendingtracker.app.ui.compact

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose check that the 480x640 F21 window really resolves to the
 * compact layout. Hardware key dispatch needs a real keypad/IME and is covered by
 * the pure KeypadInput tests plus on-device verification.
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
}
