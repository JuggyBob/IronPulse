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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 4.4 active routine becomes the active Schedule exactly once.
 *
 * The Schedule rows are created asynchronously by the Room open migration, so the promotion must
 * be able to wait for them. Once promoted, the legacy key is cleared: deliberately choosing no
 * Schedule later must not resurrect the old plan on the next start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActiveSchedulePromotionTest {

    @Test
    fun `promotion activates the migrated schedule and consumes the legacy key`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setActiveRoutineId(42L)
        repo.setActiveScheduleId(0L)
        repo.migrateActiveRoutine(
            legacyRoutineId = 42L,
            scheduleId = 7L,
            consumeLegacyKey = true
        )

        assertEquals(7L, repo.activeScheduleIdFlow.first())
        assertEquals(0L, repo.activeRoutineIdFlow.first())

        // A later deactivation must not re-promote the same legacy routine.
        repo.setActiveScheduleId(0L)
        repo.migrateActiveRoutine(
            legacyRoutineId = 42L,
            scheduleId = 7L,
            consumeLegacyKey = true
        )
        assertEquals(0L, repo.activeScheduleIdFlow.first())
    }

    @Test
    fun `promotion never overwrites a schedule the athlete already chose`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UserPreferencesRepository(context)

        repo.setActiveRoutineId(42L)
        repo.setActiveScheduleId(9L)
        repo.migrateActiveRoutine(legacyRoutineId = 42L, scheduleId = 7L)

        assertEquals(9L, repo.activeScheduleIdFlow.first())
        assertEquals(42L, repo.activeRoutineIdFlow.first())
    }
}
