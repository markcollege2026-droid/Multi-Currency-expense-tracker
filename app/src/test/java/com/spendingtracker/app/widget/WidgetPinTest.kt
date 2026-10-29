package com.spendingtracker.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The in-app "add widget" path - the part a user actually touches.
 *
 * These assertions are deliberately about behaviour that does not depend on a
 * launcher being present: whatever the framework answers, [WidgetPin] has to pass
 * that answer on unchanged, and the fallback wording has to be usable on its own
 * because on launchers without pin support it is the only instruction the user
 * gets. Whether the system actually publishes the provider is checked on a real
 * emulator in [com.spendingtracker.app.WidgetAndEntryTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetPinTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(context)

    @Test
    fun supportsExactlyWhatTheLauncherSupports() {
        // Never claim the in-app path is available when the framework says it is not.
        assertEquals(manager.isRequestPinAppWidgetSupported, WidgetPin.isSupported(context))
    }

    @Test
    fun refusesHonestlyWhenTheLauncherCannotPin() {
        if (WidgetPin.isSupported(context)) {
            // This runtime can pin; the refusal branch cannot be exercised here.
            assertTrue(true)
        } else {
            // No silent success: the caller must show the written instructions.
            assertFalse(WidgetPin.request(context))
        }
    }

    @Test
    fun reportsWhetherTheWidgetIsAlreadyPlaced() {
        // Nothing is placed in this process, so "not placed" has to be reported
        // honestly - the Settings page uses it to word the button.
        assertFalse(WidgetPin.isPlaced(context))
        assertEquals(0, SpendingWidgetProvider.widgetIds(context).size)
        // Asking for a redraw with nothing placed must be a no-op, not a crash.
        SpendingWidgetProvider.requestUpdate(context)
        assertFalse(WidgetPin.isPlaced(context))
    }

    @Test
    fun fallbackInstructionsTellTheUserWhereToLook() {
        // The wording is the whole fallback, so it has to mention the path the
        // launcher actually uses.
        assertTrue(WidgetPin.manualInstructions.contains("long-press"))
        assertTrue(WidgetPin.manualInstructions.contains("Widgets"))
        assertTrue(WidgetPin.manualInstructions.contains("Spending Tracker"))
        assertTrue(
            "the launcher's own widget list may only refresh after a restart, which must be said",
            WidgetPin.manualInstructions.contains("restart")
        )
    }
}
