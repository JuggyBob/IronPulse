/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseFieldValues
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineMainSetDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field values are stored in the existing set/routine columns, never in the exercise definition.
 *
 * The important properties are that every field maps to exactly one column, unset stays zero, and
 * copies are independent: editing one main set or drop set can never mutate a sibling.
 */
class ExerciseFieldValuesTest {

    private fun set() = LoggedSet(
        id = 1,
        sessionId = 1,
        exerciseId = 1,
        exerciseName = "Plank",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        setNumber = 1
    )

    @Test
    fun `every field round trips through its stored column`() {
        var current = set()
        current = ExerciseFieldValues.write(current, ExerciseField.REPS, 12.0)
        current = ExerciseFieldValues.write(current, ExerciseField.ADDED_WEIGHT, 10.0)
        current = ExerciseFieldValues.write(current, ExerciseField.HOLD_SECONDS, 45.0)
        current = ExerciseFieldValues.write(current, ExerciseField.DISTANCE_KM, 1.25)

        assertEquals(12, current.reps)
        assertEquals(10.0, current.addedWeight, 0.001)
        assertEquals(45, current.holdDurationSeconds)
        assertEquals(1.25, current.distanceKm, 0.001)
        assertEquals(12.0, ExerciseFieldValues.read(current, ExerciseField.REPS), 0.001)
        assertEquals(10.0, ExerciseFieldValues.read(current, ExerciseField.ADDED_WEIGHT), 0.001)
        assertEquals(45.0, ExerciseFieldValues.read(current, ExerciseField.HOLD_SECONDS), 0.001)
        assertEquals(1.25, ExerciseFieldValues.read(current, ExerciseField.DISTANCE_KM), 0.001)
    }

    @Test
    fun `hold and time share the one duration column`() {
        val held = ExerciseFieldValues.write(set(), ExerciseField.HOLD_SECONDS, 30.0)
        assertEquals(30, held.holdDurationSeconds)
        assertEquals(
            30.0,
            ExerciseFieldValues.read(held, ExerciseField.TIME_SECONDS),
            0.001
        )

        val timed = ExerciseFieldValues.write(set(), ExerciseField.TIME_SECONDS, 1800.0)
        assertEquals(1800, timed.holdDurationSeconds)
        assertEquals(
            1800.0,
            ExerciseFieldValues.read(timed, ExerciseField.HOLD_SECONDS),
            0.001
        )
    }

    @Test
    fun `an unset field stays zero instead of becoming a fabricated value`() {
        val blank = set()
        ExerciseField.entries.forEach { field ->
            assertEquals(0.0, ExerciseFieldValues.read(blank, field), 0.001)
            assertFalse(ExerciseFieldValues.hasValue(blank, field))
        }
    }

    @Test
    fun `writing a value returns a copy and never mutates the source`() {
        val original = set()
        val updated = ExerciseFieldValues.write(original, ExerciseField.REPS, 9.0)

        assertEquals(0, original.reps)
        assertEquals(9, updated.reps)
        assertTrue(ExerciseFieldValues.hasValue(updated, ExerciseField.REPS))
    }

    @Test
    fun `negative and non-finite input is clamped, not stored`() {
        val updated = ExerciseFieldValues.write(set(), ExerciseField.WEIGHT, -5.0)
        assertEquals(0.0, updated.weight, 0.001)
        val nan = ExerciseFieldValues.write(set(), ExerciseField.DISTANCE_KM, Double.NaN)
        assertEquals(0.0, nan.distanceKm, 0.001)
    }

    @Test
    fun `routine items, main-set defaults and drop sets share the same mapping`() {
        val item = RoutineExerciseItem(
            exerciseId = 1,
            exerciseName = "Farmer's Walk",
            exerciseType = ExerciseType.STANDARD_WEIGHT
        )
        val itemWithLoad = ExerciseFieldValues.write(item, ExerciseField.WEIGHT, 32.0)
        assertEquals(32.0, itemWithLoad.targetWeight, 0.001)

        val main = RoutineMainSetDefault(mainSetIndex = 2)
        val mainWithDistance = ExerciseFieldValues.write(
            main,
            ExerciseField.DISTANCE_KM,
            0.4
        )
        assertEquals(2, mainWithDistance.mainSetIndex)
        assertEquals(0.4, mainWithDistance.targetDistanceKm, 0.001)

        val drop = RoutineDropSet(mainSetIndex = 1, dropIndex = 1)
        val dropWithHold = ExerciseFieldValues.write(drop, ExerciseField.HOLD_SECONDS, 20.0)
        assertEquals(20, dropWithHold.targetHoldSeconds)
        assertEquals(0, drop.targetHoldSeconds)
    }
}
