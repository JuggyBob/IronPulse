/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.ActivityHeatmap
import com.example.data.model.ExerciseType
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.SessionWithSets
import com.example.data.model.StreakDayStatus
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The streak is adherence to the plan, not a count of opened sessions.
 *
 * These scenarios are the contract: a rest day is neutral, a missed planned day breaks the active
 * streak, today's unfinished plan is pending rather than missed, and an unscheduled workout cannot
 * repair yesterday's miss.
 */
class WorkoutStreakTest {

    private val today = ActivityHeatmap.startOfDay(System.currentTimeMillis())

    private fun day(offset: Int): Long = ActivityHeatmap.addDays(today, offset)

    private fun planned(date: Long, exerciseCount: Int = 3): ScheduledWorkoutDay =
        ScheduledWorkoutDay(
            dateStart = date,
            routineId = 1L,
            weekday = ActivityHeatmap.isoDayOfWeek(date),
            dayTitle = "Planned",
            exerciseCount = exerciseCount,
            capturedAt = 1L
        )

    private fun rest(date: Long): ScheduledWorkoutDay =
        ScheduledWorkoutDay(
            dateStart = date,
            routineId = 1L,
            weekday = ActivityHeatmap.isoDayOfWeek(date),
            dayTitle = "",
            exerciseCount = 0,
            capturedAt = 1L
        )

    private fun completed(date: Long, routineId: Long = 1L): SessionWithSets {
        val session = WorkoutSession(
            id = date,
            title = "Routine day",
            startTime = date + 10_000L,
            endTime = date + 40 * 60_000L,
            routineId = routineId,
            routineWeekday = ActivityHeatmap.isoDayOfWeek(date),
            plannedDate = date
        )
        return SessionWithSets(
            session = session,
            sets = listOf(
                LoggedSet(
                    id = date,
                    sessionId = date,
                    exerciseId = 1L,
                    exerciseName = "Push-Up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    reps = 10,
                    isCompleted = true,
                    timestamp = date + 20_000L
                )
            )
        )
    }

    private fun unscheduled(date: Long): SessionWithSets =
        completed(date).let { wrapper ->
            wrapper.copy(
                session = wrapper.session.copy(
                    id = date + 1,
                    routineId = 0L,
                    routineWeekday = 0,
                    plannedDate = 0L
                )
            )
        }

