/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.ProposedExercise
import com.example.data.ai.RoutineModificationProposal
import com.example.data.ai.RoutineModificationService
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The approved-write path: one exact change, stale-safe and atomic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineModificationServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var service: RoutineModificationService

    private val pushUp = Exercise(
        id = 1,
        name = "Push-up",
        category = "Chest",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Bodyweight"
    )
    private val pullUp = Exercise(
        id = 2,
        name = "Pull-up",
        category = "Back",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Pull-up Bar"
    )
    private val dip = Exercise(
        id = 3,
        name = "Dip",
        category = "Chest",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Parallel Bars"
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.exerciseDao().insert(pushUp)
        db.exerciseDao().insert(pullUp)
        db.exerciseDao().insert(dip)
        db.savedRoutineDao().insertRoutine(routine())
        service = RoutineModificationService(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun routine(id: Long = 20): SavedRoutine = SavedRoutine(
        id = id,
        name = "Weekly Plan",
        description = "",
        daysPerWeek = 2,
        goal = "Strength",
        exercisesJson = RoutineParser.serializeExercises(
            listOf(
                RoutineExerciseItem(
                    exerciseId = 1,
                    exerciseName = "Push-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Chest",
                    equipment = "Bodyweight",
                    targetSets = 3,
                    targetReps = 12,
                    prescription = "3x12",
                    restSeconds = 90,
                    weekday = 1,
                    dayTitle = "Push Day"
                ),
                RoutineExerciseItem(
                    exerciseId = 2,
                    exerciseName = "Pull-up",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    category = "Back",
                    equipment = "Pull-up Bar",
                    targetSets = 4,
                    targetReps = 6,
                    weekday = 3,
                    dayTitle = "Pull Day"
                )
            )
        )
    )

    private fun proposal(
        remove: String = "Push-up",
        replacementName: String = "Dip",
        replacementId: Long? = 3L,
        generated: ProposedExercise? = null,
        routine: SavedRoutine = routine()
    ) = RoutineModificationProposal(
        routineId = routine.id,
        routineName = routine.name,
        routineSignature = RoutineModificationProposal.signatureOf(routine),
        weekday = 1,
        dayTitle = "Push Day",
        removeExerciseName = remove,
        removeExerciseId = if (remove == "Push-up") 1L else 2L,
        replacementExerciseId = replacementId,
        replacementExerciseName = replacementName,
        generatedExercise = generated,
        reason = "Test reason",
        summary = "Replace $remove with $replacementName"
    )

    @Test
    fun `approval replaces exactly one exercise and keeps the original catalog row`() = runBlocking {
        val result = service.apply(proposal(), accessEnabled = true).getOrThrow()
        assertEquals("Push-up", result.removedExercise)
        assertEquals("Dip", result.addedExercise)

        val updated = db.savedRoutineDao().getRoutineById(20)!!
        val days = RoutineParser.parseWeek(updated.exercisesJson, db.exerciseDao().getAllExercisesList())
        val monday = days.first { it.weekday == 1 }
        assertEquals(listOf("Dip"), monday.exercises.map { it.exerciseName })
        // The prescription and rest default survive the slot replacement.
        assertEquals(3, monday.exercises.single().targetSets)
        assertEquals(12, monday.exercises.single().targetReps)
        assertEquals(90, monday.exercises.single().restSeconds)
        // Wednesday was not touched.
        assertTrue(days.first { it.weekday == 3 }.exercises.any { it.exerciseName == "Pull-up" })
        // Removing an exercise from a routine never deletes it from the catalog.
        assertNotNull(db.exerciseDao().getExerciseById(1))
    }

    @Test
    fun `a stale proposal is rejected without changing the routine`() = runBlocking {
        val stale = proposal()
        val changed = routine().copy(
            exercisesJson = RoutineParser.serializeExercises(
                listOf(
                    RoutineExerciseItem(
                        exerciseId = 2,
                        exerciseName = "Pull-up",
                        exerciseType = ExerciseType.PURE_BODYWEIGHT,
                        weekday = 1
                    )
                )
            )
        )
        db.savedRoutineDao().insertRoutine(changed)

        val result = service.apply(stale, accessEnabled = true)
        assertTrue(result.isFailure)
        assertEquals(
            changed.exercisesJson,
            db.savedRoutineDao().getRoutineById(20)!!.exercisesJson
        )
    }

    @Test
    fun `permission revocation blocks an otherwise valid approval`() = runBlocking {
        val before = db.savedRoutineDao().getRoutineById(20)!!
        val result = service.apply(proposal(), accessEnabled = false)
        assertTrue(result.isFailure)
        assertEquals(before, db.savedRoutineDao().getRoutineById(20))
    }

    @Test
    fun `a generated exercise is created as AI-generated and linked in the same routine`() =
        runBlocking {
            val generated = ProposedExercise(
                name = "Band Pull-apart",
                category = "Back",
                bodyPart = "upper back",
                target = "rhomboids",
                equipment = "Resistance Band",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                instructions = "Hold the band at shoulder height and pull it apart."
            )
            val result = service.apply(
                proposal(
                    remove = "Pull-up",
                    replacementName = generated.name,
                    replacementId = null,
                    generated = generated
                ).copy(weekday = 3, dayTitle = "Pull Day"),
                accessEnabled = true
            ).getOrThrow()
            assertNotNull(result.generatedExerciseId)

            val created = db.exerciseDao().getExerciseById(result.generatedExerciseId!!)!!
            assertTrue(created.isCustom)
            assertTrue(created.isAiGenerated)
            assertFalse(created.name.isBlank())
            val updated = db.savedRoutineDao().getRoutineById(20)!!
            val wednesday = RoutineParser.parseWeek(
                updated.exercisesJson,
                db.exerciseDao().getAllExercisesList()
            ).first { it.weekday == 3 }
            assertEquals(listOf("Band Pull-apart"), wednesday.exercises.map { it.exerciseName })
        }

    @Test
    fun `a generated exercise with an existing name fails and leaves the routine untouched`() =
        runBlocking {
            val before = db.savedRoutineDao().getRoutineById(20)!!
            val duplicate = ProposedExercise(
                name = "Dip",
                category = "Chest",
                bodyPart = "chest",
                target = "pectorals",
                equipment = "Parallel Bars",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                instructions = "Lower and press back up."
            )
            val result = service.apply(
                proposal(
                    remove = "Pull-up",
                    replacementName = duplicate.name,
                    replacementId = null,
                    generated = duplicate
                ).copy(weekday = 3),
                accessEnabled = true
            )
            assertTrue(result.isFailure)
            assertEquals(before, db.savedRoutineDao().getRoutineById(20))
            assertEquals(3, db.exerciseDao().getAllExercisesList().size)
        }

    private fun assertFalse(value: Boolean) {
        org.junit.Assert.assertFalse(value)
    }
}
