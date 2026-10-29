package com.spendingtracker.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The two feedback primitives: a one-shot toast and an inline progress row.
 *
 * The toast is what refused or instant actions use ("still deleting…", "rates
 * refreshed"), so it must fire exactly once - a recomposition or a rotation must
 * not repeat it - and it must clear the flag it was given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppFeedbackTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toastShowsOnceThenClearsItsOwnFlag() {
        var message: String? by mutableStateOf("Still deleting \u201CTravel\u201D")
        var shown = 0
        compose.setContent {
            AppToast(message, onShown = { shown++; message = null })
        }
        compose.waitForIdle()
        assertEquals("the toast fires once", 1, shown)
        assertNull("the flag is cleared so it cannot repeat", message)
        assertEquals("Still deleting \u201CTravel\u201D", ShadowToast.getTextOfLatestToast())

        // Further recompositions must not show it again.
        compose.waitForIdle()
        assertEquals(1, shown)
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun noToastWithoutAMessage() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppToast(null, onShown = {})
            }
        }
        compose.waitForIdle()
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun busyRowShowsWhatIsRunning() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppBusyRow("Deleting Travel\u2026")
            }
        }
        compose.onNodeWithText("Deleting Travel\u2026").assertIsDisplayed()
    }
}
