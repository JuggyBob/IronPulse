package com.example

import com.example.data.db.RoutineScheduleMigration
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The migration of the routines that were saved before the seven day model existed.
 *
 * The old format was a list of abstract days: "Day 1", "Day 2", "Day 3". The new one is a week of
 * seven weekday slots. The migration binds the old days to real weekdays and changes nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class RoutineScheduleMigrationTest {

    /** A routine in the old format: numbered days, no weekday anywhere. */
    private fun legacyRoutine(
        json: String,
        daysPerWeek: Int = 3
    ) = SavedRoutine(
        id = 7L,
        name = "Old Split",
        description = "A routine from before the seven day model",
        daysPerWeek = daysPerWeek,
        goal = "Strength",
        exercisesJson = json,
        createdAt = 1_700_000_000_000L,
        scheduleVersion = 0
    )

    private val legacyTwoDayJson = """
        [{"id":10,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT","category":"Chest",
          "equipment":"Barbell","sets":4,"reps":8,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
          "prescription":"4 sets x 8 reps","day":1,"dayTitle":"Day 1: Push"},
         {"id":11,"name":"Pull-Up","type":"PURE_BODYWEIGHT","category":"Back",
          "equipment":"Pull-up Bar","sets":4,"reps":6,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
          "prescription":"4 sets x 6 reps","day":2,"dayTitle":"Day 2: Pull"}]
    """.trimIndent()

    @Test
    fun `the old training days become the first days of the week`() {
        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(legacyTwoDayJson))!!

        val days = RoutineParser.parseDays(migrated.exercisesJson, emptyList())
        assertEquals(2, days.size)
        assertEquals(Weekday.MONDAY, days.first().weekday)
        assertEquals(Weekday.TUESDAY, days.last().weekday)
        assertEquals(listOf("Barbell Bench Press"), days.first().exercises.map { it.exerciseName })
        assertEquals(listOf("Pull-Up"), days.last().exercises.map { it.exerciseName })
    }

    @Test
    fun `the exercises of one old day stay together`() {
        // The two exercises of day 2 are written before the one of day 1 in the file.
        val json = """
            [{"id":12,"name":"Dip","type":"PURE_BODYWEIGHT","category":"Chest",
              "equipment":"Dip Bars","sets":3,"reps":10,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
              "prescription":"3 sets x 10 reps","day":2,"dayTitle":"Day 2: Pull"},
             {"id":10,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT","category":"Chest",
              "equipment":"Barbell","sets":4,"reps":8,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 8 reps","day":1,"dayTitle":"Day 1: Push"},
             {"id":13,"name":"Pull-Up","type":"PURE_BODYWEIGHT","category":"Back",
              "equipment":"Pull-up Bar","sets":4,"reps":6,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 6 reps","day":2,"dayTitle":"Day 2: Pull"}]
        """.trimIndent()

        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(json))!!
        val days = RoutineParser.parseDays(migrated.exercisesJson, emptyList())

        assertEquals(2, days.size)
        assertEquals(
            listOf("Dip", "Pull-Up"),
            days.first { it.weekday == Weekday.TUESDAY }.exercises.map { it.exerciseName }
        )
        assertEquals(
            listOf("Barbell Bench Press"),
            days.first { it.weekday == Weekday.MONDAY }.exercises.map { it.exerciseName }
        )
    }

    @Test
    fun `the details of every exercise survive the migration`() {
        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(legacyTwoDayJson))!!
        val items = RoutineParser.parseExercises(migrated.exercisesJson, emptyList())

        assertEquals(2, items.size)
        val bench = items.first { it.exerciseName == "Barbell Bench Press" }
        assertEquals(10L, bench.exerciseId)
        assertEquals(4, bench.targetSets)
        assertEquals(8, bench.targetReps)
        assertEquals("Barbell", bench.equipment)
        assertEquals("Chest", bench.category)
        assertEquals("4 sets x 8 reps", bench.prescription)
    }

    @Test
    fun `the name, the description and the goal are not touched`() {
        val routine = legacyRoutine(legacyTwoDayJson)
        val migrated = RoutineScheduleMigration.migrate(routine)!!

        assertEquals(routine.name, migrated.name)
        assertEquals(routine.description, migrated.description)
        assertEquals(routine.goal, migrated.goal)
        assertEquals(routine.createdAt, migrated.createdAt)
        assertEquals(routine.id, migrated.id)
    }

    @Test
    fun `the coach note of a day survives the migration`() {
        val json = """
            [{"id":10,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT","category":"Chest",
              "equipment":"Barbell","sets":4,"reps":8,"weight":0.0,"addedWeight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 8 reps","day":1,"dayTitle":"Day 1: Push","dayNote":"Leave two reps in reserve."}]
        """.trimIndent()

        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(json))!!
        val days = RoutineParser.parseDays(migrated.exercisesJson, emptyList())

        assertEquals(1, days.size)
        assertTrue(days.first().note.contains("two reps in reserve"))
    }

    @Test
    fun `the day count of the routine follows the days it actually has`() {
        val migrated = RoutineScheduleMigration.migrate(
            legacyRoutine(legacyTwoDayJson, daysPerWeek = 3)
        )!!
        assertEquals(2, migrated.daysPerWeek)
    }

    @Test
    fun `the migration is repeat safe`() {
        val routine = legacyRoutine(legacyTwoDayJson)
        val first = RoutineScheduleMigration.migrate(routine)!!
        assertEquals(RoutineScheduleMigration.SCHEDULE_VERSION, first.scheduleVersion)

        // A routine that has been through the migration is never touched again.
        assertNull(RoutineScheduleMigration.migrate(first))
    }

    @Test
    fun `a routine with nothing in it is marked as done and left alone`() {
        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(""))!!
        assertEquals(RoutineScheduleMigration.SCHEDULE_VERSION, migrated.scheduleVersion)
        assertEquals("", migrated.exercisesJson)
    }

    @Test
    fun `a routine that already names its weekdays keeps them`() {
        // A routine saved by the new editor already carries weekdays, and the migration must not
        // move it: the flag says it is already done, and the days are left exactly as they are.
        val json = RoutineParser.serializeExercises(
            listOf(
                com.example.data.model.RoutineExerciseItem(
                    exerciseId = 10L,
                    exerciseName = "Barbell Bench Press",
                    weekday = Weekday.WEDNESDAY
                )
            )
        )
        val routine = legacyRoutine(json).copy(scheduleVersion = RoutineScheduleMigration.SCHEDULE_VERSION)
        assertNull(RoutineScheduleMigration.migrate(routine))
    }

    @Test
    fun `a migrated routine opens in the seven day editor`() {
        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(legacyTwoDayJson))!!
        val week = RoutineParser.parseWeek(migrated.exercisesJson, emptyList())

        assertEquals(7, week.size)
        assertEquals(2, week.count { !it.isRestDay })
        assertEquals(5, week.count { it.isRestDay })
        assertNotNull(week.firstOrNull { it.weekday == Weekday.MONDAY })
    }

    @Test
    fun `the focus of a day survives without the old day number`() {
        val migrated = RoutineScheduleMigration.migrate(legacyRoutine(legacyTwoDayJson))!!
        val days = RoutineParser.parseDays(migrated.exercisesJson, emptyList())

        assertEquals("Push", days.first().dayTitle)
        assertEquals("Pull", days.last().dayTitle)
    }
}
