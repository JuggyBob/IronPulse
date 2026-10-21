package com.example

import com.example.data.model.AiRoutineSchedule
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.Weekday
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The scheduling rules the AI import applies.
 *
 * The model extracts the training meaning; the app applies the scheduling rules. These tests pin
 * those rules down with fixed answers, so a change in the model can never quietly move an
 * athlete's workout to another day.
 */
@RunWith(RobolectricTestRunner::class)
class AiRoutineScheduleTest {

    private val catalog = listOf(
        Exercise(
            id = 1,
            name = "Barbell Bench Press",
            category = "Chest",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell"
        ),
        Exercise(
            id = 2,
            name = "Pull-Up",
            category = "Back",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Pull-up Bar"
        ),
        Exercise(
            id = 3,
            name = "Barbell Full Squat",
            category = "Legs",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell"
        ),
        Exercise(
            id = 4,
            name = "Dip",
            category = "Chest",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Dip Bars"
        ),
        Exercise(
            id = 5,
            name = "Barbell Row",
            category = "Back",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell"
        ),
        Exercise(
            id = 6,
            name = "Barbell Deadlift",
            category = "Back",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell"
        ),
        Exercise(
            id = 7,
            name = "Chest Dip",
            category = "Chest",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Dip Bars"
        )
    )

    private fun resolve(name: String): Exercise? =
        catalog.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private fun parse(
        json: String,
        weekStartsOnMonday: Boolean = true
    ): AiRoutineSchedule.Parsed = runBlocking {
        AiRoutineSchedule.parse(
            rawResponse = json,
            resolveExercise = { resolve(it) },
            weekStartsOnMonday = weekStartsOnMonday
        )
    }

    /** The names on a given weekday. */
    private fun AiRoutineSchedule.Parsed.namesOn(weekday: Int): List<String> =
        days.firstOrNull { it.weekday == weekday }?.exercises?.map { it.exerciseName } ?: emptyList()

    /** The weekdays that hold a workout. */
    private fun AiRoutineSchedule.Parsed.trainingWeekdays(): List<Int> =
        days.filter { !it.isRestDay }.map { it.weekday }

    private fun exercise(name: String, sets: Int = 3, reps: Int = 8) = """
        {"name": "$name", "sets": $sets, "reps": $reps, "weight": 0.0,
         "holdSeconds": 0, "prescription": "$sets sets x $reps reps"}
    """.trimIndent()

    // === Case 1: every weekday named ===

    @Test
    fun `a routine with all seven weekdays assigned keeps every day`() {
        val json = """
            {"routine_name": "Full week",
             "days": [
               {"weekday": "Monday", "focus": "Push", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Tuesday", "focus": "Pull", "exercises": [${exercise("Pull-Up")}]},
               {"weekday": "Wednesday", "focus": "Legs", "exercises": [${exercise("Barbell Full Squat")}]},
               {"weekday": "Thursday", "focus": "Push", "exercises": [${exercise("Dip")}]},
               {"weekday": "Friday", "focus": "Pull", "exercises": [${exercise("Barbell Row")}]},
               {"weekday": "Saturday", "focus": "Legs", "exercises": [${exercise("Barbell Deadlift")}]},
               {"weekday": "Sunday", "focus": "Rest", "exercises": []}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(7, parsed.days.size)
        assertEquals(
            listOf("Barbell Bench Press"),
            parsed.namesOn(Weekday.MONDAY)
        )
        assertEquals(
            listOf("Barbell Deadlift"),
            parsed.namesOn(Weekday.SATURDAY)
        )
        // The day with no exercise in it is a rest day.
        assertTrue(parsed.days.first { it.weekday == Weekday.SUNDAY }.isRestDay)
        assertEquals(6, parsed.trainingDays)
    }

    // === Case 2: only Monday and Wednesday ===

    @Test
    fun `a routine naming only monday and wednesday leaves the rest of the week resting`() {
        val json = """
            {"routine_name": "Two days",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Wednesday", "exercises": [${exercise("Pull-Up")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf(Weekday.MONDAY, Weekday.WEDNESDAY), parsed.trainingWeekdays())
        assertEquals(5, parsed.days.count { it.isRestDay })
        // Tuesday in between stays a rest day, it is never filled with a repeat.
        assertTrue(parsed.namesOn(Weekday.TUESDAY).isEmpty())
    }

    // === Case 3: four sequential workouts, no weekday names ===

    @Test
    fun `four unnamed workouts land on the first four days of a monday first week`() {
        val json = """
            {"routine_name": "Four days",
             "exercises": [
               {"name": "Barbell Bench Press", "sets": 4, "reps": 8, "prescription": "4 sets x 8 reps", "day": 1},
               {"name": "Pull-Up", "sets": 4, "reps": 6, "prescription": "4 sets x 6 reps", "day": 2},
               {"name": "Barbell Full Squat", "sets": 4, "reps": 5, "prescription": "4 sets x 5 reps", "day": 3},
               {"name": "Barbell Row", "sets": 4, "reps": 8, "prescription": "4 sets x 8 reps", "day": 4}
             ]}
        """.trimIndent()

        val parsed = parse(json, weekStartsOnMonday = true)
        assertTrue(parsed.isValid)
        assertEquals(
            listOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY),
            parsed.trainingWeekdays()
        )
        assertEquals(listOf("Barbell Bench Press"), parsed.namesOn(Weekday.MONDAY))
        assertEquals(listOf("Barbell Row"), parsed.namesOn(Weekday.THURSDAY))
        assertTrue(parsed.namesOn(Weekday.FRIDAY).isEmpty())
    }

