/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineMainSetDefault
import com.example.data.model.RoutineParser
import com.example.data.model.sanitizedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Per-main-set defaults and field-complete prescriptions.
 *
 * Missing indexes must inherit the exercise-level target so every routine saved before this
 * feature keeps working unchanged, and serialization must not drop the new values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineMainSetDefaultsTest {

    private fun item() = RoutineExerciseItem(
        exerciseId = 42,
        exerciseName = "Farmer's Walk",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        category = "Legs",
        equipment = "Dumbbell",
        targetSets = 3,
        targetReps = 8,
        targetWeight = 32.0,
        targetHoldSeconds = 45,
        targetDistanceKm = 0.4,
        restSeconds = 90
    )

    @Test
    fun `a missing main set inherits the exercise-level targets`() {
        val item = item()
        val fallback = item.defaultForMainSet(2)
        assertEquals(8, fallback.targetReps)
        assertEquals(45, fallback.targetHoldSeconds)
        assertEquals(32.0, fallback.targetWeight, 0.001)
        assertEquals(0.4, fallback.targetDistanceKm, 0.001)
        assertEquals(90, fallback.restSeconds)
    }

    @Test
    fun `an explicit main set overrides only its own index`() {
        val updated = item().withMainSetDefault(
            RoutineMainSetDefault(
                mainSetIndex = 2,
                targetReps = 6,
                targetWeight = 30.0,
                targetDistanceKm = 0.35,
                restSeconds = 60
            )
        )
        assertEquals(6, updated.defaultForMainSet(2).targetReps)
        assertEquals(30.0, updated.defaultForMainSet(2).targetWeight, 0.001)
        // Index 1 was never materialized and still reads the item-level defaults.
        assertEquals(8, updated.defaultForMainSet(1).targetReps)
        assertEquals(32.0, updated.defaultForMainSet(1).targetWeight, 0.001)
        assertEquals(1, updated.mainSetDefaults.size)
    }

    @Test
    fun `updating the same index replaces instead of duplicating it`() {
        var updated = item().withMainSetDefault(
            RoutineMainSetDefault(mainSetIndex = 1, targetReps = 10)
        )
        updated = updated.withMainSetDefault(
            RoutineMainSetDefault(mainSetIndex = 1, targetReps = 12)
        )
        assertEquals(1, updated.mainSetDefaults.size)
        assertEquals(12, updated.defaultForMainSet(1).targetReps)
    }

    @Test
    fun `serialization preserves main sets and every drop-set field`() {
        val original = item()
            .withMainSetDefault(
                RoutineMainSetDefault(
                    mainSetIndex = 3,
                    targetReps = 5,
                    targetHoldSeconds = 20,
                    targetWeight = 28.0,
                    targetAddedWeightKg = 0.0,
                    targetDistanceKm = 0.3,
                    restSeconds = 45
                )
            )
            .copy(
                dropSets = listOf(
                    RoutineDropSet(
                        mainSetIndex = 3,
                        dropIndex = 1,
                        targetReps = 4,
                        targetHoldSeconds = 15,
                        targetWeight = 24.0,
                        targetDistanceKm = 0.25,
                        restSeconds = 30
                    )
                )
            )

        val json = RoutineParser.serializeExercises(listOf(original))
        val parsed = RoutineParser.parseExercises(json, emptyList()).single()

        assertEquals(original.mainSetDefaults.sortedBy { it.mainSetIndex }, parsed.mainSetDefaults)
        assertEquals(original.dropSets, parsed.dropSets)
        assertEquals(original.targetDistanceKm, parsed.targetDistanceKm, 0.001)
        assertEquals(original.targetHoldSeconds, parsed.targetHoldSeconds)
        assertEquals(original.targetWeight, parsed.targetWeight, 0.001)
    }

    @Test
    fun `an old routine without mainSets parses with an empty list`() {
        val json = """
            [{"id": 1, "name": "Plank", "type": "PURE_BODYWEIGHT", "sets": 3,
              "reps": 0, "holdSeconds": 60, "restSeconds": 60}]
        """.trimIndent()
        val parsed = RoutineParser.parseExercises(json, emptyList()).single()
        assertTrue(parsed.mainSetDefaults.isEmpty())
        // The fallback still exposes the recorded hold, so nothing disappears for old routines.
        assertEquals(60, parsed.defaultForMainSet(1).targetHoldSeconds)
    }

    @Test
    fun `a prescription keeps reps, hold and distance together`() {
        val text = RoutineParser.buildPrescription(item())
        assertEquals("3 sets x 8 reps / 45s / 0.4 km", text)
    }

    @Test
    fun `a hold-only prescription does not invent a rep count`() {
        val plank = item().copy(targetReps = 0, targetHoldSeconds = 60, targetDistanceKm = 0.0)
        assertEquals("3 sets x 60s", RoutineParser.buildPrescription(plank))
    }

    @Test
    fun `targets the exercise does not support are cleared when a routine is saved`() {
        val dirty = item().copy(
            targetReps = 8,
            targetWeight = 40.0,
            targetAddedWeightKg = 10.0,
            targetHoldSeconds = 30,
            targetDistanceKm = 2.0,
            dropSets = listOf(
                RoutineDropSet(
                    mainSetIndex = 1,
                    dropIndex = 1,
                    targetReps = 6,
                    targetWeight = 20.0,
                    targetAddedWeightKg = 5.0,
                    targetHoldSeconds = 20,
                    targetDistanceKm = 1.0
                )
            ),
            mainSetDefaults = listOf(
                RoutineMainSetDefault(
                    mainSetIndex = 1,
                    targetReps = 8,
                    targetWeight = 40.0,
                    targetAddedWeightKg = 10.0,
                    targetHoldSeconds = 30,
                    targetDistanceKm = 2.0
                )
            )
        )

        val plank = dirty.sanitizedFor(
            listOf(ExerciseField.HOLD_SECONDS, ExerciseField.ADDED_WEIGHT)
        )

        assertEquals(0, plank.targetReps)
        assertEquals(0.0, plank.targetWeight, 0.001)
        assertEquals(10.0, plank.targetAddedWeightKg, 0.001)
        assertEquals(30, plank.targetHoldSeconds)
        assertEquals(0.0, plank.targetDistanceKm, 0.001)
        assertEquals("3 sets x 30s", plank.prescription)

        val drop = plank.dropSets.single()
        assertEquals(0, drop.targetReps)
        assertEquals(0.0, drop.targetWeight, 0.001)
        assertEquals(5.0, drop.targetAddedWeightKg, 0.001)
        assertEquals(20, drop.targetHoldSeconds)
        assertEquals(0.0, drop.targetDistanceKm, 0.001)

        val main = plank.mainSetDefaults.single()
        assertEquals(0, main.targetReps)
        assertEquals(0.0, main.targetWeight, 0.001)
        assertEquals(10.0, main.targetAddedWeightKg, 0.001)
        assertEquals(30, main.targetHoldSeconds)
        assertEquals(0.0, main.targetDistanceKm, 0.001)
    }

    @Test
    fun `an exercise that supports everything keeps every target`() {
        val item = item().copy(targetReps = 8, targetWeight = 0.0, targetHoldSeconds = 30)
        val kept = item.sanitizedFor(
            listOf(
                ExerciseField.REPS,
                ExerciseField.ADDED_WEIGHT,
                ExerciseField.HOLD_SECONDS,
                ExerciseField.DISTANCE_KM
            )
        )
        assertEquals(8, kept.targetReps)
        assertEquals(30, kept.targetHoldSeconds)
        assertEquals(0.4, kept.targetDistanceKm, 0.001)
    }
}
