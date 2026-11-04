/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.theme.AppPalettes
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The theme is a real preference with the original IronPulse look as its default.
 *
 * It must be readable from both the dedicated flow that drives the app root and the AppSettings
 * snapshot the Settings screen uses, and it must survive a fresh repository instance. It is also
 * deliberately independent of Liquid Glass: switching one never changes the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePreferencesTest {

    @Test
    fun `theme preference round trips through DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        // An install that never opens Settings keeps the original identity.
        repo.setThemeMode(ThemeMode.IRONPULSE)
        assertEquals(ThemeMode.IRONPULSE, repo.themeModeFlow.first())
        assertEquals(ThemeMode.IRONPULSE, repo.appSettingsFlow.first().themeMode)

        ThemeMode.values().forEach { mode ->
            repo.setThemeMode(mode)
            assertEquals(mode, repo.themeModeFlow.first())
            assertEquals(mode, repo.appSettingsFlow.first().themeMode)

            // A second repository points at the same process-wide DataStore, which is the same
            // path the app takes after a restart.
            val reopened = UserPreferencesRepository(context)
            assertEquals(mode, reopened.themeModeFlow.first())
            assertEquals(mode, reopened.appSettingsFlow.first().themeMode)
        }

        repo.setThemeMode(ThemeMode.IRONPULSE)
    }

    @Test
    fun `theme and liquid glass do not disturb each other`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setThemeMode(ThemeMode.IRONPULSE)
        repo.setLiquidGlassEnabled(true)

        repo.setThemeMode(ThemeMode.LIGHT)
        assertTrue(repo.liquidGlassEnabledFlow.first())
        assertTrue(repo.appSettingsFlow.first().liquidGlassEnabled)

        repo.setLiquidGlassEnabled(false)
        assertEquals(ThemeMode.LIGHT, repo.themeModeFlow.first())
        assertEquals(ThemeMode.LIGHT, repo.appSettingsFlow.first().themeMode)

        repo.setThemeMode(ThemeMode.IRONPULSE)
        repo.setLiquidGlassEnabled(true)
    }

    @Test
    fun `unknown or missing storage keys fall back to IronPulse`() {
        assertEquals(ThemeMode.IRONPULSE, ThemeMode.fromStorageKey(null))
        assertEquals(ThemeMode.IRONPULSE, ThemeMode.fromStorageKey(""))
        assertEquals(ThemeMode.IRONPULSE, ThemeMode.fromStorageKey("not-a-theme"))
        ThemeMode.values().forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageKey(mode.storageKey))
        }
    }

    @Test
    fun `every mode has a complete distinct palette`() {
        val backgrounds = ThemeMode.values().map { mode ->
            val palette = AppPalettes.forMode(mode)
            assertEquals(mode, palette.mode)
            palette.background
        }
        assertEquals(backgrounds.size, backgrounds.distinct().size)

        val light = AppPalettes.forMode(ThemeMode.LIGHT)
        assertTrue(light.isLight)
        assertFalse(AppPalettes.forMode(ThemeMode.IRONPULSE).isLight)

        // AMOLED is the only theme whose primary large background is true black.
        assertEquals(Color(0xFF000000), AppPalettes.forMode(ThemeMode.AMOLED).background)
        assertNotEquals(
            AppPalettes.forMode(ThemeMode.AMOLED).background,
            AppPalettes.forMode(ThemeMode.AMOLED).surface
        )
    }

    @Test
    fun `every theme keeps an empty heatmap cell visible against its card surface`() {
        ThemeMode.values().forEach { mode ->
            val palette = AppPalettes.forMode(mode)
            val empty = palette.heatmap.empty
            val surface = palette.surface
            assertNotEquals("$mode empty cell must not equal its card", surface, empty)

            // A visible but quiet step: at least 20 RGB levels of total separation. The old Dark
            // and AMOLED values were one level apart and disappeared on most displays.
            val difference =
                abs((empty.red * 255).toInt() - (surface.red * 255).toInt()) +
                    abs((empty.green * 255).toInt() - (surface.green * 255).toInt()) +
                    abs((empty.blue * 255).toInt() - (surface.blue * 255).toInt())
            assertTrue("$mode empty cell contrast was $difference", difference >= 20)
        }
    }
}
