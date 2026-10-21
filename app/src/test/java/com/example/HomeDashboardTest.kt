package com.example

import com.example.data.model.ActivityHeatmap
import com.example.data.model.DAY_MS
import com.example.data.model.ExerciseType
import com.example.data.model.HomeDashboard
import com.example.data.model.LoggedSet
import com.example.data.model.SessionWithSets
import com.example.data.model.WorkoutMetrics
import com.example.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers behind the Home dashboard.
 *
 * The lifetime training day count, the rolling heat map and the weekly totals.
 */
class HomeDashboardTest {

    private val reference = ActivityHeatmap.startOfDay(1_760_000_000_000L)

    /** The weekday the reference date falls on. */
    private val referenceWeekday = ActivityHeatmap.isoDayOfWeek(reference)

    /** The start of the calendar week the reference date falls in. */
    private fun startOfWeek(weekStartsOnMonday: Boolean): Long {
        val first = if (weekStartsOnMonday) 1 else 7
        val offset = (referenceWeekday - first + 7) % 7
        return reference - offset * DAY_MS
    }

    private fun session(
        startTime: Long,
        minutes: Int = 30,
        title: String = "Workout"
    ) = WorkoutSession(
        id = startTime,
        title = title,
        startTime = startTime,
        endTime = startTime + minutes * 60_000L
    )

    private fun set(
        sessionId: Long,
        completed: Boolean = true,
        reps: Int = 10,
        weight: Double = 40.0,
        addedWeight: Double = 0.0,
        type: String = ExerciseType.STANDARD_WEIGHT,
        timestamp: Long = reference
    ) = LoggedSet(
        sessionId = sessionId,
        exerciseId = 1L,
        exerciseName = "Bench",
        exerciseType = type,
        setNumber = 1,
        weight = weight,
        addedWeight = addedWeight,
        reps = reps,
        isCompleted = completed,
        timestamp = timestamp
    )

    private fun wrapper(
        startTime: Long,
        minutes: Int = 30,
        sets: List<LoggedSet>
    ) = SessionWithSets(session(startTime, minutes), sets)

    private val noBodyWeight: (LoggedSet) -> Double? = { null }

    // === The qualifying rule ===

    @Test
    fun `exactly five minutes with two completed sets does not count`() {
        val s = wrapper(reference, minutes = 5, sets = listOf(set(1L), set(1L)))
        assertFalse(HomeDashboard.qualifies(s.session, s.sets))
    }

    @Test
    fun `more than five minutes with one completed set does not count`() {
        val s = wrapper(reference, minutes = 20, sets = listOf(set(1L)))
        assertFalse(HomeDashboard.qualifies(s.session, s.sets))
    }

    @Test
    fun `more than five minutes with two completed sets counts`() {
        val s = wrapper(reference, minutes = 20, sets = listOf(set(1L), set(1L)))
        assertTrue(HomeDashboard.qualifies(s.session, s.sets))
    }

    @Test
    fun `a workout with enough time but no completed set does not count`() {
        val s = wrapper(
            reference,
            minutes = 30,
            sets = listOf(set(1L, completed = false), set(1L, completed = false))
        )
        assertFalse(HomeDashboard.qualifies(s.session, s.sets))
    }

    @Test
    fun `one completed set and one unfinished set does not count`() {
        val s = wrapper(
            reference,
            minutes = 30,
            sets = listOf(set(1L, completed = true), set(1L, completed = false))
        )
        assertFalse(HomeDashboard.qualifies(s.session, s.sets))
    }

    @Test
    fun `two qualifying workouts on the same date count as one day`() {
        val morning = wrapper(reference + 3 * 3_600_000L, minutes = 30, sets = listOf(set(1L), set(1L)))
        val evening = wrapper(reference + 10 * 3_600_000L, minutes = 40, sets = listOf(set(1L), set(1L)))
        assertEquals(1, HomeDashboard.qualifyingDayCount(listOf(morning, evening)))
    }

    @Test
    fun `qualifying workouts on two separate dates count as two days`() {
        val first = wrapper(reference, minutes = 30, sets = listOf(set(1L), set(1L)))
        val second = wrapper(reference + DAY_MS, minutes = 30, sets = listOf(set(1L), set(1L)))
        assertEquals(2, HomeDashboard.qualifyingDayCount(listOf(first, second)))
    }

