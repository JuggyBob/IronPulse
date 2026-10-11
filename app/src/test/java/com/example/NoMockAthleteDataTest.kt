package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.SavedRoutine
import com.example.data.model.BodyWeightLog
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A fresh install must contain nothing but the exercise catalog.
 *
 * The app used to seed a complete invented athlete on first launch: "Alex Stone", 180 cm, 77.5 kg,
 * six weigh-ins, three workout sessions with ten logged sets, and two routines. Every one of those
 * numbers is fake, and once they are in the database they are indistinguishable from real ones on
 * the Progress tab. An athlete who installs the app cannot be expected to notice and delete them.
 *
 * So this asserts the absence of all of it. The profile now comes from the onboarding, and the
 * starter routines are offered as a choice rather than appearing as history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoMockAthleteDataTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The in memory builder does not run the prepopulation callback, so seed explicitly. This
        // is the same call onCreate makes.
        runBlocking {
            com.example.data.seed.DatasetSeeder.seedFresh(context, db.exerciseDao())
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a fresh install has no profile`() = runBlocking {
        assertNull(
            "the profile must be created by the onboarding, not seeded",
            db.userProfileDao().getProfileDirect()
        )
    }

    @Test
    fun `a fresh install has no weigh-ins`() = runBlocking {
        assertEquals(emptyList<Any>(), db.bodyWeightDao().getAllWeightLogs().first())
    }

    @Test
    fun `a fresh install has no workouts`() = runBlocking {
        assertEquals(emptyList<Any>(), db.workoutDao().getAllSets())
        assertEquals(emptyList<Any>(), db.workoutDao().getAllSessions().first())
    }

    @Test
    fun `a fresh install has no saved routines`() = runBlocking {
        assertEquals(
            "starter routines are offered as a choice, not seeded as history",
            emptyList<SavedRoutine>(),
            db.savedRoutineDao().getAllRoutines().first()
        )
    }

    @Test
    fun `the exercise catalog is the only thing a fresh install contains`() = runBlocking {
        val count = db.exerciseDao().getCount()

        assertTrue("the catalog must still be seeded, got $count", count > 1000)
    }

    @Test
    fun `the invented athlete is nowhere in the catalog either`() {
        // Belt and braces: "Alex Stone" was a person, not an exercise, but the point is that no
        // part of the old mock data survives anywhere a reader can see it.
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            "the invented athlete must not survive anywhere",
            context.assets.open("exercises.json").use { input ->
                val text = input.readBytes().decodeToString()
                !text.contains("Alex Stone", ignoreCase = true)
            }
        )
    }
}
