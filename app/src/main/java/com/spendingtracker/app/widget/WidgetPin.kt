package com.spendingtracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Places the widget from inside the app.
 *
 * Finding a widget means leaving the app, long-pressing the home screen and
 * hunting through the launcher's widget list - and on several launchers (small
 * "feature phone" builds especially) that list is hidden or does not refresh
 * until the launcher restarts, so a freshly installed widget looks missing.
 * [requestPinAppWidget] asks the launcher to offer it directly instead, and it
 * reports synchronously whether the launcher supports that.
 */
object WidgetPin {

    /** True when this launcher can be asked to place a widget on its own. */
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * Asks the launcher to pin the widget. Returns false without doing anything
     * when the launcher cannot do it, so the caller can show
     * [manualInstructions] instead of silently appearing to have worked.
     */
    fun request(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, SpendingWidgetProvider::class.java)
        // The success callback is what redraws the widget immediately; without it
        // the first paint waits for the launcher's own APPWIDGET_UPDATE.
        val callback = PendingIntent.getBroadcast(
            context,
            1003,
            Intent(context, SpendingWidgetProvider::class.java)
                .setAction(SpendingWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return manager.requestPinAppWidget(provider, null, callback)
    }

    /**
     * How to add the widget when the launcher cannot pin it for us. Kept here,
     * next to the code, so the wording is testable and cannot drift between the
     * places that explain it.
     */
    const val manualInstructions: String =
        "Add it from the home screen: long-press an empty spot, choose Widgets, " +
            "then Spending Tracker. On some launchers that list only picks up a " +
            "new widget after a restart."

    /** True when at least one copy of the widget is on a home screen right now. */
    fun isPlaced(context: Context): Boolean =
        SpendingWidgetProvider.widgetIds(context).isNotEmpty()
}
