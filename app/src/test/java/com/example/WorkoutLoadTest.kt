package com.example

import com.example.data.model.BodyWeightTimeline
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import com.example.data.model.WorkoutMetrics
import com.example.data.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The load a set actually moved.
 *
 * An empty weight field used to make a bodyweight set read as zero: the "BW: 80 kg" tag said the
 * athlete had lifted their own body weight while the volume and the estimated one rep max said
 * they had lifted nothing. These tests pin down the one rule every screen measures with.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutLoadTest {

    private fun set(
        type: String = ExerciseType.PURE_BODYWEIGHT,
        weight: Double = 0.0,
        addedWeight: Double = 0.0,
        reps: Int = 10,
        bodyWeightKg: Double = 0.0
    ) = LoggedSet(
        sessionId = 1,
        exerciseId = 1,
        exerciseName = "Push-Up",
        exerciseType = type,
        setNumber = 1,
        weight = weight,
        addedWeight = addedWeight,
        reps = reps,
        bodyWeightKg = bodyWeightKg
    )

    // === The rule ===

    @Test
    fun `a bodyweight set moves the body weight`() {
        assertEquals(80.0, WorkoutMetrics.effectiveWeightKg(set(), 80.0)!!, 0.001)
    }

    @Test
    fun `an added weight set with an empty added weight field moves the body weight`() {
        // The athlete added nothing to the bar, so the only load is their own body weight.
        val added = set(type = ExerciseType.ADDED_WEIGHT)
        assertEquals(80.0, WorkoutMetrics.effectiveWeightKg(added, 80.0)!!, 0.001)
    }

    @Test
    fun `an added weight set moves the body weight plus the added load`() {
        val added = set(type = ExerciseType.ADDED_WEIGHT, addedWeight = 20.0)
        assertEquals(100.0, WorkoutMetrics.effectiveWeightKg(added, 80.0)!!, 0.001)
    }

    @Test
    fun `a barbell set moves the load on the bar`() {
        val barbell = set(type = ExerciseType.STANDARD_WEIGHT, weight = 60.0)
        assertEquals(60.0, WorkoutMetrics.effectiveWeightKg(barbell, 80.0)!!, 0.001)
    }

    @Test
    fun `a cardio set has no load at all`() {
        // A run is not lifted. Giving it the body weight invented volume and let it win an
        // estimated one rep max against a real set.
        val run = set(type = ExerciseType.CARDIO)
        assertNull(WorkoutMetrics.effectiveWeightKg(run, 80.0))
    }

    @Test
    fun `a bodyweight set with no body weight on record cannot be expressed in kg`() {
        assertNull(WorkoutMetrics.effectiveWeightKg(set(), null))
    }

    // === The volume ===

    @Test
    fun `the volume of a bodyweight set is the body weight times the reps`() {
        assertEquals(800.0, WorkoutMetrics.setVolume(set(reps = 10), 80.0), 0.001)
    }

    @Test
    fun `the volume of a session adds up its sets`() {
        val sets = listOf(
            set(reps = 10),
            set(reps = 12),
            set(type = ExerciseType.STANDARD_WEIGHT, weight = 60.0, reps = 5)
        )
        val volume = WorkoutMetrics.sessionVolume(sets) { 80.0 }
        // 80x10 + 80x12 + 60x5
        assertEquals(80.0 * 10 + 80.0 * 12 + 60.0 * 5, volume, 0.001)
    }

    @Test
    fun `a set with an unknown body weight contributes nothing rather than a made up number`() {
        val volume = WorkoutMetrics.sessionVolume(listOf(set(reps = 10))) { null }
        assertEquals(0.0, volume, 0.001)
    }

    // === The estimated one rep max ===

    @Test
    fun `the estimated one rep max of a bodyweight set uses the body weight`() {
        // 80 x 8 -> 80 * 36 / 29
        assertEquals(80.0 * 36.0 / 29.0, WorkoutMetrics.estimateOneRepMax(80.0, 8), 0.001)
    }

    @Test
    fun `a single rep is its own one rep max`() {
        assertEquals(80.0, WorkoutMetrics.estimateOneRepMax(80.0, 1), 0.001)
    }

    // === The body weight at the time of the set ===

    @Test
    fun `the body weight of a set is the one it was done at, not today's`() {
        // The set was done at 80 kg. The athlete now weighs 84, and that must not rewrite it.
        val set = set(bodyWeightKg = 80.0)
        assertEquals(80.0, BodyWeightTimeline.bodyWeightForSet(set, 84.0)!!, 0.001)
        assertEquals(800.0, WorkoutMetrics.setVolume(set, BodyWeightTimeline.bodyWeightForSet(set, 84.0)), 0.001)
    }

    @Test
    fun `a set with no body weight on record falls back to the profile weight`() {
        val set = set(bodyWeightKg = 0.0)
        assertEquals(84.0, BodyWeightTimeline.bodyWeightForSet(set, 84.0)!!, 0.001)
    }

    @Test
    fun `the body weight survives the auto save round trip`() {
        // A workout that is app killed and restored must still be measured against the body
        // weight of the day it was done, not the weight of the day it came back.
        val set = set(bodyWeightKg = 80.0)
        val draft = WorkoutDraft(
            kind = WorkoutDraft.KIND_QUICK,
            title = "Quick Workout",
            startTime = 1_700_000_000_000L,
            sets = listOf(set)
        )

        val decoded = WorkoutDraftCodec.decode(WorkoutDraftCodec.encode(draft))
        assertEquals(80.0, decoded?.sets?.first()?.bodyWeightKg ?: 0.0, 0.001)
    }

    // === How the load is written ===

    @Test
    fun `a bodyweight set is written as the body weight it was done at`() {
        assertEquals("80 kg BW", WorkoutMetrics.loadLabel(set(), 80.0, WeightUnit.KG))
    }

    @Test
    fun `an added weight set shows the added load and the body weight under it`() {
        val added = set(type = ExerciseType.ADDED_WEIGHT, addedWeight = 20.0)
        assertEquals("+20 kg (+80 kg BW)", WorkoutMetrics.loadLabel(added, 80.0, WeightUnit.KG))
    }

    @Test
    fun `a barbell set shows the load on the bar`() {
        val barbell = set(type = ExerciseType.STANDARD_WEIGHT, weight = 60.0)
        assertEquals("60 kg", WorkoutMetrics.loadLabel(barbell, 80.0, WeightUnit.KG))
    }

    @Test
    fun `a barbell set with no load logged says zero rather than the body weight`() {
        // The athlete did not log a load for a barbell movement, so the load really is zero. The
        // body weight is not in it.
        val barbell = set(type = ExerciseType.STANDARD_WEIGHT, weight = 0.0)
        assertEquals("0 kg", WorkoutMetrics.loadLabel(barbell, 80.0, WeightUnit.KG))
    }

    @Test
    fun `a bodyweight set with no body weight on record never borrows today's weight`() {
        assertEquals("Own body weight", WorkoutMetrics.loadLabel(set(), null, WeightUnit.KG))
    }

    @Test
    fun `the load is written in the athlete's unit`() {
        // 80 kg is 176.4 lb.
        assertTrue(
            WorkoutMetrics.loadLabel(set(), 80.0, WeightUnit.LB).startsWith("176.4 lb")
        )
    }

    // === The duration ===

    @Test
    fun `the duration of a session is its length`() {
        val session = WorkoutSession(
            id = 1,
            title = "Workout",
            startTime = 1_000_000L,
            endTime = 1_000_000L + 45 * 60_000L
        )
        assertEquals(45, WorkoutMetrics.minutes(session, emptyList()))
    }
}
