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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The accent is a real, independent preference with the original per-theme blue as its default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccentPreferencesTest {

    @Test
    fun `accent round trips through DataStore and survives a reopened repository`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setAccentColorHex(null)
        assertEquals("", repo.accentColorHexFlow.first())
        assertEquals("", repo.appSettingsFlow.first().accentColorHex)

        repo.setAccentColorHex("#9465e8")
        assertEquals("#9465E8", repo.accentColorHexFlow.first())
        assertEquals("#9465E8", repo.appSettingsFlow.first().accentColorHex)

        val reopened = UserPreferencesRepository(context)
        assertEquals("#9465E8", reopened.accentColorHexFlow.first())
        assertEquals("#9465E8", reopened.appSettingsFlow.first().accentColorHex)

        repo.setAccentColorHex(null)
        assertEquals("", repo.accentColorHexFlow.first())
        assertEquals("", repo.appSettingsFlow.first().accentColorHex)
    }

    @Test
    fun `invalid accent values never overwrite the saved colour`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setAccentColorHex("#22B8CF")
        assertEquals("#22B8CF", repo.accentColorHexFlow.first())

        repo.setAccentColorHex("not-a-colour")
        repo.setAccentColorHex("#12345")
        repo.setAccentColorHex("##22B8CF")
        assertEquals("#22B8CF", repo.accentColorHexFlow.first())

        repo.setAccentColorHex("")
        assertEquals("", repo.accentColorHexFlow.first())
    }

    @Test
    fun `accent is independent of theme and liquid glass`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setAccentColorHex("#D6B43D")
        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setLiquidGlassEnabled(false)

        assertEquals("#D6B43D", repo.accentColorHexFlow.first())
        assertEquals(ThemeMode.LIGHT, repo.themeModeFlow.first())
        assertTrue(!repo.liquidGlassEnabledFlow.first())

        repo.setThemeMode(ThemeMode.IRONPULSE)
        repo.setLiquidGlassEnabled(true)
        assertEquals("#D6B43D", repo.accentColorHexFlow.first())

        repo.setAccentColorHex(null)
        assertEquals(ThemeMode.IRONPULSE, repo.themeModeFlow.first())
        assertTrue(repo.liquidGlassEnabledFlow.first())
    }
}
