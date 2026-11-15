/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The per-exercise default rest belongs to the exercise detail dialog, under Targets, rather than
 * an overflow menu on the card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class ExerciseDetailDefaultRestTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val exercise = Exercise(
        id = 1L,
        name = "Dumbbell Single Arm Overhead Press",
        category = "Shoulders",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Dumbbell",
        target = "Deltoids",
        instructions = "Press the dumbbell overhead without leaning back.",
        supportedFields = "weight,reps,holdSeconds,distanceKm"
    )

    @Test
    fun `default rest sits in the detail dialog and changes the saved value`() {
        val restSeconds = mutableStateOf(90)
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier) {
                    ExerciseDetailDialog(
                        exercise = exercise,
                        unit = "kg",
                        onAddSet = {},
                        onDismiss = {},
                        defaultRestSeconds = restSeconds.value,
                        onDefaultRestChange = { restSeconds.value = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("TARGETS").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("exercise_default_rest_section")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Rest 90s after each set")
            .assertIsDisplayed()

        val firstPreset = composeTestRule
            .onNodeWithTag("exercise_default_rest_preset_0")
            .getUnclippedBoundsInRoot()
        val lastPreset = composeTestRule
            .onNodeWithTag("exercise_default_rest_preset_180")
            .getUnclippedBoundsInRoot()
        assertEquals(
            "all rest presets must stay on one horizontally scrollable row",
            firstPreset.top.value,
            lastPreset.top.value,
            0.5f
        )

        composeTestRule
            .onNodeWithTag("exercise_default_rest_preset_60")
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText("Rest 60s after each set")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("exercise_default_rest_increase")
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText("Rest 75s after each set")
            .assertIsDisplayed()
    }
}
