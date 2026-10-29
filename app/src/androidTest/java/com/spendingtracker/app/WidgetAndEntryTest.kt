package com.spendingtracker.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendingtracker.app.util.AppEntry
import com.spendingtracker.app.widget.SpendingWidgetProvider
import com.spendingtracker.app.widget.WidgetSnapshot
import com.spendingtracker.app.widget.WidgetSnapshotStore
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
