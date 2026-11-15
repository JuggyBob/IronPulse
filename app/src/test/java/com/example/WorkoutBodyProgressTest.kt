package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.MusclePart
import com.example.ui.screens.workoutBodyProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body-part completion is a whole-workout measure: every set of every exercise that trains a part,
 * including warm-up sets and secondary muscles, with duplicate primary/secondary references counted
 * once per set.
 */
class WorkoutBodyProgressTest {

    private fun exercise(
        id: Long,
        name: String,
        category: String,
        secondary: String = "[]"
    ) = Exercise(
        id = id,
        name = name,
        category = category,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        secondaryMuscles = secondary
    )

    private fun set(
        exerciseId: Long,
        setNumber: Int,
        completed: Boolean,
        warmup: Boolean = false
    ) = LoggedSet(
        sessionId = 1L,
        exerciseId = exerciseId,
        exerciseName = "Exercise $exerciseId",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = setNumber,
        reps = 8,
        isCompleted = completed,
        isWarmup = warmup
    )

    @Test
    fun `two exercises training the same part are combined`() {
        val exercises = listOf(
            exercise(1, "Row", "Back"),
            exercise(2, "Legs and Lats", "Legs", """["lats"]""")
        )
        val sets = listOf(
            set(1, 1, completed = true),
            set(1, 2, completed = true),
            set(1, 3, completed = false),
            set(2, 1, completed = true),
            // A completed warm-up still counts toward workout completion.
            set(2, 2, completed = true, warmup = true)
        )

        val progress = workoutBodyProgress(exercises, sets)
        val back = progress.first { it.part == MusclePart.BACK }
        assertEquals(5, back.total)
        assertEquals(4, back.completed)
        assertEquals(0.8f, back.fraction, 0.001f)

        val legs = progress.first { it.part == MusclePart.LEGS }
        assertEquals(2, legs.total)
        assertEquals(2, legs.completed)
        assertEquals(1f, legs.fraction, 0.001f)
    }

    @Test
    fun `a primary and secondary reference to the same part is not double counted`() {
        val exercises = listOf(
            exercise(1, "Back Extension", "Back", """["lats", "upper back"]""")
        )
        val sets = listOf(
            set(1, 1, completed = false),
            set(1, 2, completed = true)
        )

        val back = workoutBodyProgress(exercises, sets).first { it.part == MusclePart.BACK }
        assertEquals(2, back.total)
        assertEquals(1, back.completed)
    }

    @Test
    fun `adding or deleting a set changes the denominator and keeps completion`() {
        val exercises = listOf(exercise(1, "Bench Press", "Chest"))
        val before = workoutBodyProgress(
            exercises,
            listOf(set(1, 1, completed = true))
        ).first { it.part == MusclePart.CHEST }
        assertEquals(1, before.total)
        assertEquals(1, before.completed)

        val afterAdd = workoutBodyProgress(
            exercises,
            listOf(set(1, 1, completed = true), set(1, 2, completed = false))
        ).first { it.part == MusclePart.CHEST }
        assertEquals(2, afterAdd.total)
        assertEquals(1, afterAdd.completed)
        assertEquals(0.5f, afterAdd.fraction, 0.001f)

        val afterDelete = workoutBodyProgress(
            exercises,
            listOf(set(1, 2, completed = false))
        ).first { it.part == MusclePart.CHEST }
        assertEquals(1, afterDelete.total)
        assertEquals(0, afterDelete.completed)
    }

    @Test
    fun `an unmapped workout has no body-part rows`() {
        val exercises = listOf(
            Exercise(
                id = 9,
                name = "Mystery",
                category = "",
                exerciseType = ExerciseType.STANDARD_WEIGHT
            )
        )
        assertTrue(workoutBodyProgress(exercises, listOf(set(9, 1, completed = true))).isEmpty())
    }
}
