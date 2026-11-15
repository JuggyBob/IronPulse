package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.WeightUnit
import com.example.ui.screens.SelectedRoutineExerciseRow
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The routine row's two separate affordances.
 *
 * The name opens the read-only exercise sheet (the same detail the workout shows). The small tune
 * button, present only when the exercise records an added weight, opens the exercise-level defaults
 * panel. Keeping them apart means a standard-weight exercise no longer has a name tap that does
 * nothing, and the defaults are still reachable for weighted calisthenics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class RoutineAddedWeightTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(addedWeightKg: Double) = RoutineExerciseItem(
        exerciseId = 1,
        exerciseName = "Single Leg Platform Slide",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        category = "Legs",
        equipment = "Bodyweight",
        targetSets = 3,
        targetReps = 8,
        targetAddedWeightKg = addedWeightKg
    )

    private fun setContent(
        item: RoutineExerciseItem,
        fields: List<ExerciseField> = listOf(ExerciseField.REPS, ExerciseField.ADDED_WEIGHT),
        onShowDetails: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                var expanded by remember { mutableStateOf(false) }
                SelectedRoutineExerciseRow(
                    index = 0,
                    item = item,
                    unit = WeightUnit.KG,
                    expanded = expanded,
                    supportedFields = fields,
                    canMoveUp = false,
                    canMoveDown = false,
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleExpanded = { expanded = !expanded },
                    onShowDetails = onShowDetails,
                    onUpdate = {},
                    onDelete = {}
                )
            }
        }
    }

    @Test
    fun `the added weight badge is visible without opening the panel`() {
        setContent(item(20.0))

        composeTestRule.onNodeWithTag("routine_added_weight_badge_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("routine_added_weight_badge_0").assertTextEquals("+20 kg")
        // The panel is closed, so the field it holds is not on screen.
        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertDoesNotExist()
    }

    @Test
    fun `there is no badge when the exercise has no added weight`() {
        setContent(item(0.0))

        composeTestRule.onNodeWithTag("routine_added_weight_badge_0").assertDoesNotExist()
    }

    @Test
    fun `tapping the exercise name opens the detail sheet`() {
        var opened = 0
        setContent(item(20.0), onShowDetails = { opened += 1 })

        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertDoesNotExist()

        composeTestRule.onNodeWithTag("routine_exercise_name_0").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, opened)
        // Opening the details must not also open the defaults panel.
        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertDoesNotExist()
    }

    @Test
    fun `the defaults button opens the added weight panel`() {
        setContent(item(20.0))

        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertDoesNotExist()

        composeTestRule.onNodeWithTag("routine_exercise_defaults_0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertIsDisplayed()
    }

    @Test
    fun `an exercise without an added-weight field has no defaults button`() {
        setContent(item(20.0), fields = listOf(ExerciseField.REPS))

        composeTestRule.onNodeWithTag("routine_exercise_defaults_0").assertDoesNotExist()
    }

    @Test
    fun `a standard-weight target shows its working load`() {
        setContent(
            item(0.0).copy(targetWeight = 9.0),
            fields = listOf(ExerciseField.WEIGHT, ExerciseField.REPS)
        )

        composeTestRule.onNodeWithTag("routine_weight_badge_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("routine_weight_badge_0").assertTextEquals("9 kg")
    }
}
