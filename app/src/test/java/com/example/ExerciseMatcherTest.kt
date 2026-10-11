package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import com.example.data.model.RoutineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fuzzy matching between an exercise name and the catalog, with the dataset sized catalog in mind.
 *
 * The dataset brings in near duplicate names that differ only by equipment or variant ("Smith
 * Bench Press" vs "Barbell Bench Press", "Kettlebell Goblet Squat" vs "Goblet Squat"), which is
 * exactly where a loose matcher starts handing back the wrong movement.
 */
class ExerciseMatcherTest {

    private val catalog = listOf(
        ex("Barbell Bench Press", "Chest"),
        ex("Barbell Close-Grip Bench Press", "Chest"),
        ex("Dumbbell Bench Press", "Chest"),
        ex("Smith Bench Press", "Chest"),
        // The dataset really does carry both spellings, which is why "smith machine bench press"
        // has to resolve to the second one rather than to the shorter first one.
        ex("Smith Machine Bench Press", "Chest"),
        ex("Barbell Full Squat", "Legs"),
        ex("Barbell Front Squat", "Legs"),
        ex("Goblet Squat", "Legs"),
        ex("Kettlebell Goblet Squat", "Legs"),
        ex("Pull-Up", "Back"),
        ex("Weighted Pull-Up", "Back"),
        ex("L-Pull-Up", "Back"),
        ex("Assisted Pull-Up", "Back"),
        ex("Band Assisted Pull-Up", "Back"),
        ex("Inverted Row", "Back"),
        ex("Inverted Row with Straps", "Back"),
        ex("Hanging Knee Raise", "Core"),
        ex("Hanging Leg Raise", "Core"),
        ex("Elbow Dips", "Arms"),
        ex("Lying Triceps Extension", "Arms"),
        ex("Overhead Triceps Extension", "Arms")
    )

    private fun ex(name: String, category: String) = Exercise(
        id = name.hashCode().toLong(),
        name = name,
        category = category,
        exerciseType = ExerciseType.STANDARD_WEIGHT
    )

    @Test
    fun `an exact title is returned unchanged`() {
        assertEquals(
            "Barbell Bench Press",
            RoutineParser.findMatchingExercise("Barbell Bench Press", catalog)?.name
        )
    }

    @Test
    fun `spelling and punctuation differences still match`() {
        assertEquals(
            "Pull-Up",
            RoutineParser.findMatchingExercise("pull up", catalog)?.name
        )
        assertEquals(
            "Pull-Up",
            RoutineParser.findMatchingExercise("pullup", catalog)?.name
        )
        assertEquals(
            "Pull-Up",
            RoutineParser.findMatchingExercise("  PULL-UP  ", catalog)?.name
        )
    }

    @Test
    fun `an equipment variant is never substituted for a plain one`() {
        // The heart of the rule: "bench press" is ambiguous between four catalog rows, so a bare
        // query must not silently become "Smith Bench Press" or "Barbell Close-Grip Bench Press".
        val match = RoutineParser.findMatchingExercise("bench press", catalog)
        assertEquals(
            "an unqualified \"bench press\" must land on the plain barbell movement",
            "Barbell Bench Press",
            match?.name
        )
    }

    @Test
    fun `a qualified variant matches its own row`() {
        // "Smith Machine Bench Press" has to win over "Smith Bench Press": the query carries a
        // modifier ("machine") the shorter row does not, and losing it would log a different bar path.
        assertEquals(
            "Smith Machine Bench Press",
            RoutineParser.findMatchingExercise("smith machine bench press", catalog)?.name
        )
        assertEquals(
            "Smith Bench Press",
            RoutineParser.findMatchingExercise("smith bench press", catalog)?.name
        )
        assertEquals(
            "Kettlebell Goblet Squat",
            RoutineParser.findMatchingExercise("kettlebell goblet squat", catalog)?.name
        )
    }

    @Test
    fun `a modifier on the query is not dropped`() {
        // "pull up" with no modifier is the plain pull-up, never the weighted or assisted variant.
        assertEquals(
            "Pull-Up",
            RoutineParser.findMatchingExercise("pull up", catalog)?.name
        )
        assertEquals(
            "Weighted Pull-Up",
            RoutineParser.findMatchingExercise("weighted pull up", catalog)?.name
        )
    }

    @Test
    fun `plural and singular forms collapse`() {
        assertEquals(
            "Hanging Knee Raise",
            RoutineParser.findMatchingExercise("hanging knee raises", catalog)?.name
        )
    }

    @Test
    fun `an exercise that is genuinely absent is not force matched`() {
        // Forcing these onto the nearest catalog row is how a routine ends up logging the wrong
        // movement; the caller is expected to create them instead.
        assertNull(RoutineParser.findMatchingExercise("Pelican Curl", catalog))
        assertNull(RoutineParser.findMatchingExercise("Jefferson Deadlift", catalog))
    }

    @Test
    fun `dips resolve to the catalog's dip rather than a triceps extension`() {
        assertEquals(
            "Elbow Dips",
            RoutineParser.findMatchingExercise("dips", catalog)?.name
        )
    }

    @Test
    fun `an inverted row with straps is not confused with the plain inverted row`() {
        assertEquals(
            "Inverted Row",
            RoutineParser.findMatchingExercise("inverted row", catalog)?.name
        )
        assertEquals(
            "Inverted Row with Straps",
            RoutineParser.findMatchingExercise("inverted row with straps", catalog)?.name
        )
    }

    @Test
    fun `the matcher stays fast enough to run on every keystroke`() {
        // The custom exercise name field matches against the whole catalog as the athlete types.
        // With 1300+ rows this is the hot path, so it is bounded rather than assumed.
        val big = (0 until 1400).map { i ->
            ex("Exercise Number $i", MuscleCategory.CHEST)
        }
        val started = System.nanoTime()
        repeat(50) {
            RoutineParser.findMatchingExercise("exercise number 777", big)
        }
        val perCallMillis = (System.nanoTime() - started) / 50 / 1_000_000.0
        assertTrue(
            "matching took ${perCallMillis}ms per call over 1400 exercises",
            perCallMillis < 250.0
        )
    }
}
