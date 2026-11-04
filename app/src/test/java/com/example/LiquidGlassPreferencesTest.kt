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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Liquid Glass switch is a real preference, not a session flag. It must be readable both from
 * the dedicated flow that drives the app root and from the AppSettings snapshot the Settings screen
 * uses, and the value must survive a fresh repository instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiquidGlassPreferencesTest {

    @Test
    fun `liquid glass preference round trips through DataStore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setLiquidGlassEnabled(false)
        assertFalse(repo.liquidGlassEnabledFlow.first())
        assertFalse(repo.appSettingsFlow.first().liquidGlassEnabled)

        // A second repository points at the same process-wide DataStore, which is the same path the
        // app takes after a restart.
        val reopened = UserPreferencesRepository(context)
        assertFalse(reopened.liquidGlassEnabledFlow.first())
        assertFalse(reopened.appSettingsFlow.first().liquidGlassEnabled)

        repo.setLiquidGlassEnabled(true)
        assertTrue(repo.liquidGlassEnabledFlow.first())
        assertTrue(repo.appSettingsFlow.first().liquidGlassEnabled)
    }
}
