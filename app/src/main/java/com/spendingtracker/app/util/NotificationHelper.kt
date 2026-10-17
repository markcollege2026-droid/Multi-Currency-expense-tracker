package com.spendingtracker.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spendingtracker.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val balanceChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_BALANCE,
                context.getString(R.string.notification_channel_balance),
                NotificationManager.IMPORTANCE_HIGH
            )
            val loansChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_LOANS,
                context.getString(R.string.notification_channel_loans),
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannels(listOf(balanceChannel, loansChannel))
        }
    }

    fun showLowBalanceNotification(balance: String, threshold: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_BALANCE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.low_balance_warning))
            .setContentText(context.getString(R.string.low_balance_message, "$balance (threshold $threshold)"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID_LOW_BALANCE, notification)
        } catch (e: SecurityException) {
            // ignore
        }
    }

    fun showLoanDueNotification(person: String, amount: String, id: Int) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_LOANS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.due_date_reminder))
            .setContentText(context.getString(R.string.due_date_message, person, amount))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID_LOAN_DUE + id, notification)
        } catch (e: SecurityException) {
            // ignore
        }
    }

    fun showAutopayFailure(cardId: Int) {
        if (!hasPermission()) return
        val intent = android.content.Intent(context, com.spendingtracker.app.MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(context, cardId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_BALANCE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Credit card payment failed")
            .setContentText("Insufficient main/debit funds. Open Home to retry, pay partially, or ignore.")
            .setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(20000 + cardId, notification) }
        catch (_: SecurityException) { /* In-app alert remains available without permission. */ }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
