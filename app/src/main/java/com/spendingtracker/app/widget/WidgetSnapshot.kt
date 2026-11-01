package com.spendingtracker.app.widget

import android.content.Context
import android.content.SharedPreferences
import java.math.BigDecimal

/**
 * What the home-screen widget shows, and the tiny store that keeps it.
 *
 * The widget never reads the database or the network itself: it renders this
 * snapshot from SharedPreferences. A small unique worker refreshes it (12 hours,
 * Wi-Fi/mobile required) and the app pushes a fresh one whenever it is opened,
 * so the widget costs no wake-locks and cannot hang the launcher.
 */
data class WidgetSnapshot(
    /** Total available money of the active ledger, in [balanceCurrency]. */
    val balance: String,
    val balanceCurrency: String,
    val rateBase: String,
    val rateTarget: String,
    /** 1 [rateBase] in [rateTarget]; null when no quote is known on this device. */
    val rate: String?,
    val updatedAt: Long
)

/** Pure formatting so the widget text can be unit-tested without Android. */
object WidgetText {

    fun balanceLine(snapshot: WidgetSnapshot): String =
        com.spendingtracker.app.util.CurrencyFormatter.formatSimple(
            snapshot.balance.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO,
            snapshot.balanceCurrency
        ) + " available"

    fun rateLine(snapshot: WidgetSnapshot): String {
        val rate = snapshot.rate?.toBigDecimalOrNullSafe()
        if (rate == null || rate <= BigDecimal.ZERO) {
            return "No ${snapshot.rateBase} \u2192 ${snapshot.rateTarget} rate"
        }
        return "1 ${snapshot.rateBase} = ${rate.stripTrailingZeros().toPlainString()} ${snapshot.rateTarget}"
    }

    /**
     * Age of the snapshot in words. Kept coarse on purpose: the widget is not a
     * ticker, and "3h ago" is all a user needs to judge whether a rate is stale.
     */
    fun updatedLine(snapshot: WidgetSnapshot, now: Long): String {
        val age = (now - snapshot.updatedAt).coerceAtLeast(0)
        val minutes = age / 60_000
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated ${minutes}m ago"
            minutes < 60 * 24 -> "Updated ${minutes / 60}h ago"
            else -> "Updated ${minutes / (60 * 24)}d ago"
        }
    }

    /**
     * Currency the rate line converts into. The user's favourite currencies are
     * the only per-widget preference the app already stores, so the first
     * favourite that is not the ledger currency is used; otherwise a common
     * counterpart (USD, or EUR for a USD ledger).
     */
    fun targetCurrency(favorites: List<String>, base: String): String =
        favorites.firstOrNull { it.isNotBlank() && it != base }
            ?: if (base == "USD") "EUR" else "USD"

    private fun String?.toBigDecimalOrNullSafe(): BigDecimal? = this?.let {
        try { BigDecimal(it) } catch (e: Exception) { null }
    }
}

/** Snapshot persistence. Deliberately not Room: the widget reads it on the main thread. */
object WidgetSnapshotStore {
    private const val PREFS = "spending_widget"
    private const val KEY_BALANCE = "balance"
    private const val KEY_CURRENCY = "currency"
    private const val KEY_BASE = "rateBase"
    private const val KEY_TARGET = "rateTarget"
    private const val KEY_RATE = "rate"
    private const val KEY_UPDATED = "updatedAt"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, snapshot: WidgetSnapshot) {
        prefs(context).edit()
            .putString(KEY_BALANCE, snapshot.balance)
            .putString(KEY_CURRENCY, snapshot.balanceCurrency)
            .putString(KEY_BASE, snapshot.rateBase)
            .putString(KEY_TARGET, snapshot.rateTarget)
            .putString(KEY_RATE, snapshot.rate)
            .putLong(KEY_UPDATED, snapshot.updatedAt)
            .apply()
    }

    fun load(context: Context): WidgetSnapshot? {
        val prefs = prefs(context)
        val balance = prefs.getString(KEY_BALANCE, null) ?: return null
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        if (updated == 0L) return null
        return WidgetSnapshot(
            balance = balance,
            balanceCurrency = prefs.getString(KEY_CURRENCY, "USD") ?: "USD",
            rateBase = prefs.getString(KEY_BASE, "USD") ?: "USD",
            rateTarget = prefs.getString(KEY_TARGET, "EUR") ?: "EUR",
            rate = prefs.getString(KEY_RATE, null),
            updatedAt = updated
        )
    }

    /**
     * Remembers which currency the rate line converts into, so a refreshed
     * favourite list does not make the widget jump between currencies.
     */
    fun rememberTarget(context: Context, target: String) {
        prefs(context).edit().putString(KEY_TARGET, target).apply()
    }

    fun savedTarget(context: Context): String? = prefs(context).getString(KEY_TARGET, null)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
