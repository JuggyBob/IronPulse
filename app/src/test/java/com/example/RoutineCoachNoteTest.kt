package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The AI routine generator answers with markdown that mixes exercises and a written coaching note.
 * The note must become the description of the training day and must NEVER be turned into an
 * exercise (that used to show up as a bogus "Coach's Note" exercise inside the workout).
 */
@RunWith(RobolectricTestRunner::class)
class RoutineCoachNoteTest {

    private fun ex(name: String) = Exercise(
        id = name.hashCode().toLong(),
        name = name,
        category = "Chest",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell",
        description = ""
    )

    private val catalog = listOf(
        ex("Barbell Bench Press"),
        ex("Barbell Squat"),
        ex("Barbell Row"),
        ex("Pull-Up"),
        ex("Dip"),
        ex("Lateral Raise")
    )

    private val aiProgram = """
        ### Day 1: Upper Body Push

        **Coach's Note:** Warm up with an empty bar and leave 2 reps in reserve on every working set.
        Focus on full range of motion and controlled tempo throughout.

        EXERCISE: Barbell Bench Press | 4 sets x 8 reps
        EXERCISE: Dip | 3 sets x 10 reps

        ### Day 2: Upper Body Pull

        **Coach's Note:** Keep a neutral grip and avoid swinging on every rep.

        EXERCISE: Pull-Up | 4 sets x 8 reps
        EXERCISE: Barbell Row | 4 sets x 10 reps
    """.trimIndent()

    @Test
    fun `coach note becomes the day description and never an exercise`() {
        val days = RoutineParser.parseDays(aiProgram, catalog)

        assertEquals(2, days.size)

        // No day may contain an exercise that came from the note
        days.forEach { day ->
            day.exercises.forEach { item ->
                val lower = item.exerciseName.lowercase()
                assertFalse("note leaked into an exercise: ${item.exerciseName}", lower.contains("coach"))
                assertFalse("note leaked into an exercise: ${item.exerciseName}", lower.startsWith("focus"))
                assertFalse("note leaked into an exercise: ${item.exerciseName}", lower.startsWith("keep"))
                assertFalse("note leaked into an exercise: ${item.exerciseName}", lower.startsWith("warm"))
            }
        }

        assertEquals(listOf("Barbell Bench Press", "Dip"), days[0].exercises.map { it.exerciseName })
        assertEquals(listOf("Pull-Up", "Barbell Row"), days[1].exercises.map { it.exerciseName })

        assertTrue("day 1 note missing", days[0].note.contains("2 reps in reserve"))
        assertTrue("day 1 note missing second sentence", days[0].note.contains("full range of motion"))
        assertTrue("day 2 note missing", days[1].note.contains("neutral grip"))

        // The markdown decorations must be gone
        assertFalse(days[0].note.contains("*"))
    }

    @Test
    fun `day note survives the save and reload round trip`() {
        val days = RoutineParser.parseDays(aiProgram, catalog)
        val saved = RoutineParser.serializeExercises(days.flatMap { it.exercises })
        val reloaded = RoutineParser.parseDays(saved, catalog)

        assertEquals(2, reloaded.size)
        assertEquals(days[0].note, reloaded[0].note)
        assertEquals(days[1].note, reloaded[1].note)
    }

    @Test
    fun `routines saved before the note existed still load`() {
        val legacyJson = """
            [{"id":10,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT","category":"Chest",
              "equipment":"Barbell","sets":4,"reps":8,"weight":0.0,"holdSeconds":0,
              "prescription":"4 sets x 8 reps","day":1,"dayTitle":"Day 1"}]
        """.trimIndent()

        val days = RoutineParser.parseDays(legacyJson, catalog)
        assertEquals(1, days.size)
        assertEquals("", days[0].note)
        assertEquals("Barbell Bench Press", days[0].exercises.first().exerciseName)
    }

    @Test
    fun `real exercise lines are never mistaken for a note`() {
        val exerciseLines = listOf(
            "Barbell Bench Press",
            "Barbell Squat",
            "EXERCISE: Barbell Bench Press | 4 sets x 8 reps",
            "Barbell Bench Press - 4x8",
            "Weighted Dip",
            "Lateral Raise",
            "EZ-Bar Curl",
            "Incline Dumbbell Fly",
            "Cable Lateral Raise",
            "Push-Up",
            "Pull-Up",
            "Kettlebell Swing",
            "Romanian Deadlift",
            "Face Pull"
        )

        exerciseLines.forEach { line ->
            assertFalse("real exercise classified as a note: $line", RoutineParser.isCoachNoteLine(line))
        }
    }

    @Test
    fun `note lines are recognised`() {
        val noteLines = listOf(
            "**Coach's Note:** Leave 2 reps in reserve.",
            "Coach's Note: keep the tempo strict",
            "> Brace your core before every rep.",
            "- Focus on controlled eccentrics.",
            "Remember to hydrate before the session.",
            "Prioritize the heaviest compound lift first.",
            "Progression: add 2.5kg next week.",
            "Intensity: moderate, about 3 reps in reserve.",
            "Use 80 percent of your max on the accessory work.",
            "Rest 90 seconds between sets on the main lift."
        )

        noteLines.forEach { line ->
            assertTrue("note line not recognised: $line", RoutineParser.isCoachNoteLine(line))
        }
    }

    @Test
    fun `a bare note label carries no description`() {
        val text = """
            ### Day 1: Full Body

            **Coach's Note:**

            Keep the tempo strict and breathe out on the concentric.
            EXERCISE: Barbell Squat | 5 sets x 5 reps
        """.trimIndent()

        val days = RoutineParser.parseDays(text, catalog)
        assertEquals(1, days.size)
        assertEquals(1, days[0].exercises.size)
        assertEquals("Keep the tempo strict and breathe out on the concentric.", days[0].note)
    }
}