/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CustomRestTimer
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import com.example.ui.viewmodel.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestTimerControllerTest {

    private lateinit var context: Context
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: RestTimerController

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.userDataStore.edit { it.clear() }
        preferences = UserPreferencesRepository(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        controller = RestTimerController(
            application = ApplicationProvider.getApplicationContext(),
            preferences = preferences,
            scope = scope
        )
    }

    @After
    fun tearDown() {
        controller.stop()
        scope.cancel()
    }

    @Test
    fun `custom timer save updates an existing name and delete removes it`() = runBlocking {
        // Activate the process-wide DataStore before the controller's first write.
        preferences.savedRestTimersFlow.first()

        controller.saveCustomTimer("My Rest", 45)
        val saved = awaitTimers { timers -> timers.any { it.name == "My Rest" } }
        assertEquals(45, saved.first { it.name == "My Rest" }.totalSeconds)

        controller.saveCustomTimer("My Rest", 90)
        val updated = awaitTimers { timers ->
            timers.count { it.name == "My Rest" } == 1 &&
                timers.first { it.name == "My Rest" }.totalSeconds == 90
        }
        assertEquals(1, updated.count { it.name == "My Rest" })

        val id = updated.first { it.name == "My Rest" }.id
        controller.deleteCustomTimer(id)
        val afterDelete = awaitTimers { timers -> timers.none { it.id == id } }
        assertTrue(afterDelete.none { it.id == id })
    }

    /**
     * Reads the current persisted list until [predicate] is true.
     *
     * DataStore can commit a write before this collector has subscribed to updates, so waiting for
     * a particular emission is racy on a fast CI runner: the value is correct but the emission was
     * missed. Re-reading the current value is deterministic. The first assertion in this class
     * still exercises the normal flow path.
     */
    private suspend fun awaitTimers(
        predicate: (List<CustomRestTimer>) -> Boolean
    ): List<CustomRestTimer> = withTimeout(60_000) {
        var timers = preferences.savedRestTimersFlow.first()
        while (!predicate(timers)) {
            delay(50)
            timers = preferences.savedRestTimersFlow.first()
        }
        timers
    }
}
