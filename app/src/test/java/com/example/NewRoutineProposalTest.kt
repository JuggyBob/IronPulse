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
import com.example.data.ai.NewRoutineProposal
import com.example.data.ai.ProposedExercise
import com.example.data.ai.ProposedRoutineDay
import com.example.data.ai.ProposedRoutineExercise
import com.example.data.ai.RoutineModificationService
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineParser
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

/** AI Coach new-routine proposals are inert until approval and then saved atomically. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewRoutineProposalTest {

    private lateinit var db: AppDatabase
    private lateinit var service: RoutineModificationService

    private val pushUp = Exercise(
        id = 1,
        name = "Push-up",
        category = "Chest",
        bodyPart = "chest",
        target = "pectorals",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Bodyweight",
        instructions = "Keep a straight line."
    )
    private val pullUp = Exercise(
        id = 2,
        name = "Pull-up",
        category = "Back",
        bodyPart = "back",
        target = "lats",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Pull-up Bar",
        instructions = "Pull your chest to the bar."
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.exerciseDao().insert(pushUp)
        db.exerciseDao().insert(pullUp)
        service = RoutineModificationService(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newExercise() = ProposedExercise(
        name = "Banded Front Squat",
        category = "Legs",
        bodyPart = "upper legs",
        target = "Quadriceps",
        equipment = "Band",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        instructions = "Stand on the band. | Squat down under control. | Drive up."
    )

    private fun proposal(includeGenerated: Boolean = true) = NewRoutineProposal(
        routineName = "Coach Strength",
        goal = "Strength",
        summary = "Two full-body days.",
        days = listOf(
            ProposedRoutineDay(
                weekday = 1,
                focus = "Full Body A",
                exercises = listOf(
                    ProposedRoutineExercise(
                        name = "Push-up",
                        exerciseId = 1,
                        category = "Chest",
                        equipment = "Bodyweight",
                        exerciseType = ExerciseType.PURE_BODYWEIGHT,
                        targetSets = 4,
                        targetReps = 10,
                        prescription = "4 sets x 10 reps"
                    )
                )
            ),
            ProposedRoutineDay(
                weekday = 3,
                focus = "Full Body B",
                exercises = listOfNotNull(
                    ProposedRoutineExercise(
                        name = "Pull-up",
                        exerciseId = 2,
                        category = "Back",
                        equipment = "Pull-up Bar",
                        exerciseType = ExerciseType.PURE_BODYWEIGHT,
                        targetSets = 3,
                        targetReps = 8,
                        prescription = "3 sets x 8 reps"
                    ),
                    if (includeGenerated) {
                        ProposedRoutineExercise(
                            name = "Banded Front Squat",
                            category = "Legs",
                            equipment = "Band",
                            exerciseType = ExerciseType.STANDARD_WEIGHT,
                            generatedExercise = newExercise(),
                            targetSets = 3,
                            targetReps = 12,
                            prescription = "3 sets x 12 reps"
                        )
                    } else {
                        null
                    }
                )
            )
        )
    )

    @Test
    fun `approval saves the routine and creates only the approved new exercise`() = runBlocking {
        val result = service.applyNewRoutine(proposal(), accessEnabled = true).getOrThrow()

        assertEquals("Coach Strength", result.routineName)
        assertEquals(2, result.trainingDays)
        assertEquals(3, result.exerciseCount)
        assertEquals(listOf("Banded Front Squat"), result.createdExercises)

        val stored = db.savedRoutineDao().getRoutineById(result.routineId)
        assertNotNull(stored)
        val days = RoutineParser.parseWeek(stored!!.exercisesJson, db.exerciseDao().getAllExercisesList())
        assertEquals(listOf("Push-up"), days.first { it.weekday == 1 }.exercises.map { it.exerciseName })
        assertEquals(
            listOf("Pull-up", "Banded Front Squat"),
            days.first { it.weekday == 3 }.exercises.map { it.exerciseName }
        )
        assertNotNull(db.exerciseDao().getExerciseByName("Banded Front Squat"))
    }

    @Test
    fun `a disabled permission writes nothing`() = runBlocking {
        val result = service.applyNewRoutine(proposal(), accessEnabled = false)

        assertTrue(result.isFailure)
        assertTrue(db.savedRoutineDao().getAllRoutinesOnce().isEmpty())
        assertNull(db.exerciseDao().getExerciseByName("Banded Front Squat"))
    }

    @Test
    fun `a missing catalog reference rolls back the whole routine`() = runBlocking {
        val broken = proposal(includeGenerated = false).copy(
            days = proposal(includeGenerated = false).days.map { day ->
                day.copy(
                    exercises = day.exercises.map { exercise ->
                        if (exercise.name == "Pull-up") exercise.copy(exerciseId = 999L) else exercise
                    }
                )
            }
        )

        val result = service.applyNewRoutine(broken, accessEnabled = true)

        assertTrue(result.isFailure)
        assertTrue(db.savedRoutineDao().getAllRoutinesOnce().isEmpty())
    }

    @Test
    fun `proposal JSON round-trips and merges duplicate weekdays`() = runBlocking {
        val json = proposal().toJson()
        val days = json.getJSONArray("days")
        // Duplicate the Monday entry with one extra exercise.
        val monday = org.json.JSONObject(days.getJSONObject(0).toString())
        monday.put(
            "exercises",
            org.json.JSONArray().put(
                org.json.JSONObject().put("name", "Extra").put("exerciseId", 2).put("sets", 3).put("reps", 5)
            )
        )
        days.put(monday)

        val parsed = NewRoutineProposal.fromJson(json.toString())
        assertNotNull(parsed)
        assertEquals(2, parsed!!.days.size)
        assertEquals(2, parsed.days.first { it.weekday == 1 }.exercises.size)
        assertEquals(2, parsed.trainingDays)
        assertEquals(4, parsed.exerciseCount)

        val emptyDays = proposal().toJson().put("days", org.json.JSONArray())
        assertNull(NewRoutineProposal.fromJson(emptyDays.toString()))
    }

    @Test
    fun `a proposal with a generated exercise round-trips its metadata`() {
        val parsed = NewRoutineProposal.fromJson(proposal().toJson().toString())!!
        val generated = parsed.days.flatMap { it.exercises }
            .first { it.name == "Banded Front Squat" }
            .generatedExercise
        assertNotNull(generated)
        assertEquals("Band", generated!!.equipment)
        assertFalse(generated.instructions.isBlank())
    }
}
