package com.spendingtracker.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class DateInputTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun validDateBecomesEndOfDay() {
        val millis = DateInput.endOfDayMillis("5", "3", "2026", zone)
        assertNotNull(millis)
        val parts = DateInput.partsFrom(millis!!, java.util.TimeZone.getTimeZone(zone))
        assertEquals("5", parts.day)
        assertEquals("3", parts.month)
        assertEquals("2026", parts.year)
    }

    @Test
    fun impossibleDateIsRejected() {
        assertNull(DateInput.endOfDayMillis("31", "2", "2026", zone))
        assertNull(DateInput.endOfDayMillis("30", "2", "2024", zone))
    }

    @Test
    fun leapDayIsAccepted() {
        assertNotNull(DateInput.endOfDayMillis("29", "2", "2024", zone))
    }

    @Test
    fun outOfRangeYearIsRejected() {
        assertNull(DateInput.endOfDayMillis("1", "1", "1899", zone))
        assertNull(DateInput.endOfDayMillis("1", "1", "2201", zone))
    }

    @Test
    fun nonNumericAndBlankAreRejected() {
        assertNull(DateInput.endOfDayMillis("", "1", "2026", zone))
        assertNull(DateInput.endOfDayMillis("a", "1", "2026", zone))
        assertNull(DateInput.endOfDayMillis("1", "", "2026", zone))
    }

    @Test
    fun partsArePaddedOnlyForParsingNotDisplay() {
        val parts = DateInput.partsFrom(0L, java.util.TimeZone.getTimeZone("UTC"))
        assertEquals("1", parts.day)
        assertEquals("1", parts.month)
        assertEquals("1970", parts.year)
    }
}
