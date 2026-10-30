package com.spendingtracker.app.domain

import java.math.BigDecimal

/**
 * The rate a loan settlement must apply, and whether one is genuinely needed.
 *
 * Shared by every path that records a repayment so the "refuse instead of
 * converting 1:1" rule cannot drift: the dialog refuses to confirm without a rate
 * ([com.spendingtracker.app.ui.screens.loans.settlementFormState]), and the view
 * models used to undo that guarantee by falling back to `BigDecimal.ONE` when they
 * parsed the rate themselves.
 *
 * External settlements (no balance) and same-currency settlements need no rate -
 * one unit in equals one unit out in that case, which is not a conversion.
 */
object SettlementRate {

    /** True only when the loan and the balance it moves through use different currencies. */
    fun required(loanCurrency: String, accountCurrency: String?): Boolean =
        accountCurrency != null && !accountCurrency.equals(loanCurrency, ignoreCase = true)

    /**
     * The rate to record with the payment, or null when one is required but missing.
     *
     * @param rate the rate the UI obtained for the pair; null, blank, zero, negative
     *   or unparseable all mean "no rate".
     */
    fun resolve(loanCurrency: String, accountCurrency: String?, rate: String?): BigDecimal? {
        if (!required(loanCurrency, accountCurrency)) return BigDecimal.ONE
        val parsed = rate?.trim()?.toBigDecimalOrNull() ?: return null
        return parsed.takeIf { it > BigDecimal.ZERO }
    }

    /** Message shown when [resolve] returns null; kept here so every path says the same thing. */
    fun missingRateMessage(loanCurrency: String, accountCurrency: String?): String =
        "No exchange rate for " + loanCurrency + " \u2192 " + (accountCurrency ?: loanCurrency) +
            ". Refresh rates in Settings."
}
