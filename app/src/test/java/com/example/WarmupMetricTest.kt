package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The central warm-up rule: volume includes warm-ups, peak working performance excludes them.
 */
class WarmupMetricTest {

    @Test
    fun `set rest override is independent and falls back to the inherited default`() {
        val overridden = set(reps = 8, weight = 80.0, setNumber = 1)
            .copy(restSecondsOverride = 90)
        val inherited = set(reps = 8, weight = 80.0, setNumber = 2)
            .copy(restSecondsOverride = null)
        val explicitlyOff = inherited.copy(restSecondsOverride = 0)

        assertEquals(90, WorkoutMetrics.effectiveRestSeconds(overridden, 150))
        assertEquals(150, WorkoutMetrics.effectiveRestSeconds(inherited, 150))
        assertEquals(0, WorkoutMetrics.effectiveRestSeconds(inherited, 0))
        assertEquals(0, WorkoutMetrics.effectiveRestSeconds(explicitlyOff, 150))
        // Resolving one set's timer never changes another set's stored override.
        assertNull(inherited.restSecondsOverride)
        assertEquals(90, overridden.restSecondsOverride)
    }

    private fun set(
        reps: Int,
        weight: Double,
        warmup: Boolean = false,
        setNumber: Int = 1
    ) = LoggedSet(
        sessionId = 1L,
        exerciseId = 1L,
        exerciseName = "Barbell Front Squat",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        isWarmup = warmup
    )

    @Test
    fun `a higher-rep warmup does not raise max working reps`() {
        val sets = listOf(
            set(reps = 20, weight = 40.0, warmup = true),
            set(reps = 12, weight = 80.0, setNumber = 2)
        )
        assertEquals(12, WorkoutMetrics.maxWorkingReps(sets))
    }

    @Test
    fun `a stronger warmup does not raise estimated one rep max`() {
        val normal = set(reps = 5, weight = 100.0, setNumber = 2)
        val warmup = set(reps = 5, weight = 110.0, warmup = true)
        val best = WorkoutMetrics.bestWorkingSet(listOf(warmup, normal)) { 75.0 }
        assertEquals(normal, best)
        assertEquals(
            112.5,
            WorkoutMetrics.estimateOneRepMax(100.0, 5),
            0.001
        )
    }

    @Test
    fun `warmups still contribute to volume`() {
        val sets = listOf(
            set(reps = 10, weight = 30.0, warmup = true),
            set(reps = 10, weight = 60.0, setNumber = 2),
            set(reps = 10, weight = 60.0, setNumber = 3),
            set(reps = 10, weight = 60.0, setNumber = 4)
        )
        // 300 + 600 + 600 + 600 = 2100 kg-reps
        assertEquals(2100.0, WorkoutMetrics.sessionVolume(sets) { null }, 0.001)
    }

    @Test
    fun `only warmups produces no working record`() {
        val sets = listOf(
            set(reps = 20, weight = 40.0, warmup = true, setNumber = 1),
            set(reps = 15, weight = 50.0, warmup = true, setNumber = 2)
        )
        assertEquals(0, WorkoutMetrics.maxWorkingReps(sets))
        assertNull(WorkoutMetrics.bestWorkingSet(sets) { 75.0 })
        assertTrue(WorkoutMetrics.workingSets(sets).isEmpty())
    }

    @Test
    fun `classification is independent per set`() {
        val sets = listOf(
            set(reps = 10, weight = 40.0, warmup = true, setNumber = 1),
            set(reps = 8, weight = 80.0, setNumber = 2),
            set(reps = 8, weight = 80.0, warmup = true, setNumber = 3)
        )
        assertEquals(setOf(1, 3), sets.filter { it.isWarmup }.map { it.setNumber }.toSet())
        assertEquals(listOf(2), sets.filter { !it.isWarmup }.map { it.setNumber })
        // Reading or changing one set never changes its neighbours.
        assertEquals(2, sets.count { it.isWarmup })
        assertEquals(1, sets.count { !it.isWarmup })
    }
}
