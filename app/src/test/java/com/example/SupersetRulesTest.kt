package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetRulesTest {

    private fun item(id: Long, name: String) = RoutineExerciseItem(
        exerciseId = id,
        exerciseName = name,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        weekday = 1
    )

    private fun set(exerciseId: Long, name: String, groupId: String? = null) = LoggedSet(
        sessionId = 1,
        exerciseId = exerciseId,
        exerciseName = name,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = 1,
        reps = 8,
        setUid = LoggedSet.newUid(),
        supersetGroupId = groupId
    )

    @Test
    fun `linking requires two adjacent unlinked exercises`() {
        val items = listOf(item(1, "Push-ups"), item(2, "Rows"), item(3, "Squats"))
        val linked = SupersetRules.link(items, 0, 1)
        assertNotNull(linked[0].supersetGroupId)
        assertEquals(linked[0].supersetGroupId, linked[1].supersetGroupId)
        assertNull(linked[2].supersetGroupId)

        assertTrue(SupersetRules.link(items, 0, 2).isEmpty().not())
        assertEquals(items, SupersetRules.link(items, 0, 2))
        val once = SupersetRules.link(items, 0, 1)
        assertEquals(once, SupersetRules.link(once, 0, 2))
    }

    @Test
    fun `unlinking clears both members`() {
        val linked = SupersetRules.link(
            listOf(item(1, "Push-ups"), item(2, "Rows"), item(3, "Squats")),
            0,
            1
        )
        val unlinked = SupersetRules.unlink(linked, 1)
        assertTrue(unlinked.all { it.supersetGroupId.isNullOrBlank() })
    }

    @Test
    fun `normalise clears a group that lost a member`() {
        val linked = SupersetRules.link(
            listOf(item(1, "Push-ups"), item(2, "Rows"), item(3, "Squats")),
            0,
            1
        )
        val broken = SupersetRules.normalize(linked.take(1))
        assertTrue(broken.all { it.supersetGroupId.isNullOrBlank() })
    }

    @Test
    fun `moving one member moves the whole pair`() {
        val linked = SupersetRules.link(
            listOf(item(1, "Pull-ups"), item(2, "Push-ups"), item(3, "Rows"), item(4, "Squats")),
            1,
            1
        )
        val moved = SupersetRules.moveBlock(linked, 2, 3)
        assertEquals(listOf(1L, 4L, 2L, 3L), moved.map { it.exerciseId })
        val group = moved.filter { !it.supersetGroupId.isNullOrBlank() }
        assertEquals(2, group.size)
        assertEquals(2L, group[0].exerciseId)
        assertEquals(3L, group[1].exerciseId)
    }

    @Test
    fun `linking sets groups exactly two exercises and unlink clears both`() {
        val sets = listOf(
            set(1, "Push-ups"),
            set(1, "Push-ups"),
            set(2, "Rows"),
            set(3, "Squats")
        )
        val linked = SupersetRules.linkSets(sets, 1, 2)
        assertFalse(linked.filter { it.exerciseId == 1L }.any { it.supersetGroupId.isNullOrBlank() })
        assertFalse(linked.filter { it.exerciseId == 2L }.any { it.supersetGroupId.isNullOrBlank() })
        assertTrue(linked.filter { it.exerciseId == 3L }.all { it.supersetGroupId == null })
        val unlinked = SupersetRules.unlinkSets(linked, 2)
        assertTrue(unlinked.all { it.supersetGroupId == null })
    }
}
