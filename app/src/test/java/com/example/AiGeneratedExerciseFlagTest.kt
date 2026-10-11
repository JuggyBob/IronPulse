package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
 * The "ai_generated" search flag and the ownership of the AI registered exercises:
 *
 *  1) typing "ai_generated" lists exactly the exercises the AI parser registered,
 *  2) typing "user_generated" still lists exactly the exercises the user typed,
 *  3) the two never overlap,
 *  4) an AI exercise can be edited and deleted, and stays flagged as AI generated,
 *  5) "Delete App Data" removes no exercise at all.
 *
 * Everything runs inside a single test method on purpose: the app database is a process wide
 * singleton, so building a second view model in the same test class would reuse a database file
 * that Robolectric already cleaned up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiGeneratedExerciseFlagTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the ai_generated flag lists only the exercises the AI registered`() = runBlocking {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

        // Wait until the built-in library is seeded
        withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.first { it.size > 100 }
        }
        val librarySize = viewModel.allExercises.value.size

        // =====================================================================
        // 1) An exercise registered by the AI is flagged as AI generated
        // =====================================================================
        val aiName = "Zygote Squeeze"
        val aiId = viewModel.insertCustomExercise(
            Exercise(
                name = aiName,
                category = "Arms",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                equipment = "Rings",
                description = "AI registered exercise"
            )
        )
        assertTrue(aiId > 0)

        val aiStored = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.map { list -> list.firstOrNull { it.id == aiId } }.first { it != null }!!
        }
        assertTrue("an AI exercise is custom, so the dataset seed leaves it alone", aiStored.isCustom)
        assertTrue(aiStored.isAiGenerated)

        // =====================================================================
        // 2) A user created exercise is flagged as user created only
        // =====================================================================
        val userName = "Kettlebell Decaf Row"
        viewModel.createCustomExercise(
            name = userName,
            category = "Back",
            equipment = "Kettlebell",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            description = "Typed by the user."
        )
        val userStored = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.map { list -> list.firstOrNull { it.name == userName } }.first { it != null }!!
        }
        assertTrue(userStored.isCustom)
        assertFalse("a user exercise must never be flagged as AI generated", userStored.isAiGenerated)

        // =====================================================================
        // 3) The two search flags return exactly the right list
        //    (the search is debounced, so wait for the expected value instead of "any value")
        // =====================================================================
        viewModel.searchQuery.value = "ai_generated"
        val aiResults = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.map { list -> list.map { it.name } }.first { it == listOf(aiName) }
        }
        assertEquals(listOf(aiName), aiResults)
        val aiFlagged = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.map { list -> list.map { it.isAiGenerated to it.isCustom } }
                .first { it == listOf(true to true) }
        }
        assertEquals(listOf(true to true), aiFlagged)

        // The flag is case insensitive
        viewModel.searchQuery.value = "AI_GENERATED"
        val aiResultsCaseInsensitive = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.map { list -> list.map { it.name } }.first { it == listOf(aiName) }
        }
        assertEquals(listOf(aiName), aiResultsCaseInsensitive)

        viewModel.searchQuery.value = "user_generated"
        val userResults = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.map { list -> list.map { it.name } }.first { it == listOf(userName) }
        }
        assertEquals(listOf(userName), userResults)
        val userFlagged = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.map { list -> list.map { it.isAiGenerated to it.isCustom } }
                .first { it == listOf(false to true) }
        }
        assertEquals(listOf(false to true), userFlagged)

        // Clearing the flag brings the whole library back
        viewModel.searchQuery.value = ""
        val allResults = withTimeout(TIMEOUT_MS) {
            viewModel.filteredExercises.first { list -> list.size > librarySize }
        }
        assertEquals(librarySize + 2, allResults.size)

        // =====================================================================
        // 4) An AI exercise can be edited (stays AI generated) and deleted
        // =====================================================================
        viewModel.updateCustomExercise(
            id = aiId,
            name = "Zygote Squeeze v2",
            category = "Arms",
            equipment = "Rings / Bar",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            description = "Corrected by the user."
        )
        val edited = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.map { list -> list.firstOrNull { it.id == aiId && it.name == "Zygote Squeeze v2" } }
                .first { it != null }!!
        }
        assertEquals("Rings / Bar", edited.equipment)
        assertEquals("Corrected by the user.", edited.description)
        // Editing keeps both flags: still AI generated (so it stays out of "user_generated"),
        // and still custom (so the dataset seed will never overwrite it).
        assertTrue("editing must not clear the AI generated flag", edited.isAiGenerated)
        assertTrue("editing must keep an AI exercise protected from the dataset seed", edited.isCustom)

        // A built-in library exercise is still rejected
        val builtIn = viewModel.allExercises.value.first { !it.isCustom && !it.isAiGenerated }
        viewModel.updateCustomExercise(
            id = builtIn.id,
            name = "Hacked Library Move",
            category = "Chest",
            equipment = "Barbell",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            description = "Should never be applied."
        )
        val builtInAfter = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.map { list -> list.firstOrNull { it.id == builtIn.id } }.first { it != null }!!
        }
        assertEquals("the built-in library must stay locked", builtIn.name, builtInAfter.name)

        viewModel.deleteCustomExercise(edited)
        val afterDelete = withTimeout(TIMEOUT_MS) {
            viewModel.allExercises.first { list -> list.none { it.id == aiId } }
        }
        assertTrue(afterDelete.none { it.name == "Zygote Squeeze v2" })
        assertTrue(afterDelete.any { it.name == userName })

        // =====================================================================
        // 5) "Delete App Data" removes no exercise at all
        // =====================================================================
        val beforeWipe = viewModel.allExercises.value.size
        viewModel.deleteAllAppData()
        withTimeout(TIMEOUT_MS) {
            viewModel.allSessions.first { it.isEmpty() }
        }
        val afterWipe = viewModel.allExercises.value
        assertEquals("Delete App Data must never delete an exercise", beforeWipe, afterWipe.size)
        assertTrue(afterWipe.any { it.name == userName })
        assertTrue(afterWipe.any { it.name == builtIn.name })
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}