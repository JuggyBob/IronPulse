package com.example

import com.example.data.model.BodyWeightLog
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.Exercise
import com.example.data.model.ExerciseSearch
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineParser
import com.example.data.model.describeLoggedSetEffort
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The exercise search must rank by how close the typed text is to the exercise name, character by
 * character. An alphabetical list hides the exercise the user wants in the middle of similarly
 * named ones ("Archer Push Ups", "Weighted Push Ups", ...).
 */
class ExerciseSearchTest {

    private fun ex(name: String, category: String = "Chest", equipment: String = "Bodyweight") =
        Exercise(
            id = name.hashCode().toLong(),
            name = name,
            category = category,
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = equipment
        )

    private val catalog = listOf(
        ex("Archer Push Ups"),
        ex("Weighted Push Ups"),
        ex("Push Ups"),
        ex("Push-Up", category = "Chest"),
        ex("Decline Push Up", category = "Chest"),
        ex("Pike Push Up"),
        ex("Barbell Bench Press", category = "Chest", equipment = "Barbell"),
        ex("Bench Press", category = "Chest", equipment = "Barbell"),
        ex("Incline Bench Press", category = "Chest", equipment = "Barbell"),
        ex("Dumbbell Bench Press", category = "Chest", equipment = "Dumbbells"),
        ex("Pull-Up", category = "Back"),
        ex("Weighted Pull-Up", category = "Back"),
        ex("Chin-Up", category = "Back"),
        ex("Lateral Raise", category = "Shoulders", equipment = "Dumbbells")
    )

    @Test
    fun `an exact name match always comes first`() {
        val names = ExerciseSearch.rank(catalog, "Push Ups").map { it.name }
        assertEquals("Push Ups", names.first())
        // The other push variations follow, but never before the exact match
        assertTrue(names.containsAll(listOf("Archer Push Ups", "Weighted Push Ups", "Pike Push Up")))
    }

    @Test
    fun `a shorter exact name wins over a longer one containing it`() {
        val names = ExerciseSearch.rank(catalog, "push up").map { it.name }
        // "Push-Up" normalizes to "push up": an exact hit, so it beats "Archer Push Ups"
        assertEquals("push up", ExerciseSearch.normalize(names.first()))
        assertTrue(names.contains("Push-Up"))
        assertTrue(names.indexOf("Push-Up") < names.indexOf("Archer Push Ups"))
        assertTrue(names.indexOf("Push-Up") < names.indexOf("Decline Push Up"))
    }

    @Test
    fun `typos and plurals are tolerated`() {
        val names = ExerciseSearch.rank(catalog, "lateral reise").map { it.name }
        assertEquals("Lateral Raise", names.first())

        val plural = ExerciseSearch.rank(catalog, "push-up").map { it.name }
        assertTrue(plural.contains("Push Ups"))
        assertTrue(plural.contains("Archer Push Ups"))
    }

    @Test
    fun `matching a muscle group still works and ranks below name matches`() {
        val names = ExerciseSearch.rank(catalog, "chest").map { it.name }
        assertTrue("category match must still return exercises", names.isNotEmpty())
        assertTrue(names.contains("Barbell Bench Press"))

        // A name that contains the query always beats a pure category match
        val withBoth = ExerciseSearch.rank(catalog, "bench").map { it.name }
        assertEquals("Bench Press", withBoth.first())
        assertTrue(withBoth.contains("Barbell Bench Press"))
        assertTrue(withBoth.contains("Incline Bench Press"))
    }

    @Test
    fun `an unrelated query does not drag the whole catalog back in`() {
        val names = ExerciseSearch.rank(catalog, "zzzzqqqq").map { it.name }
        assertTrue("nonsense query must not match anything", names.isEmpty())
    }

    @Test
    fun `a blank query returns everything untouched`() {
        assertEquals(catalog.size, ExerciseSearch.rank(catalog, "   ").size)
    }

