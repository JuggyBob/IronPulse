package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityDay
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MusclePart
import com.example.ui.components.DayDropdown
import com.example.ui.screens.ChatBubble
import com.example.ui.viewmodel.ChatMessage
import com.example.ui.components.ActivityHeatmapCard
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.MuscleBodyMap
import com.example.ui.components.MuscleSelectorDialog
import com.example.ui.components.PredefinedRoutinesSheet
import com.example.ui.theme.DarkBg
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the new visual pieces so they can be looked at, not only compiled.
 *
 * These are not assertions: they write PNGs under `src/test/screenshots/`, which is where the
 * anatomy of the body map and the alignment of the heat map labels get verified.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NewUiScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleExercise = Exercise(
        id = 1,
        name = "Weighted Pull-up",
        category = "Back",
        exerciseType = ExerciseType.ADDED_WEIGHT,
        description = "A vertical pull with added load. Pull the chest to the bar and control the descent.",
        equipment = "Pull-up Bar",
        isCustom = false
    )

    @Test
    fun body_map_with_highlight() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    MuscleBodyMap(highlighted = MusclePart.BACK, onSelect = null)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/muscle_body_map.png")
    }

    @Test
    fun body_map_interactive_state() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    MuscleBodyMap(highlighted = MusclePart.LEGS, onSelect = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/muscle_body_map_legs.png")
    }

    @Test
    fun muscle_selector_dialog() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    MuscleSelectorDialog(selected = MusclePart.CHEST, onSelect = {}, onDismiss = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/muscle_selector_dialog.png")
    }

    @Test
    fun exercise_detail_dialog() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    ExerciseDetailDialog(
                        exercise = sampleExercise,
                        unit = "kg",
                        onAddSet = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/exercise_detail_dialog.png")
    }

    @Test
    fun activity_heatmap() {
        val today = System.currentTimeMillis()
        val days = (0 until 200).mapNotNull { back ->
            val offset = back * 3.5 * 24 * 60 * 60 * 1000
            if (back % 4 == 3) null else ActivityDay(
                epochDayStart = today - offset.toLong(),
                minutes = (20 + (back * 7) % 100),
                volume = (500.0 + (back * 613) % 22000),
                setCount = 6
            )
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.background(DarkBg).padding(12.dp)) {
                    Text("Activity — last 12 months")
                    ActivityHeatmapCard(days = days)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/activity_heatmap.png")
    }

    @Test
    fun day_dropdown() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.background(DarkBg).padding(12.dp)) {
                    Text("Assign added exercises to:")
                    DayDropdown(daysPerWeek = 2, selectedDay = 1, onSelect = {})
                    DayDropdown(daysPerWeek = 4, selectedDay = 3, onSelect = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/day_dropdown.png")
    }

    @Test
    fun coach_bubble_with_badge_beside_it() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.background(DarkBg).padding(12.dp)) {
                    ChatBubble(
                        ChatMessage(
                            id = "1",
                            sender = ChatMessage.Sender.AI,
                            text = "Keep your elbows tucked and drive through the whole range."
                        )
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/coach_bubble.png")
    }

    @Test
    fun starter_routine_browser() {
        // The real catalog, so the render shows what an athlete actually sees: no "not in your
        // catalog" warnings, because every template movement resolves.
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val array = org.json.JSONArray(
            context.assets.open("exercises.json").use { it.readBytes().decodeToString() }
        )
        val catalog = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Exercise(
                name = o.getString("name"),
                category = o.getString("category"),
                exerciseType = o.optString("exerciseType", "STANDARD_WEIGHT")
            )
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    PredefinedRoutinesSheet(
                        allExercises = catalog,
                        onAdopt = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/predefined_routines.png")
    }
}
