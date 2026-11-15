package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The routine editor opens the workout's exercise sheet in read-only mode. It must not offer the
 * pen (fields are not edited from a routine) or the live "Add Set" action (there is no running
 * workout to add to).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class ExerciseDetailReadOnlyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun exercise() = Exercise(
        id = 1345,
        name = "Bentover Rear Delt Fly",
        category = "Shoulders",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Dumbbells",
        isCustom = true
    )

    @Test
    fun `the read-only sheet shows the exercise without edit or add-set actions`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseDetailDialog(
                    exercise = exercise(),
                    unit = "kg",
                    onAddSet = null,
                    onDismiss = {},
                    autoplayDemos = false,
                    onEdit = null
                )
            }
        }

        composeTestRule.onNodeWithTag("exercise_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bentover Rear Delt Fly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_exercise_detail_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("exercise_detail_add_set_button").assertDoesNotExist()
    }
}
