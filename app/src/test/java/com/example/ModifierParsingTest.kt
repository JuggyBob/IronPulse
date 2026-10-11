package com.example

import com.example.data.model.Exercise
import com.example.data.model.RoutineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Modifiers must survive the parser.
 *
 * The reported bug: a pasted workout containing "Decline Push Ups" came out as plain "Push-Up". That
 * is not a cosmetic slip, because the two movements are different work — a decline push-up loads the
 * upper chest and front deltoid far more heavily, so the logged history for one is not history for
 * the other.
 *
 * The same reasoning applies to every modifier that names a different movement: incline, close grip,
 * wide grip, reverse, pike, archer, explosive, and the rest of [RoutineParser.MODIFIER_WORDS]. A
 * modifier in a query is a claim about which exercise this is, so the matcher may not quietly drop it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModifierParsingTest {

    private fun exercise(name: String, type: String = "STANDARD_WEIGHT") = Exercise(
        name = name,
        category = "Chest",
        exerciseType = type,
        description = "",
        equipment = "Bodyweight"
    )
    private val library = listOf(
        exercise("Push-Up"),
        exercise("Decline Push-Up"),
        exercise("Incline Push-Up"),
        exercise("Close-Grip Push-Up"),
        exercise("Wide-Grip Push-Up"),
        exercise("Pike Push-Up"),
        exercise("Archer Push-Up"),
        exercise("Barbell Bench Press"),
        exercise("Barbell Incline Bench Press"),
        exercise("Barbell Decline Bench Press"),
        exercise("Barbell Close-Grip Bench Press"),
        exercise("Squat"),
        exercise("Barbell Full Squat"),
        exercise("Explosive Push-Up", "PURE_BODYWEIGHT"),
        exercise("Hindu Push-Up", "PURE_BODYWEIGHT")
    )

    private fun matchedName(line: String): String {
        val days = RoutineParser.parseExercises(line, library)
        return days.first().exerciseName
    }

    @Test
    fun `decline push ups parses to the decline variant and not the plain push up`() {
        val name = matchedName("Decline Push Ups - 3 sets of 12 reps")

        // "Push-Up" is what the athlete did not type, and it is the closest plain match, so this is
        // exactly the failure that was reported.
        assertTrue(
            "decline push ups must not collapse onto a plain push up, got: $name",
            name != "Push-Up"
        )
        assertEquals("Decline Push-Up", name)
    }

    @Test
    fun `the plain push up still parses to the plain push up`() {
        // The fix must not swing the other way: an unmodified name has to keep matching simply.
        assertEquals("Push-Up", matchedName("Push Ups - 4 sets of 15 reps"))
        assertEquals("Push-Up", matchedName("pushups"))
    }

    @Test
    fun `every modifier that names a different movement survives`() {
        val cases = mapOf(
            "Incline Push Ups - 3 x 12" to "Incline Push-Up",
            "Close-Grip Push Ups - 3 x 12" to "Close-Grip Push-Up",
            "Wide-Grip Push Ups - 3 x 12" to "Wide-Grip Push-Up",
            "Pike Push Ups - 3 x 10" to "Pike Push-Up",
            "Archer Push Ups - 3 x 8" to "Archer Push-Up",
            "Hindu Push Ups - 3 x 15" to "Hindu Push-Up",
            "Explosive Push Ups - 5 x 3" to "Explosive Push-Up"
        )

        for ((line, expected) in cases) {
            val actual = matchedName(line)
            assertEquals("line: $line", expected, actual)
        }
    }

    @Test
    fun `the same applies to bench press modifiers`() {
        val cases = mapOf(
            "Incline Bench Press - 4 x 8 @ 60 kg" to "Barbell Incline Bench Press",
            "Decline Bench Press - 4 x 8 @ 60 kg" to "Barbell Decline Bench Press",
            "Close Grip Bench Press - 4 x 10 @ 50 kg" to "Barbell Close-Grip Bench Press",
            "Bench Press - 4 x 8 @ 60 kg" to "Barbell Bench Press"
        )

        for ((line, expected) in cases) {
            assertEquals("line: $line", expected, matchedName(line))
        }
    }

    @Test
    fun `a modifier that has no catalog entry does not silently become the plain exercise`() {
        // "Decline Push-Up" exists, so this one must resolve. But a modifier the catalog genuinely
        // lacks must not be dropped on the floor: an unmatched name keeps the athlete's words, and
        // the routine editor shows it as an exercise to create rather than mislabelled history.
        val days = RoutineParser.parseExercises("Zottman Curls - 3 x 12", library)
        assertNotNull(days.first().exerciseName)
        assertTrue(
            "an unmatched compound name must survive rather than be trimmed to a root",
            days.first().exerciseName.contains("Zottman", ignoreCase = true)
        )
    }

    @Test
    fun `a full multi line paste keeps the modifiers on every line`() {
        val paste = """
            Day 1 - Push
            Bench Press - 4 x 8 @ 60 kg
            Incline Bench Press - 3 x 10 @ 40 kg
            Decline Push Ups - 3 x 12
            Close-Grip Push Ups - 3 x 10
        """.trimIndent()

        val days = RoutineParser.parseDays(paste, library)
        val names = days.first().exercises.map { it.exerciseName }

        assertEquals(
            listOf(
                "Barbell Bench Press",
                "Barbell Incline Bench Press",
                "Decline Push-Up",
                "Close-Grip Push-Up"
            ),
            names
        )
    }

    /*
     * The AI path. The parser is fine; the loss happens earlier, when the model flattens the name and
     * the app's exact-name lookup then succeeds. These cover the recovery step.
     */

    @Test
    fun `a flattened ai name is recovered from the source text`() {
        val source = "Decline Push Ups - 3 sets of 12 reps"

        // The AI returned the plain movement; the athlete typed a decline.
        val resolved = RoutineParser.resolveAgainstSource("Push-Up", source, library)

        assertNotNull("the decline must be recoverable from what the athlete typed", resolved)
        assertEquals("Decline Push-Up", resolved!!.name)
    }

    @Test
    fun `the plain push up is left to the normal path`() {
        val source = "Push Ups - 4 sets of 15 reps"

        // The mirror of the bug above, in the other direction: recovery must not add a modifier the
        // athlete never wrote. With nothing to correct there is nothing to return, and the ordinary
        // exact-name lookup handles this line correctly on its own.
        val resolved = RoutineParser.resolveAgainstSource("Push-Up", source, library)

        assertEquals(null, resolved)
    }

    @Test
    fun `recovery picks the right line when several mention the same movement`() {
        val source = """
            Day 1 - Push
            Bench Press - 4 x 8 @ 60 kg
            Decline Push Ups - 3 x 12
            Push Ups - 3 x 20
        """.trimIndent()

        // "Push-Up" as the AI name could belong to either push-up line. The one that carries the
        // modifier is the one the AI flattened, so it must win.
        val resolved = RoutineParser.resolveAgainstSource("Push-Up", source, library)

        assertEquals("Decline Push-Up", resolved?.name)
    }

    @Test
    fun `a name the source cannot recover is left alone`() {
        // The AI may legitimately invent nothing; where the source is silent there is nothing to
        // correct, and inventing a modifier would be as wrong as dropping one.
        val resolved = RoutineParser.resolveAgainstSource("Goblet Squat", "Bench Press - 4 x 8", library)

        assertEquals(null, resolved)
    }

    @Test
    fun `an unambiguous ai name resolves to itself`() {
        // Recovery only fires when the source carries a modifier the AI dropped, so a line where the
        // AI got it right either passes through or resolves back to the same exercise. Either way the
        // answer is unchanged, which is the property that matters: recovery must never move a name
        // that was already right.
        val source = "Barbell Bench Press - 4 x 8 @ 60 kg"
        val resolved = RoutineParser.resolveAgainstSource(
            "Barbell Bench Press",
            source,
            library
        )

        // "barbell" is itself a modifier word, so this line participates; the point is it resolves to
        // exactly the exercise the AI named, so recovery is a no-op here.
        assertEquals(null, resolved?.takeIf { it.name != "Barbell Bench Press" })
    }
}
