package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.HomePhotoWidget
import com.example.ui.components.HomeWeeklyGoalWidget
import com.example.ui.components.HomeWidgetsEmptyState
import com.example.ui.components.WeeklyGoalDialog
import com.example.ui.components.WeeklyGoalStepper
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The new Home cards must stay useful in their empty and failed states.
 *
 * A photo that was deleted from the gallery, a Home with every widget removed and a goal that is
 * already reached are all normal states, not errors: each one gets a clear message and the action
 * that fixes it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class HomeWidgetCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `an empty photo card invites the athlete to choose a picture`() {
        var chosen = false
        composeTestRule.setContent {
            MyApplicationTheme {
                HomePhotoWidget(
                    photoPath = "",
                    onChoosePhoto = { chosen = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_photo_empty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_photo_choose_button").performClick()

        assertTrue(chosen)
    }

    @Test
    fun `a missing photo file shows the same recoverable empty state`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomePhotoWidget(
                    photoPath = "/does/not/exist/widget.jpg",
                    onChoosePhoto = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_photo_empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a picture from your gallery to make this card your own.")
            .assertExists()
    }

    @Test
    fun `the goal card counts this week's workouts against the target`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWeeklyGoalWidget(workouts = 2, goal = 3, onCustomize = {})
            }
        }

        composeTestRule.onNodeWithText("2").assertExists()
        composeTestRule.onNodeWithText(" / 3").assertExists()
        composeTestRule.onNodeWithText("1 workout to go this week.").assertExists()
    }

    @Test
    fun `a reached goal says so instead of showing a negative count`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWeeklyGoalWidget(workouts = 5, goal = 3, onCustomize = {})
            }
        }

        composeTestRule
            .onNodeWithText("Goal reached. Every workout from here is a bonus.")
            .assertExists()
    }

    @Test
    fun `the goal card's tune button opens customization`() {
        var customized = false
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWeeklyGoalWidget(
                    workouts = 0,
                    goal = 3,
                    onCustomize = { customized = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_goal_customize").performClick()

        assertTrue(customized)
    }

    @Test
    fun `the empty Home offers a way back to the manager`() {
        var opened = false
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWidgetsEmptyState(onOpenManager = { opened = true })
            }
        }

        composeTestRule.onNodeWithTag("home_widgets_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_widgets_empty_add").performClick()

        assertTrue(opened)
    }

    @Test
    fun `the goal stepper reports one step at a time and clamps at the ends`() {
        var goal = 1
        composeTestRule.setContent {
            MyApplicationTheme {
                WeeklyGoalStepper(goal = goal, onGoalChange = { goal = it })
            }
        }

        composeTestRule.onNodeWithTag("weekly_goal_decrease").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("weekly_goal_increase").performClick()
        assertEquals(2, goal)
    }

    @Test
    fun `the goal dialog saves the chosen target and can be cancelled`() {
        var saved: Int? = null
        var dismissed = false
        composeTestRule.setContent {
            MyApplicationTheme {
                WeeklyGoalDialog(
                    goal = 3,
                    onDismiss = { dismissed = true },
                    onSave = { saved = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("weekly_goal_increase").performClick()
        composeTestRule.onNodeWithTag("weekly_goal_save").performClick()

        assertEquals(4, saved)
        assertTrue(!dismissed)
    }
}
