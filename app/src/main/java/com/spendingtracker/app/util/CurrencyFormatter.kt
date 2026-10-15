package com.spendingtracker.app.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

    // NumberFormat is relatively expensive to create and is not thread-safe;
    // cache one instance per (currency, locale) per thread for smooth list scrolling.
    private val formatterCache = ThreadLocal<MutableMap<String, NumberFormat>>()

    private fun cachedFormatter(currencyCode: String, locale: Locale): NumberFormat? {
        return try {
            val key = "$currencyCode|${locale.toLanguageTag()}"
            val cache = formatterCache.get()
                ?: mutableMapOf<String, NumberFormat>().also { formatterCache.set(it) }
            cache.getOrPut(key) {
                val currency = Currency.getInstance(currencyCode)
                NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun format(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        return try {
            val formatter = cachedFormatter(currencyCode, locale)
            if (formatter != null) {
                formatter.format(amount)
            } else {
                // Fallback
                val symbol = CurrencyProvider.getSymbol(currencyCode)
                "$symbol${amount.toPlainString()}"
            }
        } catch (e: Exception) {
            // Fallback
            val symbol = CurrencyProvider.getSymbol(currencyCode)
            "$symbol${amount.toPlainString()}"
        }
    }

    fun formatWithSymbol(amount: BigDecimal, currencyCode: String): String {
        val symbol = CurrencyProvider.getSymbol(currencyCode)
        return "$symbol${amount.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()} $currencyCode"
    }

    fun formatSimple(amount: BigDecimal, currencyCode: String): String {
        val symbol = CurrencyProvider.getSymbol(currencyCode)
        return try {
            "$symbol${amount.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()}"
        } catch (e: Exception) {
            "$symbol${amount.toPlainString()}"
        }
    }
}
