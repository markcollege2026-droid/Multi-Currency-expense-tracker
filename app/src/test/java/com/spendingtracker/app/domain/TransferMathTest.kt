package com.spendingtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A withdrawal or deposit can be typed in either currency. The typed side is
 * stored exactly as typed and only the derived side is rounded once, so a €100
 * withdrawal does not arrive as €99.99 after a round trip through ILS.
 */
class TransferMathTest {

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals(expected, actual.setScale(2, RoundingMode.HALF_EVEN).toPlainString())

    /** 1 ILS = 0.25334 EUR - an intentionally awkward rate. */
    private val rate = BigDecimal("0.25334")

    @Test fun mainSideStaysExactAndCashSideIsDerived() {
        // ₪394.73 at 0.25334 is €100.0009 - rounded once, on the derived side only.
        val amounts = TransferMath.amountsFromSource(BigDecimal("394.73"), rate)
        assertMoney("394.73", amounts.from)
        assertMoney("100.00", amounts.to)
    }

    @Test fun cashSideStaysExactAndMainSideIsDerived() {
        val amounts = TransferMath.amountsFromTarget(BigDecimal("100"), rate)
        assertMoney("394.73", amounts.from)
        assertMoney("100.00", amounts.to)
    }

    @Test fun typedSideNeverDriftsThroughRounding() {
        // Typing the cash side gives exactly what was typed, not a round-trip value.
        val typedInCash = TransferMath.amounts(BigDecimal("100"), rate, typedInTargetCurrency = true)
        assertMoney("100.00", typedInCash.to)
        val typedInMain = TransferMath.amounts(BigDecimal("394.73"), rate, typedInTargetCurrency = false)
        assertMoney("394.73", typedInMain.from)
    }

    @Test fun amountsTooSmallAfterConversionCollapseToZero() {
        // 1 EUR at 1000 EUR per ILS is 0.001 ILS: the form rejects it rather than
        // silently moving nothing.
        val amounts = TransferMath.amountsFromTarget(BigDecimal("1"), BigDecimal("1000"))
        assertMoney("0.00", amounts.from)
    }

    @Test fun totalEffectOnlyChangesAcrossTheSetAsideBoundary() {
        assertEquals(TotalEffect.UNCHANGED, TransferMath.totalEffect(true, true))
        assertEquals(TotalEffect.UNCHANGED, TransferMath.totalEffect(false, false))
        // Counted money parked in a set-aside balance leaves the total...
        assertEquals(TotalEffect.DECREASES, TransferMath.totalEffect(true, false))
        // ...and comes back into it when transferred out again.
        assertEquals(TotalEffect.INCREASES, TransferMath.totalEffect(false, true))
    }

    @Test fun nonPositiveRateFallsBackToParity() {
        assertMoney("10.00", TransferMath.amountsFromSource(BigDecimal("10"), BigDecimal.ZERO).to)
        assertMoney("10.00", TransferMath.amountsFromTarget(BigDecimal("10"), BigDecimal("-2")).from)
    }
}
