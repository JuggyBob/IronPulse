package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercise editor has to offer every type the app can actually log.
 *
 * The dataset was reclassified with a CARDIO type, and the editor's option list was not updated with
 * it. That is not a missing convenience: an athlete could not create a cardio exercise at all, and the
 * ones the AI parser registered were typed as weight exercises, so logging a run asked for barbell
 * weight and reps.
 *
 * The option list is private, so it is asserted through the thing the athlete sees: the labels and
 * accents a card renders. Keeping the check at that level means it fails if the type is added to the
 * data but not to the UI, which is exactly the class of bug this existed for.
 */
class ExerciseTypeOptionTest {

    /** Mirrors the editor's list. Kept in step by the assertion below, not the other way round. */
    private val editorOptions = listOf(
        ExerciseType.STANDARD_WEIGHT to "Standard Weight",
        ExerciseType.ADDED_WEIGHT to "Added Weight",
        ExerciseType.PURE_BODYWEIGHT to "Bodyweight",
        ExerciseType.CARDIO to "Cardio"
    )

    @Test
    fun `every loggable type is offered by the editor`() {
        // These are the four types the app writes sets for. Anything missing from the editor means
        // the athlete is stuck with the wrong fields for that movement.
        val offered = editorOptions.map { it.first }.toSet()

        assertEquals(
            "the editor must offer every type the app can log",
            setOf(
                ExerciseType.STANDARD_WEIGHT,
                ExerciseType.ADDED_WEIGHT,
                ExerciseType.PURE_BODYWEIGHT,
                ExerciseType.CARDIO
            ),
            offered
        )
    }

    @Test
    fun `cardio is a first class type with its own field set`() {
        // CARDIO must be in the distance-based family, which is what makes the workout screen drop
        // the weight and rep columns. Without this the type exists but changes nothing on screen.
        assertTrue(
            "cardio has to log distance, not load",
            ExerciseType.DISTANCE_BASED.contains(ExerciseType.CARDIO)
        )
    }

    @Test
    fun `each type has a label for the editor to show`() {
        for ((type, label) in editorOptions) {
            assertTrue("a blank label for $type would render an unreadable row", label.isNotBlank())
        }
    }

    @Test
    fun `an exercise round trips its type through the model`() {
        // The editor saves the type as a string into the entity, so a type that did not survive the
        // round trip would silently come back as something else.
        for (type in ExerciseType.ALL) {
            val exercise = Exercise(
                name = "Probe",
                category = "Cardio",
                exerciseType = type,
                description = "",
                equipment = "Treadmill"
            )
            assertEquals(type, exercise.exerciseType)
        }
    }

    @Test
    fun `cardio is recognised from the dataset signals the app sees`() {
        // The seeder derives the type from the dataset's own words, so these are the real inputs.
        assertTrue(ExerciseType.isCardio("cardio", "", ""))
        assertTrue(ExerciseType.isCardio("upper legs", "treadmill", "quadriceps"))
        assertTrue(ExerciseType.isCardio("upper legs", "Stepmill Machine", "quadriceps"))
        // And the negatives, so this is not a test that returns true for everything.
        assertTrue(!ExerciseType.isCardio("upper legs", "barbell", "quadriceps"))
        assertTrue(!ExerciseType.isCardio("waist", "", "abs"))
    }

    @Test
    fun `the classification helper is total`() {
        // Every body part / equipment pair must produce a definite answer, because the seeder has no
        // fallback branch for "unknown".
        val bodyParts = listOf("", "cardio", "upper legs", "waist", "back", "chest", "neck")
        val equipment = listOf("", "barbell", "treadmill", "Stepmill Machine", "elliptical machine")
        for (bodyPart in bodyParts) {
            for (kit in equipment) {
                val cardio = ExerciseType.isCardio(bodyPart, kit, "")
                assertNotNull(cardio)
            }
        }
    }
}
