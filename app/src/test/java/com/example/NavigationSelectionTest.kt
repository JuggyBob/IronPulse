/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/** The bottom dock keeps a tab selected for pushed detail routes such as Settings. */
class NavigationSelectionTest {

    @Test
    fun `settings keeps profile selected`() {
        assertEquals(
            Screen.Profile.route,
            Screen.selectedTabRoute(Screen.Settings.route)
        )
    }

    @Test
    fun `ordinary tab routes are unchanged`() {
        listOf(
            Screen.Home,
            Screen.Progress,
            Screen.Profile,
            Screen.LogWorkout,
            Screen.AiCoach
        ).forEach { screen ->
            assertEquals(screen.route, Screen.selectedTabRoute(screen.route))
        }
    }
}