    // === Case 4: the same input, Sunday first ===

    @Test
    fun `the same four unnamed workouts land on the first four days of a sunday first week`() {
        val json = """
            {"routine_name": "Four days",
             "exercises": [
               {"name": "Barbell Bench Press", "sets": 4, "reps": 8, "prescription": "4 sets x 8 reps", "day": 1},
               {"name": "Pull-Up", "sets": 4, "reps": 6, "prescription": "4 sets x 6 reps", "day": 2},
               {"name": "Barbell Full Squat", "sets": 4, "reps": 5, "prescription": "4 sets x 5 reps", "day": 3},
               {"name": "Barbell Row", "sets": 4, "reps": 8, "prescription": "4 sets x 8 reps", "day": 4}
             ]}
        """.trimIndent()

        val parsed = parse(json, weekStartsOnMonday = false)
        assertTrue(parsed.isValid)
        assertEquals(
            listOf(Weekday.SUNDAY, Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY),
            parsed.trainingWeekdays()
        )
        assertEquals(listOf("Barbell Bench Press"), parsed.namesOn(Weekday.SUNDAY))
        assertEquals(listOf("Barbell Row"), parsed.namesOn(Weekday.WEDNESDAY))
    }

    // === Case 5: an explicit rest day between the workouts ===

    @Test
    fun `an explicit rest day between the workouts is kept`() {
        val json = """
            {"routine_name": "With a rest",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Tuesday", "rest": true, "exercises": []},
               {"weekday": "Wednesday", "exercises": [${exercise("Pull-Up")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf(Weekday.MONDAY, Weekday.WEDNESDAY), parsed.trainingWeekdays())
        assertTrue(parsed.days.first { it.weekday == Weekday.TUESDAY }.isRestDay)
    }

    // === Case 6: several omitted days ===

    @Test
    fun `a routine with several omitted days keeps the days it does name`() {
        val json = """
            {"routine_name": "Sparse",
             "days": [
               {"weekday": "Wednesday", "exercises": [${exercise("Pull-Up")}]},
               {"weekday": "Saturday", "exercises": [${exercise("Barbell Full Squat")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf(Weekday.WEDNESDAY, Weekday.SATURDAY), parsed.trainingWeekdays())
        assertEquals(5, parsed.days.count { it.isRestDay })
    }

    // === Case 7: explicit weekdays given out of order ===

    @Test
    fun `explicit weekdays given out of order land on the days they name`() {
        val json = """
            {"routine_name": "Out of order",
             "days": [
               {"weekday": "Friday", "exercises": [${exercise("Barbell Row")}]},
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Wednesday", "exercises": [${exercise("Pull-Up")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf("Barbell Row"), parsed.namesOn(Weekday.FRIDAY))
        assertEquals(listOf("Barbell Bench Press"), parsed.namesOn(Weekday.MONDAY))
        assertEquals(listOf("Pull-Up"), parsed.namesOn(Weekday.WEDNESDAY))
    }

    // === Case 8: duplicated weekday assignments ===

    @Test
    fun `two entries that claim the same weekday are merged into it`() {
        val json = """
            {"routine_name": "Duplicated",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Monday", "exercises": [${exercise("Dip")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        // Nothing is lost: both entries end up on the Monday.
        assertEquals(
            listOf("Barbell Bench Press", "Dip"),
            parsed.namesOn(Weekday.MONDAY)
        )
        assertEquals(1, parsed.trainingDays)
    }

    // === Case 9: an incomplete or malformed answer ===

    @Test
    fun `an answer that is not json at all is rejected`() {
        val parsed = parse("I am terribly sorry, I could not do that.")
        assertFalse(parsed.isValid)
        assertTrue(parsed.days.isEmpty())
        assertTrue(parsed.errorReason.isNotBlank())
    }

    @Test
    fun `an answer with no exercise at all is rejected`() {
        val parsed = parse("""{"routine_name": "Empty", "exercises": []}""")
        assertFalse(parsed.isValid)
        assertTrue(parsed.errorReason.isNotBlank())
    }

    @Test
    fun `an answer that refuses the request is rejected with its reason`() {
        val parsed = parse(
            """{"is_valid_request": false, "error_reason": "Your prompt is too vague."}"""
        )
        assertFalse(parsed.isValid)
        assertEquals("Your prompt is too vague.", parsed.errorReason)
    }

    @Test
    fun `an answer that says the input is gibberish is rejected`() {
        val parsed = parse("""{"is_gibberish": true, "error_reason": "Unreadable input."}""")
        assertFalse(parsed.isValid)
        assertEquals("Unreadable input.", parsed.errorReason)
    }

    // === Case 10: no valid exercise on any day ===

    @Test
    fun `a routine with no valid exercise on any day is rejected`() {
        val json = """
            {"routine_name": "Nothing",
             "days": [
               {"weekday": "Monday", "exercises": []},
               {"weekday": "Tuesday", "exercises": []},
               {"weekday": "Wednesday", "exercises": []}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertFalse(parsed.isValid)
        assertTrue(parsed.days.isEmpty())
    }

    // === Case 11: fewer than seven entries ===

    @Test
    fun `a routine with fewer than seven workout days still has seven slots`() {
        val json = """
            {"routine_name": "Three days",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Tuesday", "exercises": [${exercise("Pull-Up")}]},
               {"weekday": "Wednesday", "exercises": [${exercise("Barbell Full Squat")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(7, parsed.days.size)
        assertEquals(3, parsed.trainingDays)
        assertEquals(4, parsed.days.count { it.isRestDay })
    }

    // === Case 12: explicit weekdays despite the week start preference ===

    @Test
    fun `explicit weekdays are honoured whatever the week start is`() {
        val json = """
            {"routine_name": "Explicit",
             "days": [
               {"weekday": "Tuesday", "exercises": [${exercise("Barbell Bench Press")}]},
               {"weekday": "Thursday", "exercises": [${exercise("Pull-Up")}]}
             ]}
        """.trimIndent()

        val mondayFirst = parse(json, weekStartsOnMonday = true)
        val sundayFirst = parse(json, weekStartsOnMonday = false)

        assertEquals(listOf(Weekday.TUESDAY, Weekday.THURSDAY), mondayFirst.trainingWeekdays())
        assertEquals(listOf(Weekday.TUESDAY, Weekday.THURSDAY), sundayFirst.trainingWeekdays())
        assertEquals(listOf("Barbell Bench Press"), sundayFirst.namesOn(Weekday.TUESDAY))
    }

    // === The details of the exercises ===

    @Test
    fun `the details of an exercise are kept`() {
        val json = """
            {"routine_name": "Details",
             "days": [
               {"weekday": "Monday", "focus": "Upper Push", "exercises": [
                 {"name": "Barbell Bench Press", "sets": 5, "reps": 3, "weight": 60.0,
                  "holdSeconds": 0, "prescription": "5 sets x 3 reps", "addedWeight": 0.0}
               ]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        val item = parsed.days.first { it.weekday == Weekday.MONDAY }.exercises.first()
        assertEquals("Barbell Bench Press", item.exerciseName)
        assertEquals(1L, item.exerciseId)
        assertEquals(5, item.targetSets)
        assertEquals(3, item.targetReps)
        assertEquals(60.0, item.targetWeight, 0.0001)
        assertEquals("5 sets x 3 reps", item.prescription)
        assertEquals("Upper Push", item.dayTitle)
    }

    @Test
    fun `a note attached to a day is kept on every exercise of that day`() {
        val json = """
            {"routine_name": "With a note",
             "days": [
               {"weekday": "Monday", "note": "Leave two reps in reserve.",
                "exercises": [${exercise("Barbell Bench Press")}, ${exercise("Dip")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        val monday = parsed.days.first { it.weekday == Weekday.MONDAY }
        assertEquals("Leave two reps in reserve.", monday.note)
        assertTrue(monday.exercises.all { it.dayNote.contains("two reps in reserve") })
    }

    @Test
    fun `a prose line that leaked into the exercises list is never an exercise`() {
        val json = """
            {"routine_name": "Prose",
             "days": [
               {"weekday": "Monday", "exercises": [
                 {"name": "Welcome to your program, designed to build strength.", "sets": 3, "reps": 8},
                 ${exercise("Barbell Bench Press")}
               ]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf("Barbell Bench Press"), parsed.namesOn(Weekday.MONDAY))
    }

    @Test
    fun `an answer wrapped in prose and a code fence is still read`() {
        val json = """
            Here is your routine:

            ```json
            {"routine_name": "Fenced",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]}
             ]}
            ```

            Enjoy!
        """.trimIndent()

        val parsed = parse(json)
        assertTrue(parsed.isValid)
        assertEquals(listOf("Barbell Bench Press"), parsed.namesOn(Weekday.MONDAY))
    }

    @Test
    fun `the name of the routine and the goal are read`() {
        val json = """
            {"routine_name": "Push Pull Legs", "routine_goal": "Hypertrophy",
             "days": [
               {"weekday": "Monday", "exercises": [${exercise("Barbell Bench Press")}]}
             ]}
        """.trimIndent()

        val parsed = parse(json)
        assertEquals("Push Pull Legs", parsed.routineName)
        assertEquals("Hypertrophy", parsed.routineGoal)
        assertNotNull(parsed.days.firstOrNull { it.weekday == Weekday.MONDAY })
    }
}
