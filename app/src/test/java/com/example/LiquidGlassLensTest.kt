/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.ui.components.LiquidGlassLens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar lens is only convincing if the magnification is a continuous function of distance
 * rather than a per-item lookup. These tests pin the shape of that function: strongest at the
 * centre, zero at the edge, and strictly decreasing in between.
 */
class LiquidGlassLensTest {

    @Test
    fun `falloff peaks at the centre and reaches zero at the edge`() {
        assertEquals(1f, LiquidGlassLens.falloff(0f, 100f), 0.0001f)
        assertEquals(0f, LiquidGlassLens.falloff(100f, 100f), 0.0001f)
        assertEquals(0f, LiquidGlassLens.falloff(250f, 100f), 0.0001f)
    }

    @Test
    fun `falloff decreases monotonically across the influence radius`() {
        val radius = 100f
        var previous = LiquidGlassLens.falloff(0f, radius)
        for (distance in 1..100) {
            val current = LiquidGlassLens.falloff(distance.toFloat(), radius)
            assertTrue(
                "falloff increased at distance $distance",
                current <= previous + 0.0001f
            )
            previous = current
        }
    }

    @Test
    fun `scale grows towards the lens centre and never exceeds the maximum`() {
        val radius = 100f
        val edge = LiquidGlassLens.scaleFor(radius, radius)
        val middle = LiquidGlassLens.scaleFor(radius / 2f, radius)
        val centre = LiquidGlassLens.scaleFor(0f, radius)

        assertEquals(1f, edge, 0.0001f)
        assertTrue(middle > edge)
        assertTrue(centre > middle)
        assertEquals(LiquidGlassLens.MAX_SCALE, centre, 0.0001f)
    }

    @Test
    fun `a zero radius disables magnification instead of dividing by zero`() {
        assertEquals(0f, LiquidGlassLens.falloff(0f, 0f), 0.0001f)
        assertEquals(1f, LiquidGlassLens.scaleFor(0f, 0f), 0.0001f)
    }

    @Test
    fun `release hit testing uses measured item bounds`() {
        val bounds = listOf(
            Rect(0f, 0f, 100f, 80f),
            Rect(100f, 0f, 210f, 80f),
            Rect(210f, 0f, 300f, 80f)
        )

        assertEquals(0, LiquidGlassLens.itemIndexAt(Offset(50f, 40f), bounds))
        assertEquals(1, LiquidGlassLens.itemIndexAt(Offset(105f, 10f), bounds))
        assertEquals(2, LiquidGlassLens.itemIndexAt(Offset(280f, 79f), bounds))

        // Between/outside bounds and a release above the bar must not select a destination.
        assertNull(LiquidGlassLens.itemIndexAt(Offset(320f, 40f), bounds))
        assertNull(LiquidGlassLens.itemIndexAt(Offset(50f, 120f), bounds))
        assertNull(LiquidGlassLens.itemIndexAt(Offset(50f, 40f), emptyList()))
    }
}
