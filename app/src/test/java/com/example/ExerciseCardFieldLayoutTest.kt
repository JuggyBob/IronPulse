/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.ui.screens.ExerciseCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The exercise card must stay usable when an exercise declares four fields.
 *
 * The old row let the fields summary take its full intrinsic width, which squeezed Add Set into a
 * narrow three-line button. The card now gives the summary the flexible width and the button a
 * minimum width. The overflow menu has been removed; the exercise name remains the route to the
 * detail dialog.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class ExerciseCardFieldLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val exercise = Exercise(
        id = 1L,
        name = "Dumbbell Single Arm Overhead Press",
        category = "Shoulders",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Dumbbell",
        instructions = "Press the dumbbell overhead without leaning back.",
        supportedFields = "weight,reps,holdSeconds,distanceKm"
    )

    @Test
    fun `four fields wrap without compressing the add-set button`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ExerciseCard(
                        exercise = exercise,
                        unit = "kg",
                        onClick = {},
                        onAddToWorkout = {}
                    )
                }
            }
        }

        val button = composeTestRule
            .onNodeWithTag("exercise_add_set_1")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val buttonWidth = button.right - button.left
        assertTrue(
            "Add Set must keep a usable minimum width, got $buttonWidth",
            buttonWidth >= 100.dp
        )
        composeTestRule
            .onNodeWithText(
                "Fields: Sets  Weight (kg)  Reps  Hold (s)  Distance (km)"
            )
            .assertIsDisplayed()
    }

    @Test
    fun `the exercise card has no overflow menu`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ExerciseCard(
                        exercise = exercise,
                        unit = "kg",
                        onClick = {},
                        onAddToWorkout = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("exercise_menu_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("exercise_card_name_1").assertIsDisplayed()
    }
}
