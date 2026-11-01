package com.spendingtracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.spendingtracker.app.MainActivity
import com.spendingtracker.app.R
import com.spendingtracker.app.util.AppEntry

/**
 * Home-screen widget: total available money, one conversion rate and an
 * "add expense" button that opens the expense form directly.
 *
 * Rendering is deliberately dumb - it reads the last snapshot written by
 * [WidgetRefreshWorker] (or by the app itself) and draws it. No database access,
 * no network, no coroutine: `onUpdate` must return in milliseconds or the
 * launcher shows a blank widget.
 */
class SpendingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds)
        // First paint has no snapshot yet; fetch one in the background.
        if (appWidgetIds.any { WidgetSnapshotStore.load(context) == null }) {
            WidgetRefreshWorker.refreshNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Scheduled only while at least one widget exists - no widget, no work.
        WidgetRefreshWorker.schedulePeriodic(context)
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
        WidgetSnapshotStore.clear(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetRefreshWorker.refreshNow(context)
        }
    }

    companion object {
        /** Manual "tap to refresh" action, handled here (not a network call in onUpdate). */
        const val ACTION_REFRESH = "com.spendingtracker.app.widget.REFRESH"

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SpendingWidgetProvider::class.java))

        /** Redraws every placed widget from the stored snapshot. */
        fun requestUpdate(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            render(context, AppWidgetManager.getInstance(context), ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val snapshot = WidgetSnapshotStore.load(context)
            val views = RemoteViews(context.packageName, R.layout.widget_spending)

            if (snapshot == null) {
                views.setTextViewText(R.id.widget_balance, context.getString(R.string.widget_placeholder_balance))
                views.setTextViewText(R.id.widget_rate, context.getString(R.string.widget_placeholder_rate))
                views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_placeholder_updated))
            } else {
                views.setTextViewText(R.id.widget_balance, WidgetText.balanceLine(snapshot))
                views.setTextViewText(R.id.widget_rate, WidgetText.rateLine(snapshot))
                views.setTextViewText(
                    R.id.widget_updated,
                    WidgetText.updatedLine(snapshot, System.currentTimeMillis())
                )
            }

            views.setOnClickPendingIntent(R.id.widget_add_expense, addExpenseIntent(context))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            manager.updateAppWidget(ids, views)
        }

        private fun addExpenseIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                1001,
                Intent(context, MainActivity::class.java)
                    .setAction(AppEntry.ACTION_ADD_EXPENSE)
                    .putExtra(AppEntry.EXTRA_ACTION, AppEntry.ACTION_ADD_EXPENSE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                1002,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
