package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WarmupSetDataTest {

    @Test
    fun `new sets default to normal and inherit the existing rest`() {
        val set = LoggedSet(
            sessionId = 0L,
            exerciseId = 1L,
            exerciseName = "Push-Up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            setNumber = 1
        )
        assertFalse(set.isWarmup)
        assertNull(set.restSecondsOverride)
    }

    @Test
    fun `draft round trip keeps warmup and set rest exactly`() {
        val draft = WorkoutDraft(
            title = "Test",
            startTime = 1L,
            sets = listOf(
                LoggedSet(
                    sessionId = 0L,
                    exerciseId = 1L,
                    exerciseName = "Barbell Front Squat",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 1,
                    weight = 40.0,
                    reps = 10,
                    isWarmup = true,
                    restSecondsOverride = 90
                ),
                LoggedSet(
                    sessionId = 0L,
                    exerciseId = 1L,
                    exerciseName = "Barbell Front Squat",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 2,
                    weight = 80.0,
                    reps = 5,
                    isWarmup = false,
                    restSecondsOverride = null
                ),
                LoggedSet(
                    sessionId = 0L,
                    exerciseId = 1L,
                    exerciseName = "Barbell Front Squat",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 3,
                    weight = 80.0,
                    reps = 5,
                    isWarmup = false,
                    restSecondsOverride = 0
                )
            )
        )

        val decoded = WorkoutDraftCodec.decode(WorkoutDraftCodec.encode(draft))!!
        assertEquals(3, decoded.sets.size)
        assertTrue(decoded.sets[0].isWarmup)
        assertEquals(90, decoded.sets[0].restSecondsOverride)
        assertFalse(decoded.sets[1].isWarmup)
        assertNull(decoded.sets[1].restSecondsOverride)
        assertEquals(0, decoded.sets[2].restSecondsOverride)
    }

    @Test
    fun `legacy draft without warmup fields reads as normal and inherited`() {
        val legacy = """
            {
              "kind": "quick",
              "title": "Legacy",
              "startTime": 1,
              "exerciseIds": [1],
              "sets": [
                {
                  "id": 0,
                  "exerciseId": 1,
                  "exerciseName": "Push-Up",
                  "exerciseType": "PURE_BODYWEIGHT",
                  "setNumber": 1,
                  "weight": 0.0,
                  "addedWeight": 0.0,
                  "reps": 12,
                  "holdDurationSeconds": 0,
                  "distanceKm": 0.0,
                  "isCompleted": true,
                  "timestamp": 1,
                  "bodyWeightKg": 75.0
                }
              ]
            }
        """.trimIndent()

        val decoded = WorkoutDraftCodec.decode(legacy)!!
        assertEquals(1, decoded.sets.size)
        assertFalse(decoded.sets.first().isWarmup)
        assertNull(decoded.sets.first().restSecondsOverride)
    }
}
