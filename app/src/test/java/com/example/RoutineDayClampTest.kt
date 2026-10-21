package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDayClamp
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A routine is a week of seven days, so a day can no longer be stranded by shortening the routine:
 * there is always a Monday to hold an exercise.
 *
 * What can still go wrong is an exercise that carries a weekday outside the week, which can only
 * come from data written before the seven day model existed. Such an exercise would be invisible
 * in the editor and in the dashboard, so it is clamped back onto the week.
 */
class RoutineDayClampTest {

    private fun onDay(weekday: Int, name: String = "Weighted Dips") = RoutineExerciseItem(
        exerciseName = name,
        weekday = weekday
    )

    @Test
    fun `an exercise on a day outside the week moves onto the Monday`() {
        val clamped = RoutineDayClamp.clampExercises(listOf(onDay(1), onDay(9)))

        assertEquals(Weekday.MONDAY, clamped.last().weekday)
        // The first entry is untouched.
        assertEquals(Weekday.MONDAY, clamped.first().weekday)
    }

    @Test
    fun `the athlete still has every exercise after the clamp`() {
        // The whole point: nothing may be silently dropped, only reassigned.
        val before = listOf(onDay(1), onDay(9, "Pull-up"), onDay(0, "Dips"))
        val after = RoutineDayClamp.clampExercises(before)

        assertEquals(3, after.size)
        assertEquals(
            listOf("Weighted Dips", "Pull-up", "Dips"),
            after.map { it.exerciseName }
        )
        assertTrue("no exercise may keep a day outside the week", after.all { it.weekday in 1..7 })
    }

    @Test
    fun `the order of the exercises is preserved`() {
        val before = listOf(onDay(3, "A"), onDay(1, "B"), onDay(9, "C"))
        val after = RoutineDayClamp.clampExercises(before)

        assertEquals(listOf("A", "B", "C"), after.map { it.exerciseName })
        assertEquals(listOf(3, 1, 1), after.map { it.weekday })
    }

    @Test
    fun `a weekday below one is pulled back up`() {
        val broken = RoutineExerciseItem(exerciseName = "Broken", weekday = 0)
        val clamped = RoutineDayClamp.clampExercises(listOf(broken))

        assertEquals(Weekday.MONDAY, clamped.single().weekday)
    }

    @Test
    fun `the selected day is pulled back onto the week`() {
        assertEquals(7, RoutineDayClamp.clampDay(9))
        assertEquals(2, RoutineDayClamp.clampDay(2))
        assertEquals(1, RoutineDayClamp.clampDay(0))
        assertEquals(1, RoutineDayClamp.clampDay(-3))
    }

    @Test
    fun `a routine that is already on the week is not touched`() {
        val before = listOf(onDay(1), onDay(7))
        val after = RoutineDayClamp.clampExercises(before)

        assertEquals(before.map { it.weekday }, after.map { it.weekday })
        assertFalse(RoutineDayClamp.hasStrandedExercises(after))
    }

    @Test
    fun `hasStrandedExercises detects exactly the broken state`() {
        assertTrue(RoutineDayClamp.hasStrandedExercises(listOf(onDay(8))))
        assertFalse(RoutineDayClamp.hasStrandedExercises(listOf(onDay(7))))
        assertTrue(
            "an empty routine is not stranded",
            !RoutineDayClamp.hasStrandedExercises(emptyList())
        )
    }

    @Test
    fun `clamping never touches a cardio exercise's own type`() {
        // The clamp is about the weekday only; it must not rewrite the exercise type, which is
        // what decides the logging fields.
        val run = RoutineExerciseItem(
            exerciseName = "Run",
            exerciseType = ExerciseType.CARDIO,
            weekday = 9
        )
        val clamped = RoutineDayClamp.clampExercises(listOf(run))

        assertEquals(ExerciseType.CARDIO, clamped.single().exerciseType)
        assertEquals(Weekday.MONDAY, clamped.single().weekday)
    }
}
