package com.example

import com.example.data.model.DropSetRules
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutMetrics
import com.example.ui.components.orderedSetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure parent/child rules shared by the workout, routine, history and AI layers. */
class DropSetRulesTest {

    private fun set(
        uid: String,
        number: Int,
        parent: String? = null,
        dropIndex: Int = 0,
        completed: Boolean = false,
        reps: Int = 8,
        weight: Double = 40.0,
        warmup: Boolean = false
    ) = LoggedSet(
        sessionId = 1L,
        exerciseId = 7L,
        exerciseName = "Barbell Bench Press",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = number,
        weight = weight,
        reps = reps,
        isCompleted = completed,
        timestamp = 1_000L + number * 10L + dropIndex,
        isWarmup = warmup,
        setUid = uid,
        parentSetUid = parent,
        dropIndex = dropIndex
    )

    @Test
    fun `main set and children keep an explicit parent link through ordering`() {
        val sets = listOf(
            set("m1", 1, completed = true),
            set("m1d2", 1, parent = "m1", dropIndex = 2, reps = 6, weight = 20.0),
            set("m1d1", 1, parent = "m1", dropIndex = 1, reps = 8, weight = 30.0),
            set("m2", 2)
        )

        val rows = orderedSetRows(sets)

        assertEquals(listOf("m1", "m1d1", "m1d2", "m2"), rows.map { it.set.setUid })
        assertEquals(listOf(false, true, true, false), rows.map { it.isDrop })
        assertEquals(listOf(false, false, true, false), rows.map { it.isLastChild })
        assertEquals(listOf(1, 2), rows.filter { it.isDrop }.map { it.set.dropIndex })
    }

    @Test
    fun `normalize gives old rows a uid and repairs an orphan instead of hiding it`() {
        val old = set("", 1).copy(parentSetUid = null)
        val orphan = set("c1", 1, parent = "missing", dropIndex = 1)

        val normalized = DropSetRules.normalize(listOf(old, orphan))

        assertTrue(normalized[0].setUid.isNotBlank())
        assertFalse(normalized[1].isDropSet)
        assertNull(normalized[1].parentSetUid)
        assertEquals(0, normalized[1].dropIndex)
    }

    @Test
    fun `completing a child completes its parent but not the reverse`() {
        val sets = listOf(set("m1", 1), set("c1", 1, parent = "m1", dropIndex = 1))

        val childCompleted = DropSetRules.toggleCompletion(sets, "c1", true)
        assertTrue(childCompleted.first { it.setUid == "m1" }.isCompleted)
        assertTrue(childCompleted.first { it.setUid == "c1" }.isCompleted)

        val parentCompleted = DropSetRules.toggleCompletion(sets, "m1", true)
        assertTrue(parentCompleted.first { it.setUid == "m1" }.isCompleted)
        assertFalse(parentCompleted.first { it.setUid == "c1" }.isCompleted)

        val parentReopened = DropSetRules.toggleCompletion(childCompleted, "m1", false)
        assertFalse(parentReopened.first { it.setUid == "m1" }.isCompleted)
        assertFalse(parentReopened.first { it.setUid == "c1" }.isCompleted)
    }

    @Test
    fun `exercise completion requires every main and drop set and at least one main set`() {
        val partial = listOf(
            set("m1", 1, completed = true),
            set("c1", 1, parent = "m1", dropIndex = 1, completed = false)
        )
        val complete = partial.map { it.copy(isCompleted = true) }

        assertFalse(DropSetRules.isExerciseComplete(partial, 7L))
        assertTrue(DropSetRules.isExerciseComplete(complete, 7L))
        assertFalse(DropSetRules.isExerciseComplete(emptyList(), 7L))
        assertFalse(
            DropSetRules.isExerciseComplete(
                listOf(set("c1", 1, parent = "m1", dropIndex = 1, completed = true)),
                7L
            )
        )
    }

    @Test
    fun `deleting a main set removes its branch and renumbers siblings`() {
        val sets = listOf(
            set("m1", 1),
            set("m1c1", 1, parent = "m1", dropIndex = 1),
            set("m2", 2),
            set("m2c1", 2, parent = "m2", dropIndex = 1),
            set("m2c2", 2, parent = "m2", dropIndex = 2),
            set("m3", 3)
        )

        val afterParent = DropSetRules.removeSet(sets, "m2")
        assertEquals(listOf("m1", "m1c1", "m3"), afterParent.map { it.setUid })
        assertEquals(listOf(1, 2), afterParent.filter { !it.isDropSet }.map { it.setNumber })

        val afterChild = DropSetRules.removeSet(sets, "m2c1")
        assertEquals(listOf("m2c2"), afterChild.filter { it.parentSetUid == "m2" }.map { it.setUid })
        assertEquals(1, afterChild.first { it.setUid == "m2c2" }.dropIndex)
        assertEquals(2, afterChild.first { it.setUid == "m2c2" }.setNumber)
    }

    @Test
    fun `drop work counts toward volume and reps but never claims a working-set record`() {
        val sets = listOf(
            set("m1", 1, reps = 10, weight = 100.0, completed = true),
            set("c1", 1, parent = "m1", dropIndex = 1, reps = 12, weight = 50.0, completed = true),
            set("w1", 2, reps = 20, weight = 20.0, warmup = true)
        )

        assertEquals(2000.0, WorkoutMetrics.sessionVolume(sets) { 0.0 }, 0.001)
        assertEquals(1, WorkoutMetrics.workingSets(sets).size)
        assertEquals(1, WorkoutMetrics.dropSets(sets).size)
        assertEquals(2, WorkoutMetrics.mainSets(sets).size)
        assertEquals(10, WorkoutMetrics.maxWorkingReps(sets))
        assertEquals(1000.0, WorkoutMetrics.setVolume(sets[0], 0.0), 0.001)
        assertEquals(600.0, WorkoutMetrics.setVolume(sets[1], 0.0), 0.001)
    }

    @Test
    fun `renumbering main sets rewrites their children to the same parent number`() {
        val sets = listOf(
            set("m1", 2),
            set("m1c1", 2, parent = "m1", dropIndex = 1),
            set("m2", 5)
        )

        val renumbered = DropSetRules.renumberExerciseSets(sets, 7L)

        assertEquals(1, renumbered.first { it.setUid == "m1" }.setNumber)
        assertEquals(1, renumbered.first { it.setUid == "m1c1" }.setNumber)
        assertEquals(2, renumbered.first { it.setUid == "m2" }.setNumber)
        assertNotNull(renumbered.first { it.setUid == "m1c1" }.parentSetUid)
    }
}