    @Test
    fun `normalize strips punctuation and collapses spacing`() {
        assertEquals("push up", ExerciseSearch.normalize("  Push-Up  "))
        assertEquals("barbell bench press", ExerciseSearch.normalize("Barbell / Bench, Press"))
        assertEquals("biceps curl 45", ExerciseSearch.normalize("Biceps Curl (45)"))
    }

    @Test
    fun `similarity is maximal for identical strings and lower for typos`() {
        assertEquals(1.0, ExerciseSearch.bigramSimilarity("bench press", "bench press"), 0.0001)
        assertTrue(ExerciseSearch.bigramSimilarity("bench press", "bench pres") > 0.7)
        assertTrue(ExerciseSearch.bigramSimilarity("bench press", "zzz") < 0.2)
    }
}

/**
 * A body weight set must always use the athlete profile weight stat.
 *
 * Weigh-ins are optional (Delete App Data or deleting the weigh-in log must not strip the weight
 * from every body weight exercise) but the profile stat is required, so it is the only source.
 */
class BodyWeightTimelineTest {

    @Test
    fun `the athlete profile weight is used`() {
        assertEquals(77.5, BodyWeightTimeline.currentWeightKg(77.5), 0.001)
        assertEquals(77.5, BodyWeightTimeline.currentWeightOrNull(77.5)!!, 0.001)
    }

    @Test
    fun `a profile edit immediately changes the body weight exercises use`() {
        // The athlete edits the profile popup: the exercises must follow the new stat right away
        val before = BodyWeightTimeline.currentWeightKg(75.0)
        val after = BodyWeightTimeline.currentWeightKg(81.0)
        assertEquals(75.0, before, 0.001)
        assertEquals(81.0, after, 0.001)
    }

    @Test
    fun `an unusable profile weight falls back instead of showing nothing`() {
        // 0 is not a body weight: the exercises would otherwise have no weight at all
        assertEquals(BodyWeightTimeline.FALLBACK_KG, BodyWeightTimeline.currentWeightKg(0.0), 0.001)
        assertEquals(null, BodyWeightTimeline.currentWeightOrNull(0.0))
    }

    @Test
    fun `the weigh-in timeline helper is only for the weight chart`() {
        val logs = listOf(
            BodyWeightLog(weightKg = 75.0, timestamp = 1000L, note = ""),
            BodyWeightLog(weightKg = 76.0, timestamp = 2000L, note = "")
        )
        assertEquals(75.0, BodyWeightTimeline.weighInAt(logs, 1500L)!!, 0.001)
        assertEquals(76.0, BodyWeightTimeline.weighInAt(logs, 9999L)!!, 0.001)
        // Before the very first weigh-in there is nothing on the timeline
        assertEquals(null, BodyWeightTimeline.weighInAt(logs, 500L))
    }

    @Test
    fun `deleting every weigh-in never affects the exercise body weight`() {
        val logs = emptyList<BodyWeightLog>()
        // No weigh-in at all, but the profile stat is still there
        assertEquals(80.0, BodyWeightTimeline.currentWeightKg(80.0), 0.001)
        assertEquals(null, BodyWeightTimeline.weighInAt(logs, System.currentTimeMillis()))
    }
}

/**
 * Reps and hold seconds are independent inputs: showing only the hold hid the reps the athlete
 * had entered (an explosive pull-up is 3 reps, a plank is 30 seconds).
 */
class LoggedSetEffortTest {

    private fun set(reps: Int, hold: Int, type: String = ExerciseType.PURE_BODYWEIGHT) = LoggedSet(
        id = 1L,
        sessionId = 1L,
        exerciseId = 1L,
        exerciseName = "Explosive Pull-ups",
        exerciseType = type,
        setNumber = 1,
        weight = 0.0,
        addedWeight = 0.0,
        reps = reps,
        holdDurationSeconds = hold,
        timestamp = 0L
    )

    @Test
    fun `reps are shown when there is no hold`() {
        assertEquals("12 reps", describeLoggedSetEffort(set(reps = 12, hold = 0)))
    }

