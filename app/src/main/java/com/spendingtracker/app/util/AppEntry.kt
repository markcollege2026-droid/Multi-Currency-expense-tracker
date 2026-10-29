package com.spendingtracker.app.util

/**
 * Way the app was entered from outside itself (the home-screen widget today).
 *
 * Kept in `util` so the widget, `MainActivity` and the navigation graph share one
 * definition instead of the widget importing an Activity constant.
 */
object AppEntry {
    /** `Intent` extra telling the app which screen to open. */
    const val EXTRA_ACTION = "com.spendingtracker.app.extra.ACTION"

    /** Open the Add-expense form as soon as the ledger is ready. */
    const val ACTION_ADD_EXPENSE = "com.spendingtracker.app.action.ADD_EXPENSE"
}
