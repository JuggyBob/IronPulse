package com.example

import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.RoutineSchedule
import com.example.data.model.ScheduledWorkout
import com.example.data.model.SavedRoutine
import com.example.data.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * The seven day routine model.
 *
 * A routine is a week: seven slots, one per weekday, and a slot with nothing in it is a rest day.
 */
@RunWith(RobolectricTestRunner::class)
class SevenDayScheduleTest {

    private fun item(name: String, weekday: Int) = RoutineExerciseItem(
        exerciseName = name,
        weekday = weekday
    )

    // === The seven slots ===

    @Test
    fun `a routine always has seven weekday slots`() {
        val days = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.MONDAY))),
            weekStartsOnMonday = true
        )

        assertEquals(7, days.size)
        assertEquals((Weekday.MONDAY..Weekday.SUNDAY).toList(), days.map { it.weekday })
    }

    @Test
    fun `an empty day is a rest day`() {
        val days = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.MONDAY))),
            weekStartsOnMonday = true
        )

        assertFalse(days.first { it.weekday == Weekday.MONDAY }.isRestDay)
        assertTrue(days.filter { it.weekday != Weekday.MONDAY }.all { it.isRestDay })
    }

    @Test
    fun `adding an exercise turns a rest day into a workout day`() {
        val empty = RoutineSchedule.buildDays(emptyList(), weekStartsOnMonday = true)
        assertTrue(empty.all { it.isRestDay })

        val withExercise = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.WEDNESDAY))),
            weekStartsOnMonday = true
        )
        val wednesday = withExercise.first { it.weekday == Weekday.WEDNESDAY }
        assertFalse(wednesday.isRestDay)
        assertEquals(1, wednesday.exercises.size)
    }

    @Test
    fun `removing the last exercise turns the day back into a rest day`() {
        val days = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.TUESDAY))),
            weekStartsOnMonday = true
        )
        val before = days.first { it.weekday == Weekday.TUESDAY }
        assertFalse(before.isRestDay)

        val after = RoutineSchedule.buildDays(emptyList(), weekStartsOnMonday = true)
        assertTrue(after.all { it.isRestDay })
    }

    @Test
    fun `exercises never leak between days`() {
        val days = RoutineSchedule.buildDays(
            listOf(
                scheduled(item("Squat", Weekday.MONDAY)),
                scheduled(item("Bench", Weekday.MONDAY)),
                scheduled(item("Row", Weekday.WEDNESDAY))
            ),
            weekStartsOnMonday = true
        )

        assertEquals(
            listOf("Squat", "Bench"),
            days.first { it.weekday == Weekday.MONDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("Row"),
            days.first { it.weekday == Weekday.WEDNESDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("Squat", "Bench", "Row"),
            days.flatMap { it.exercises }.map { it.exerciseName }
        )
    }

    @Test
    fun `the order the exercises were written in is kept inside a day`() {
        val days = RoutineSchedule.buildDays(
            listOf(
                scheduled(item("A", Weekday.MONDAY)),
                scheduled(item("B", Weekday.MONDAY)),
                scheduled(item("C", Weekday.MONDAY))
            ),
            weekStartsOnMonday = true
        )
        assertEquals(
            listOf("A", "B", "C"),
            days.first { it.weekday == Weekday.MONDAY }.exercises.map { it.exerciseName }
        )
    }

    // === The week start ===

    @Test
    fun `a monday first week and a sunday first week order the days differently`() {
        val mondayFirst = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.MONDAY))),
            weekStartsOnMonday = true
        )
        val sundayFirst = RoutineSchedule.buildDays(
            listOf(scheduled(item("Squat", Weekday.MONDAY))),
            weekStartsOnMonday = false
        )

        assertEquals(Weekday.MONDAY, mondayFirst.first().weekday)
        assertEquals(Weekday.SUNDAY, sundayFirst.first().weekday)
        // The set of days is the same either way.
        assertEquals(
            mondayFirst.map { it.weekday }.toSet(),
            sundayFirst.map { it.weekday }.toSet()
        )
    }

    @Test
    fun `changing the week start does not move a workout off its weekday`() {
        val workouts = listOf(
            scheduled(item("Squat", Weekday.WEDNESDAY)),
            scheduled(item("Bench", Weekday.SATURDAY))
        )

        val mondayFirst = RoutineSchedule.buildDays(workouts, weekStartsOnMonday = true)
        val sundayFirst = RoutineSchedule.buildDays(workouts, weekStartsOnMonday = false)

        // The Wednesday workout is on the Wednesday in both.
        assertEquals(
            listOf("Squat"),
            mondayFirst.first { it.weekday == Weekday.WEDNESDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("Squat"),
            sundayFirst.first { it.weekday == Weekday.WEDNESDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("Bench"),
            sundayFirst.first { it.weekday == Weekday.SATURDAY }.exercises.map { it.exerciseName }
        )
    }

    @Test
    fun `a sequence with no weekday named starts at the first day of the week`() {
        val workouts = listOf(
            scheduled(item("A", 0), slot = 1),
            scheduled(item("B", 0), slot = 2),
            scheduled(item("C", 0), slot = 3)
        )

        val mondayFirst = RoutineSchedule.buildDays(workouts, weekStartsOnMonday = true)
        assertEquals(
            listOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY),
            mondayFirst.filter { !it.isRestDay }.map { it.weekday }
        )

        val sundayFirst = RoutineSchedule.buildDays(workouts, weekStartsOnMonday = false)
        assertEquals(
            listOf(Weekday.SUNDAY, Weekday.MONDAY, Weekday.TUESDAY),
            sundayFirst.filter { !it.isRestDay }.map { it.weekday }
        )
    }

    @Test
    fun `a named weekday is never taken away to make room for another workout`() {
        val workouts = listOf(
            scheduled(item("A", 0), slot = 1),
            scheduled(item("B", Weekday.WEDNESDAY)),
            scheduled(item("C", 0), slot = 2)
        )

        val days = RoutineSchedule.buildDays(workouts, weekStartsOnMonday = true)
        assertEquals(
            listOf("B"),
            days.first { it.weekday == Weekday.WEDNESDAY }.exercises.map { it.exerciseName }
        )
        // The unnamed workouts take the days that are left, starting at the Monday.
        assertEquals(
            listOf("A"),
            days.first { it.weekday == Weekday.MONDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("C"),
            days.first { it.weekday == Weekday.TUESDAY }.exercises.map { it.exerciseName }
        )
    }

    // === The weekday of a date ===

    @Test
    fun `the weekday of a date is the real day of the week`() {
        // 21 October 2026 is a Wednesday.
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 21, 12, 0, 0)
        }
        assertEquals(Weekday.WEDNESDAY, Weekday.of(cal.timeInMillis))

        // The Monday of that week.
        cal.set(2026, Calendar.OCTOBER, 19, 12, 0, 0)
        assertEquals(Weekday.MONDAY, Weekday.of(cal.timeInMillis))

        // The Sunday of that week.
        cal.set(2026, Calendar.OCTOBER, 25, 12, 0, 0)
        assertEquals(Weekday.SUNDAY, Weekday.of(cal.timeInMillis))
    }

    @Test
    fun `a weekday name is understood in every spelling`() {
        assertEquals(Weekday.MONDAY, Weekday.fromName("Monday"))
        assertEquals(Weekday.MONDAY, Weekday.fromName("monday"))
        assertEquals(Weekday.MONDAY, Weekday.fromName("MON"))
        assertEquals(Weekday.MONDAY, Weekday.fromName("mon"))
        assertEquals(Weekday.WEDNESDAY, Weekday.fromName("3"))
        assertNull(Weekday.fromName("Funday"))
        assertNull(Weekday.fromName("0"))
        assertNull(Weekday.fromName("8"))
        assertNull(Weekday.fromName(""))
    }

    // === Reading and writing a routine ===

    @Test
    fun `a routine survives the round trip through the saved format`() {
        val routine = SavedRoutine(
            name = "Push Pull",
            description = "",
            daysPerWeek = 2,
            exercisesJson = RoutineParser.serializeExercises(
                listOf(
                    item("Barbell Bench Press", Weekday.MONDAY),
                    item("Pull-Up", Weekday.WEDNESDAY)
                )
            )
        )

        val days = RoutineParser.parseDays(routine.exercisesJson, emptyList())
        assertEquals(2, days.size)
        assertEquals(
            "Barbell Bench Press",
            days.first { it.weekday == Weekday.MONDAY }.exercises.first().exerciseName
        )
        assertEquals(
            "Pull-Up",
            days.first { it.weekday == Weekday.WEDNESDAY }.exercises.first().exerciseName
        )
    }

    @Test
    fun `a routine written before the seven day model existed still loads`() {
        // The old format: numbered training days, no weekday anywhere.
        val legacy = """
            [{"id":10,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT","category":"Chest",
              "equipment":"Barbell","sets":4,"reps":8,"weight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 8 reps","day":1,"dayTitle":"Day 1: Push"},
             {"id":11,"name":"Pull-Up","type":"PURE_BODYWEIGHT","category":"Back",
              "equipment":"Pull-up Bar","sets":4,"reps":6,"weight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 6 reps","day":2,"dayTitle":"Day 2: Pull"}]
        """.trimIndent()

        val days = RoutineParser.parseDays(legacy, emptyList())
        assertEquals(2, days.size)
        assertEquals(Weekday.MONDAY, days.first().weekday)
        assertEquals(Weekday.TUESDAY, days.last().weekday)
        // The focus of the day survives, without the old "Day N:" prefix.
        assertEquals("Push", days.first().dayTitle)
        assertEquals("Pull", days.last().dayTitle)
    }

    @Test
    fun `a routine with nothing in it has no training days`() {
        val days = RoutineParser.parseDays("[]", emptyList())
        assertTrue(days.isEmpty())
    }

    @Test
    fun `the seven slots of a routine include the rest days`() {
        val routine = SavedRoutine(
            name = "Push Pull",
            description = "",
            exercisesJson = RoutineParser.serializeExercises(
                listOf(
                    item("Barbell Bench Press", Weekday.MONDAY),
                    item("Pull-Up", Weekday.WEDNESDAY)
                )
            )
        )

        val week = RoutineParser.parseWeek(routine.exercisesJson, emptyList())
        assertEquals(7, week.size)
        assertEquals(5, week.count { it.isRestDay })
        assertNotNull(week.firstOrNull { it.weekday == Weekday.SUNDAY })
    }

    // === The day of a slot ===

    @Test
    fun `a slot lands on the right weekday for the week start`() {
        assertEquals(Weekday.MONDAY, RoutineSchedule.weekdayForSlot(1, weekStartsOnMonday = true))
        assertEquals(Weekday.SUNDAY, RoutineSchedule.weekdayForSlot(1, weekStartsOnMonday = false))
        assertEquals(Weekday.WEDNESDAY, RoutineSchedule.weekdayForSlot(3, weekStartsOnMonday = true))
        assertEquals(Weekday.TUESDAY, RoutineSchedule.weekdayForSlot(3, weekStartsOnMonday = false))
        assertEquals(Weekday.MONDAY, RoutineSchedule.weekdayForSlot(8, weekStartsOnMonday = true))
    }

    /** A workout that is already bound to a weekday. */
    private fun scheduled(item: RoutineExerciseItem, slot: Int = 0) = ScheduledWorkout(
        item = item,
        weekday = item.weekday,
        slot = if (slot == 0) item.weekday.coerceAtLeast(1) else slot
    )
}
