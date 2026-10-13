package com.spendingtracker.app.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {
    fun format(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        return try {
            val currency = Currency.getInstance(currencyCode)
            val formatter = NumberFormat.getCurrencyInstance(locale)
            formatter.currency = currency
            // Ensure BigDecimal is used
            formatter.format(amount)
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
