/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Calculations must follow recorded values, not the legacy classification.
 *
 * The old type switch ignored an added weight on a bodyweight-classified exercise, and treated a
 * zero-rep hold as a one-rep set (reporting body weight as its 1RM). Both produced plausible but
 * wrong numbers, which is worse than no number.
 */
class WorkoutMetricsFieldSemanticsTest {

    private fun set(
        type: String = ExerciseType.STANDARD_WEIGHT,
        reps: Int = 0,
        weight: Double = 0.0,
        addedWeight: Double = 0.0,
        hold: Int = 0,
        distanceKm: Double = 0.0
    ) = LoggedSet(
        id = 1,
        sessionId = 1,
        exerciseId = 1,
        exerciseName = "Test",
        exerciseType = type,
        setNumber = 1,
        reps = reps,
        weight = weight,
        addedWeight = addedWeight,
        holdDurationSeconds = hold,
        distanceKm = distanceKm
    )

    @Test
    fun `added weight is load regardless of the classification`() {
        // A weighted push-up saved under the legacy bodyweight type.
        val weightedPushUp = set(
            type = ExerciseType.PURE_BODYWEIGHT,
            reps = 8,
            addedWeight = 20.0
        )
        assertEquals(
            100.0,
            WorkoutMetrics.effectiveWeightKg(weightedPushUp, 80.0)!!,
            0.001
        )

        // And it still counts for a standard-classified row that gained an added-weight field.
        val weightedDip = set(
            type = ExerciseType.STANDARD_WEIGHT,
            reps = 5,
            addedWeight = 15.0
        )
        assertEquals(95.0, WorkoutMetrics.effectiveWeightKg(weightedDip, 80.0)!!, 0.001)
    }

    @Test
    fun `a weighted set without a body-weight snapshot stays unknown`() {
        val weightedPullUp = set(
            type = ExerciseType.ADDED_WEIGHT,
            reps = 5,
            addedWeight = 20.0
        )
        assertNull(WorkoutMetrics.effectiveWeightKg(weightedPullUp, null))
    }

    @Test
    fun `a hold-only set has no estimated one-rep max`() {
        val plank = set(type = ExerciseType.PURE_BODYWEIGHT, reps = 0, hold = 60)

        assertEquals(0.0, WorkoutMetrics.estimateOneRepMax(80.0, plank.reps), 0.001)
        assertNull(WorkoutMetrics.bestWorkingSet(listOf(plank)) { 80.0 })
        assertEquals(0, WorkoutMetrics.maxWorkingReps(listOf(plank)))
        assertEquals(0.0, WorkoutMetrics.sessionVolume(listOf(plank)) { 80.0 }, 0.001)
    }

    @Test
    fun `a combined rep and hold set still counts as rep work`() {
        val combined = set(
            type = ExerciseType.PURE_BODYWEIGHT,
            reps = 8,
            hold = 20,
            addedWeight = 10.0
        )
        assertEquals(8, WorkoutMetrics.maxWorkingReps(listOf(combined)))
        assertEquals(90.0, WorkoutMetrics.effectiveWeightKg(combined, 80.0)!!, 0.001)
        assertEquals(720.0, WorkoutMetrics.sessionVolume(listOf(combined)) { 80.0 }, 0.001)
    }

    @Test
    fun `warm-up sets count for volume but never win a personal record`() {
        val warmup = set(reps = 12, weight = 40.0).copy(isWarmup = true)
        val working = set(reps = 8, weight = 80.0)
        assertEquals(80.0, WorkoutMetrics.bestWorkingSet(listOf(warmup, working)) { null }!!.weight, 0.001)
        // 12 x 40 + 8 x 80: the warm-up is real work even though it cannot be a PR.
        assertEquals(1120.0, WorkoutMetrics.sessionVolume(listOf(warmup, working)) { null }, 0.001)
    }

    @Test
    fun `the recorded effort label shows reps, hold and distance together`() {
        val combined = set(
            type = ExerciseType.STANDARD_WEIGHT,
            reps = 10,
            hold = 30,
            distanceKm = 0.4
        )
        val label = WorkoutMetrics.recordedEffortLabel(combined, WeightUnit.KG)
        assertEquals("10 reps · 30s · 0.40 km", label)
    }

    @Test
    fun `cardio duration is labelled in minutes while a hold stays in seconds`() {
        val run = set(type = ExerciseType.CARDIO, hold = 1800, distanceKm = 5.0)
        assertEquals("30 min · 5.00 km", WorkoutMetrics.recordedEffortLabel(run, WeightUnit.KG))

        val plank = set(type = ExerciseType.PURE_BODYWEIGHT, hold = 45)
        assertEquals("45s hold", WorkoutMetrics.recordedEffortLabel(plank, WeightUnit.KG))
    }

    @Test
    fun `a cardio classification does not hide an explicitly recorded load`() {
        val weightedCardio = set(
            type = ExerciseType.CARDIO,
            reps = 10,
            weight = 20.0,
            hold = 600,
            distanceKm = 1.0
        )
        assertEquals(20.0, WorkoutMetrics.effectiveWeightKg(weightedCardio, 80.0)!!, 0.001)
        assertEquals(200.0, WorkoutMetrics.sessionVolume(listOf(weightedCardio)) { 80.0 }, 0.001)

        val unloadedRun = set(type = ExerciseType.CARDIO, hold = 1800, distanceKm = 5.0)
        assertNull(WorkoutMetrics.effectiveWeightKg(unloadedRun, 80.0))
    }
}
