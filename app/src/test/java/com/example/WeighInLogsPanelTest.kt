package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.BodyWeightLog
import com.example.data.model.WeightUnit
import com.example.ui.screens.WeighInLogsPanel
import com.example.ui.theme.DarkBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

/**
 * The profile weigh-in panel has two states in one slot: the collapsed summary and the full log.
 *
 * These tests pin the behaviour that changed: the summary and the list are never on screen at the
 * same time, and the open state is one card containing one row per weigh-in rather than a stack of
 * cards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class WeighInLogsPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val logs = listOf(
        BodyWeightLog(id = 3, weightKg = 82.5, timestamp = 3_000L, note = ""),
        BodyWeightLog(
            id = 2,
            weightKg = 81.0,
            timestamp = 2_000L,
            note = MainViewModel.WEIGH_IN_NOTE_PROFILE_UPDATE
        ),
        BodyWeightLog(id = 1, weightKg = 80.0, timestamp = 1_000L, note = "Fasted")
    )

    @Test
    fun `the summary opens into one card holding every weigh-in`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    WeighInLogsPanel(
                        expanded = expanded,
                        logs = logs,
                        weightUnit = WeightUnit.KG,
                        dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) },
                        onShowAll = { expanded = true },
                        onDelete = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("weigh_in_logs_summary").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("weigh_in_logs_card").assertCountEquals(0)

        composeTestRule.onNodeWithTag("view_all_weigh_in_logs_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("weigh_in_logs_summary").assertDoesNotExist()
        composeTestRule.onAllNodesWithTag("weigh_in_logs_card").assertCountEquals(1)
        composeTestRule.onNodeWithTag("weigh_in_logs_card").assertIsDisplayed()
        listOf(3L, 2L, 1L).forEach { id ->
            composeTestRule.onNodeWithTag("weigh_in_row_$id").assertIsDisplayed()
        }
    }

    @Test
    fun `the collapsed summary counts every entry`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                WeighInLogsPanel(
                    expanded = false,
                    logs = logs,
                    weightUnit = WeightUnit.KG,
                    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
                    onShowAll = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule.onNodeWithText("3 weigh-ins recorded").assertIsDisplayed()
        composeTestRule.onNodeWithTag("view_all_weigh_in_logs_button").assertIsDisplayed()
    }
}
