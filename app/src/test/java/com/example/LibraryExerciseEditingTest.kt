package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.SavedRoutine
import com.example.data.repository.FitnessRepository
import com.example.data.seed.DatasetSeeder
import com.example.data.seed.LibraryEditTracker
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
 * Editing a library exercise, and putting the library back.
 *
 * These two are one feature: an athlete who can edit the shipped catalog can get it wrong, so the
 * revert has to be exactly the inverse. What makes it delicate is that the library rows are the ones
 * logged sets point at, so both operations must preserve the row ids and must never rewrite history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryExerciseEditingTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("library_edit_test.db")
        db = Room.databaseBuilder(context, AppDatabase::class.java, "library_edit_test.db")
            .addMigrations(*AppDatabase::class.java.getDeclaredField("MIGRATION_7_8").let { field ->
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                arrayOf(field.get(null) as androidx.room.migration.Migration)
            })
            .allowMainThreadQueries()
            .build()
        runBlocking { DatasetSeeder.seedFresh(context, db.exerciseDao()) }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("library_edit_test.db")
    }

    private suspend fun libraryExercise() = db.exerciseDao()
        .getAllExercisesList()
        .first { it.name == "Decline Push-Up" }

    /** The repository under test, wired to the same database. */
    private fun repository(dao: AppDatabase = db) = FitnessRepository(
        exerciseDao = dao.exerciseDao(),
        workoutDao = dao.workoutDao(),
        userProfileDao = dao.userProfileDao(),
        savedRoutineDao = dao.savedRoutineDao(),
        bodyWeightDao = dao.bodyWeightDao(),
        chatDao = dao.chatDao(),
        geminiSummaryDao = dao.geminiSummaryDao()
    )

    @Test
    fun `a library exercise can be retyped without ownership`() = runBlocking {
        val before = libraryExercise()
        // Whichever type it shipped as, the test writes the other one, so it can tell whether the
        // write actually landed rather than reading back what was already there.
        val newType =
            if (before.exerciseType == ExerciseType.ADDED_WEIGHT) ExerciseType.PURE_BODYWEIGHT
            else ExerciseType.ADDED_WEIGHT

        val wrote = repository().updateLibraryExercise(before.copy(exerciseType = newType))

        assertTrue("the write must land", wrote)
        val after = libraryExercise()
        assertEquals(newType, after.exerciseType)
        // The row id is never touched, which is what keeps logged sets pointing at it.
        assertEquals(before.id, after.id)
    }

    @Test
    fun `editing a library exercise rewrites every field it exposes`() = runBlocking {
        val before = libraryExercise()
        val originalId = before.id

        val fixed = before.copy(
            name = "Decline Push-Up (My Variant)",
            category = "Chest",
            exerciseType = ExerciseType.ADDED_WEIGHT,
            equipment = "Dumbbell",
            bodyPart = "chest",
            target = "Pectorals",
            secondaryMuscles = "[\"Triceps\"]",
            instructions = "Feet on a bench, hands on dumbbells.",
            description = "Feet on a bench, hands on dumbbells."
        )
        repository().updateLibraryExercise(fixed)

        val after = db.exerciseDao().getAllExercisesList()
            .first { it.id == originalId }

        assertEquals("Decline Push-Up (My Variant)", after.name)
        assertEquals("Chest", after.category)
        assertEquals(ExerciseType.ADDED_WEIGHT, after.exerciseType)
        assertEquals("Dumbbell", after.equipment)
        assertEquals("chest", after.bodyPart)
        assertEquals("Pectorals", after.target)
        assertEquals("[\"Triceps\"]", after.secondaryMuscles)
        assertTrue(after.instructions.isNotBlank())
    }

    @Test
    fun `retagging a library exercise does not touch logged history`() = runBlocking {
        // The heart of "workouts and previously logged exercises remain unchanged": correcting an
        // exercise must not rewrite what the athlete already logged under it.
        val exercise = libraryExercise()
        db.workoutDao().insertSet(
            LoggedSet(
                sessionId = 1,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                // Recorded under the old, wrong type
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = 10,
                holdDurationSeconds = 3
            )
        )

        repository().updateLibraryExercise(exercise.copy(exerciseType = ExerciseType.ADDED_WEIGHT))

        val logged = db.workoutDao().getSetsForExerciseList(exercise.name).single()
        assertEquals(
            "a logged set keeps the type it was recorded under",
            ExerciseType.PURE_BODYWEIGHT,
            logged.exerciseType
        )
        // And its numbers are untouched.
        assertEquals(10, logged.reps)
        assertEquals(3, logged.holdDurationSeconds)
    }

    @Test
    fun `reverting restores the dataset values of an edited library row`() = runBlocking {
        val original = libraryExercise()
        val originalName = original.name
        val originalType = original.exerciseType

        // Rename and retype, which is the worst case for a revert: the row is no longer findable by
        // its name, so the restore has to go by the dataset id instead.
        repository().updateLibraryExercise(
            original.copy(name = "Renamed Push-Up", exerciseType = ExerciseType.ADDED_WEIGHT)
        )
        val renamed = db.exerciseDao().getAllExercisesList().first { it.id == original.id }
        assertEquals("Renamed Push-Up", renamed.name)

        DatasetSeeder.seedFresh(context, repository().exerciseDaoForReseed())
        val reports = repository().revertCatalogToDataset(keepUserCreated = true, keepAiCreated = true)

        val restored = db.exerciseDao().getAllExercisesList().first { it.id == original.id }
        assertEquals(originalName, restored.name)
        assertEquals(originalType, restored.exerciseType)
        assertFalse("a revert must not silently drop the row", restored.id == 0L)
        assertTrue(reports.libraryRestored > 0)
    }

    @Test
    fun `reverting keeps the library rows and their ids`() = runBlocking {
        val before = db.exerciseDao().getAllExercisesList()
        val idsBefore = before.map { it.id }.sorted()

        DatasetSeeder.seedFresh(context, repository().exerciseDaoForReseed())
        repository().revertCatalogToDataset(keepUserCreated = true, keepAiCreated = true)

        val after = db.exerciseDao().getAllExercisesList()
        assertEquals(
            "every library row keeps its id through a revert, so logged sets stay attached",
            idsBefore,
            after.map { it.id }.sorted()
        )
    }

    @Test
    fun `reverting deletes exercises the athlete chose not to keep`() = runBlocking {
        val manual = db.exerciseDao().insert(
            Exercise(
                name = "My Custom Lift",
                category = "Legs",
                exerciseType = ExerciseType.ADDED_WEIGHT,
                equipment = "Barbell",
                description = "",
                isCustom = true,
                isAiGenerated = false
            )
        )
        val ai = db.exerciseDao().insert(
            Exercise(
                name = "AI Invented Lift",
                category = "Back",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Cable",
                description = "",
                isCustom = true,
                isAiGenerated = true
            )
        )

        DatasetSeeder.seedFresh(context, repository().exerciseDaoForReseed())
        val report = repository().revertCatalogToDataset(keepUserCreated = true, keepAiCreated = false)

        assertNotNull("a kept row stays", db.exerciseDao().getUserCreatedExerciseById(manual))
        assertNull("an unkept row goes", db.exerciseDao().getEditableExerciseById(ai))
        assertEquals(1, report.removedAiCreated)
        assertEquals(0, report.removedUserCreated)
    }

    @Test
    fun `reverting with both kept removes nothing the athlete made`() = runBlocking {
        db.exerciseDao().insert(
            Exercise(
                name = "My Custom Lift",
                category = "Legs",
                exerciseType = ExerciseType.ADDED_WEIGHT,
                equipment = "Barbell",
                description = ""
            ).copy(isCustom = true)
        )
        db.exerciseDao().insert(
            Exercise(
                name = "AI Invented Lift",
                category = "Back",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                equipment = "Cable",
                description = ""
            ).copy(isCustom = true, isAiGenerated = true)
        )

        DatasetSeeder.seedFresh(context, repository().exerciseDaoForReseed())
        val report = repository().revertCatalogToDataset(keepUserCreated = true, keepAiCreated = true)

        // The default action is "restore the library", not "delete my work".
        assertEquals(0, report.removedUserCreated)
        assertEquals(0, report.removedAiCreated)
        assertEquals(1, report.keptUserCreated)
        assertEquals(1, report.keptAiCreated)
    }

    @Test
    fun `reverting never touches workouts routines or weigh-ins`() = runBlocking {
        val exercise = libraryExercise()
        val sessionId = db.workoutDao().insertSession(
            com.example.data.model.WorkoutSession(
                title = "Before the edit",
                startTime = 1_000,
                endTime = 2_000,
                notes = "keep me"
            )
        )
        db.workoutDao().insertSet(
            LoggedSet(
                sessionId = sessionId,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseType = exercise.exerciseType,
                setNumber = 1,
                weight = 80.0,
                reps = 8
            )
        )
        val routineId = db.savedRoutineDao().insertRoutine(
            SavedRoutine(
                name = "Keep this routine",
                description = "d",
                daysPerWeek = 4,
                goal = "Strength",
                exercisesJson = "[]",
                createdAt = 1
            )
        )
        db.bodyWeightDao().insertWeightLog(
            com.example.data.model.BodyWeightLog(weightKg = 76.0, timestamp = 500, note = "keep")
        )

        // Edit a library row, then revert.
        repository().updateLibraryExercise(
            exercise.copy(name = "Renamed Push-Up", exerciseType = ExerciseType.ADDED_WEIGHT)
        )
        DatasetSeeder.seedFresh(context, repository().exerciseDaoForReseed())
        repository().revertCatalogToDataset(keepUserCreated = true, keepAiCreated = true)

        val sessions = db.workoutDao().getAllSessions().first()
        assertEquals(1, sessions.size)
        assertEquals("Before the edit", sessions.first().title)
        assertEquals(
            "the logged set keeps its exerciseName so history still resolves",
            exercise.name,
            db.workoutDao().getSetsForExerciseList(exercise.name).single().exerciseName
        )
        assertNotNull(db.savedRoutineDao().getAllRoutines().first().firstOrNull { it.id == routineId })
        assertEquals(1, db.bodyWeightDao().getAllWeightLogs().first().size)
    }

    @Test
    fun `a library edit survives the reseed that runs on app open`() = runBlocking {
        // The whole point of the edit feature: an edit has to persist past the reseed that runs every
        // time the database is opened, or the athlete's repair would vanish on the next launch.
        val original = libraryExercise()
        val originalType = original.exerciseType
        val newType =
            if (originalType == ExerciseType.ADDED_WEIGHT) ExerciseType.PURE_BODYWEIGHT
            else ExerciseType.ADDED_WEIGHT
        repository().updateLibraryExercise(original.copy(exerciseType = newType))
        LibraryEditTracker.recordEdit(context, original.id)

        // Exactly what AppDatabase.onOpen does in a fresh process.
        DatasetSeeder.seed(context, repository().exerciseDaoForReseed())

        val after = db.exerciseDao().getAllExercisesList().first { it.id == original.id }
        assertEquals(
            "the reseed must not overwrite a row the athlete edited",
            newType,
            after.exerciseType
        )
    }

    @Test
    fun `an unedited library row is still refreshed by the reseed`() = runBlocking {
        // The tracker must not turn the library read only: rows the athlete never touched keep
        // receiving their dataset values, which is how a reseed fixes a genuinely stale catalog.
        val control = db.exerciseDao().getAllExercisesList()
            .first { it.name != "Decline Push-Up" }
        val controlType = control.exerciseType

        db.exerciseDao().updateLibraryExercise(
            id = control.id,
            name = "Corrupted",
            category = "Core",
            exerciseType = ExerciseType.CARDIO,
            equipment = "None",
            description = "",
            instructions = "",
            bodyPart = "",
            target = "",
            secondaryMuscles = "[]"
        )
        DatasetSeeder.seed(context, repository().exerciseDaoForReseed())

        val after = db.exerciseDao().getAllExercisesList().first { it.id == control.id }
        assertEquals("an untouched row is restored from the dataset", control.name, after.name)
        assertEquals(controlType, after.exerciseType)
    }

    @Test
    fun `clearing the tracker lets the reseed restore an edited row`() = runBlocking {
        // The revert path: forget the edit, then reseed, and the shipped values are back.
        val original = libraryExercise()
        val originalType = original.exerciseType
        val newType =
            if (originalType == ExerciseType.ADDED_WEIGHT) ExerciseType.PURE_BODYWEIGHT
            else ExerciseType.ADDED_WEIGHT
        repository().updateLibraryExercise(original.copy(exerciseType = newType))
        LibraryEditTracker.recordEdit(context, original.id)

        LibraryEditTracker.clearAll(context)
        DatasetSeeder.seed(context, repository().exerciseDaoForReseed())

        val after = db.exerciseDao().getAllExercisesList().first { it.id == original.id }
        assertEquals("the revert must give back the shipped type", originalType, after.exerciseType)
    }
}
