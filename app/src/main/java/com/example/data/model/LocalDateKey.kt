/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

import java.util.Calendar

/**
 * A timezone-stable local calendar date, encoded as `yyyyMMdd` in a Long.
 *
 * A streak is about the athlete's calendar, not about a UTC instant. Storing only a local midnight
 * epoch is not enough: if the athlete flies from Berlin to New York, that epoch can resolve to the
 * neighbouring date. The key is derived from the calendar fields and therefore survives timezone
 * changes, DST and clock changes without moving a planned workout to another day.
 */
object LocalDateKey {

    /** The key for the local date containing [timestamp], or 0 for a missing timestamp. */
    fun keyOf(timestamp: Long): Long {
        if (timestamp <= 0L) return 0L
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return keyOf(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun keyOf(year: Int, month: Int, day: Int): Long =
        year * 10_000L + month * 100L + day

    /** Local midnight of [key] in the device's current timezone. */
    fun timestampOf(key: Long): Long {
        if (key <= 0L) return 0L
        val year = (key / 10_000L).toInt()
        val month = ((key / 100L) % 100L).toInt()
        val day = (key % 100L).toInt()
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis
    }

    /** Moves [key] by [days] calendar days, staying on the same wall-clock date. */
    fun plusDays(key: Long, days: Int): Long {
        if (key <= 0L) return 0L
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestampOf(key)
            add(Calendar.DAY_OF_MONTH, days)
        }
        return keyOf(calendar.timeInMillis)
    }

    /** ISO weekday of [key]: 1 = Monday ... 7 = Sunday. */
    fun isoDayOfWeek(key: Long): Int = ActivityHeatmap.isoDayOfWeek(timestampOf(key))

    /** The first day of the calendar week containing [key]. */
    fun startOfWeekKey(key: Long, weekStartsOnMonday: Boolean): Long {
        val iso = isoDayOfWeek(key)
        val offset = if (weekStartsOnMonday) iso - 1 else iso % 7
        return plusDays(key, -offset)
    }
}
