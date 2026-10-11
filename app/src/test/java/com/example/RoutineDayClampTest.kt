package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDayClamp
import com.example.data.model.RoutineExerciseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stranded exercise bug: an exercise put on day 5, then the routine shortened to 4 days.
 *
 * The exercise used to stay in the routine. It was invisible on every day, so it looked deleted, yet
 * it was still saved and still logged, and it came back the moment the routine was widened to 5
 * again. Both readings were wrong, so the day number has to be clamped instead.
 */
class RoutineDayClampTest {

    private fun onDay(day: Int, name: String = "Weighted Dips") = RoutineExerciseItem(
        exerciseName = name,
        dayNumber = day,
        dayTitle = "Day $day"
    )

    @Test
    fun `an exercise on a day that no longer exists moves to the last day`() {
        val clamped = RoutineDayClamp.clampExercises(
            listOf(onDay(1), onDay(5)),
            daysPerWeek = 4
        )

        assertEquals(4, clamped.last().dayNumber)
        assertEquals("Day 4", clamped.last().dayTitle)
        // The first entry is untouched.
        assertEquals(1, clamped.first().dayNumber)
    }

    @Test
    fun `the athlete still has every exercise after the shrink`() {
        // The whole point: nothing may be silently dropped, only reassigned.
        val before = listOf(onDay(1), onDay(5, "Pull-up"), onDay(5, "Dips"))
        val after = RoutineDayClamp.clampExercises(before, daysPerWeek = 4)

        assertEquals(3, after.size)
        assertEquals(
            listOf("Weighted Dips", "Pull-up", "Dips"),
            after.map { it.exerciseName }
        )
        assertTrue("no exercise may keep an out of range day", after.all { it.dayNumber in 1..4 })
    }

    @Test
    fun `the order of the exercises is preserved`() {
        val before = listOf(onDay(3, "A"), onDay(1, "B"), onDay(5, "C"))
        val after = RoutineDayClamp.clampExercises(before, daysPerWeek = 2)

        assertEquals(listOf("A", "B", "C"), after.map { it.exerciseName })
        assertEquals(listOf(2, 1, 2), after.map { it.dayNumber })
    }

    @Test
    fun `a one day routine collapses every exercise onto day one`() {
        val clamped = RoutineDayClamp.clampExercises(
            listOf(onDay(1), onDay(3), onDay(4)),
            daysPerWeek = 1
        )
        assertTrue(clamped.all { it.dayNumber == 1 })
    }

    @Test
    fun `a zero or negative day count is treated as a single day`() {
        // A malformed routine must not throw and must not leave day numbers pointing nowhere.
        val clamped = RoutineDayClamp.clampExercises(listOf(onDay(3)), daysPerWeek = 0)
        assertEquals(1, clamped.single().dayNumber)
        assertEquals(1, RoutineDayClamp.clampDay(4, 0))
    }

    @Test
    fun `an exercise on a day below one is pulled back up`() {
        val broken = RoutineExerciseItem(exerciseName = "Broken", dayNumber = 0, dayTitle = "Day 0")
        val clamped = RoutineDayClamp.clampExercises(listOf(broken), daysPerWeek = 3)

        assertEquals(1, clamped.single().dayNumber)
        assertEquals("Day 1", clamped.single().dayTitle)
    }

    @Test
    fun `the selected day is pulled back when the routine shrinks`() {
        // Viewing day 5 of a 4 day routine would show an empty list with no way to tell why.
        assertEquals(4, RoutineDayClamp.clampSelectedDay(5, 4))
        assertEquals(2, RoutineDayClamp.clampSelectedDay(2, 4))
        assertEquals(1, RoutineDayClamp.clampSelectedDay(1, 4))
    }

    @Test
    fun `widening a routine does not move anything`() {
        val before = listOf(onDay(1), onDay(2))
        val after = RoutineDayClamp.clampExercises(before, daysPerWeek = 6)

        assertEquals(before.map { it.dayNumber }, after.map { it.dayNumber })
        assertFalse(RoutineDayClamp.hasStrandedExercises(after, daysPerWeek = 6))
    }

    @Test
    fun `hasStrandedExercises detects exactly the broken state`() {
        assertTrue(RoutineDayClamp.hasStrandedExercises(listOf(onDay(5)), daysPerWeek = 4))
        assertFalse(RoutineDayClamp.hasStrandedExercises(listOf(onDay(4)), daysPerWeek = 4))
        assertTrue(
            "an empty routine is not stranded",
            !RoutineDayClamp.hasStrandedExercises(emptyList(), daysPerWeek = 4)
        )
    }

    @Test
    fun `clamping never touches a cardio exercise's own type`() {
        // The clamp is about the day number only; it must not rewrite the exercise type, which is
        // what decides the logging fields.
        val run = RoutineExerciseItem(
            exerciseName = "Run",
            exerciseType = ExerciseType.CARDIO,
            dayNumber = 5,
            dayTitle = "Day 5"
        )
        val clamped = RoutineDayClamp.clampExercises(listOf(run), daysPerWeek = 3)

        assertEquals(ExerciseType.CARDIO, clamped.single().exerciseType)
        assertEquals(3, clamped.single().dayNumber)
    }
}
