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
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.WeightUnit
import com.example.ui.screens.SelectedRoutineExerciseRow
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The default added weight of a routine exercise, and the panel it is edited in.
 *
 * The panel is opened by tapping the name of the exercise, and only then: a routine with twenty
 * exercises must not carry twenty visible weight fields, which is what the athlete asked for
 * explicitly. The badge beside the name is the one thing always visible, so the value can be read
 * without opening anything.
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

    private fun setContent(item: RoutineExerciseItem, interactive: Boolean = false) {
        composeTestRule.setContent {
            MyApplicationTheme {
                var expanded by remember { mutableStateOf(false) }
                SelectedRoutineExerciseRow(
                    index = 0,
                    item = item,
                    unit = WeightUnit.KG,
                    expanded = expanded,
                    canMoveUp = false,
                    canMoveDown = false,
                    onMoveUp = {},
                    onMoveDown = {},
                    onToggleExpanded = { expanded = !expanded },
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
    fun `tapping the exercise name opens the added weight panel`() {
        setContent(item(20.0), interactive = true)

        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertDoesNotExist()

        composeTestRule.onNodeWithTag("routine_exercise_name_0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("routine_added_weight_field_0").assertIsDisplayed()
    }
}
