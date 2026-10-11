package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Milliseconds in one day. */
const val DAY_MS: Long = 24L * 60L * 60L * 1000L

/** One training day of the activity grid. */
data class ActivityDay(
    /** Local midnight of that day. */
    val epochDayStart: Long,
    val minutes: Int,
    val volume: Double,
    val setCount: Int
)

/**
 * A single column of the activity grid: the seven days of one week, Monday first.
 */
data class ActivityWeek(
    val mondayStart: Long,
    /** The local midnight of each day, Monday to Sunday. */
    val days: List<Long>
) {
    /** Local midnight of a weekday, 0 = Monday. */
    fun dayAt(weekdayIndex: Int): Long = days[weekdayIndex.coerceIn(0, 6)]

    /** True when [dayStart] is inside this week. */
    fun contains(dayStart: Long): Boolean = dayStart >= mondayStart && dayStart < mondayStart + 7 * DAY_MS
}

/**
 * Activity heat map maths.
 *
 * Everything here is pure so the grid, the month labels and the colours can be verified without a
 * device: the previous version of this screen looked correct but never lit up, because the colours
 * were derived from a duration that is often still zero right after saving a workout.
 */
object ActivityHeatmap {

    /** A day is fully vivid at 90 minutes of training. */
    const val SATURATION_MINUTES = 90

    /** Lifted volume (kg) that counts as fully vivid in the "Volume" mode. */
    const val SATURATION_VOLUME = 20_000.0

    /**
     * Any day with logged sets keeps at least this much colour.
     *
     * A session saved in a few seconds has no measurable duration and a bodyweight session has no
     * volume, but the athlete did train: the cell must still show it.
     */
    const val MIN_VISIBLE_INTENSITY = 0.22f

    /** Number of weeks shown, i.e. a full year of history. */
    const val WEEK_COUNT = 53

    /** Local midnight of [timestamp]. */
    fun startOfDay(timestamp: Long): Long = timestamp - Math.floorMod(timestamp, DAY_MS)

    /**
     * How vivid a day is, from 0 to 1.
     *
     * @param setCount logged sets of that day; a trained day never renders as empty.
     */
    fun intensity(minutes: Int, volume: Double, setCount: Int, useVolume: Boolean): Float {
        if (setCount <= 0) return 0f
        val raw = if (useVolume) {
            (volume / SATURATION_VOLUME).toFloat()
        } else {
            minutes.toFloat() / SATURATION_MINUTES.toFloat()
        }
        return raw.coerceIn(0f, 1f).coerceAtLeast(MIN_VISIBLE_INTENSITY)
    }

    /** ISO weekday of a timestamp: 1 = Monday … 7 = Sunday. */
    fun isoDayOfWeek(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val raw = cal.get(Calendar.DAY_OF_WEEK)
        return if (raw == Calendar.SUNDAY) 7 else raw - 1
    }

    /** Local midnight of the Monday of the week that contains [dayStart]. */
    fun mondayOf(dayStart: Long): Long = dayStart - (isoDayOfWeek(dayStart) - 1) * DAY_MS

    /**
     * The [weekCount] weeks ending with the week of [today].
     *
     * The list is ordered oldest week first, exactly like the grid renders it.
     */
    fun weeksEndingAt(today: Long, weekCount: Int = WEEK_COUNT): List<ActivityWeek> {
        val lastMonday = mondayOf(startOfDay(today))
        return (0 until weekCount).map { index ->
            val monday = lastMonday - (weekCount - 1 - index) * 7 * DAY_MS
            ActivityWeek(
                mondayStart = monday,
                days = (0 until 7).map { monday + it * DAY_MS }
            )
        }
    }

    /**
     * Month label of one column.
     *
     * A column is labelled with the month its Monday belongs to. The last column is special: if its
     * Sunday already sits in a later month, that month is used instead, so the month the athlete is
     * living in is always labelled (this is the case in the first days of a month).
     */
    fun monthLabelFor(week: ActivityWeek, isLastColumn: Boolean): String? {
        val mondayMonth = monthKey(week.mondayStart)
        val sundayMonth = monthKey(week.dayAt(6))
        val key = if (isLastColumn && sundayMonth != mondayMonth) sundayMonth else mondayMonth
        return monthFormatter().format(java.util.Date(if (isLastColumn) week.dayAt(6) else week.mondayStart))
            .takeIf { key.isNotBlank() }
    }

    /**
     * Month labels of every column, keyed by column index.
     *
     * Only the first column of each month carries a label, the rest stay blank: that is what keeps
     * the labels sitting above the right squares instead of marching evenly across the grid.
     */
    fun monthLabels(weeks: List<ActivityWeek>): Map<Int, String> {
        val labels = LinkedHashMap<Int, String>()
        var previousKey = ""
        weeks.forEachIndexed { index, week ->
            val isLast = index == weeks.lastIndex
            val anchor = if (isLast) week.dayAt(6) else week.mondayStart
            val key = monthKey(anchor)
            if (key != previousKey) {
                labels[index] = monthFormatter().format(java.util.Date(anchor))
                previousKey = key
            }
        }
        return labels
    }

    private fun monthKey(dayStart: Long): String =
        java.text.SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date(dayStart))

    private fun monthFormatter(): SimpleDateFormat =
        SimpleDateFormat("MMM", Locale.getDefault())
}
