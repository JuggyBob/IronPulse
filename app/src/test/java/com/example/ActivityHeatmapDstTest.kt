package com.example

import com.example.data.model.ActivityHeatmap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The heat map's calendar arithmetic across daylight saving transitions.
 *
 * The old implementation moved between days with `timestamp + n * 86_400_000`. That is wrong twice
 * a year: a local day is 23 or 25 hours long at a transition, so the result lands at 23:00 of the
 * previous date or 01:00 of the next one. A Monday evening workout then painted the Sunday square,
 * and a week could appear to have eight days. These tests run in a zone that really observes DST
 * (America/New_York) instead of the UTC default, because UTC would hide the defect entirely.
 */
class ActivityHeatmapDstTest {

    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUp() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousTimeZone)
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun fields(timestamp: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = timestamp }

    private fun assertLocalMidnight(timestamp: Long, label: String) {
        val cal = fields(timestamp)
        assertEquals("$label must be at hour 0", 0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals("$label must be at minute 0", 0, cal.get(Calendar.MINUTE))
        assertEquals("$label must be at second 0", 0, cal.get(Calendar.SECOND))
        assertEquals("$label must be at millisecond 0", 0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `start of day is local midnight on the spring forward date`() {
        // 8 March 2026: clocks jump from 02:00 to 03:00 in America/New_York.
        val start = ActivityHeatmap.startOfDay(calendar(2026, Calendar.MARCH, 8, 1, 30))
        assertLocalMidnight(start, "spring forward midnight")
        val cal = fields(start)
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(8, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `add days keeps local midnight across the spring forward transition`() {
        // 8 March 2026 is the 23-hour day: midnight to midnight spans the transition at 02:00.
        val sunday = ActivityHeatmap.startOfDay(calendar(2026, Calendar.MARCH, 8, 12))
        val monday = ActivityHeatmap.addDays(sunday, 1)

        assertLocalMidnight(monday, "Monday after spring forward")
        assertEquals(9, fields(monday).get(Calendar.DAY_OF_MONTH))
        // Fixed millisecond arithmetic would land at 01:00 and then paint the wrong square.
        assertEquals(23L * 60L * 60L * 1000L, monday - sunday)
    }

    @Test
    fun `add days keeps local midnight across the fall back transition`() {
        // 1 November 2026 is the 25-hour day: clocks fall from 02:00 back to 01:00.
        val sunday = ActivityHeatmap.startOfDay(calendar(2026, Calendar.NOVEMBER, 1, 12))
        val monday = ActivityHeatmap.addDays(sunday, 1)

        assertLocalMidnight(monday, "Monday after fall back")
        assertEquals(2, fields(monday).get(Calendar.DAY_OF_MONTH))
        assertEquals(25L * 60L * 60L * 1000L, monday - sunday)
    }

    @Test
    fun `a late evening session stays on its own local day across the transition`() {
        val lateSunday = calendar(2026, Calendar.MARCH, 8, 23, 45)
        val start = ActivityHeatmap.startOfDay(lateSunday)
        assertEquals(8, fields(start).get(Calendar.DAY_OF_MONTH))

        // The previous implementation subtracted fixed days and could return 23:00 of 7 March.
        val previous = ActivityHeatmap.addDays(start, -1)
        assertLocalMidnight(previous, "previous day")
        assertEquals(7, fields(previous).get(Calendar.DAY_OF_MONTH))
        assertNotEquals(start, previous)
    }

    @Test
    fun `a week still has seven consecutive local midnights across both transitions`() {
        listOf(
            calendar(2026, Calendar.MARCH, 8, 12),   // spring forward
            calendar(2026, Calendar.NOVEMBER, 1, 12) // fall back
        ).forEach { today ->
            val weeks = ActivityHeatmap.weeksEndingAt(today, weekCount = 4)
            assertEquals(4, weeks.size)
            weeks.forEachIndexed { index, week ->
                assertEquals("week $index has seven days", 7, week.days.size)
                week.days.forEachIndexed { dayIndex, day ->
                    assertLocalMidnight(day, "week $index day $dayIndex")
                    assertEquals(
                        "weekday of week $index day $dayIndex",
                        dayIndex + 1,
                        ActivityHeatmap.isoDayOfWeek(day)
                    )
                    if (dayIndex > 0) {
                        // Never a fixed 24 hours at a transition, but always a later timestamp at
                        // local midnight. The exact date is covered by the distinct-midnight check.
                        assertTrue(
                            "week $index day $dayIndex must be after the previous day",
                            day > week.days[dayIndex - 1]
                        )
                    }
                }
                // Every day of the week belongs to it, and the day after the Sunday does not.
                week.days.forEach { assertTrue(week.contains(it)) }
                assertTrue(!week.contains(ActivityHeatmap.addDays(week.dayAt(6), 1)))
            }
        }
    }

    @Test
    fun `the transition week contains the transition day and no duplicate`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.MARCH, 8, 12), weekCount = 2)
        val week = weeks.last()

        assertEquals("the week must have seven distinct midnights", 7, week.days.toSet().size)
        val transition = ActivityHeatmap.startOfDay(calendar(2026, Calendar.MARCH, 8, 2, 30))
        assertTrue("the DST day must be inside the current week", week.contains(transition))

        val weekdays = week.days.map { ActivityHeatmap.isoDayOfWeek(it) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), weekdays)
    }

    @Test
    fun `weeks ending on a transition day still start on Monday`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.NOVEMBER, 1, 12), weekCount = 2)
        val week = weeks.last()

        // 1 November 2026 is a Sunday, so the current week starts on 26 October.
        assertEquals(1, ActivityHeatmap.isoDayOfWeek(week.mondayStart))
        assertEquals(26, fields(week.mondayStart).get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.OCTOBER, fields(week.mondayStart).get(Calendar.MONTH))
        assertLocalMidnight(week.mondayStart, "Monday before fall back")
    }
}
