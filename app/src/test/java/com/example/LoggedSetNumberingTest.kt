package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.LoggedSetNumbering
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Verifies the set numbering rules used by the Progress page session history.
 */
class LoggedSetNumberingTest {

    private val day1 = timestampOf(2026, Calendar.MARCH, 2, 9, 0)
    private val day2 = timestampOf(2026, Calendar.MARCH, 3, 9, 0)

    private fun set(id: Long, setNumber: Int, timestamp: Long, exercise: String = "Bench Press") =
        LoggedSet(
            id = id,
            sessionId = 1L,
            exerciseId = 10L,
            exerciseName = exercise,
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            setNumber = setNumber,
            weight = 80.0,
            reps = 8,
            timestamp = timestamp
        )

    @Test
    fun `set numbers are continuous per training day`() {
        val sets = listOf(
            set(1L, 1, day1),
            set(2L, 2, day1 + 1000),
            set(3L, 3, day1 + 2000)
        )

        val positions = LoggedSetNumbering.positionsBySetId(sets)

        assertEquals(1, positions[1L])
        assertEquals(2, positions[2L])
        assertEquals(3, positions[3L])
    }

    @Test
    fun `numbering restarts on every new training day`() {
        val sets = listOf(
            set(1L, 1, day1),
            set(2L, 2, day1 + 1000),
            set(3L, 1, day2),
            set(4L, 2, day2 + 1000)
        )

        val positions = LoggedSetNumbering.positionsBySetId(sets)

        assertEquals(1, positions[1L])
        assertEquals(2, positions[2L])
        assertEquals(1, positions[3L])
        assertEquals(2, positions[4L])
    }

    @Test
    fun `gaps left by deleted sets are closed automatically`() {
        // Set #1 was deleted, so the stored numbering is now 2 and 3 instead of 1 and 2.
        val setsAfterDeletingFirstSet = listOf(
            set(2L, 2, day1 + 1000),
            set(3L, 3, day1 + 2000)
        )

        val positions = LoggedSetNumbering.positionsBySetId(setsAfterDeletingFirstSet)

        assertEquals(1, positions[2L])
        assertEquals(2, positions[3L])
    }

    @Test
    fun `gaps are only closed inside the affected day`() {
        val sets = listOf(
            set(1L, 1, day1),
            set(2L, 2, day1 + 1000),
            set(3L, 3, day1 + 2000),
            set(4L, 1, day2),
            set(5L, 2, day2 + 1000)
        )

        // Deleting set #2 of day 1
        val afterDeletion = sets.filterNot { it.id == 2L }
        val positions = LoggedSetNumbering.positionsBySetId(afterDeletion)

        assertEquals(1, positions[1L])
        assertEquals(2, positions[3L])
        // The untouched day keeps its own numbering
        assertEquals(1, positions[4L])
        assertEquals(2, positions[5L])
    }

    @Test
    fun `sets sharing the same timestamp keep their insertion order`() {
        val sets = listOf(
            set(7L, 1, day1),
            set(8L, 2, day1),
            set(9L, 3, day1)
        )

        val positions = LoggedSetNumbering.positionsBySetId(sets)

        assertEquals(listOf(1, 2, 3), listOf(positions[7L], positions[8L], positions[9L]))
    }

    private fun timestampOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
