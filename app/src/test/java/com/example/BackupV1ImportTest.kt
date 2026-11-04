/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupFormat
import com.example.data.backup.UserDataBackupManager
import com.example.data.db.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.userDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The committed v1 archive must still import through the real manager, including the schema v1 to
 * v2 defaulting. This is deliberately separate from the DTO-level fixture test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupV1ImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var manager: UserDataBackupManager
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.userDataStore.edit { it.clear() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        manager = UserDataBackupManager(context, db)
    }

    @After
    fun tearDown() = runBlocking {
        context.userDataStore.edit { it.clear() }
        db.close()
    }

    @Test
    fun `the pre-feature v1 fixture imports with safe defaults`() = runBlocking {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("backups/v1-minimal.ironpulse")
            ?.use { it.readBytes() }
            ?: error("missing v1 fixture")
        val archive = temp.newFile("v1-minimal.ironpulse")
        archive.writeBytes(bytes)

        val prepared = manager.inspectFile(archive, archive.name)
        assertEquals(BackupFormat.CURRENT_DATA_SCHEMA_VERSION, prepared.preview.dataSchemaVersion)
        manager.applyImport(prepared)

        assertEquals(2, db.workoutDao().getAllSessionsOnce().size)
        assertTrue(db.workoutAiSummaryDao().getAllSummariesOnce().isEmpty())
        assertTrue(preferences.aiWorkoutDataAccessFlow.first().not())
        assertTrue(preferences.postWorkoutAiSummariesFlow.first().not())
        val themes = preferences.androidWidgetThemesFlow.first()
        assertEquals(ThemeMode.IRONPULSE, themes.streak)
        assertEquals(ThemeMode.IRONPULSE, themes.weekly)
        assertEquals(ThemeMode.IRONPULSE, themes.profile)
    }
}
