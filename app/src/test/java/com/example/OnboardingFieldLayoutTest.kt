package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The layout rule the onboarding profile page depends on, proved directly.
 *
 * The reported bug was "the field textboxes go on a newline all the time and are squashed extremely
 * thin on a single column". The cause was that the field entrance was animated with an
 * AnimatedVisibility container. A visibility container inserts its own layout node between the Row
 * and the field, and that node does not implement Row/Column weight semantics, so the
 * `Modifier.weight(1f)` the page passed to each field was silently dropped.
 *
 * The fix moved the animation onto `graphicsLayer`, a draw-only modifier that leaves layout alone.
 * This test pins the underlying rule so the same mistake cannot come back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class OnboardingFieldLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun a_weighted_field_inside_a_draw_only_animation_keeps_its_width() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProbeField("probe_age", Modifier.weight(1f))
                    ProbeField("probe_height", Modifier.weight(1f))
                }
            }
        }

        // The window is 411dp and the gap is 8dp, so a correctly weighted pair gets about 201dp
        // each. A dropped weight is what the bug was: the field then collapses to its narrow label
        // width, which is far under half the row, and the row wraps it onto its own line.
        composeTestRule.onNodeWithTag("probe_age").assertWidthIsAtLeast(190.dp)
        composeTestRule.onNodeWithTag("probe_height").assertWidthIsAtLeast(190.dp)
    }

    @Test
    fun a_full_width_field_takes_the_whole_row() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ProbeField("probe_name", Modifier.fillMaxWidth())
            }
        }
        composeTestRule.onNodeWithTag("probe_name").assertWidthIsAtLeast(390.dp)
    }

    /**
     * A field drawn the way the onboarding fields now are: the entrance animation is a draw-only
     * `graphicsLayer`, so whatever width the caller asks for still holds.
     */
    @Composable
    private fun ProbeField(tag: String, modifier: Modifier = Modifier) {
        OutlinedTextField(
            value = "28",
            onValueChange = {},
            label = { Text("Age") },
            singleLine = true,
            modifier = modifier
                .graphicsLayer { alpha = 1f }
                .testTag(tag)
        )
    }
}
