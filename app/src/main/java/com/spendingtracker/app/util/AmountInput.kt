package com.spendingtracker.app.util

import java.math.BigDecimal

/**
 * One amount-text model for every money field in the app.
 *
 * Before this existed, each screen parsed with `replace(',', '.')` + BigDecimal
 * and nothing filtered what the soft keyboard produced: a second separator
 * ("12.5.0"), a thousands group ("1,234.56" -> "1.234.56") or a stray symbol
 * made the amount unparseable and silently disabled Save. The converter even
 * treated ',' as a grouping character while expenses treated it as the decimal
 * separator, so the same text meant different money on different screens.
 *
 * The rules now:
 * - While typing, [sanitize] keeps the text canonical: digits, at most one
 *   decimal separator (always shown as '.'), an optional leading '-'. Nothing
 *   else can enter an amount field, whichever keyboard produced it.
 * - A multi-character jump (paste, autocomplete) is normalised tolerantly via
 *   [parseOrNull], so "1,234.56" and "1.234,56" both become "1234.56".
 * - [parseOrNull] is the one parser: last separator wins when both '.' and ','
 *   appear, thousands groups are removed, spaces (incl. NBSP) and non-ASCII
 *   digits/separators are understood.
 * - [isIncomplete] names the transient states ("", ".", "-", "-.") that are
 *   not yet numbers but must not be painted as errors while typing.
 */
object AmountInput {

    /**
     * Cleans [candidate] (the raw text a field wants to show) given what the
     * field showed before. Single-character edits keep the first separator and
     * drop anything invalid; larger jumps are treated as pasted text and are
     * normalised through [parseOrNull].
     */
    fun sanitize(
        previous: String,
        candidate: String,
        allowDecimal: Boolean = true,
        allowNegative: Boolean = false
    ): String {
        if (candidate.length - previous.length > 1) {
            val parsed = parseOrNull(candidate)
            if (parsed != null && (allowNegative || parsed.signum() >= 0)) {
                return if (allowDecimal) parsed.toPlainString() else parsed.toBigInteger().toString()
            }
        }
        return sanitizeTyped(candidate, allowDecimal, allowNegative)
    }

    /** Character-by-character filter used for ordinary typing. */
    fun sanitizeTyped(
        candidate: String,
        allowDecimal: Boolean = true,
        allowNegative: Boolean = false
    ): String {
        val out = StringBuilder()
        var separatorSeen = false
        for (raw in candidate) {
            val c = normalizeChar(raw)
            when {
                c in '0'..'9' -> out.append(c)
                (c == '.' || c == ',') && allowDecimal -> {
                    if (!separatorSeen) {
                        out.append('.')
                        separatorSeen = true
                    }
                }
                c == '-' && allowNegative && out.isEmpty() -> out.append('-')
                else -> Unit // drop spaces, symbols, extra separators
            }
        }
        return out.toString()
    }

    /** True for text that is on its way to being an amount but is not one yet. */
    fun isIncomplete(text: String): Boolean =
        text.trim() in setOf("", "-", ".", "-.", ",", "-,")

    /** The one tolerant amount parser. Null when the text is not an amount. */
    fun parseOrNull(text: String?): BigDecimal? {
        if (text.isNullOrBlank()) return null
        var cleaned = text.map(::normalizeChar)
            .filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
            .joinToString("")
        if (cleaned.isEmpty()) return null

        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        cleaned = when {
            lastDot >= 0 && lastComma >= 0 -> {
                // Both kinds present: whichever comes last is the decimal point,
                // the other one is thousands grouping.
                if (lastDot > lastComma) cleaned.replace(",", "")
                else cleaned.replace(".", "").replace(',', '.')
            }
            lastComma >= 0 -> resolveRepeated(cleaned, ',')
            lastDot >= 0 -> resolveRepeated(cleaned, '.')
            else -> cleaned
        }
        // After resolution at most one '.' may remain; if a second survived a
        // grouping check (e.g. "1.234.56"), the last one is the decimal point.
        val firstDot = cleaned.indexOf('.')
        val finalDot = cleaned.lastIndexOf('.')
        if (firstDot >= 0 && finalDot > firstDot) {
            cleaned = cleaned.replaceRange(firstDot, finalDot, cleaned.substring(firstDot, finalDot).replace(".", ""))
        }
        return try {
            BigDecimal(cleaned)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A single kind of separator used more than once is either thousands
     * grouping ("1,234,567" / "1.234.567": every group after the first has
     * exactly three digits) or sloppy input, where the last one is treated as
     * the decimal separator.
     */
    private fun resolveRepeated(text: String, separator: Char): String {
        if (text.count { it == separator } <= 1) {
            return if (separator == ',') text.replace(',', '.') else text
        }
        val unsigned = text.removePrefix("-")
        val groups = unsigned.split(separator)
        val isGrouping = groups.size > 1 && groups.first().isNotEmpty() &&
            groups.drop(1).all { it.length == 3 && it.all { c -> c.isDigit() } }
        return if (isGrouping) {
            text.replace(separator.toString(), "")
        } else {
            val last = text.lastIndexOf(separator)
            text.substring(0, last).replace(separator.toString(), "") + "." + text.substring(last + 1)
        }
    }

    /** Maps keyboard/locale variants onto ASCII: other digit sets, full-width and Arabic separators. */
    private fun normalizeChar(c: Char): Char = when (c) {
        '\uFF0E', '\u066B' -> '.'
        '\uFF0C' -> ','
        '\u2212' -> '-'
        in '0'..'9' -> c
        else -> {
            val digit = Character.digit(c, 10)
            if (digit >= 0) '0' + digit else c
        }
    }
}

/** Convenience so call sites read like the [String.toBigDecimalOrNull] they replace. */
fun String.parseAmountOrNull(): BigDecimal? = AmountInput.parseOrNull(this)
