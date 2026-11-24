package com.example

import com.example.data.model.EffortDisplay
import com.example.data.model.EffortScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffortScaleTest {

    @Test
    fun `RIR maps to the canonical RPE scale including the four plus bucket`() {
        assertEquals(10.0, EffortScale.rirToRpe(0), 0.0)
        assertEquals(9.0, EffortScale.rirToRpe(1), 0.0)
        assertEquals(8.0, EffortScale.rirToRpe(2), 0.0)
        assertEquals(7.0, EffortScale.rirToRpe(3), 0.0)
        assertEquals(6.0, EffortScale.rirToRpe(4), 0.0)
        assertEquals(6.0, EffortScale.rirToRpe(9), 0.0)
    }

    @Test
    fun `RPE renders back to the same RIR bucket`() {
        assertEquals(0, EffortScale.rpeToRir(10.0))
        assertEquals(1, EffortScale.rpeToRir(9.0))
        assertEquals(2, EffortScale.rpeToRir(8.0))
        assertEquals(3, EffortScale.rpeToRir(7.0))
        assertEquals(4, EffortScale.rpeToRir(6.0))
    }

    @Test
    fun `a missing value is not displayed as zero effort`() {
        assertEquals("Not logged", EffortScale.display(null, EffortDisplay.RIR, true))
        assertEquals("Not logged", EffortScale.display(null, EffortDisplay.RPE, true))
        assertNull(EffortScale.normalize(null))
    }

    @Test
    fun `RIR is hidden for a non-repetition modality`() {
        assertEquals("RPE 8", EffortScale.display(8.0, EffortDisplay.RIR, false))
        assertEquals("RIR 2", EffortScale.display(8.0, EffortDisplay.RIR, true))
        assertEquals("RPE 8", EffortScale.display(8.0, EffortDisplay.RPE, true))
    }

    @Test
    fun `normalisation clamps and rounds to half steps`() {
        assertEquals(6.0, EffortScale.normalize(4.0))
        assertEquals(10.0, EffortScale.normalize(12.0))
        assertEquals(8.5, EffortScale.normalize(8.25))
    }

    @Test
    fun `stepping follows the visible preference`() {
        assertEquals(9.0, EffortScale.step(8.0, EffortDisplay.RIR, true, 1))
        assertEquals(10.0, EffortScale.step(8.0, EffortDisplay.RPE, true, 4))
        assertEquals(6.0, EffortScale.step(6.0, EffortDisplay.RPE, true, -1))
        assertEquals(7.0, EffortScale.step(null, EffortDisplay.RIR, true, 1))
    }
}
