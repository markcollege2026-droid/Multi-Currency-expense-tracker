package com.spendingtracker.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
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
 * The confirm button used to be a `fillMaxWidth` child of an `End`-aligned `Row`,
 * so it was measured at 0dp and disappeared: dialogs such as "Set up cash" only
 * showed "Cancel". These tests assert that every action is really displayed and
 * clickable on both a regular phone window and the 240x320dp Qin F21 window.
 */
abstract class AppDialogActionsTestBase {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmAndDismissAreBothDisplayed() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppDialog(
                    onDismissRequest = {},
                    title = "Add cash",
                    confirmLabel = "Create",
                    dismissLabel = "Cancel",
                    onConfirm = {}
                ) {
                    Text("Opening cash")
                }
            }
        }
        compose.onNodeWithText("Create").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmIsClickable() {
        var created = false
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppDialog(
                    onDismissRequest = {},
                    title = "Add cash",
                    confirmLabel = "Create",
                    dismissLabel = "Cancel",
                    onConfirm = { created = true }
                ) {
                    Text("Opening cash")
                }
            }
        }
        compose.onNodeWithText("Create").performClick()
        assertTrue("Create did not run its action", created)
    }

    @Test
    fun allThreeActionsFitIncludingExtraAction() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppDialog(
                    onDismissRequest = {},
                    title = "Edit cash",
                    confirmLabel = "Save",
                    dismissLabel = "Cancel",
                    onConfirm = {},
                    extraAction = { Text("Delete") }
                ) {
                    Text("Name")
                }
            }
        }
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun longBodyDoesNotHideActions() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppDialog(
                    onDismissRequest = {},
                    title = "Long form",
                    confirmLabel = "Save",
                    dismissLabel = "Cancel",
                    onConfirm = {}
                ) {
                    Column {
                        repeat(12) { index ->
                            Column(modifier = Modifier.height(40.dp)) { Text("Row $index") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirmDialogOffersBothChoices() {
        compose.setContent {
            SpendingTrackerTheme(dynamicColor = false) {
                AppConfirmDialog(
                    title = "Delete transaction?",
                    message = "This cannot be undone.",
                    confirmLabel = "Delete",
                    dismissLabel = "Cancel",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
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
                Text(if (com.spendingtracker.app.ui.compact.LocalCompactUi.current) "compact" else "regular")
            }
        }
        compose.onNodeWithText("compact").assertExists()
    }
}
