/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fullscreen Mode is a real preference, enabled by default and independent of the visual settings.
 *
 * The activity applies the flow to the window, so this test covers the persistence half: a fresh
 * repository must see the same value after a process restart, and changing it must not disturb the
 * theme or Liquid Glass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullscreenPreferencesTest {

    @Test
    fun `fullscreen defaults on and round trips through DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.clearAllPreferences()
        assertTrue(repo.fullscreenEnabledFlow.first())
        assertTrue(repo.appSettingsFlow.first().fullscreenEnabled)

        repo.setFullscreenEnabled(false)
        assertFalse(repo.fullscreenEnabledFlow.first())
        assertFalse(repo.appSettingsFlow.first().fullscreenEnabled)

        // A second repository sees the same process-wide DataStore, as it would after a restart.
        val reopened = UserPreferencesRepository(context)
        assertFalse(reopened.fullscreenEnabledFlow.first())
        assertFalse(reopened.appSettingsFlow.first().fullscreenEnabled)

        repo.setFullscreenEnabled(true)
    }

    @Test
    fun `fullscreen does not disturb theme or liquid glass`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setLiquidGlassEnabled(false)
        repo.setFullscreenEnabled(false)

        repo.setFullscreenEnabled(true)
        assertTrue(repo.fullscreenEnabledFlow.first())
        assertFalse(repo.liquidGlassEnabledFlow.first())
        assertTrue(repo.themeModeFlow.first() == ThemeMode.LIGHT)

        repo.setThemeMode(ThemeMode.IRONPULSE)
        repo.setLiquidGlassEnabled(true)
        repo.setFullscreenEnabled(true)
    }
}