    @Test
    fun `the hold is shown when there is no rep`() {
        assertEquals("30s hold", describeLoggedSetEffort(set(reps = 0, hold = 30)))
    }

    @Test
    fun `a hold never hides the reps`() {
        val text = describeLoggedSetEffort(set(reps = 3, hold = 30))
        assertTrue("reps must be listed", text.contains("3 reps"))
        assertTrue("hold must be listed", text.contains("30s hold"))
    }

    @Test
    fun `an empty set says so instead of showing a bare zero`() {
        assertEquals("no reps logged", describeLoggedSetEffort(set(reps = 0, hold = 0)))
    }
}

/**
 * The AI routine generator used to ask for markdown, and the model's intro sentence became the
 * first exercise of the first day. It now has to answer with JSON and every flow drops prose.
 */
@RunWith(RobolectricTestRunner::class)
class AiRoutineJsonParsingTest {

    private val catalog = listOf(
        Exercise(
            id = 1L,
            name = "Explosive Pull-ups",
            category = "Back",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Pull-up Bar"
        ),
        Exercise(
            id = 2L,
            name = "Pull-up",
            category = "Back",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Pull-up Bar"
        )
    )

    @Test
    fun `json wrapped in prose and a code fence is extracted`() {
        val answer = """
            Welcome to your program!

            Here it is:
            ```json
            {"routine_name": "Pull Day", "exercises": [{"name": "Pull-up", "sets": 4, "reps": 8}]}
            ```
            Enjoy!
        """.trimIndent()

        val json = JSONObject(RoutineParser.extractJson(answer))
        assertEquals("Pull Day", json.optString("routine_name"))
        assertEquals(1, json.optJSONArray("exercises")!!.length())
    }

    @Test
    fun `braces inside strings do not break the extraction`() {
        val answer = """{"routine_name": "A {weird} title", "exercises": []} hope this helps {not json}"""
        val json = JSONObject(RoutineParser.extractJson(answer))
        assertEquals("A {weird} title", json.optString("routine_name"))
    }

    @Test
    fun `an intro sentence is rejected as an exercise name`() {
        assertTrue(
            RoutineParser.looksLikeProseNotExercise(
                "Welcome to your Muscle-up & Calisthenics Mastery program, designed..."
            )
        )
        assertTrue(RoutineParser.looksLikeProseNotExercise("Here's the plan."))
        assertTrue(RoutineParser.looksLikeProseNotExercise("Focus on controlled eccentric."))
        assertTrue(RoutineParser.looksLikeProseNotExercise(""))
    }

    @Test
    fun `real exercise names are never rejected`() {
        val names = listOf(
            "Explosive Pull-ups",
            "Pull-up",
            "Barbell Bench Press",
            "Single-Leg Bulgarian Split Squat",
            "Dumbbell Incline Row with Supinated Grip",
            "TRX Push-up",
            "Jefferson Deadlift"
        )
        names.forEach {
            assertFalse("real exercise rejected: $it", RoutineParser.looksLikeProseNotExercise(it))
        }
    }

    @Test
    fun `a markdown answer no longer leaks prose into the exercise list`() {
        // What the old prompt produced: a greeting, a note and real exercises
        val legacyMarkdown = """
            ### Day 1: Pull & Muscle-up Foundation
            Welcome to your Muscle-up & Calisthenics Mastery program, designed to progress you steadily.
            EXERCISE: Pull-up | 4 sets x 5 reps
            EXERCISE: Explosive Pull-ups | 4 sets x 3 reps
            Focus on pulling force, scapular control, and building explosive pulling power.
        """.trimIndent()

        val days: List<com.example.data.model.RoutineDay> = RoutineParser.parseDays(legacyMarkdown, catalog)

        assertEquals(1, days.size)
        val names: List<String> = days[0].exercises.map { item -> item.exerciseName }
        assertEquals(listOf("Pull-up", "Explosive Pull-ups"), names)
        assertTrue("the intro must not become an exercise", days[0].note.isNotBlank())
    }
}