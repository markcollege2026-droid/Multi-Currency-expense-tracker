package com.spendingtracker.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

class RecurringPaymentTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun partsOf(millis: Long): IntArray {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return intArrayOf(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE)
        )
    }

    @Test
    fun nextAfterDailyAdvancesOneDay() {
        val start = at(2026, Calendar.MAY, 10)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_DAILY)
        assertArrayEquals(intArrayOf(2026, Calendar.MAY, 11, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterWeeklyAdvancesSevenDays() {
        val start = at(2026, Calendar.MAY, 10)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_WEEKLY)
        assertArrayEquals(intArrayOf(2026, Calendar.MAY, 17, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterMonthlyKeepsDayAndTime() {
        val start = at(2026, Calendar.MAY, 15)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_MONTHLY)
        assertArrayEquals(intArrayOf(2026, Calendar.JUNE, 15, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterMonthlyClampsEndOfMonth() {
        // Jan 31 + 1 month clamps to the last day of February.
        val start = at(2026, Calendar.JANUARY, 31)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_MONTHLY)
        assertArrayEquals(intArrayOf(2026, Calendar.FEBRUARY, 28, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterMonthlyClampsEndOfLeapFebruary() {
        val start = at(2024, Calendar.JANUARY, 31)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_MONTHLY)
        assertArrayEquals(intArrayOf(2024, Calendar.FEBRUARY, 29, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterYearlyKeepsDate() {
        val start = at(2026, Calendar.MAY, 10)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_YEARLY)
        assertArrayEquals(intArrayOf(2027, Calendar.MAY, 10, 9, 30), partsOf(next))
    }

    @Test
    fun nextAfterYearlyFromLeapDayClamps() {
        val start = at(2024, Calendar.FEBRUARY, 29)
        val next = RecurringPayment.nextAfter(start, RecurringPayment.FREQ_YEARLY)
        assertArrayEquals(intArrayOf(2025, Calendar.FEBRUARY, 28, 9, 30), partsOf(next))
    }

    @Test
    fun frequenciesCoverAllSupportedSchedules() {
        assertEquals(
            listOf(
                RecurringPayment.FREQ_DAILY,
                RecurringPayment.FREQ_WEEKLY,
                RecurringPayment.FREQ_MONTHLY,
                RecurringPayment.FREQ_YEARLY
            ),
            RecurringPayment.FREQUENCIES
        )
    }

    @Test
    fun amountParsesPlainDecimal() {
        val payment = recurringPayment(amountOriginal = "12.50")
        assertEquals(BigDecimal("12.50"), payment.amountAsBigDecimal())
    }

    @Test
    fun amountFallsBackToZeroOnGarbage() {
        val payment = recurringPayment(amountOriginal = "not-a-number")
        assertEquals(BigDecimal.ZERO, payment.amountAsBigDecimal())
    }

    private fun recurringPayment(amountOriginal: String) = RecurringPayment(
        accountId = 1,
        title = "Test",
        amountOriginal = amountOriginal,
        currencyOriginal = "USD",
        frequency = RecurringPayment.FREQ_MONTHLY,
        startDate = at(2026, Calendar.MAY, 1),
        nextDueAt = at(2026, Calendar.MAY, 1)
    )

    private fun assertArrayEquals(expected: IntArray, actual: IntArray) {
        assertTrue(
            "expected ${expected.toList()} but was ${actual.toList()}",
            expected.contentEquals(actual)
        )
    }
}
