package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.repository.FitnessRepository
import com.example.ui.viewmodel.QuickWorkoutSessionController
import com.example.ui.viewmodel.RoutineWorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Session rules that must never leak into a saved routine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DropSetSessionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var scope: CoroutineScope

    private val bench = Exercise(
        id = 1L,
        name = "Barbell Bench Press",
        category = "Chest",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )
    private val squat = Exercise(
        id = 2L,
        name = "Barbell Full Squat",
        category = "Legs",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )
    private val row = Exercise(
        id = 3L,
        name = "Barbell Row",
        category = "Back",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )

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
            scheduleDao = db.scheduleDao(),
            bodyWeightDao = db.bodyWeightDao(),
            chatDao = db.chatDao(),
            geminiSummaryDao = db.geminiSummaryDao()
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun quickController() = QuickWorkoutSessionController(
        repository = repository,
        scope = scope,
        currentBodyWeightKg = { 80.0 },
        resolveBodyWeightKg = { 80.0 },
            defaultRestForExercise = { 0 },
        stopRestTimer = {},
        refreshBenchmarks = {},
        clearWorkoutDraft = {},
        refreshWidgets = {},
        onSessionSaved = {}
    )

    private fun routineController() = RoutineWorkoutSessionController(
        repository = repository,
        scope = scope,
        allExercises = { listOf(bench, squat, row) },
        weekStartsOnMonday = { true },
        currentBodyWeightKg = { 80.0 },
        resolveBodyWeightKg = { 80.0 },
            defaultRestForExercise = { 0 },
        stopRestTimer = {},
        clearQuickWorkout = {},
        clearWorkoutDraft = {},
        refreshWidgets = {},
        onSessionSaved = {}
    )

    private fun routineItem(exercise: Exercise, sets: Int = 2) = RoutineExerciseItem(
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        exerciseType = exercise.exerciseType,
        category = exercise.category,
        equipment = exercise.equipment,
        targetSets = sets,
        targetReps = 8,
        prescription = "$sets sets x 8 reps"
    )

    private suspend fun savedRoutine(): SavedRoutine {
        val id = repository.saveRoutine(
            SavedRoutine(
                name = "Push",
                description = "",
                exercisesJson = RoutineParser.serializeExercises(
                    listOf(routineItem(bench), routineItem(squat))
                ),
                modelVersion = 2
            )
        )
        return repository.getSavedRoutineById(id)!!
    }

    @Test
    fun `adding a drop set creates a child and completing it completes the parent`() = runBlocking {
        val controller = quickController()
        controller.startNewSession()
        controller.addExercise(bench)

        val main = controller.activeSets.value.single()
        controller.addDropSet(main)
        val child = controller.activeSets.value.single { it.isDropSet }

        assertNotNull(child.setUid)
        assertEquals(main.setUid, child.parentSetUid)
        assertEquals(1, child.dropIndex)
        assertFalse(controller.activeSets.value.single { !it.isDropSet }.isCompleted)

        controller.toggleSetCompletion(child, true)
        assertTrue(controller.activeSets.value.single { it.setUid == main.setUid }.isCompleted)
        assertTrue(controller.activeSets.value.single { it.setUid == child.setUid }.isCompleted)

        controller.toggleSetCompletion(main, false)
        assertFalse(controller.activeSets.value.single { it.setUid == main.setUid }.isCompleted)
        assertFalse(controller.activeSets.value.single { it.setUid == child.setUid }.isCompleted)
    }

    @Test
    fun `deleting one child keeps siblings and renumbers them`() = runBlocking {
        val controller = quickController()
        controller.startNewSession()
        controller.addExercise(bench)
        val main = controller.activeSets.value.single()
        controller.addDropSet(main)
        controller.addDropSet(main)

        val children = controller.activeSets.value.filter { it.isDropSet }.sortedBy { it.dropIndex }
        assertEquals(listOf(1, 2), children.map { it.dropIndex })
        controller.deleteSet(children.first())

        val remaining = controller.activeSets.value.filter { it.isDropSet }.single()
        assertEquals(1, remaining.dropIndex)
        assertEquals(main.setUid, remaining.parentSetUid)
    }

    @Test
    fun `removing an exercise and undoing restores position sets and drop links`() = runBlocking {
        val controller = quickController()
        controller.startNewSession()
        controller.addExercise(bench)
        controller.addExercise(squat)
        val benchMain = controller.activeSets.value.first { it.exerciseId == bench.id }
        controller.addDropSet(benchMain)
        val child = controller.activeSets.value.first { it.isDropSet }

        val snapshot = controller.removeExercise(bench.id)
        assertNotNull(snapshot)
        assertEquals(listOf(squat.id), controller.activeSessionExercises.value.map { it.id })
        assertTrue(controller.activeSets.value.none { it.exerciseId == bench.id })

        controller.restoreExercise(snapshot!!)

        assertEquals(listOf(bench.id, squat.id), controller.activeSessionExercises.value.map { it.id })
        val restoredChild = controller.activeSets.value.first { it.setUid == child.setUid }
        assertEquals(benchMain.setUid, restoredChild.parentSetUid)
        assertEquals(1, restoredChild.dropIndex)
        assertEquals(bench.id, controller.activeSets.value.first { it.setUid == benchMain.setUid }.exerciseId)
    }

    @Test
    fun `reorder persists routine identity and ignores session-only exercises`() = runBlocking {
        val routine = savedRoutine()
        val controller = routineController()
        controller.start(routine)
        controller.addExercise(row)

        controller.moveExercise(0, 2)

        withTimeout(5_000) {
            while (true) {
                val items = RoutineParser.parseSingleWorkout(
                    repository.getSavedRoutineById(routine.id)!!.exercisesJson,
                    listOf(bench, squat, row)
                )
                if (items.map { it.exerciseId } == listOf(squat.id, bench.id)) break
                delay(20)
            }
        }
        val persisted = repository.getSavedRoutineById(routine.id)!!
        assertEquals(routine.id, persisted.id)
        val items = RoutineParser.parseSingleWorkout(persisted.exercisesJson, listOf(bench, squat, row))
        assertEquals(listOf(squat.id, bench.id), items.map { it.exerciseId })
        assertEquals(2, items.size)

        val afterSessionAdd = controller.activeRoutineWorkout.value!!
        assertEquals(listOf(squat.id, row.id, bench.id), afterSessionAdd.exercises.map { it.id })
    }

    @Test
    fun `deleting a routine exercise in the session never edits the saved routine`() = runBlocking {
        val routine = savedRoutine()
        val controller = routineController()
        controller.start(routine)

        controller.removeExercise(bench.id)

        val persisted = repository.getSavedRoutineById(routine.id)!!
        val items = RoutineParser.parseSingleWorkout(persisted.exercisesJson, listOf(bench, squat))
        assertEquals(listOf(bench.id, squat.id), items.map { it.exerciseId })
        assertEquals(listOf(squat.id), controller.activeRoutineWorkout.value!!.exercises.map { it.id })
    }
}
