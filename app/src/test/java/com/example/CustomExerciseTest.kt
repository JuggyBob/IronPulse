package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import com.example.data.model.RoutineParser
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Custom exercises created by the user and exercises registered by the AI parser:
 *  - the user can edit and delete both kinds,
 *  - the built-in IronPulse library can never be modified,
 *  - every exercise survives "Delete App Data",
 *  - a name that is even remotely similar to an existing exercise is rejected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomExerciseTest {

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
    fun `a user created exercise is flagged as user owned`() = runBlocking {
        val id = repository.createUserExercise(
            Exercise(
                name = "Z-Bar Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "EZ-Bar",
                description = "Curl on an angled bar."
            )
        )

        assertTrue(id > 0)
        val stored = repository.getUserCreatedExerciseById(id)
        assertNotNull(stored)
        assertEquals("Z-Bar Curl", stored!!.name)
        assertTrue(stored.isCustom)
        assertFalse(stored.isAiGenerated)
        assertEquals(listOf("Z-Bar Curl"), repository.getUserCreatedExercises().first().map { it.name })
        // A user created exercise is never listed as AI generated
        assertTrue(repository.getAiGeneratedExercises().first().isEmpty())
    }

    @Test
    fun `an AI registered exercise is flagged as AI generated and never as user created`() = runBlocking {
        val id = repository.createAiGeneratedExercise(
            Exercise(
                name = "Pelican Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Rings",
                description = "AI registered exercise"
            )
        )

        assertTrue(id > 0)
        val stored = repository.getEditableExerciseById(id)
        assertNotNull(stored)
        assertEquals("Pelican Curl", stored!!.name)
        assertTrue("an AI exercise is custom, so the dataset seed leaves it alone", stored.isCustom)
        assertTrue(stored.isAiGenerated)
        assertEquals(listOf("Pelican Curl"), repository.getAiGeneratedExercises().first().map { it.name })
        assertTrue(repository.getUserCreatedExercises().first().isEmpty())
    }

    @Test
    fun `the user can edit and delete their own exercise`() = runBlocking {
        val id = repository.createUserExercise(
            Exercise(
                name = "Z-Bar Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "EZ-Bar"
            )
        )

        val updated = repository.getUserCreatedExerciseById(id)!!.copy(
            name = "Angled Bar Curl",
            equipment = "Barbell",
            description = "Updated by the user."
        )
        assertTrue(repository.updateUserExercise(updated))
        assertEquals("Angled Bar Curl", repository.getUserCreatedExerciseById(id)!!.name)

        assertTrue(repository.deleteUserExercise(id))
        assertNull(repository.getUserCreatedExerciseById(id))
        assertTrue(repository.getUserCreatedExercises().first().isEmpty())
    }

    @Test
    fun `the user can edit and delete an AI generated exercise and it stays AI generated`() = runBlocking {
        val id = repository.createAiGeneratedExercise(
            Exercise(
                name = "Pelican Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Rings",
                description = "AI registered exercise"
            )
        )

        // The ownership flags are not part of the UPDATE statement, so editing cannot clear them
        val edited = repository.getEditableExerciseById(id)!!.copy(
            name = "Pelican Ring Curl",
            equipment = "Rings / Bar",
            description = "Corrected by the user."
        )
        assertTrue(repository.updateUserExercise(edited))
        val stored = repository.getEditableExerciseById(id)!!
        assertEquals("Pelican Ring Curl", stored.name)
        assertEquals("Rings / Bar", stored.equipment)
        assertEquals("Corrected by the user.", stored.description)
        assertTrue(stored.isCustom)
        assertTrue(stored.isAiGenerated)
        assertEquals(listOf("Pelican Ring Curl"), repository.getAiGeneratedExercises().first().map { it.name })

        assertTrue(repository.deleteUserExercise(id))
        assertNull(repository.getEditableExerciseById(id))
        assertTrue(repository.getAiGeneratedExercises().first().isEmpty())
    }

    @Test
    fun `built-in library exercises can never be edited or deleted`() = runBlocking {
        val builtIn = builtInExercise()
        val builtInId = db.exerciseDao().insert(builtIn)

        val stored = db.exerciseDao().getAllExercises().first().first { it.id == builtInId }
        assertFalse(stored.isCustom)
        assertFalse(stored.isAiGenerated)

        // Both update and delete attempts are rejected by the database guards
        assertFalse(repository.updateUserExercise(stored.copy(name = "Hacked Name")))
        assertFalse(repository.deleteUserExercise(builtInId))

        // And nothing changed in the database
        val names = db.exerciseDao().getAllExercises().first().map { it.name }
        assertEquals(listOf(builtIn.name), names)
        // A locked exercise is listed neither as user owned nor as AI generated
        assertTrue(repository.getUserCreatedExercises().first().isEmpty())
        assertTrue(repository.getAiGeneratedExercises().first().isEmpty())
        assertNull(repository.getEditableExerciseById(builtInId))
    }

    @Test
    fun `the backfill only flags exercises that are not part of the shipped library`() = runBlocking {
        val builtIn = builtInExercise()
        val builtInId = db.exerciseDao().insert(builtIn)
        val aiId = db.exerciseDao().insert(
            Exercise(
                name = "Legacy AI Exercise",
                category = MuscleCategory.LEGS,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Barbell"
            )
        )
        val userId = repository.createUserExercise(
            Exercise(
                name = "Z-Bar Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "EZ-Bar"
            )
        )

        // This is exactly what MainViewModel.backfillAiGeneratedExercises does on start up
        val builtInNames = setOf(builtInExercise().name.trim())
        suspend fun runBackfill() {
            db.exerciseDao().getAllExercises().first()
                .filter { !it.isCustom && !it.isAiGenerated && it.name.trim() !in builtInNames }
                .forEach { repository.markExerciseAsAiGenerated(it.id) }
        }
        runBackfill()

        val byId = db.exerciseDao().getAllExercises().first().associateBy { it.id }
        assertFalse("the shipped library must stay locked", byId[builtInId]!!.isAiGenerated)
        assertTrue("the exercise stored by an older AI parse must be flagged", byId[aiId]!!.isAiGenerated)
        assertFalse("the user's own exercise must not be re-flagged", byId[userId]!!.isAiGenerated)
        assertEquals(listOf("Legacy AI Exercise"), repository.getAiGeneratedExercises().first().map { it.name })

        // Running it again on the next app start changes nothing
        runBackfill()
        assertFalse(db.exerciseDao().getAllExercises().first().first { it.id == userId }.isAiGenerated)
        assertEquals(listOf("Legacy AI Exercise"), repository.getAiGeneratedExercises().first().map { it.name })
    }

    @Test
    fun `delete app data keeps every exercise in the database`() = runBlocking {
        repository.createUserExercise(
            Exercise(
                name = "Z-Bar Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "EZ-Bar"
            )
        )
        repository.createAiGeneratedExercise(
            Exercise(
                name = "Pelican Curl",
                category = MuscleCategory.ARMS,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Rings"
            )
        )
        db.exerciseDao().insert(builtInExercise())

        repository.clearAllUserData()

        val remaining = db.exerciseDao().getAllExercises().first()
        assertEquals("no exercise may be removed by Delete App Data", 3, remaining.size)
        assertEquals(listOf("Z-Bar Curl"), repository.getUserCreatedExercises().first().map { it.name })
        assertEquals(listOf("Pelican Curl"), repository.getAiGeneratedExercises().first().map { it.name })
        assertTrue(remaining.any { it.name == builtInExercise().name })
    }

    @Test
    fun `a name similar to an existing exercise is rejected`() {
        val library = listOf(
            Exercise(id = 1, name = "Push-up", category = MuscleCategory.CHEST, exerciseType = ExerciseType.ADDED_WEIGHT, equipment = "Bodyweight"),
            Exercise(id = 2, name = "Weighted Pull-up", category = MuscleCategory.BACK, exerciseType = ExerciseType.ADDED_WEIGHT, equipment = "Dip Belt / Pull-up Bar"),
            Exercise(id = 3, name = "Barbell Bench Press", category = MuscleCategory.CHEST, exerciseType = ExerciseType.STANDARD_WEIGHT, equipment = "Barbell")
        )

        // Only punctuation / spacing / plural differences
        assertEquals("Push-up", RoutineParser.findMatchingExercise("push up", library)?.name)
        assertEquals("Push-up", RoutineParser.findMatchingExercise("pushups", library)?.name)
        assertEquals("Push-up", RoutineParser.findMatchingExercise("Push Up", library)?.name)
        assertEquals("Push-up", RoutineParser.findMatchingExercise("  push-up  ", library)?.name)
        // A different, more specific exercise already exists
        assertEquals("Weighted Pull-up", RoutineParser.findMatchingExercise("pull up", library)?.name)
        assertEquals("Barbell Bench Press", RoutineParser.findMatchingExercise("barbell bench", library)?.name)
    }

    @Test
    fun `a genuinely new exercise name is accepted`() {
        val library = listOf(
            Exercise(id = 1, name = "Push-up", category = MuscleCategory.CHEST, exerciseType = ExerciseType.ADDED_WEIGHT, equipment = "Bodyweight"),
            Exercise(id = 2, name = "Weighted Pull-up", category = MuscleCategory.BACK, exerciseType = ExerciseType.ADDED_WEIGHT, equipment = "Dip Belt / Pull-up Bar")
        )

        assertNull(RoutineParser.findMatchingExercise("Z-Bar Curl", library))
        assertNull(RoutineParser.findMatchingExercise("Jefferson Deadlift", library))
    }

    /** A locked library exercise, standing in for a row imported from the dataset catalog. */
    private fun builtInExercise() = Exercise(
        name = "Barbell Bench Press",
        category = MuscleCategory.CHEST,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell",
        sourceId = "0025"
    )
}