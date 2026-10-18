package com.spendingtracker.app.util

import android.view.KeyEvent

/**
 * Pure numeric-key helpers for hardware keypads such as the Qin F21 Pro.
 *
 * The keypad emits ordinary Android number key events. Decimal points are not
 * guaranteed: the '.' key may be KEYCODE_PERIOD, KEYCODE_NUMPAD_DOT or, on some
 * layouts, a comma. All are accepted for amount fields.
 */
object KeypadInput {

    fun digitFor(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> '0'
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> '1'
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> '2'
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> '3'
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> '4'
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> '5'
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> '6'
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> '7'
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> '8'
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> '9'
        else -> null
    }

    fun isDecimalKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_PERIOD ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_DOT ||
        keyCode == KeyEvent.KEYCODE_COMMA ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_COMMA

    fun isMinusKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_MINUS ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT

    /**
     * Appends a hardware-key character to a numeric field while keeping the text
     * parseable by BigDecimal. Commas are normalised to '.' so both 1,5 and 1.5
     * hardware input produce 1.5.
     */
    fun append(
        current: String,
        keyCode: Int,
        allowDecimal: Boolean = true,
        allowNegative: Boolean = false
    ): String? {
        val digit = digitFor(keyCode)
        if (digit != null) {
            return when (current) {
                "" -> digit.toString()
                "-" -> "-$digit"
                "0" -> digit.toString()
                "-0" -> "-$digit"
                else -> current + digit
            }
        }
        if (allowDecimal && isDecimalKey(keyCode)) {
            if (current.contains('.')) return null
            return when (current) {
                "" -> "0."
                "-" -> "-0."
                else -> "$current."
            }
        }
        if (allowNegative && isMinusKey(keyCode)) {
            if (current.startsWith("-")) return null
            return if (current.isEmpty()) "-" else "-$current"
        }
        return null
    }
}
