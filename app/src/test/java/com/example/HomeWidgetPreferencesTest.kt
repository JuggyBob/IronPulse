package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restoring the default Home widgets is a reset of configuration, not a data wipe.
 *
 * One test covers the whole round trip because the preferences file is process-wide: a second test
 * would open a second DataStore for the same file. The assertions pin both halves of the promise:
 * the widget keys return to their defaults, and an unrelated setting survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeWidgetPreferencesTest {

    @Test
    fun `restoring defaults resets only the widget configuration`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        assertTrue(repo.homeWidgetConfigFlow.first().order.isEmpty())
        assertNull(repo.homeWidgetConfigFlow.first().hiddenIds)
        assertEquals(
            UserPreferencesRepository.DEFAULT_HOME_WIDGET_WEEKLY_GOAL,
            repo.homeWidgetWeeklyGoalFlow.first()
        )
        assertEquals("", repo.homeWidgetPhotoPathFlow.first())

        // An unrelated setting that a reset must never touch.
        repo.setWeekStartsOnMonday(false)

        repo.setHomeWidgetConfig(
            order = listOf("quote", "today", "photo"),
            hiddenIds = setOf("week", "recent")
        )
        repo.setHomeWidgetPhotoPath("/tmp/widget.jpg")
        repo.setHomeWidgetWeeklyGoal(6)

        val configured = repo.homeWidgetConfigFlow.first()
        assertEquals(listOf("quote", "today", "photo"), configured.order)
        assertEquals(setOf("week", "recent"), configured.hiddenIds)
        assertEquals("/tmp/widget.jpg", repo.homeWidgetPhotoPathFlow.first())
        assertEquals(6, repo.homeWidgetWeeklyGoalFlow.first())

        repo.resetHomeWidgets()

        val restored = repo.homeWidgetConfigFlow.first()
        assertTrue(restored.order.isEmpty())
        assertNull(restored.hiddenIds)
        assertEquals("", repo.homeWidgetPhotoPathFlow.first())
        assertEquals(
            UserPreferencesRepository.DEFAULT_HOME_WIDGET_WEEKLY_GOAL,
            repo.homeWidgetWeeklyGoalFlow.first()
        )
        assertFalse(repo.appSettingsFlow.first().weekStartsOnMonday)
    }
}
