package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import com.example.ui.components.ExerciseGif
import com.example.ui.components.ExerciseGifThumbnail
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
 * How an exercise with media and one without it render. Robolectric cannot fetch the real GIF, so
 * the images stay blank here; what these renders verify is the layout contract: the animation
 * block reserves its space, the attribution is rendered, and an exercise with no media falls back
 * to the placeholder instead of collapsing the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExerciseMediaScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val withMedia = Exercise(
        name = "Barbell Bench Press",
        category = MuscleCategory.CHEST,
        bodyPart = "chest",
        target = "Pectorals",
        secondaryMuscles = "[\"Triceps\",\"Shoulders\"]",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        instructions = "Lie on a flat bench and grip the bar just outside shoulder width.",
        equipment = "Barbell",
        mediaPath = "videos/0025-EIeI8Vf.gif",
        sourceId = "0025"
    )

    private val withoutMedia = Exercise(
        name = "Pelican Curl",
        category = MuscleCategory.ARMS,
        exerciseType = ExerciseType.PURE_BODYWEIGHT,
        equipment = "Rings",
        description = "The user's own exercise.",
        isCustom = true,
        isAiGenerated = true
    )

    @Test
    fun detail_animation_with_and_without_media() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .background(DarkBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Dataset exercise, autoplay on", style = MaterialTheme.typography.titleSmall)
                    // Fixed height, as the detail dialog and the expanded log row both use, so the
                    // block reserves its space instead of collapsing to the loaded image.
                    ExerciseGif(
                        localUri = withMedia.customMediaUri,
                        localIsVideo = withMedia.customMediaIsVideo,
                        mediaPath = withMedia.mediaPath,
                        autoplay = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        showAttribution = true
                    )
                    Text("Custom exercise, no media", style = MaterialTheme.typography.titleSmall)
                    ExerciseGif(
                        localUri = withoutMedia.customMediaUri,
                        localIsVideo = withoutMedia.customMediaIsVideo,
                        mediaPath = withoutMedia.mediaPath,
                        autoplay = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        showAttribution = true
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/exercise_media_detail.png")
    }

    @Test
    fun detail_animation_paused_when_autoplay_is_off() {
        // The setting has to produce a real affordance, not just a hidden animation: with autoplay
        // off the athlete has to be able to see there is something to tap.
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .background(DarkBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Autoplay off", style = MaterialTheme.typography.titleSmall)
                    ExerciseGif(
                        localUri = withMedia.customMediaUri,
                        localIsVideo = withMedia.customMediaIsVideo,
                        mediaPath = withMedia.mediaPath,
                        autoplay = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        showAttribution = true
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/exercise_media_paused.png")
    }

    @Test
    fun list_row_thumbnails() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Row(
                    modifier = Modifier
                        .background(DarkBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExerciseGifThumbnail(
                        localUri = withMedia.customMediaUri,
                        localIsVideo = withMedia.customMediaIsVideo,
                        mediaPath = withMedia.mediaPath,
                        modifier = Modifier.size(96.dp)
                    )
                    ExerciseGifThumbnail(
                        localUri = withoutMedia.customMediaUri,
                        localIsVideo = withoutMedia.customMediaIsVideo,
                        mediaPath = withoutMedia.mediaPath,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/exercise_media_thumbnails.png")
    }
}
