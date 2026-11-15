/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.ai.AiExerciseFactory
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AI-created exercises must be able to declare field combinations that no legacy type expresses.
 *
 * The factory is the validation boundary for every AI path, so an invalid combination falls back
 * to the type defaults instead of storing a configuration the UI cannot render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiExerciseFieldsTest {

    private fun json(
        type: String = ExerciseType.PURE_BODYWEIGHT,
        fields: String? = null
    ): String = buildString {
        append(
            """
            {"name":"Weighted Plank","category":"Core","bodyPart":"waist","target":"abs",
             "secondaryMuscles":[],"equipment":"Bodyweight","exerciseType":"$type",
             "instructions":"Brace and hold."
            """.trimIndent()
        )
        if (fields != null) append(""","supportedFields":[$fields]""")
        append("}")
    }

    @Test
    fun `a generated exercise keeps an explicit hold plus added-weight configuration`() {
        val exercise = AiExerciseFactory.parse(
            json(fields = "\"holdSeconds\",\"addedWeight\""),
            fallbackName = "Weighted Plank"
        )

        assertNotNull(exercise)
        assertEquals("holdSeconds,addedWeight", exercise!!.supportedFields)
        assertEquals(
            listOf(ExerciseField.HOLD_SECONDS, ExerciseField.ADDED_WEIGHT),
            exercise.exerciseFields
        )
    }

    @Test
    fun `cardio is a valid generated classification now`() {
        val exercise = AiExerciseFactory.parse(
            json(type = ExerciseType.CARDIO, fields = "\"timeSeconds\",\"distanceKm\""),
            fallbackName = "Run"
        )

        assertNotNull(exercise)
        assertEquals(ExerciseType.CARDIO, exercise!!.exerciseType)
        assertEquals(
            listOf(ExerciseField.TIME_SECONDS, ExerciseField.DISTANCE_KM),
            exercise.exerciseFields
        )
    }

    @Test
    fun `missing fields use the classification defaults`() {
        val exercise = AiExerciseFactory.parse(json(fields = null), fallbackName = "Weighted Plank")

        assertNotNull(exercise)
        assertEquals(
            "reps,holdSeconds",
            exercise!!.supportedFields
        )
    }

    @Test
    fun `an invalid generated combination falls back instead of being stored`() {
        val exercise = AiExerciseFactory.parse(
            json(fields = "\"weight\",\"addedWeight\""),
            fallbackName = "Weighted Plank"
        )

        assertNotNull(exercise)
        // The bodyweight default is safe; the conflicting pair is never persisted.
        assertEquals("reps,holdSeconds", exercise!!.supportedFields)
    }
}
