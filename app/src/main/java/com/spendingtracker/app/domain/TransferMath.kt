package com.spendingtracker.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Both sides of a transfer between two balances, each in its own currency. */
data class TransferAmounts(val from: BigDecimal, val to: BigDecimal)

/**
 * Amounts of a transfer between two balances.
 *
 * [rate] is 1 unit of the source currency expressed in the target currency
 * (1 ILS = 0.25 EUR), so [amountTo] = [amountFrom] × rate.
 *
 * The side the user typed is kept exactly as typed; only the derived side is
 * rounded, so a withdrawal entered in the cash currency arrives at the amount
 * the user asked for instead of drifting by a cent.
 */
object TransferMath {
    const val SCALE = 2

    private fun safeRate(rate: BigDecimal): BigDecimal =
        if (rate > BigDecimal.ZERO) rate else BigDecimal.ONE

    /** [amountFrom] is in the source currency; computes the target side. */
    fun amountsFromSource(amountFrom: BigDecimal, rate: BigDecimal): TransferAmounts =
        TransferAmounts(
            from = amountFrom,
            to = amountFrom.multiply(safeRate(rate)).setScale(SCALE, RoundingMode.HALF_EVEN)
        )

    /** [amountTo] is in the target currency; computes the source side. */
    fun amountsFromTarget(amountTo: BigDecimal, rate: BigDecimal): TransferAmounts =
        TransferAmounts(
            from = amountTo.divide(safeRate(rate), SCALE, RoundingMode.HALF_EVEN),
            to = amountTo
        )

    /** Whichever side the user is typing, returns both. */
    fun amounts(typed: BigDecimal, rate: BigDecimal, typedInTargetCurrency: Boolean): TransferAmounts =
        if (typedInTargetCurrency) amountsFromTarget(typed, rate) else amountsFromSource(typed, rate)
}