    @Test
    fun `a week off never resets the lifetime count`() {
        val first = wrapper(reference, minutes = 30, sets = listOf(set(1L), set(1L)))
        val later = wrapper(reference + 60 * DAY_MS, minutes = 30, sets = listOf(set(1L), set(1L)))
        // A long gap in between is not a workout and must not subtract anything.
        assertEquals(2, HomeDashboard.qualifyingDayCount(listOf(first, later)))
    }

    @Test
    fun `deleting a formerly qualifying workout lowers the count`() {
        val first = wrapper(reference, minutes = 30, sets = listOf(set(1L), set(1L)))
        val second = wrapper(reference + DAY_MS, minutes = 30, sets = listOf(set(1L), set(1L)))
        assertEquals(2, HomeDashboard.qualifyingDayCount(listOf(first, second)))
        assertEquals(1, HomeDashboard.qualifyingDayCount(listOf(second)))
    }

    @Test
    fun `editing a formerly qualifying workout lowers the count`() {
        // The same session, but with only one set left.
        val edited = wrapper(reference, minutes = 30, sets = listOf(set(1L)))
        assertEquals(0, HomeDashboard.qualifyingDayCount(listOf(edited)))
    }

    @Test
    fun `an old qualifying workout is still counted`() {
        // Far in the past, but a real workout.
        val old = wrapper(reference - 300 * DAY_MS, minutes = 30, sets = listOf(set(1L), set(1L)))
        assertEquals(1, HomeDashboard.qualifyingDayCount(listOf(old)))
    }

    @Test
    fun `the same workout is never counted on two dates`() {
        // Two sets an hour apart must not look like two days.
        val s = wrapper(
            reference,
            minutes = 30,
            sets = listOf(
                set(1L, timestamp = reference),
                set(1L, timestamp = reference + 3_600_000L)
            )
        )
        assertEquals(1, HomeDashboard.qualifyingDayCount(listOf(s)))
    }

    // === The rolling heat map ===

    @Test
    fun `the heat map covers exactly seven dates with today included`() {
        val window = HomeDashboard.rollingWindow(reference)
        assertEquals(7, window.size)
        assertEquals(reference, window.last())
        assertEquals(reference - 6 * DAY_MS, window.first())
        // The dates are consecutive.
        assertEquals(
            (1..6).map { window[it] - window[it - 1] }.distinct(),
            listOf(DAY_MS)
        )
    }

    @Test
    fun `the heat map has one cell per date even when nothing was trained`() {
        val cells = HomeDashboard.rollingHeatmap(emptyList(), reference, noBodyWeight)
        assertEquals(7, cells.size)
        assertTrue(cells.none { it.hasActivity })
        assertTrue(cells.none { it.hasSession })
    }

    @Test
    fun `the heat map adds up the sessions of the same date`() {
        val morning = wrapper(reference + 2 * 3_600_000L, minutes = 30, sets = listOf(set(1L, weight = 50.0)))
        val evening = wrapper(reference + 9 * 3_600_000L, minutes = 20, sets = listOf(set(1L, weight = 30.0)))

        val cells = HomeDashboard.rollingHeatmap(listOf(morning, evening), reference, noBodyWeight)
        val today = cells.last()
        // 50 kg x 10 reps plus 30 kg x 10 reps.
        assertEquals(800.0, today.volume, 0.0001)
        assertEquals(50, today.minutes)
        assertEquals(2, today.setCount)
        assertTrue(today.hasSession)
    }

    @Test
    fun `a workout outside the window is not in the heat map`() {
        val old = wrapper(reference - 30 * DAY_MS, minutes = 30, sets = listOf(set(1L), set(1L)))
        val cells = HomeDashboard.rollingHeatmap(listOf(old), reference, noBodyWeight)
        assertTrue(cells.none { it.hasActivity })
    }

    @Test
    fun `the volume of a bodyweight set uses the body weight that applied`() {
        val bodyweightSet = set(
            1L,
            reps = 10,
            weight = 0.0,
            type = ExerciseType.PURE_BODYWEIGHT
        )
        assertEquals(0.0, WorkoutMetrics.setVolume(bodyweightSet, null), 0.0001)
        assertEquals(800.0, WorkoutMetrics.setVolume(bodyweightSet, 80.0), 0.0001)
    }

