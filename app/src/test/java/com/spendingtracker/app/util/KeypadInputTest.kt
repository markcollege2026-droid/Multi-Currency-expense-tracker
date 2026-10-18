package com.spendingtracker.app.util

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hardware keypad helpers. These are plain JVM tests: KeyEvent key codes are
 * compile-time constants, so no Android runtime is required.
 */
class KeypadInputTest {

    @Test
    fun digitKeysProduceDigits() {
        assertEquals('0', KeypadInput.digitFor(KeyEvent.KEYCODE_0))
        assertEquals('7', KeypadInput.digitFor(KeyEvent.KEYCODE_7))
        assertEquals('9', KeypadInput.digitFor(KeyEvent.KEYCODE_NUMPAD_9))
        assertNull(KeypadInput.digitFor(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun typingDigitsBuildsAmount() {
        var value = ""
        listOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3).forEach {
            value = KeypadInput.append(value, it)!!
        }
        assertEquals("123", value)
    }

    @Test
    fun leadingZeroIsReplaced() {
        val value = KeypadInput.append("0", KeyEvent.KEYCODE_5)
        assertEquals("5", value)
    }

    @Test
    fun oneDecimalPointOnly() {
        var value = KeypadInput.append("12", KeyEvent.KEYCODE_PERIOD)!!
        assertEquals("12.", value)
        value = KeypadInput.append(value, KeyEvent.KEYCODE_NUMPAD_5)!!
        assertEquals("12.5", value)
        assertNull(KeypadInput.append(value, KeyEvent.KEYCODE_COMMA))
    }

    @Test
    fun commaKeyBecomesDecimalPoint() {
        // Some layouts report the decimal key as a comma; amounts stay parseable.
        assertEquals("0.", KeypadInput.append("", KeyEvent.KEYCODE_COMMA))
        assertEquals("3.", KeypadInput.append("3", KeyEvent.KEYCODE_NUMPAD_COMMA))
    }

    @Test
    fun decimalRejectedWhenNotAllowed() {
        assertNull(KeypadInput.append("12", KeyEvent.KEYCODE_PERIOD, allowDecimal = false))
    }

    @Test
    fun negativeOnlyWhenAllowed() {
        assertNull(KeypadInput.append("12", KeyEvent.KEYCODE_MINUS))
        assertEquals("-", KeypadInput.append("", KeyEvent.KEYCODE_MINUS, allowNegative = true))
        assertEquals("-5", KeypadInput.append("5", KeyEvent.KEYCODE_MINUS, allowNegative = true))
        assertNull(KeypadInput.append("-5", KeyEvent.KEYCODE_MINUS, allowNegative = true))
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        assertNull(KeypadInput.append("12", KeyEvent.KEYCODE_ENTER))
        assertNull(KeypadInput.append("12", KeyEvent.KEYCODE_A))
    }

    @Test
    fun digitAfterMinusKeepsSign() {
        assertEquals("-7", KeypadInput.append("-", KeyEvent.KEYCODE_7))
    }
}