    @Test
    fun `consecutive scheduled workouts build the streak`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-2)), planned(day(-1)), planned(today)),
            sessions = listOf(completed(day(-2)), completed(day(-1)), completed(today)),
            today = today
        )

        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.bestStreak)
        assertTrue(summary.isAlive)
        assertFalse(summary.isBroken)
        assertTrue(summary.todayCompleted)
        assertFalse(summary.pendingToday)
    }

    @Test
    fun `a rest day does not break the streak`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-2)), rest(day(-1)), planned(today)),
            sessions = listOf(completed(day(-2)), completed(today)),
            today = today
        )

        assertEquals(2, summary.currentStreak)
        assertEquals(2, summary.bestStreak)
        assertTrue(summary.isAlive)
    }

    @Test
    fun `a missed scheduled workout breaks the streak`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-2)), planned(day(-1)), rest(today)),
            sessions = listOf(completed(day(-2))),
            today = today
        )

        assertEquals(0, summary.currentStreak)
        assertEquals(1, summary.bestStreak)
        assertTrue(summary.isBroken)
        assertFalse(summary.isAlive)
        assertEquals(day(-1), summary.lastMissedDay)
        assertEquals(StreakDayStatus.MISSED, summary.days.first { it.dayStart == day(-1) }.status)
    }

    @Test
    fun `today's incomplete plan is pending not missed`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-1)), planned(today)),
            sessions = listOf(completed(day(-1))),
            today = today
        )

        assertEquals(1, summary.currentStreak)
        assertTrue(summary.isAlive)
        assertFalse(summary.isBroken)
        assertTrue(summary.pendingToday)
        assertTrue(summary.todayPlanned)
        assertFalse(summary.todayCompleted)
        assertEquals(StreakDayStatus.PENDING, summary.days.first { it.dayStart == today }.status)
    }

    @Test
    fun `a new streak starts after a missed day`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-3)), planned(day(-2)), planned(day(-1)), rest(today)),
            sessions = listOf(completed(day(-3)), completed(day(-1))),
            today = today
        )

        assertEquals(1, summary.currentStreak)
        assertEquals(1, summary.bestStreak)
        assertTrue(summary.isAlive)
    }

    @Test
    fun `an arbitrary workout on a rest day does not repair a miss`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(day(-2)), planned(day(-1)), rest(today)),
            sessions = listOf(completed(day(-2)), unscheduled(today)),
            today = today
        )

        assertEquals(0, summary.currentStreak)
        assertTrue(summary.isBroken)
        assertFalse(summary.todayCompleted)
        // The rest-day session must not appear as a completed scheduled day.
        assertEquals(1, summary.completedDays)
    }

    @Test
    fun `several sessions on one planned day count once`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(today)),
            sessions = listOf(completed(today), completed(today)),
            today = today
        )

        assertEquals(1, summary.currentStreak)
        assertEquals(1, summary.completedDays)
    }

    @Test
    fun `a session with no planned date does not count`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(today)),
            sessions = listOf(unscheduled(today)),
            today = today
        )

        assertEquals(0, summary.currentStreak)
        assertTrue(summary.pendingToday)
    }

    @Test
    fun `future planned days are shown but not counted yet`() {
        val summary = WorkoutStreak.calculate(
            schedule = listOf(planned(today), planned(day(1)), planned(day(2))),
            sessions = emptyList(),
            today = today
        )

        assertEquals(0, summary.currentStreak)
        assertEquals(StreakDayStatus.PENDING, summary.days.first { it.dayStart == today }.status)
        assertEquals(StreakDayStatus.FUTURE, summary.days.first { it.dayStart == day(1) }.status)
        assertEquals(today, summary.nextPlannedDay)
    }

    @Test
    fun `editing the future schedule leaves past statuses intact`() {
        val past = listOf(planned(day(-2)), planned(day(-1)))
        val before = WorkoutStreak.calculate(
            schedule = past + planned(today),
            sessions = listOf(completed(day(-2))),
            today = today
        )

        // A future edit only replaces today and later rows; the old past row is still the source.
        val after = WorkoutStreak.calculate(
            schedule = past + rest(today) + planned(day(1)),
            sessions = listOf(completed(day(-2))),
            today = today
        )

        assertEquals(before.days.first { it.dayStart == day(-1) }.status, after.days.first { it.dayStart == day(-1) }.status)
        assertEquals(StreakDayStatus.MISSED, after.days.first { it.dayStart == day(-1) }.status)
        assertEquals(StreakDayStatus.REST, after.days.first { it.dayStart == today }.status)
    }

    @Test
    fun `daylight saving does not lose a calendar day`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val march7 = Calendar.getInstance().apply {
                set(2026, Calendar.MARCH, 7, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val march7Start = ActivityHeatmap.startOfDay(march7)
            val march8Start = ActivityHeatmap.addDays(march7Start, 1)
            val march9Start = ActivityHeatmap.addDays(march7Start, 2)

            val summary = WorkoutStreak.calculate(
                schedule = listOf(planned(march7Start), planned(march8Start), planned(march9Start)),
                sessions = listOf(completed(march7Start), completed(march8Start), completed(march9Start)),
                today = march9Start
            )

            assertEquals(3, summary.currentStreak)
            assertEquals(3, summary.bestStreak)
            assertEquals(3, summary.completedDays)
            assertEquals(StreakDayStatus.COMPLETED, summary.days.first { it.dayStart == march8Start }.status)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `a timezone change does not move a schedule date`() {
        val original = TimeZone.getDefault()
        try {
            val key = LocalDateKey.keyOf(2026, 11, 3)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val schedule = listOf(
                ScheduledWorkoutDay(
                    dateStart = LocalDateKey.timestampOf(key),
                    dateKey = key,
                    routineId = 1L,
                    weekday = ActivityHeatmap.isoDayOfWeek(LocalDateKey.timestampOf(key)),
                    dayTitle = "Planned",
                    exerciseCount = 3,
                    capturedAt = 1L
                )
            )
            val session = completed(LocalDateKey.timestampOf(key)).let { wrapper ->
                wrapper.copy(
                    session = wrapper.session.copy(
                        plannedDate = LocalDateKey.timestampOf(key),
                        plannedDateKey = key
                    )
                )
            }

            val berlin = WorkoutStreak.calculate(
                schedule = schedule,
                sessions = listOf(session),
                today = LocalDateKey.timestampOf(key)
            )
            assertEquals(1, berlin.currentStreak)

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val newYork = WorkoutStreak.calculate(
                schedule = schedule,
                sessions = listOf(session),
                today = LocalDateKey.timestampOf(key)
            )
            assertEquals(1, newYork.currentStreak)
            assertEquals(1, newYork.bestStreak)
            assertEquals(StreakDayStatus.COMPLETED, newYork.days.first { it.dateKey == key }.status)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `date for weekday stays inside the current calendar week`() {
        val monday = WorkoutStreak.dateForWeekdayInWeek(1, today, weekStartsOnMonday = true)
        val sunday = WorkoutStreak.dateForWeekdayInWeek(7, today, weekStartsOnMonday = true)
        assertEquals(ActivityHeatmap.startOfDay(monday), monday)
        assertEquals(ActivityHeatmap.startOfDay(sunday), sunday)
        assertTrue(sunday > monday)
        assertEquals(
            ActivityHeatmap.startOfDay(today),
            WorkoutStreak.dateForWeekdayInWeek(9, today, true)
        )
    }
}
