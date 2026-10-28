package com.spendingtracker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The one amount-text model: typing is sanitised at the field, parsing is
 * tolerant of grouping and of either decimal separator.
 */
class AmountInputTest {

    private fun assertMoney(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(requireNotNull(actual)))

    // ----- typing -----

    @Test fun secondDecimalPointCannotBeTyped() {
        assertEquals("12.56", AmountInput.sanitizeTyped("12.5.6"))
        assertEquals("12.5", AmountInput.sanitizeTyped("12..5"))
        assertEquals("12.5", AmountInput.sanitize(previous = "12.5", candidate = "12.5."))
    }

    @Test fun commaIsTheDecimalSeparator() {
        assertEquals("1.5", AmountInput.sanitizeTyped("1,5"))
        // One separator total, whichever key produced it.
        assertEquals("1.25", AmountInput.sanitizeTyped("1,2.5"))
    }

    @Test fun junkNeverEntersTheField() {
        assertEquals("", AmountInput.sanitizeTyped("abc"))
        assertEquals("12", AmountInput.sanitizeTyped("1 2"))
        assertEquals("12.5", AmountInput.sanitizeTyped("\$12.5"))
        assertEquals("5", AmountInput.sanitizeTyped("-5", allowNegative = false))
        assertEquals("-5", AmountInput.sanitizeTyped("-5", allowNegative = true))
        assertEquals("125", AmountInput.sanitizeTyped("12.5", allowDecimal = false))
    }

    @Test fun pastedGroupedAmountsAreNormalised() {
        assertEquals("1234.56", AmountInput.sanitize(previous = "", candidate = "1,234.56"))
        assertEquals("1234.56", AmountInput.sanitize(previous = "", candidate = "1.234,56"))
        assertEquals("12.50", AmountInput.sanitize(previous = "", candidate = "12.50"))
    }

    @Test fun ordinaryTypingIsUntouched() {
        assertEquals("12.", AmountInput.sanitize(previous = "12", candidate = "12."))
        assertEquals("12.5", AmountInput.sanitize(previous = "12.", candidate = "12.5"))
        assertEquals(".5", AmountInput.sanitize(previous = ".", candidate = ".5"))
    }

    // ----- parsing -----

    @Test fun parsesBothSeparatorConventions() {
        assertMoney("1234.56", AmountInput.parseOrNull("1,234.56"))
        assertMoney("1234.56", AmountInput.parseOrNull("1.234,56"))
        assertMoney("1.5", AmountInput.parseOrNull("1,5"))
        assertMoney("12.5", AmountInput.parseOrNull("12.5"))
    }

    @Test fun parsesGroupingAndSpaces() {
        assertMoney("1234567", AmountInput.parseOrNull("1,234,567"))
        assertMoney("1234567", AmountInput.parseOrNull("1.234.567"))
        assertMoney("1234.5", AmountInput.parseOrNull("1 234,5"))
        assertMoney("1234.5", AmountInput.parseOrNull("1\u00A0234.5"))
    }

    @Test fun parsesTransientShapesAndRejectsJunk() {
        assertMoney("12", AmountInput.parseOrNull("12."))
        assertMoney("0.5", AmountInput.parseOrNull(".5"))
        assertMoney("-3.25", AmountInput.parseOrNull("-3,25"))
        assertNull(AmountInput.parseOrNull("."))
        assertNull(AmountInput.parseOrNull(""))
        assertNull(AmountInput.parseOrNull(null))
        assertNull(AmountInput.parseOrNull("abc"))
        // Arabic-Indic digits from some keyboards still mean 12.5.
        assertMoney("12.5", AmountInput.parseOrNull("\u0661\u0662.\u0665"))
    }

    @Test fun incompleteStatesAreNamed() {
        assertTrue(AmountInput.isIncomplete(""))
        assertTrue(AmountInput.isIncomplete("."))
        assertTrue(AmountInput.isIncomplete("-"))
        assertTrue(AmountInput.isIncomplete("-."))
        assertFalse(AmountInput.isIncomplete("12."))
        assertFalse(AmountInput.isIncomplete("0"))
        assertFalse(AmountInput.isIncomplete("12.5"))
    }
}
