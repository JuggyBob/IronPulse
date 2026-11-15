/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseFieldCatalog
import com.example.data.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The field catalogue is the one place that decides which inputs an exercise can record.
 *
 * These tests pin the two rules the whole feature rests on: a non-empty explicit list is
 * authoritative, and the first two entries are the inline ones. Everything else (type defaults,
 * conflict validation, parsing) exists to keep those two rules safe across old data.
 */
class ExerciseFieldCatalogTest {

    private fun exercise(type: String, fields: String) = Exercise(
        name = "Test",
        category = "Core",
        exerciseType = type,
        supportedFields = fields
    )

    @Test
    fun `an explicit list wins over the type in both directions`() {
        val plank = exercise(ExerciseType.PURE_BODYWEIGHT, "holdSeconds,addedWeight")
        assertEquals(
            listOf(ExerciseField.HOLD_SECONDS, ExerciseField.ADDED_WEIGHT),
            plank.exerciseFields
        )

        val weighted = exercise(ExerciseType.ADDED_WEIGHT, "weight,reps")
        assertEquals(listOf(ExerciseField.WEIGHT, ExerciseField.REPS), weighted.exerciseFields)
    }

    @Test
    fun `an empty list keeps the legacy type fallback`() {
        assertEquals(
            listOf(ExerciseField.REPS, ExerciseField.HOLD_SECONDS),
            exercise(ExerciseType.PURE_BODYWEIGHT, "").exerciseFields
        )
        assertEquals(
            listOf(ExerciseField.TIME_SECONDS, ExerciseField.DISTANCE_KM),
            exercise(ExerciseType.CARDIO, "").exerciseFields
        )
        assertEquals(
            listOf(ExerciseField.WEIGHT, ExerciseField.REPS),
            exercise("SOMETHING_NEW", "").exerciseFields
        )
    }

    @Test
    fun `the first two fields are inline and the rest are expanded`() {
        val movement = exercise(
            ExerciseType.STANDARD_WEIGHT,
            "weight,reps,holdSeconds,distanceKm"
        )
        assertEquals(
            listOf(ExerciseField.WEIGHT, ExerciseField.REPS),
            movement.primaryFields
        )
        assertEquals(
            listOf(ExerciseField.HOLD_SECONDS, ExerciseField.DISTANCE_KM),
            movement.detailFields
        )
    }

    @Test
    fun `a single field is a valid complete configuration`() {
        val stretch = exercise(ExerciseType.PURE_BODYWEIGHT, "holdSeconds")
        assertEquals(listOf(ExerciseField.HOLD_SECONDS), stretch.exerciseFields)
        assertTrue(stretch.detailFields.isEmpty())
    }

    @Test
    fun `serialization drops duplicates and keeps display order`() {
        val serialized = ExerciseFieldCatalog.serialize(
            listOf(
                ExerciseField.HOLD_SECONDS,
                ExerciseField.ADDED_WEIGHT,
                ExerciseField.HOLD_SECONDS,
                ExerciseField.REPS
            )
        )
        assertEquals("holdSeconds,addedWeight,reps", serialized)
        assertEquals(
            listOf(
                ExerciseField.HOLD_SECONDS,
                ExerciseField.ADDED_WEIGHT,
                ExerciseField.REPS
            ),
            ExerciseFieldCatalog.parseStored(serialized)
        )
    }

    @Test
    fun `unknown identifiers are ignored rather than crashing an old row`() {
        assertEquals(
            listOf(ExerciseField.REPS, ExerciseField.REPS, ExerciseField.WEIGHT),
            listOf(
                ExerciseField.REPS,
                ExerciseField.fromId("futureField") ?: ExerciseField.REPS,
                ExerciseField.WEIGHT
            )
        )
        assertEquals(
            listOf(ExerciseField.REPS, ExerciseField.WEIGHT),
            ExerciseFieldCatalog.parseStored("reps,futureField,weight")
        )
    }

    @Test
    fun `validation normalizes but preserves the chosen display order`() {
        val validated = ExerciseFieldCatalog.validate(
            listOf(ExerciseField.REPS, ExerciseField.HOLD_SECONDS, ExerciseField.HOLD_SECONDS)
        )
        assertEquals(listOf(ExerciseField.REPS, ExerciseField.HOLD_SECONDS), validated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `weight and added weight cannot be combined`() {
        ExerciseFieldCatalog.validate(
            listOf(ExerciseField.WEIGHT, ExerciseField.ADDED_WEIGHT)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hold and time cannot share the stored duration column`() {
        ExerciseFieldCatalog.validate(
            listOf(ExerciseField.HOLD_SECONDS, ExerciseField.TIME_SECONDS)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an exercise must support at least one field`() {
        ExerciseFieldCatalog.validate(emptyList())
    }

    @Test
    fun `invalid save input falls back to the classification defaults`() {
        assertEquals(
            listOf(ExerciseField.REPS, ExerciseField.HOLD_SECONDS),
            ExerciseFieldCatalog.safeExplicit(
                listOf(ExerciseField.WEIGHT, ExerciseField.ADDED_WEIGHT),
                ExerciseType.PURE_BODYWEIGHT
            )
        )
        assertEquals(
            listOf(ExerciseField.WEIGHT, ExerciseField.REPS),
            ExerciseFieldCatalog.safeExplicit(emptyList(), ExerciseType.STANDARD_WEIGHT)
        )
    }
}
