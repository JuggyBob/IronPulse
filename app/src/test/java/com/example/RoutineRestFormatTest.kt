/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.ui.components.formatRestDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The saved-routine rest tag must use the same value the workout timer reads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineRestFormatTest {

    @Test
    fun `rest durations format compactly`() {
        assertEquals("0s", formatRestDuration(0))
        assertEquals("45s", formatRestDuration(45))
        assertEquals("1m 30s", formatRestDuration(90))
        assertEquals("2m", formatRestDuration(120))
        assertEquals("10m", formatRestDuration(600))
    }

    @Test
    fun `rest seconds survive the routine JSON round trip`() {
        val item = RoutineExerciseItem(
            exerciseId = 42L,
            exerciseName = "Push-Up",
            targetSets = 3,
            targetReps = 10,
            restSeconds = 90,
            weekday = 1,
            dayTitle = "Push"
        )
        val parsed = RoutineParser.parseExercises(
            RoutineParser.serializeExercises(listOf(item)),
            allExercises = emptyList()
        ).single()

        assertEquals(42L, parsed.exerciseId)
        assertEquals("Push-Up", parsed.exerciseName)
        assertEquals(90, parsed.restSeconds)
    }
}
