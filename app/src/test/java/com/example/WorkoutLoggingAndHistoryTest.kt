package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.WorkoutSession
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end regression tests for the workout logging / history / data deletion fixes:
 *
 * 1) Finishing a workout only saves the sets (and exercises) the user ticked.
 * 2) Deleting a session from the workout history also removes its sets from the Progress tab.
 * 3) Deleting a single set renumbers the remaining sets of that day.
 *
 * Everything runs inside a single test method on purpose: the app database is a process wide
 * singleton, so building a second view model in the same test class would reuse a database file
 * that Robolectric already cleaned up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutLoggingAndHistoryTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `workout logging, session deletion and set renumbering behave correctly`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

        val benchPress = viewModel.awaitExercise("Barbell Bench Press")
        // The dataset calls the barbell squat "Barbell Full Squat"; there is no "Back Squat".
        val backSquat = viewModel.awaitExercise("Barbell Full Squat")

        // =====================================================================
        // 1) Only ticked sets / exercises are saved
        // =====================================================================
        viewModel.startNewSession("Quick Workout")
        viewModel.addExerciseToActiveSession(benchPress)
        viewModel.addExerciseToActiveSession(benchPress)
        viewModel.addExerciseToActiveSession(benchPress)
        viewModel.addExerciseToActiveSession(backSquat)

        val quickSets = viewModel.activeSets.value
        assertEquals(4, quickSets.size)
        // Pre-filled default values exist, but nothing is logged yet
        assertTrue(quickSets.none { it.isCompleted })

        viewModel.updateActiveSet(0, quickSets[0].copy(isCompleted = true))
        viewModel.finishAndSaveActiveSession("Only the first bench set was ticked")

        val quickSession = viewModel.awaitSession("Quick Workout")
        val quickSavedSets = viewModel.awaitSets(quickSession.id, expectedSize = 1)

        // Exactly one set is persisted, and the untouched exercise is not logged at all
        assertEquals("Barbell Bench Press", quickSavedSets.single().exerciseName)
        assertTrue(quickSavedSets.single().isCompleted)
        assertEquals("Only the first bench set was ticked", quickSession.notes)
        assertTrue(
            "An exercise without ticked sets must not be logged",
            viewModel.distinctLoggedExercises.value.none { it == "Barbell Full Squat" }
        )
        assertNull(viewModel.activeSession.value)

        // Nothing ticked -> nothing is saved at all
        viewModel.startNewSession("Untouched Workout")
        viewModel.addExerciseToActiveSession(benchPress)
        viewModel.addExerciseToActiveSession(backSquat)
        assertEquals(2, viewModel.activeSets.value.size)

        viewModel.finishAndSaveActiveSession("Nothing was ticked")

        assertNull(
            "A workout without ticked sets must not be saved",
            withTimeoutOrNull(2_000L) {
                viewModel.allSessions.map { list -> list.firstOrNull { it.title == "Untouched Workout" } }
                    .first { it != null }
            }
        )
        assertNull(viewModel.activeSession.value)
        assertTrue(viewModel.activeSets.value.isEmpty())

        // =====================================================================
        // 2) Deleting a session removes its sets from the Progress tab
        // =====================================================================
        // An exercise registered by the AI parser, i.e. one that is not part of the seed data
        val aiExercise = Exercise(
            name = "Pelican Curl",
            category = "Arms",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Rings",
            description = "AI registered exercise"
        )
        val pelicanCurl = aiExercise.copy(id = viewModel.insertCustomExercise(aiExercise))

        viewModel.startNewSession("Custom Day")
        viewModel.addExerciseToActiveSession(pelicanCurl)
        viewModel.addExerciseToActiveSession(pelicanCurl)
        viewModel.tickAllActiveSets()
        viewModel.finishAndSaveActiveSession("")

        val customSession = viewModel.awaitSession("Custom Day")
        viewModel.awaitSets(customSession.id, expectedSize = 2)
        // Awaited rather than read from .value: the flow is derived from Room, so reading it
        // synchronously here raced the emission and failed whenever it had not landed yet.
        assertTrue(
            withTimeout(TIMEOUT_MS) {
                viewModel.distinctLoggedExercises.first { list -> list.contains("Pelican Curl") }
            }.contains("Pelican Curl")
        )

        viewModel.deleteSession(customSession)

        // The sets of the deleted session must disappear as well
        withTimeout(TIMEOUT_MS) {
            viewModel.getSetsForSession(customSession.id).map { it.size }.first { it == 0 }
        }
        val exercisesAfterDelete = withTimeout(TIMEOUT_MS) {
            viewModel.distinctLoggedExercises.first { list -> list.none { it == "Pelican Curl" } }
        }
        assertTrue(exercisesAfterDelete.none { it == "Pelican Curl" })
        // Deleting history never deletes the exercise library itself. Waited on the flow rather
        // than read .value, because the catalog is a large list and Room re-emits it asynchronously.
        val libraryStillHasIt = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.first { list -> list.any { it.name == "Pelican Curl" } }
        }
        assertTrue(libraryStillHasIt.any { it.name == "Pelican Curl" })

        // =====================================================================
        // 3) Deleting a set renumbers the remaining sets of that day
        // =====================================================================
        viewModel.startNewSession("Renumber Day")
        repeat(3) { viewModel.addExerciseToActiveSession(pelicanCurl) }
        viewModel.tickAllActiveSets()
        viewModel.finishAndSaveActiveSession("")

        val renumberSession = viewModel.awaitSession("Renumber Day")
        val beforeDelete = viewModel.awaitSets(renumberSession.id, expectedSize = 3)
        assertEquals(listOf(1, 2, 3), beforeDelete.map { it.setNumber })

        // Delete set #1 exactly like the Progress tab does
        viewModel.deleteLoggedSet(beforeDelete.first())

        val afterDelete = withTimeout(TIMEOUT_MS) {
            viewModel.getSetsForSession(renumberSession.id)
                .first { sets -> sets.size == 2 && sets.map { it.setNumber }.sorted() == listOf(1, 2) }
        }
        assertEquals(
            "The remaining sets must be renumbered to 1 and 2",
            listOf(1, 2),
            afterDelete.sortedBy { it.timestamp }.map { it.setNumber }
        )
    }

    private fun MainViewModel.tickAllActiveSets() {
        activeSets.value.forEachIndexed { index, set ->
            updateActiveSet(index, set.copy(isCompleted = true))
        }
    }

    private suspend fun MainViewModel.awaitExercise(name: String): Exercise =
        withTimeout(TIMEOUT_MS) {
            allExercises.map { list -> list.firstOrNull { it.name == name } }.first { it != null }!!
        }

    private suspend fun MainViewModel.awaitSession(title: String): WorkoutSession =
        withTimeout(TIMEOUT_MS) {
            allSessions.map { list -> list.firstOrNull { it.title == title } }.first { it != null }!!
        }

    private suspend fun MainViewModel.awaitSets(sessionId: Long, expectedSize: Int): List<LoggedSet> =
        withTimeout(TIMEOUT_MS) { getSetsForSession(sessionId).first { it.size == expectedSize } }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
