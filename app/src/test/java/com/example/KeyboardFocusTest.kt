package com.example

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A text field must not come back focused.
 *
 * The reported problem: the athlete taps a field, dismisses the keyboard, and the field is still
 * selected. The focus never lets go, so the next time the app is opened from the recents the
 * field is still waiting for a keyboard that opens by itself, and there is nothing left to
 * dismiss.
 *
 * The app drops that focus on the first frame, before the keyboard can open for it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class KeyboardFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a field that comes back focused is dropped on the first frame`() {
        val focusEvents = mutableListOf<Boolean>()

        composeTestRule.setContent {
            MyApplicationTheme {
                val focusRequester = remember { FocusRequester() }

                // A field that grabs the focus the moment it appears, which is what a restored
                // one does. The restored focus is in place before the app's cleanup runs, so the
                // request is modelled ahead of it: the order here is the order that matters.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusEvents.add(it.isFocused) }
                        .testTag("field")
                )

                KeepTheKeyboardFromComingBack()
            }
        }
        composeTestRule.waitForIdle()

        // The field did take the focus, and the app took it back.
        assertTrue(
            "the field should have been focused at least once, was $focusEvents",
            focusEvents.contains(true)
        )
        assertFalse(
            "the field must not still be focused, focus events were $focusEvents",
            focusEvents.lastOrNull() == true
        )
    }
}
