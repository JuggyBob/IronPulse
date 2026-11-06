/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AccentColors
import com.example.ui.theme.AppPalettes
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorTest {

    @Test
    fun `presets match the required names and colours`() {
        val expected = linkedMapOf(
            "IronPulse Blue" to "#58A6FF",
            "Purple" to "#9465E8",
            "Cyan" to "#22B8CF",
            "Green" to "#35AD75",
            "Orange" to "#E99539",
            "Red" to "#E45D65",
            "Pink" to "#DF70A8",
            "Yellow" to "#D6B43D"
        )

        assertEquals(expected.keys.toList(), AccentColors.presets.map { it.displayName })
        assertEquals(expected.values.toList(), AccentColors.presets.map { it.hex })
        assertTrue(AccentColors.presets.first().isDefault)
    }

    @Test
    fun `the existing default blue is preserved until an override is chosen`() {
        ThemeMode.values().forEach { mode ->
            val base = AppPalettes.forMode(mode)
            val withNullOverride = AppPalettes.forMode(mode, null)
            assertEquals(base, withNullOverride)
        }

        assertEquals(Color(0xFF58A6FF), AppPalettes.forMode(ThemeMode.IRONPULSE).accentPrimary)
        assertEquals(Color(0xFF58A6FF), AppPalettes.forMode(ThemeMode.DARK).accentPrimary)
        assertEquals(Color(0xFF58A6FF), AppPalettes.forMode(ThemeMode.AMOLED).accentPrimary)
        assertEquals(Color(0xFF0969DA), AppPalettes.forMode(ThemeMode.LIGHT).accentPrimary)
    }

    @Test
    fun `hex parsing accepts optional hash and upper or lower case`() {
        val expected = Color(0xFF3985E8)
        assertEquals(expected, AccentColors.parseHex("#3985E8"))
        assertEquals(expected, AccentColors.parseHex("3985e8"))
        assertEquals(expected, AccentColors.parseHex(" #3985E8 "))
        assertEquals("#3985E8", AccentColors.normalizeHex("3985e8"))
    }

    @Test
    fun `hex parsing rejects incomplete or invalid values`() {
        assertNull(AccentColors.parseHex(null))
        assertNull(AccentColors.parseHex(""))
        assertNull(AccentColors.parseHex("#"))
        assertNull(AccentColors.parseHex("3985E"))
        assertNull(AccentColors.parseHex("#3985E80"))
        assertNull(AccentColors.parseHex("GGGGGG"))
        assertNull(AccentColors.parseHex("##3985E8"))
        assertNull(AccentColors.normalizeHex("3985 E8"))
    }

    @Test
    fun `hex round trips preserve the exact channels`() {
        listOf(
            Color(0xFF000000),
            Color(0xFFFFFFFF),
            Color(0xFF3985E8),
            Color(0xFFE45D65),
            Color(0xFF01A2F3)
        ).forEach { color ->
            assertEquals(color, AccentColors.parseHex(AccentColors.toHex(color)))
        }
    }

    @Test
    fun `preset lookup distinguishes the default, presets and custom values`() {
        assertEquals("IronPulse Blue", AccentColors.presetFor(null)?.displayName)
        assertEquals("IronPulse Blue", AccentColors.presetFor("")?.displayName)
        assertEquals("Purple", AccentColors.presetFor("#9465E8")?.displayName)
        assertNull(AccentColors.presetFor("#123456"))
        assertTrue(AccentColors.isCustom("#123456"))
        assertFalse(AccentColors.isCustom("#9465E8"))
        assertFalse(AccentColors.isCustom(null))
    }

    @Test
    fun `accent override changes only the primary accent role`() {
        val base = AppPalettes.forMode(ThemeMode.IRONPULSE)
        val overridden = AppPalettes.forMode(ThemeMode.IRONPULSE, Color(0xFFE45D65))

        assertNotEquals(base.accentPrimary, overridden.accentPrimary)
        assertEquals(base.mode, overridden.mode)
        assertEquals(base.background, overridden.background)
        assertEquals(base.surface, overridden.surface)
        assertEquals(base.surfaceVariant, overridden.surfaceVariant)
        assertEquals(base.textPrimary, overridden.textPrimary)
        assertEquals(base.textSecondary, overridden.textSecondary)
        assertEquals(base.accentSecondary, overridden.accentSecondary)
        assertEquals(base.accentTertiary, overridden.accentTertiary)
        assertEquals(base.accentPurple, overridden.accentPurple)
        assertEquals(base.accentAmber, overridden.accentAmber)
        assertEquals(base.danger, overridden.danger)
        assertEquals(base.aiAccent, overridden.aiAccent)
        assertEquals(base.onAccent, overridden.onAccent)
        assertEquals(base.chart, overridden.chart)
        assertEquals(base.heatmap, overridden.heatmap)
        assertEquals(base.muscle, overridden.muscle)
        assertEquals(base.glass, overridden.glass)
    }

    @Test
    fun `custom accents stay readable on every theme`() {
        val customColors = listOf(
            Color.White,
            Color.Black,
            Color(0xFF58A6FF),
            Color(0xFF0969DA),
            Color(0xFFD6B43D),
            Color(0xFF123456),
            Color(0xFF00FF00)
        )

        ThemeMode.values().forEach { mode ->
            val base = AppPalettes.forMode(mode)
            customColors.forEach { color ->
                val adjusted = AppPalettes.forMode(mode, color).accentPrimary
                assertTrue(
                    "$mode ${AccentColors.toHex(color)} vs onAccent was " +
                        contrastRatio(adjusted, base.onAccent),
                    contrastRatio(adjusted, base.onAccent) >= AccentColors.MIN_CONTRAST - 0.05f
                )
                assertTrue(
                    "$mode ${AccentColors.toHex(color)} vs surface was " +
                        contrastRatio(adjusted, base.surface),
                    contrastRatio(adjusted, base.surface) >= AccentColors.MIN_CONTRAST - 0.05f
                )
            }
        }
    }

    @Test
    fun `every preset stays readable on every theme`() {
        AccentColors.presets.forEach { preset ->
            val color = AccentColors.parseHex(preset.hex)!!
            ThemeMode.values().forEach { mode ->
                val base = AppPalettes.forMode(mode)
                val adjusted = AppPalettes.forMode(mode, color).accentPrimary
                assertTrue(
                    "${preset.displayName} on $mode vs onAccent was " +
                        contrastRatio(adjusted, base.onAccent),
                    contrastRatio(adjusted, base.onAccent) >= AccentColors.MIN_CONTRAST - 0.05f
                )
                assertTrue(
                    "${preset.displayName} on $mode vs surface was " +
                        contrastRatio(adjusted, base.surface),
                    contrastRatio(adjusted, base.surface) >= AccentColors.MIN_CONTRAST - 0.05f
                )
            }
        }
    }

    @Test
    fun `semantic colours keep their original meaning under every accent`() {
        val semanticPairs = listOf(
            ThemeMode.IRONPULSE to AppPalettes.forMode(ThemeMode.IRONPULSE),
            ThemeMode.DARK to AppPalettes.forMode(ThemeMode.DARK),
            ThemeMode.LIGHT to AppPalettes.forMode(ThemeMode.LIGHT),
            ThemeMode.AMOLED to AppPalettes.forMode(ThemeMode.AMOLED)
        )

        semanticPairs.forEach { (mode, base) ->
            val overridden = AppPalettes.forMode(mode, Color(0xFF35AD75))
            assertEquals(base.accentSecondary, overridden.accentSecondary)
            assertEquals(base.danger, overridden.danger)
            assertEquals(base.heatmap, overridden.heatmap)
            assertEquals(base.chart, overridden.chart)
        }
    }
}
