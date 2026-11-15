package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.DropSetRules
import com.example.data.model.LoggedSet
import com.example.data.model.RemovedActiveExercise
import com.example.data.model.WeightUnit
import com.example.ui.screens.ActiveWorkoutInterface
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class ActiveWorkoutInterfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val exercise = Exercise(
        id = 1L,
        name = "Barbell Front Squat",
        category = "Legs",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )

    private fun sampleSet() = LoggedSet(
        sessionId = 0L,
        exerciseId = 1L,
        exerciseName = "Barbell Front Squat",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = 1,
        weight = 80.0,
        reps = 6,
        isCompleted = false
    )

    @Test
    fun `warmup and completion are independent on one set`() {
        var sets by mutableStateOf(listOf(sampleSet()))
        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveWorkoutInterface(
                    exercises = listOf(exercise),
                    sets = sets,
                    unit = WeightUnit.KG,
                    workoutKey = 1L,
                    onAddSet = {},
                    onUpdateSet = { old, updated ->
                        sets = sets.map { if (it.setNumber == old.setNumber) updated else it }
                    },
                    onDeleteSet = {},
                    onShowDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Barbell Front Squat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_set_number_1:1").performClick()
        composeTestRule.onNodeWithText("Set type").assertIsDisplayed()

        composeTestRule.onNodeWithTag("active_set_warmup_toggle").performClick()
        composeTestRule.waitForIdle()
        assertTrue(sets.single().isWarmup)
        assertFalse(sets.single().isCompleted)

        composeTestRule.onNodeWithTag("active_set_complete_1:1").performClick()
        composeTestRule.waitForIdle()
        assertTrue(sets.single().isWarmup)
        assertTrue(sets.single().isCompleted)
    }

    @Test
    fun `set rest and numeric steppers update only the selected set`() {
        var sets by mutableStateOf(listOf(sampleSet(), sampleSet().copy(setNumber = 2)))
        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveWorkoutInterface(
                    exercises = listOf(exercise),
                    sets = sets,
                    unit = WeightUnit.KG,
                    workoutKey = 2L,
                    onAddSet = {},
                    onUpdateSet = { old, updated ->
                        sets = sets.map {
                            if (it.setNumber == old.setNumber) updated else it
                        }
                    },
                    onDeleteSet = {},
                    onShowDetails = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("active_set_number_1:1").performClick()
        composeTestRule.onNodeWithTag("active_set_rest_input_plus").performClick()
        composeTestRule.onNodeWithTag("active_set_weight_1:1_plus").performClick()
        composeTestRule.onNodeWithTag("active_set_warmup_toggle").performClick()
        composeTestRule.onNodeWithTag("active_set_complete_1:1").performClick()
        composeTestRule.waitForIdle()

        val first = sets.first { it.setNumber == 1 }
        val second = sets.first { it.setNumber == 2 }
        assertEquals(15, first.restSecondsOverride)
        assertEquals(82.5, first.weight, 0.001)
        assertTrue(first.isWarmup)
        assertTrue(first.isCompleted)
        // The neighbouring set is untouched.
        assertNull(second.restSecondsOverride)
        assertEquals(80.0, second.weight, 0.001)
        assertFalse(second.isWarmup)
        assertFalse(second.isCompleted)
    }
    @Test
    fun `add drop set targets the parent and child completion is published`() {
        val main = sampleSet().copy(setUid = "main-1")
        var sets by mutableStateOf(listOf(main))
        var lastToggle: Pair<String, Boolean>? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveWorkoutInterface(
                    exercises = listOf(exercise),
                    sets = sets,
                    unit = WeightUnit.KG,
                    workoutKey = 3L,
                    onAddSet = {},
                    onUpdateSet = { _, updated ->
                        sets = sets.map { if (it.setUid == updated.setUid) updated else it }
                    },
                    onDeleteSet = {},
                    onShowDetails = {},
                    onAddDropSet = { parent ->
                        sets = sets + main.copy(
                            setUid = "child-1",
                            parentSetUid = parent.setUid,
                            dropIndex = 1,
                            weight = 30.0,
                            reps = 8
                        )
                    },
                    onToggleSetComplete = { target, completed ->
                        lastToggle = target.setUid to completed
                        sets = DropSetRules.toggleCompletion(sets, target.setUid, completed)
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("active_workout_add_drop_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, sets.size)
        assertEquals("main-1", sets.first { it.isDropSet }.parentSetUid)

        composeTestRule.onNodeWithTag("active_set_complete_1:1:d1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("child-1" to true, lastToggle)
        assertTrue(sets.first { it.setUid == "main-1" }.isCompleted)
        assertTrue(sets.first { it.setUid == "child-1" }.isCompleted)
        assertEquals(setOf(1L), DropSetRules.completedExerciseIds(sets))
    }

    @Test
    fun `deleting an exercise offers Undo and restores the snapshot`() {
        val main = sampleSet().copy(setUid = "main-undo")
        val snapshot = RemovedActiveExercise(
            exercise = exercise,
            routineItem = null,
            sets = listOf(main),
            index = 0,
            wasSessionOnly = false
        )
        var sets by mutableStateOf(listOf(main))
        var restored: RemovedActiveExercise? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveWorkoutInterface(
                    exercises = listOf(exercise),
                    sets = sets,
                    unit = WeightUnit.KG,
                    workoutKey = 4L,
                    onAddSet = {},
                    onUpdateSet = { _, _ -> },
                    onDeleteSet = {},
                    onShowDetails = {},
                    onRemoveExercise = {
                        sets = emptyList()
                        snapshot
                    },
                    onRestoreExercise = { restored = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("active_workout_delete_exercise_1").performClick()
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()

        assertEquals(snapshot, restored)
    }


    @Test
    fun `an exercise-specific field combination renders inline and in the expansion`() {
        // Plank is the case the old type model could not express: a hold primary field, optional
        // added weight, and a rep count kept as an expanded detail.
        val plank = Exercise(
            id = 9L,
            name = "Weighted Plank",
            category = "Core",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            equipment = "Bodyweight",
            supportedFields = "holdSeconds,addedWeight,reps"
        )
        var sets by mutableStateOf(
            listOf(
                LoggedSet(
                    sessionId = 0L,
                    exerciseId = 9L,
                    exerciseName = "Weighted Plank",
                    exerciseType = ExerciseType.PURE_BODYWEIGHT,
                    setNumber = 1,
                    holdDurationSeconds = 60,
                    isCompleted = false
                )
            )
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveWorkoutInterface(
                    exercises = listOf(plank),
                    sets = sets,
                    unit = WeightUnit.KG,
                    workoutKey = 9L,
                    onAddSet = {},
                    onUpdateSet = { old, updated ->
                        sets = sets.map { if (it.setNumber == old.setNumber) updated else it }
                    },
                    onDeleteSet = {},
                    onShowDetails = {}
                )
            }
        }

        // The first two configured fields are the inline columns.
        composeTestRule.onNodeWithText("Hold (s)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Added (kg)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_set_hold_9:1_plus").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_set_added_weight_9:1_plus").assertIsDisplayed()

        // The third field lives in the existing expansion, not in a separate dialog.
        composeTestRule.onNodeWithTag("active_set_number_9:1").performClick()
        composeTestRule.onNodeWithText("MORE FIELDS FOR THIS EXERCISE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_set_detail_reps_9:1_plus").performClick()
        composeTestRule.onNodeWithTag("active_set_added_weight_9:1_plus").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, sets.single().reps)
        assertEquals(2.5, sets.single().addedWeight, 0.001)
        assertEquals(60, sets.single().holdDurationSeconds)
    }
}
