package com.example

import com.example.data.model.ExerciseFilters
import com.example.data.model.MusclePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercise list has two sorts, the muscle group chips and the body part map. Both being set
 * intersects and silently hides most of the catalog, so picking one always clears the other.
 */
class ExerciseFiltersTest {

    @Test
    fun `a muscle group chip clears a body part chosen on the map`() {
        val start = ExerciseFilters(category = "All", musclePart = MusclePart.CHEST)

        val after = start.selectCategory("Legs")

        assertEquals("Legs", after.category)
        assertNull(after.musclePart)
    }

    @Test
    fun `a body part on the map clears the muscle group chip`() {
        val start = ExerciseFilters(category = "Legs", musclePart = null)

        val after = start.selectMusclePart(MusclePart.CHEST)

        assertEquals("All", after.category)
        assertEquals(MusclePart.CHEST, after.musclePart)
    }

    @Test
    fun `clearing the body part leaves the muscle group chip alone`() {
        // The "Clear" button on the filter banner is not a new selection, so it must not also
        // undo the chip the athlete picked before
        val start = ExerciseFilters(category = "Legs", musclePart = MusclePart.CHEST)

        val after = start.selectMusclePart(null)

        assertEquals("Legs", after.category)
        assertNull(after.musclePart)
    }

    @Test
    fun `switching between the two sorts repeatedly always ends with exactly one set`() {
        var filters = ExerciseFilters()

        filters = filters.selectMusclePart(MusclePart.LEGS)
        assertTrue(filters.hasMusclePart)
        assertFalse(filters.hasCategory)

        filters = filters.selectCategory("Chest")
        assertFalse(filters.hasMusclePart)
        assertTrue(filters.hasCategory)

        filters = filters.selectMusclePart(MusclePart.BACK)
        assertTrue(filters.hasMusclePart)
        assertFalse(filters.hasCategory)

        filters = filters.selectCategory("Legs")
        assertTrue(filters.hasCategory)
        assertFalse(filters.hasMusclePart)
    }

    @Test
    fun `the All chip reads as no chip at all`() {
        assertFalse(ExerciseFilters(category = "All").hasCategory)
        assertFalse(ExerciseFilters(category = null).hasCategory)
        assertFalse(ExerciseFilters(category = "").hasCategory)
        assertFalse(ExerciseFilters(category = "all").hasCategory)
        assertTrue(ExerciseFilters(category = "Legs").hasCategory)
    }

    @Test
    fun `the default state sorts nothing`() {
        val start = ExerciseFilters()
        assertFalse(start.hasCategory)
        assertFalse(start.hasMusclePart)
    }
}