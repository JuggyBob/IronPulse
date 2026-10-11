package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.ExerciseEditorDialog
import com.example.ui.components.MuscleBodyMap
import com.example.ui.components.PredefinedRoutinesSheet
import com.example.ui.components.WeightInputField
import com.example.data.model.Exercise
import com.example.data.model.PredefinedRoutines
import com.example.data.model.WeightUnit
import com.example.data.model.ExerciseType
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots for the polish round.
 *
 * Every one of these exists because of a defect reported from a device: the editor using chips where
 * the rest of the app uses dropdowns, the pre-made routines sheet with a split background, the
 * onboarding profile page collapsing its fields into one narrow column, and the weigh-in log markup
 * not matching the app theme. A unit test cannot see any of those, so they are recorded as PNGs and
 * read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PolishRoundScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val libraryExercise = Exercise(
        id = 7,
        name = "Decline Push-Up",
        category = "Chest",
        bodyPart = "chest",
        target = "pectorals",
        secondaryMuscles = "[\"Triceps\",\"Shoulders\"]",
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Bodyweight",
        description = "Hands on the floor, feet elevated on a bench.",
        sourceId = "0279"
    )

    // ---------------------------------------------------------------- routines sheet

    @Test
    fun predefined_routines_sheet() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    PredefinedRoutinesSheet(
                        allExercises = listOf(libraryExercise),
                        onAdopt = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/predefined_routines_sheet.png")
    }

    // ---------------------------------------------------------------- onboarding profile page

    /**
     * The profile page of the first run flow, reproduced here with the same field structure.
     *
     * It is rendered as its own composable rather than through `OnboardingScreen` because that one is
     * gated on a view model. What matters is the layout of the two-up rows, which is where the
     * collapse happened.
     */
    @Test
    fun onboarding_profile_page_field_layout() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    OnboardingProfileSample()
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/onboarding_profile_page.png")
    }

    // ---------------------------------------------------------------- weigh in log

    @Test
    fun weigh_in_log_rows() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    WeighInLogSample()
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/weigh_in_log.png")
    }

    // ---------------------------------------------------------------- reference widgets

    @Test
    fun dropdown_reference() {
        // The dropdown the athlete already knows, recorded next to the editor so the two can be
        // compared directly rather than trusting that they match.
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.background(DarkBg)) {
                    com.example.ui.components.AnimatedDropdownField(
                        label = "Equipment *",
                        value = "Barbell",
                        icon = Icons.Default.FitnessCenter,
                        accent = com.example.ui.theme.GoldenAmber,
                        options = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight"),
                        expanded = false,
                        onExpandedChange = {},
                        onOptionSelected = {},
                        fieldTestTag = "reference_equipment_dropdown",
                        optionTestTagPrefix = "reference_equipment_option"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/editor_dropdown_reference.png")
    }

}

/**
 * The shape of the onboarding profile page's content: a full width field, a row of chips, two
 * two-up rows, and two label/chip rows. Each field is given `weight(1f)`, exactly as the real page
 * does, so a regression that swallows the weight shows up here as a squashed column.
 */
@androidx.compose.runtime.Composable
private fun OnboardingProfileSample() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "About you",
            style = MaterialTheme.typography.headlineSmall,
            color = TextWhite
        )
        Field("Name", "Alex")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Age", "28", Modifier.weight(1f))
            Field("Height (cm)", "182", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Current weight (kg)", "78.5", Modifier.weight(1f))
            Field("Target weight (kg)", "72.0", Modifier.weight(1f))
        }
        Text("Fitness level", color = TextMuted)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Beginner", "Intermediate", "Advanced").forEach {
                Box(
                    modifier = Modifier
                        .background(
                            if (it == "Intermediate") {
                                com.example.ui.theme.ElectricCyan.copy(alpha = 0.18f)
                            } else {
                                DarkSurface
                            },
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(it, color = TextWhite)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        singleLine = true,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = com.example.ui.theme.ElectricCyan,
            unfocusedBorderColor = DarkBorder,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@androidx.compose.runtime.Composable
private fun WeighInLogSample() {
    // Mirrors the weigh-in section of the Profile tab: a header, a scroll body of dated rows and the
    // weight formatted through the app's own converter. Recorded so the markup can be compared against
    // the rest of the app.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Weigh in logs",
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite
        )
        listOf("Oct 8" to 78.4, "Oct 1" to 79.1, "Sep 24" to 80.0).forEach { (date, kg) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(date, color = TextWhite, modifier = Modifier.weight(1f))
                Text(
                    com.example.data.model.WeightConversion.format(kg, WeightUnit.KG, withUnit = true),
                    color = com.example.ui.theme.ElectricCyan
                )
            }
        }
    }
}
