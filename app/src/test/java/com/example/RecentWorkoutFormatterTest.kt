/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.LoggedSet
import com.example.ui.components.RecentWorkoutFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentWorkoutFormatterTest {

    private var nextId = 1L
    private var nextTimestamp = 1_000L

    private fun mainSet(
        name: String,
        setId: Long,
        setNumber: Int,
        supersetGroupId: String? = null,
        warmup: Boolean = false
    ): LoggedSet = LoggedSet(
        id = nextId++,
        sessionId = 1L,
        exerciseId = setId,
        exerciseName = name,
        exerciseType = "STANDARD_WEIGHT",
        setNumber = setNumber,
        reps = 10,
        timestamp = nextTimestamp++,
        setUid = "set-${nextId - 1}",
        isWarmup = warmup,
        supersetGroupId = supersetGroupId
    )

    private fun dropSet(
        parent: LoggedSet,
        dropIndex: Int,
        warmup: Boolean = parent.isWarmup
    ): LoggedSet = LoggedSet(
        id = nextId++,
        sessionId = parent.sessionId,
        exerciseId = parent.exerciseId,
        exerciseName = parent.exerciseName,
        exerciseType = parent.exerciseType,
        setNumber = parent.setNumber,
        reps = 8,
        timestamp = nextTimestamp++,
        setUid = "set-${nextId - 1}",
        parentSetUid = parent.setUid,
        dropIndex = dropIndex,
        isWarmup = warmup,
        supersetGroupId = parent.supersetGroupId
    )

    @Test
    fun `ordinary exercises keep their workbook order and set labels`() {
        val sets = listOf(
            mainSet("Push-Up", setId = 1, setNumber = 1),
            mainSet("Push-Up", setId = 1, setNumber = 2),
            mainSet("Row", setId = 2, setNumber = 1)
        )

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("Push-Up", "Row"), blocks.map { it.exerciseName })
        assertEquals(listOf("Set 1", "Set 2"), blocks[0].rows.map { it.label })
        assertEquals(listOf("Set 1"), blocks[1].rows.map { it.label })
        assertTrue(blocks.all { it.supersetLabel == null })
        assertTrue(blocks.flatMap { it.rows }.none { it.isDrop })
    }

    @Test
    fun `drop children are labelled D1 and D2 under their own main set`() {
        val first = mainSet("Squat", setId = 1, setNumber = 1)
        val second = mainSet("Squat", setId = 1, setNumber = 2)
        val sets = listOf(
            first,
            second,
            dropSet(first, dropIndex = 1),
            dropSet(first, dropIndex = 2),
            dropSet(second, dropIndex = 1)
        )

        val rows = RecentWorkoutFormatter.blocks(sets).single().rows

        assertEquals(listOf("Set 1", "D1", "D2", "Set 2", "D1"), rows.map { it.label })
        assertEquals(listOf(false, true, true, false, true), rows.map { it.isDrop })
        assertEquals(
            listOf(first.setUid, first.setUid, first.setUid, second.setUid, second.setUid),
            rows.map { it.set.parentSetUid ?: it.set.setUid }
        )
    }

    @Test
    fun `two supersetted exercises get A1 and A2 without merging their sets`() {
        val a = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val b = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val sets = listOf(a, b, a.copy(id = 3, setNumber = 2, setUid = "a2"), b.copy(id = 4, setNumber = 2, setUid = "b2"))

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("A1", "A2"), blocks.map { it.supersetLabel })
        assertEquals(listOf("Set 1", "Set 2"), blocks[0].rows.map { it.label })
        assertEquals(listOf("Set 1", "Set 2"), blocks[1].rows.map { it.label })
        assertTrue(blocks[0].rows.all { it.set.exerciseName == "A" })
        assertTrue(blocks[1].rows.all { it.set.exerciseName == "B" })
    }

    @Test
    fun `superset with drops on only the first exercise keeps the hierarchy separate`() {
        val a1 = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val a2 = mainSet("A", setId = 1, setNumber = 2, supersetGroupId = "g1")
        val b1 = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val sets = listOf(a1, a2, b1, dropSet(a1, 1), dropSet(a1, 2), dropSet(a2, 1))

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("A1", "A2"), blocks.map { it.supersetLabel })
        assertEquals(listOf("Set 1", "D1", "D2", "Set 2", "D1"), blocks[0].rows.map { it.label })
        assertEquals(listOf("Set 1"), blocks[1].rows.map { it.label })
        assertTrue(blocks[1].rows.none { it.isDrop })
    }

    @Test
    fun `superset with drops on only the second exercise keeps the hierarchy separate`() {
        val a1 = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val b1 = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val b2 = mainSet("B", setId = 2, setNumber = 2, supersetGroupId = "g1")
        val sets = listOf(a1, b1, b2, dropSet(b1, 1), dropSet(b2, 1), dropSet(b2, 2))

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("A1", "A2"), blocks.map { it.supersetLabel })
        assertEquals(listOf("Set 1"), blocks[0].rows.map { it.label })
        assertEquals(listOf("Set 1", "D1", "Set 2", "D1", "D2"), blocks[1].rows.map { it.label })
    }

    @Test
    fun `both superset exercises with multiple drops keep both branches independent`() {
        val a1 = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val a2 = mainSet("A", setId = 1, setNumber = 2, supersetGroupId = "g1")
        val b1 = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val b2 = mainSet("B", setId = 2, setNumber = 2, supersetGroupId = "g1")
        val sets = listOf(
            a1, a2, b1, b2,
            dropSet(a1, 1), dropSet(a1, 2),
            dropSet(a2, 1),
            dropSet(b1, 1), dropSet(b1, 2),
            dropSet(b2, 1), dropSet(b2, 2)
        )

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("A1", "A2"), blocks.map { it.supersetLabel })
        assertEquals(
            listOf("Set 1", "D1", "D2", "Set 2", "D1"),
            blocks[0].rows.map { it.label }
        )
        assertEquals(
            listOf("Set 1", "D1", "D2", "Set 2", "D1", "D2"),
            blocks[1].rows.map { it.label }
        )
        assertEquals(
            setOf(a1.setUid, a2.setUid),
            blocks[0].rows.filter { it.isDrop }.mapNotNull { it.set.parentSetUid }.toSet()
        )
        assertEquals(
            setOf(b1.setUid, b2.setUid),
            blocks[1].rows.filter { it.isDrop }.mapNotNull { it.set.parentSetUid }.toSet()
        )
        assertTrue(blocks[0].rows.all { it.set.exerciseName == "A" })
        assertTrue(blocks[1].rows.all { it.set.exerciseName == "B" })
    }

    @Test
    fun `warmup markers survive on both main and drop rows`() {
        val warmupMain = mainSet("Squat", setId = 1, setNumber = 0, warmup = true)
        val workMain = mainSet("Squat", setId = 1, setNumber = 1)
        val warmupDrop = dropSet(warmupMain, dropIndex = 1, warmup = true)
        val workDrop = dropSet(workMain, dropIndex = 1)
        val sets = listOf(warmupMain, workMain, warmupDrop, workDrop)

        val rows = RecentWorkoutFormatter.blocks(sets).single().rows

        assertEquals(listOf("Set 0", "D1", "Set 1", "D1"), rows.map { it.label })
        assertTrue(rows[0].set.isWarmup)
        assertTrue(rows[1].set.isWarmup)
        assertFalse(rows[2].set.isWarmup)
        assertFalse(rows[3].set.isWarmup)
    }

    @Test
    fun `multiple supersets are labelled independently and ungrouped exercises stay plain`() {
        val a = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val b = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val c = mainSet("C", setId = 3, setNumber = 1, supersetGroupId = "g2")
        val d = mainSet("D", setId = 4, setNumber = 1, supersetGroupId = "g2")
        val plain = mainSet("E", setId = 5, setNumber = 1)

        val blocks = RecentWorkoutFormatter.blocks(listOf(a, b, c, d, plain))

        assertEquals(listOf("A1", "A2", "A1", "A2", null), blocks.map { it.supersetLabel })
    }

    @Test
    fun `older workouts without superset metadata render as ordinary exercises`() {
        val sets = listOf(
            mainSet("Legacy A", setId = 1, setNumber = 1),
            mainSet("Legacy A", setId = 1, setNumber = 2),
            mainSet("Legacy B", setId = 2, setNumber = 1)
        )

        val blocks = RecentWorkoutFormatter.blocks(sets)

        assertEquals(listOf("Legacy A", "Legacy B"), blocks.map { it.exerciseName })
        assertTrue(blocks.all { it.supersetLabel == null })
    }

    @Test
    fun `a drop row is never used to invent a superset label`() {
        val a = mainSet("A", setId = 1, setNumber = 1, supersetGroupId = "g1")
        val b = mainSet("B", setId = 2, setNumber = 1, supersetGroupId = "g1")
        val strayDrop = mainSet("C", setId = 3, setNumber = 1, supersetGroupId = "g1")
            .copy(parentSetUid = a.setUid, dropIndex = 1)

        val labels = RecentWorkoutFormatter.supersetLabels(listOf(a, b, strayDrop))

        assertEquals("A1", labels["A"])
        assertEquals("A2", labels["B"])
        assertNull(labels["C"])
    }

    @Test
    fun `a drop row with a missing parent is normalised into a main set`() {
        val orphan = mainSet("A", setId = 1, setNumber = 1).copy(
            parentSetUid = "missing-parent",
            dropIndex = 1
        )

        val rows = RecentWorkoutFormatter.blocks(listOf(orphan)).single().rows

        assertEquals(listOf("Set 1"), rows.map { it.label })
        assertFalse(rows.single().isDrop)
    }
}
