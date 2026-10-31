package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.components.HomeWidget
import com.example.ui.components.HomeWidgetManagerContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget manager is the accessible, discoverable way to change Home.
 *
 * These tests pin its contract: every widget can be identified and added, enabled widgets can be
 * removed or moved with buttons rather than a drag, and the photo and goal controls appear inline
 * only where they belong.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [36])
class HomeWidgetManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        enabled: List<HomeWidget>,
        photoPath: String = "",
        weeklyGoal: Int = 3,
        onAdd: (HomeWidget) -> Unit = {},
        onRemove: (HomeWidget) -> Unit = {},
        onMove: (HomeWidget, Int) -> Unit = { _, _ -> },
        onChoosePhoto: () -> Unit = {},
        onRemovePhoto: () -> Unit = {},
        onGoalChange: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWidgetManagerContent(
                    enabled = enabled,
                    hasWeekRoutine = true,
                    photoPath = photoPath,
                    weeklyGoal = weeklyGoal,
                    photoError = null,
                    onDismiss = {},
                    onAdd = onAdd,
                    onRemove = onRemove,
                    onMove = onMove,
                    onChoosePhoto = onChoosePhoto,
                    onRemovePhoto = onRemovePhoto,
                    onGoalChange = onGoalChange
                )
            }
        }
    }

    @Test
    fun `every widget that is not on Home is listed with an add button`() {
        setContent(enabled = emptyList())

        composeTestRule.onNodeWithTag("widget_manager_empty").assertIsDisplayed()
        for (widget in HomeWidget.entries) {
            composeTestRule.onNodeWithTag("widget_available_${widget.id}").assertExists()
            composeTestRule.onNodeWithTag("widget_add_${widget.id}").assertExists()
        }
    }

    @Test
    fun `adding an available widget reports its stable id`() {
        var added: HomeWidget? = null
        setContent(
            enabled = listOf(HomeWidget.TODAY, HomeWidget.ACTIONS),
            onAdd = { added = it }
        )

        composeTestRule.onNodeWithTag("widget_add_photo").performScrollTo().performClick()

        assertEquals(HomeWidget.PHOTO, added)
    }

    @Test
    fun `removing an enabled widget reports its stable id`() {
        var removed: HomeWidget? = null
        setContent(
            enabled = listOf(HomeWidget.TODAY, HomeWidget.WEIGHT, HomeWidget.PHOTO),
            onRemove = { removed = it }
        )

        composeTestRule.onNodeWithTag("widget_remove_weight").performClick()

        assertEquals(HomeWidget.WEIGHT, removed)
    }

    @Test
    fun `reorder buttons are disabled at the ends and report a one slot move`() {
        var moved: Pair<HomeWidget, Int>? = null
        setContent(
            enabled = listOf(HomeWidget.TODAY, HomeWidget.ACTIONS),
            onMove = { widget, delta -> moved = widget to delta }
        )

        composeTestRule.onNodeWithTag("widget_move_up_today").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("widget_move_down_actions").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("widget_move_down_today").assertIsEnabled()
        composeTestRule.onNodeWithTag("widget_move_down_today").performClick()
        assertEquals(HomeWidget.TODAY to 1, moved)

        composeTestRule.onNodeWithTag("widget_move_up_actions").performClick()
        assertEquals(HomeWidget.ACTIONS to -1, moved)
    }

    @Test
    fun `the photo row offers choosing a photo and reports the action`() {
        var chosen = false
        setContent(
            enabled = listOf(HomeWidget.PHOTO),
            onChoosePhoto = { chosen = true }
        )

        composeTestRule.onNodeWithText("No photo selected").assertExists()
        composeTestRule.onNodeWithTag("widget_photo_choose").performClick()

        assertEquals(true, chosen)
    }

    @Test
    fun `the goal row shows the target and reports a change`() {
        var goal: Int? = null
        setContent(
            enabled = listOf(HomeWidget.GOAL),
            weeklyGoal = 3,
            onGoalChange = { goal = it }
        )

        composeTestRule.onNodeWithText("Target: 3 workouts per week").assertExists()
        composeTestRule.onNodeWithTag("weekly_goal_increase").performClick()

        assertEquals(4, goal)
    }

    @Test
    fun `the goal stepper cannot leave the one to seven range`() {
        var goal: Int? = null
        setContent(
            enabled = listOf(HomeWidget.GOAL),
            weeklyGoal = 7,
            onGoalChange = { goal = it }
        )

        composeTestRule.onNodeWithTag("weekly_goal_increase").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("weekly_goal_decrease").performClick()
        assertEquals(6, goal)
    }

    @Test
    fun `the week row explains why it is not visible without a routine`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWidgetManagerContent(
                    enabled = listOf(HomeWidget.WEEK),
                    hasWeekRoutine = false,
                    photoPath = "",
                    weeklyGoal = 3,
                    photoError = null,
                    onDismiss = {},
                    onAdd = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    onChoosePhoto = {},
                    onRemovePhoto = {},
                    onGoalChange = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("Appears once you schedule a routine.")
            .assertExists()
    }

    @Test
    fun `a photo error is shown in the photo controls`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWidgetManagerContent(
                    enabled = listOf(HomeWidget.PHOTO),
                    hasWeekRoutine = true,
                    photoPath = "",
                    weeklyGoal = 3,
                    photoError = "That image could not be read.",
                    onDismiss = {},
                    onAdd = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    onChoosePhoto = {},
                    onRemovePhoto = {},
                    onGoalChange = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("widget_photo_error").assertExists()
        composeTestRule
            .onNodeWithText("That image could not be read.")
            .assertExists()
    }
}
