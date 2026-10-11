package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.GeminiSummary
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Gemini monthly summaries are real, persisted user data:
 * they can be listed, opened, deleted one by one and they are wiped by "Delete App Data".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiSummaryStorageTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FitnessRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FitnessRepository(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            userProfileDao = db.userProfileDao(),
            savedRoutineDao = db.savedRoutineDao(),
            bodyWeightDao = db.bodyWeightDao(),
            chatDao = db.chatDao(),
            geminiSummaryDao = db.geminiSummaryDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `summaries are stored newest first and can be deleted individually`() = runBlocking {
        val older = GeminiSummary(
            exerciseName = "Barbell Bench Press",
            report = "# Older report",
            daysLogged = 4,
            setCount = 12,
            bestOneRm = 100.0,
            trendPercent = 4.2,
            createdAt = 1_000L
        )
        val newer = GeminiSummary(
            exerciseName = "Weighted Pull-up",
            report = "# Newer report",
            daysLogged = 5,
            setCount = 15,
            bestOneRm = 120.0,
            trendPercent = -1.5,
            createdAt = 2_000L
        )

        repository.insertGeminiSummary(older)
        repository.insertGeminiSummary(newer)

        val stored = repository.getAllGeminiSummaries().first()
        assertEquals(2, stored.size)
        // Newest first
        assertEquals("Weighted Pull-up", stored.first().exerciseName)
        assertEquals(120.0, stored.first().bestOneRm, 0.001)

        // Opening one by id returns the full markdown report
        val opened = repository.getGeminiSummaryById(stored.first().id)
        assertEquals("# Newer report", opened?.report)

        repository.deleteGeminiSummary(stored.first())
        val remaining = repository.getAllGeminiSummaries().first()
        assertEquals(1, remaining.size)
        assertEquals("# Older report", remaining.first().report)
    }

    @Test
    fun `delete app data removes every saved summary`() = runBlocking {
        // An exercise created by the user must survive the wipe
        repository.createUserExercise(
            com.example.data.model.Exercise(
                name = "Z-Bar Curl",
                category = com.example.data.model.MuscleCategory.ARMS,
                exerciseType = com.example.data.model.ExerciseType.STANDARD_WEIGHT,
                equipment = "EZ-Bar"
            )
        )

        repository.insertGeminiSummary(GeminiSummary(exerciseName = "Pull-up", report = "# A"))
        repository.insertGeminiSummary(GeminiSummary(exerciseName = "Dip", report = "# B"))
        assertEquals(2, repository.getAllGeminiSummaries().first().size)

        repository.clearAllUserData()

        assertTrue(repository.getAllGeminiSummaries().first().isEmpty())
        // Summaries are gone, but the exercises are still there
        assertEquals(listOf("Z-Bar Curl"), repository.getAllExercises().first().map { it.name })
    }
}
