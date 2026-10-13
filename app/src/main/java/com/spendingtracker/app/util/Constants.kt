package com.spendingtracker.app.util

object Constants {
    const val DATABASE_NAME = "spending_tracker.db"
    const val EXCHANGE_RATE_BASE_URL = "https://open.er-api.com/"
    const val RATE_REFRESH_INTERVAL_HOURS = 12
    const val NOTIFICATION_CHANNEL_BALANCE = "balance_channel"
    const val NOTIFICATION_CHANNEL_LOANS = "loans_channel"
    const val NOTIFICATION_ID_LOW_BALANCE = 1001
    const val NOTIFICATION_ID_LOAN_DUE = 1002
}
