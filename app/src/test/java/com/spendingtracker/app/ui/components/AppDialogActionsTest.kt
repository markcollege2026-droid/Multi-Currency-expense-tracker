package com.spendingtracker.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the dialog action bar.
 *
 * The confirm button used to be a `fillMaxWidth` child of an `End`-aligned `Row`, so
 * it was measured at 0dp and disappeared: every confirmation ("Add cash balance",
 * "Delete account?", "Erase all data?") appeared to offer only "Cancel". The action
 * bar is now a [AppDialogActions] FlowRow with intrinsic widths; these tests render it
 * inside a deliberately narrow column - the worst case that used to break - and assert
 * every action is displayed and clickable. Both a regular phone window and the
 * 240x320dp Qin F21 window are covered.
 */
abstract class AppDialogActionsTestBase {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bothActionsAreDisplayedInANarrowDialog() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(
                        dismissLabel = "Cancel",
                        onDismiss = {},
                        confirmLabel = "Create",
                        onConfirm = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Create").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmRunsItsAction() {
        var created = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(
                        dismissLabel = "Cancel",
                        onDismiss = {},
                        confirmLabel = "Create",
                        onConfirm = { created = true }
                    )
                }
            }
        }
        compose.onNodeWithText("Create").performClick()
        assertTrue("Create did not run its action", created)
    }

    @Test
    fun dismissRunsItsAction() {
        var dismissed = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(
                        dismissLabel = "Cancel",
                        onDismiss = { dismissed = true },
                        confirmLabel = "Save",
                        onConfirm = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertTrue("Cancel did not run its action", dismissed)
    }

    @Test
    fun threeActionsAllFit() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(
                        dismissLabel = "Cancel",
                        onDismiss = {},
                        confirmLabel = "Save",
                        onConfirm = {},
                        extraAction = { Text("Delete") }
                    )
                }
            }
        }
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun disabledConfirmIsStillVisible() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(
                        dismissLabel = "Cancel",
                        onDismiss = {},
                        confirmLabel = "Create",
                        onConfirm = {},
                        confirmEnabled = false
                    )
                }
            }
        }
        compose.onNodeWithText("Create").assertIsDisplayed()
    }

    @Test
    fun noActionsRenderNothingAndDoNotCrash() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Column(modifier = Modifier.width(180.dp)) {
                    AppDialogActions(dismissLabel = "")
                    Text("body")
                }
            }
        }
        compose.onNodeWithText("body").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
class AppDialogActionsRegularTest : AppDialogActionsTestBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w240dp-h320dp-mdpi")
class AppDialogActionsCompactTest : AppDialogActionsTestBase() {

    @Test
    fun compactWindowReallyIsCompact() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                Text(
                    text = if (com.spendingtracker.app.ui.compact.LocalCompactUi.current) "compact" else "regular"
                )
            }
        }
        compose.onNodeWithText("compact").assertIsDisplayed()
    }
}
