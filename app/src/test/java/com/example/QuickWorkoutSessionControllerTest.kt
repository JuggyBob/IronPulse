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
import com.example.data.repository.FitnessRepository
import com.example.ui.viewmodel.QuickWorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
class QuickWorkoutSessionControllerTest {

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

    private fun controller(
        defaultRestSeconds: Int = 0,
        onDraftCleared: () -> Unit = {},
        onWidgetsChanged: () -> Unit = {},
        onSessionSaved: (Long) -> Unit = {}
    ) = QuickWorkoutSessionController(
        repository = repository,
        scope = scope,
        currentBodyWeightKg = { 80.0 },
        resolveBodyWeightKg = { 80.0 },
        defaultRestForExercise = { defaultRestSeconds },
        stopRestTimer = {},
        refreshBenchmarks = {},
        clearWorkoutDraft = onDraftCleared,
        refreshWidgets = onWidgetsChanged,
        onSessionSaved = onSessionSaved
    )

    private fun exercise() = Exercise(
        id = 1L,
        name = "Push-Up",
        category = "Chest",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Bodyweight"
    )

    @Test
    fun `active quick workout keeps sets and persists only completed work`() = runBlocking {
        var draftCleared = false
        var widgetsChanged = false
        var savedId: Long? = null
        val controller = controller(
            onDraftCleared = { draftCleared = true },
            onWidgetsChanged = { widgetsChanged = true },
            onSessionSaved = { savedId = it }
        )

        controller.startNewSession("Test Workout")
        controller.addExercise(exercise())
        assertEquals("Test Workout", controller.activeSession.value?.title)
        assertEquals(1, controller.activeSessionExercises.value.size)
        assertEquals(1, controller.activeSets.value.size)
        assertEquals(80.0, controller.activeSets.value.single().bodyWeightKg, 0.0)

        val set = controller.activeSets.value.single()
        controller.updateSet(set.copy(isCompleted = true, reps = 12))
        controller.finishAndSave("done")

        val saved = withTimeout(5_000) {
            repository.getAllSessionsWithSets().first { sessions -> sessions.any { it.sets.isNotEmpty() } }
        }.single()
        assertEquals("Test Workout", saved.session.title)
        assertEquals(1, saved.sets.size)
        assertEquals(12, saved.sets.single().reps)
        assertEquals(80.0, saved.sets.single().bodyWeightKg, 0.0)

        withTimeout(5_000) {
            while (controller.activeSession.value != null) {
                shadowOf(Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(10)
            }
        }
        assertNull(controller.activeSession.value)
        assertTrue(controller.activeSets.value.isEmpty())
        assertTrue(draftCleared)
        assertTrue(widgetsChanged)
        assertEquals(saved.session.id, savedId)
    }

    @Test
    fun `discard clears the live session and the saved draft`() {
        var draftCleared = false
        val controller = controller(onDraftCleared = { draftCleared = true })
        controller.startNewSession()
        controller.addExercise(exercise())

        controller.discard()

        assertNull(controller.activeSession.value)
        assertTrue(controller.activeSets.value.isEmpty())
        assertTrue(controller.activeSessionExercises.value.isEmpty())
        assertTrue(draftCleared)
    }

    @Test
    fun `a per-exercise rest default seeds the first set`() {
        val controller = controller(defaultRestSeconds = 90)
        controller.startNewSession()
        controller.addExercise(exercise())

        assertEquals(90, controller.activeSets.value.single().restSecondsOverride)
    }
}
