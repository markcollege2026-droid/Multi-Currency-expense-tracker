package com.spendingtracker.app.util

import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * Date helpers used by the compact date dialog. The Material date picker needs a
 * ~360dp-wide window, which does not fit the F21 Pro, so the app edits the day,
 * month and year directly and converts the chosen calendar day to local end-of-day.
 */
object DateInput {

    data class Parts(val day: String, val month: String, val year: String)

    fun partsFrom(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Parts {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return Parts(
            day = calendar.get(Calendar.DAY_OF_MONTH).toString(),
            month = (calendar.get(Calendar.MONTH) + 1).toString(),
            year = calendar.get(Calendar.YEAR).toString()
        )
    }

    /**
     * Returns local end-of-day millis for a valid calendar date, or null for
     * empty/out-of-range/impossible dates (for example 31 February).
     */
    fun endOfDayMillis(
        dayText: String,
        monthText: String,
        yearText: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val day = dayText.trim().toIntOrNull() ?: return null
        val month = monthText.trim().toIntOrNull() ?: return null
        val year = yearText.trim().toIntOrNull() ?: return null
        if (year !in 1900..2200) return null
        return try {
            LocalDate.of(year, month, day)
                .atTime(23, 59, 59)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeException) {
            null
        }
    }

    fun todayParts(zoneId: ZoneId = ZoneId.systemDefault()): Parts {
        val today = LocalDate.now(zoneId)
        return Parts(today.dayOfMonth.toString(), today.monthValue.toString(), today.year.toString())
    }
}
