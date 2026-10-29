package com.spendingtracker.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendingtracker.app.util.AppEntry
import com.spendingtracker.app.widget.SpendingWidgetProvider
import com.spendingtracker.app.widget.WidgetSnapshot
import com.spendingtracker.app.widget.WidgetSnapshotStore
import com.spendingtracker.app.widget.WidgetPin
import com.spendingtracker.app.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks for the home-screen widget and the entry point it uses.
 *
 * These need a real Android runtime: `RemoteViews.apply` enforces which view
 * types and attributes a widget may use (a plain unit test cannot), and the
 * receiver/metadata lookup exercises the merged manifest.
 */
@RunWith(AndroidJUnit4::class)
class WidgetAndEntryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun widgetLayoutOnlyUsesViewsRemoteViewsSupports() {
        // Throws if the layout contains a view type or attribute widgets cannot use.
        val views = RemoteViews(context.packageName, R.layout.widget_spending)
        val inflated = views.apply(context, null)
        assertNotNull(inflated)
        assertNotNull(inflated.findViewById<android.view.View>(R.id.widget_balance))
        assertNotNull(inflated.findViewById<android.view.View>(R.id.widget_rate))
        assertNotNull(inflated.findViewById<android.view.View>(R.id.widget_add_expense))
    }

    @Test
    fun theWidgetReceiverIsDeclaredWithItsProviderInfo() {
        val component = ComponentName(context, SpendingWidgetProvider::class.java)
        val info = context.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
        assertNotNull("the widget receiver must be in the manifest", info)
        assertTrue("the receiver must receive widget updates", info.enabled)
        assertNotNull(
            "android.appwidget.provider metadata is what makes it selectable",
            info.metaData?.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)
        )
    }

    /**
     * The real gate for "why is there no widget in my widget list": the system,
     * not the manifest, decides what the launcher's picker can offer. If the
     * provider is not in [AppWidgetManager.getInstalledProviders], no launcher can
     * show it no matter how correct the rest is - which is exactly what a
     * receiver with a broken intent-filter, missing metadata or an unparseable
     * appwidget-provider XML looks like.
     */
    @Test
    fun theSystemListsTheWidgetAsAnInstalledProvider() {
        val component = ComponentName(context, SpendingWidgetProvider::class.java)
        val installed = AppWidgetManager.getInstance(context).installedProviders
        val mine = installed.firstOrNull { it.provider == component }
        assertNotNull(
            "AppWidgetManager does not list $component, so no widget picker can offer it. " +
                "Registered providers: " + installed.joinToString { it.provider.flattenToShortString() },
            mine
        )
        // The values below come from parsing res/xml/spending_widget_info.xml, so a
        // malformed attribute surfaces here rather than as an invisible widget.
        assertTrue("minWidth must be positive", mine!!.minWidth > 0)
        assertTrue("minHeight must be positive", mine.minHeight > 0)
        assertTrue("initialLayout must resolve", mine.initialLayout != 0)
        assertTrue("the widget must be resizeable", mine.resizeMode != 0)
        assertEquals("home_screen widgets only", AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            mine.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        // A provider whose minimum does not fit the launcher grid is not offered
        // at all. Requiring the minimum to fit in half the screen holds whether the
        // framework reports dp or px, and rules out the 180dp minimum this used to
        // ship with on a 320dp-wide launcher.
        val screenWidth = context.resources.displayMetrics.widthPixels
        assertTrue(
            "minWidth ${mine.minWidth} must fit within half of ${screenWidth}px",
            mine.minWidth * 2 <= screenWidth
        )
    }

    @Test
    fun widgetPlacementIsReportedHonestly() {
        // Whether this launcher can pin a widget is a system answer, not a guess:
        // the button must offer the in-app path only when the launcher supports it
        // and otherwise fall back to written steps.
        assertEquals(
            "isSupported must report the launcher's own answer",
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported,
            WidgetPin.isSupported(context)
        )
        assertTrue("the fallback instructions must say where to add it", WidgetPin.manualInstructions.contains("Widgets"))
        // No widget is placed during an instrumented run, and asking for a redraw
        // must stay a no-op rather than throwing.
        assertEquals(0, SpendingWidgetProvider.widgetIds(context).size)
        SpendingWidgetProvider.requestUpdate(context)
    }

    @Test
    fun theProviderRendersWithoutPlacedWidgetsOrADatabase() {
        // onUpdate must never block on IO: with no widget ids it is a no-op, and
        // requestUpdate must also survive having nothing to draw.
        SpendingWidgetProvider().onUpdate(context, AppWidgetManager.getInstance(context), IntArray(0))
        SpendingWidgetProvider.requestUpdate(context)
        WidgetSnapshotStore.clear(context)
        SpendingWidgetProvider.requestUpdate(context)
    }

    @Test
    fun snapshotSurvivesAProcessFreeReload() {
        WidgetSnapshotStore.clear(context)
        assertEquals(null, WidgetSnapshotStore.load(context))
        WidgetSnapshotStore.save(
            context,
            WidgetSnapshot(balance = "120.5", balanceCurrency = "ILS", rateBase = "ILS", rateTarget = "EUR", rate = "0.25", updatedAt = 1234L)
        )
        val loaded = WidgetSnapshotStore.load(context)!!
        assertEquals("120.5", loaded.balance)
        assertEquals("EUR", loaded.rateTarget)
        assertEquals(1234L, loaded.updatedAt)
        assertEquals("1 ILS = 0.25 EUR", WidgetText.rateLine(loaded))
        WidgetSnapshotStore.clear(context)
    }

    @Test
    fun theEntryExtraTheWidgetSendsIsTheOneTheAppReads() {
        // The widget, MainActivity and the nav graph all read this constant.
        assertEquals("com.spendingtracker.app.action.ADD_EXPENSE", AppEntry.ACTION_ADD_EXPENSE)
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), 0
        )
        assertEquals(
            "singleTop is what lets the widget reuse the running task",
            ActivityInfo.LAUNCH_SINGLE_TOP,
            info.launchMode
        )
    }
}
