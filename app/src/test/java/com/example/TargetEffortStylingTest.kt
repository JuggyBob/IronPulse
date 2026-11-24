/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Target Effort stepper uses the neutral filled pair, not the accent.
 *
 * `inverseSurface` is the design-system fill (near-white in the three dark themes) and
 * `inverseOnSurface` is the glyph colour (near-black). In Light the pair inverts so the control
 * stays a standard filled button instead of a white square on a white page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class TargetEffortStylingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun schemeFor(mode: ThemeMode): ColorScheme {
        var captured: ColorScheme? = null
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = mode) {
                captured = MaterialTheme.colorScheme
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(captured) { "Theme did not provide a Material color scheme" }
    }

    private fun assertDarkThemePair(mode: ThemeMode) {
        val scheme = schemeFor(mode)
        assertTrue(
            "$mode fill should be light, was ${scheme.inverseSurface}",
            scheme.inverseSurface.luminance() > 0.7f
        )
        assertTrue(
            "$mode glyph should be dark, was ${scheme.inverseOnSurface}",
            scheme.inverseOnSurface.luminance() < 0.2f
        )
    }

    @Test
    fun `ironpulse theme uses a light fill and dark glyphs`() = assertDarkThemePair(ThemeMode.IRONPULSE)

    @Test
    fun `dark theme uses a light fill and dark glyphs`() = assertDarkThemePair(ThemeMode.DARK)

    @Test
    fun `amoled theme uses a light fill and dark glyphs`() = assertDarkThemePair(ThemeMode.AMOLED)

    @Test
    fun `light theme inverts the pair instead of painting white on white`() {
        val scheme = schemeFor(ThemeMode.LIGHT)
        assertTrue(
            "Light fill should be dark, was ${scheme.inverseSurface}",
            scheme.inverseSurface.luminance() < 0.2f
        )
        assertTrue(
            "Light glyph should be light, was ${scheme.inverseOnSurface}",
            scheme.inverseOnSurface.luminance() > 0.7f
        )
    }
}
