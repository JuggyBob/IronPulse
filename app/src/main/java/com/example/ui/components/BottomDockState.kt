/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measured relationship between the page and the bottom navigation surface.
 *
 * [topInset] is the distance from the page's bottom edge to the dock's visible top. Screens that
 * draw their own bottom surface, such as the Quick Workout tray, use it instead of a hardcoded
 * clearance so the two surfaces meet exactly at any font scale or inset.
 */
@Immutable
data class BottomDockState(
    val visible: Boolean = true,
    val attached: Boolean = false,
    val topInset: Dp = 92.dp
)

val LocalBottomDockState = compositionLocalOf { BottomDockState() }