    @Test
    fun `the volume of an added weight set is the body weight plus the added weight`() {
        val added = set(
            1L,
            reps = 5,
            weight = 0.0,
            addedWeight = 20.0,
            type = ExerciseType.ADDED_WEIGHT
        )
        assertEquals(500.0, WorkoutMetrics.setVolume(added, 80.0), 0.0001)
    }

    @Test
    fun `the duration of a session is its length`() {
        val s = wrapper(reference, minutes = 42, sets = listOf(set(1L)))
        assertEquals(42, HomeDashboard.minutes(s.session, s.sets))
    }

    @Test
    fun `the span between the sets stands in for a session that was saved at once`() {
        // A workout saved a second after it started has no measurable duration of its own.
        val quick = SessionWithSets(
            session = session(reference, minutes = 0),
            sets = listOf(
                set(1L, timestamp = reference),
                set(1L, timestamp = reference + 20 * 60_000L)
            )
        )
        assertEquals(20, HomeDashboard.minutes(quick.session, quick.sets))
    }

    // === The week totals ===

    @Test
    fun `the week totals respect the week start`() {
        // One workout on the first day of the monday first week, one on the first day of the
        // sunday first week. Which of the two is inside the week depends on the setting.
        val mondayFirstWeekStart = startOfWeek(weekStartsOnMonday = true)
        val sundayFirstWeekStart = startOfWeek(weekStartsOnMonday = false)

        val mondayFirst = HomeDashboard.weekTotals(
            sessions = listOf(
                wrapper(mondayFirstWeekStart, minutes = 30, sets = listOf(set(1L, weight = 50.0))),
                wrapper(sundayFirstWeekStart, minutes = 20, sets = listOf(set(1L, weight = 20.0)))
            ),
            today = reference,
            weekStartsOnMonday = true,
            bodyWeightForSet = noBodyWeight
        )
        assertEquals(1, mondayFirst.workouts)
        assertEquals(30, mondayFirst.minutes)
        assertEquals(500.0, mondayFirst.volume, 0.0001)

        val sundayFirst = HomeDashboard.weekTotals(
            sessions = listOf(
                wrapper(mondayFirstWeekStart, minutes = 30, sets = listOf(set(1L, weight = 50.0))),
                wrapper(sundayFirstWeekStart, minutes = 20, sets = listOf(set(1L, weight = 20.0)))
            ),
            today = reference,
            weekStartsOnMonday = false,
            bodyWeightForSet = noBodyWeight
        )
        assertEquals(2, sundayFirst.workouts)
        assertEquals(50, sundayFirst.minutes)
        assertEquals(700.0, sundayFirst.volume, 0.0001)
    }

    @Test
    fun `an unfinished session does not inflate the week totals`() {
        val empty = wrapper(reference, minutes = 30, sets = emptyList())
        val totals = HomeDashboard.weekTotals(
            sessions = listOf(empty),
            today = reference,
            weekStartsOnMonday = true,
            bodyWeightForSet = noBodyWeight
        )
        assertEquals(0, totals.workouts)
        assertEquals(0, totals.minutes)
        assertEquals(0.0, totals.volume, 0.0001)
    }

    @Test
    fun `the week dates are the seven days of the week`() {
        val mondayFirst = HomeDashboard.weekDates(reference, weekStartsOnMonday = true)
        assertEquals(7, mondayFirst.size)
        assertEquals(startOfWeek(weekStartsOnMonday = true), mondayFirst.first())

        val sundayFirst = HomeDashboard.weekDates(reference, weekStartsOnMonday = false)
        assertEquals(startOfWeek(weekStartsOnMonday = false), sundayFirst.first())
    }

    @Test
    fun `the week start does not change which dates the rolling heat map covers`() {
        // The rolling window always ends today, whatever the athlete's week looks like.
        val mondayFirst = HomeDashboard.rollingWindow(reference)
        val sundayFirst = HomeDashboard.rollingWindow(reference)
        assertEquals(mondayFirst, sundayFirst)
    }

    @Test
    fun `the most recent workout is the newest one that holds sets`() {
        val older = wrapper(reference - DAY_MS, minutes = 30, sets = listOf(set(1L)))
        val newer = wrapper(reference, minutes = 30, sets = listOf(set(1L)))
        val empty = wrapper(reference + DAY_MS, minutes = 30, sets = emptyList())

        val recent = HomeDashboard.mostRecentWorkout(listOf(older, newer, empty))
        assertEquals(newer.session.id, recent?.session?.id)
    }
}
