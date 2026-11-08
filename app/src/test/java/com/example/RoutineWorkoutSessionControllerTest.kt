/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.repository.FitnessRepository
import com.example.ui.viewmodel.RoutineWorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineWorkoutSessionControllerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FitnessRepository
    private lateinit var scope: CoroutineScope

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

    @Test
    fun `routine workout resolves prescriptions and persists completed sets`() = runBlocking {
        val exercise = Exercise(
            id = 1L,
            name = "Barbell Squat",
            category = "Legs",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            equipment = "Barbell"
        )
        val routine = SavedRoutine(
            id = 42L,
            name = "Test Routine",
            description = "",
            exercisesJson = RoutineParser.serializeExercises(
                listOf(
                    RoutineExerciseItem(
                        exerciseId = 1L,
                        exerciseName = "Barbell Squat",
                        exerciseType = ExerciseType.STANDARD_WEIGHT,
                        targetSets = 2,
                        targetReps = 10,
                        targetWeight = 50.0,
                        weekday = 1,
                        dayTitle = "Leg Day"
                    )
                )
            )
        )
        var quickCleared = false
        var draftCleared = false
        var widgetsChanged = false
        val controller = RoutineWorkoutSessionController(
            repository = repository,
            scope = scope,
            allExercises = { listOf(exercise) },
            weekStartsOnMonday = { true },
            currentBodyWeightKg = { 80.0 },
            resolveBodyWeightKg = { 80.0 },
            stopRestTimer = {},
            clearQuickWorkout = { quickCleared = true },
            clearWorkoutDraft = { draftCleared = true },
            refreshWidgets = { widgetsChanged = true },
            onSessionSaved = {}
        )

        controller.start(routine, targetWeekday = 1)
        val active = controller.activeRoutineWorkout.value
        assertNotNull(active)
        assertEquals(2, active!!.sets.size)
        assertEquals(10, active.sets.first().reps)
        assertEquals(50.0, active.sets.first().weight, 0.0)
        assertEquals("Leg Day", active.dayTitle)
        assertTrue(active.plannedDateKey > 0L)

        controller.updateSet(0, active.sets.first().copy(isCompleted = true))
        assertEquals(true, controller.activeRoutineWorkout.value!!.sets.first().isCompleted)
        controller.finish("done")
        val saved = withTimeout(5_000) {
            repository.getAllSessionsWithSets().first { sessions -> sessions.any { it.sets.isNotEmpty() } }
        }.single()
        assertEquals("Test Routine (Leg Day)", saved.session.title)
        assertEquals(42L, saved.session.routineId)
        assertEquals(1, saved.sets.size)
        assertEquals(80.0, saved.sets.single().bodyWeightKg, 0.0)

        withTimeout(5_000) {
            while (controller.activeRoutineWorkout.value != null) {
                shadowOf(Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(10)
            }
        }
        assertNull(controller.activeRoutineWorkout.value)
        assertTrue(quickCleared)
        assertTrue(draftCleared)
        assertTrue(widgetsChanged)
    }
}
