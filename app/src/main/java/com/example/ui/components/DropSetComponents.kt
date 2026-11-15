/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.DropSetRules
import com.example.data.model.LoggedSet

/**
 * One row of a set list after drop sets have been attached to their main set.
 *
 * [isDrop] is false for a main set. [isLastChild] is true for the final child of a parent, so the
 * connector stops at that row instead of running on to a sibling that does not exist.
 */
data class OrderedSetRow(
    val set: LoggedSet,
    val isDrop: Boolean,
    val isLastChild: Boolean
)

/**
 * Turns the flat `logged_sets` rows of one exercise into the order a person reads them in:
 * main set 1, its children, main set 2, its children, and so on.
 *
 * Every caller that displays sets uses this, so history, Progress and the active workout can never
 * disagree about which child belongs to which parent. Rows whose parent is missing are normalised
 * into main sets by [DropSetRules], never silently hidden.
 */
fun orderedSetRows(sets: List<LoggedSet>): List<OrderedSetRow> {
    val normalized = DropSetRules.normalize(sets)
    val mains = DropSetRules.mainSets(normalized)
        .sortedWith(compareBy({ it.setNumber }, { it.timestamp }, { it.id }))
    val result = ArrayList<OrderedSetRow>(normalized.size)
    mains.forEach { main ->
        result += OrderedSetRow(main, isDrop = false, isLastChild = false)
        val children = DropSetRules.childrenOf(normalized, main.setUid)
        children.forEachIndexed { index, child ->
            result += OrderedSetRow(
                set = child,
                isDrop = true,
                isLastChild = index == children.lastIndex
            )
        }
    }
    return result
}

/**
 * The drawn branch that joins a drop set to its parent.
 *
 * A vertical line runs down the left gutter; a short horizontal arm meets the child card. The last
 * child's vertical line stops at the arm so the branch visibly ends instead of dangling.
 */
@Composable
fun DropSetBranchConnector(
    isLastChild: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF33556A),
    width: Dp = 28.dp,
    gutter: Dp = 20.dp
) {
    Canvas(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
    ) {
        // The gutter sits under the centre of the parent's numbered circle, so the branch
        // visibly grows out of that set instead of floating at the card edge.
        val gutterX = gutter.toPx()
        val armEnd = size.width
        val centerY = size.height / 2f
        val stroke = 1.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(gutterX, 0f),
            end = Offset(gutterX, if (isLastChild) centerY else size.height),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(gutterX, centerY),
            end = Offset(armEnd, centerY),
            strokeWidth = stroke
        )
    }
}

/**
 * The short lead-in below a main set, before its first child branch starts.
 *
 * Kept separate from [DropSetBranchConnector] because the parent row has a variable height and the
 * lead-in belongs in the gap, not inside the parent's own card.
 */
@Composable
fun DropSetBranchLeadIn(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF33556A),
    width: Dp = 28.dp,
    gutter: Dp = 20.dp
) {
    Canvas(modifier = modifier.width(width)) {
        val gutterX = gutter.toPx()
        drawLine(
            color = color,
            start = Offset(gutterX, 0f),
            end = Offset(gutterX, size.height),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

/**
 * A drop child that can collapse when removed.
 *
 * This is a top-level composable on purpose. Calling AnimatedVisibility inside a Box that is itself
 * inside a Column makes Kotlin resolve the ColumnScope extension, which cannot be used in the Box.
 * This helper keeps the generic overload and the same exit animation everywhere.
 */
@Composable
fun DropSetRowVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
    ) {
        content()
    }
}

/** Row used by history and Progress to indent a child under its parent. */
@Composable
fun DropSetBranchRow(
    isLastChild: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(modifier = modifier) {
        DropSetBranchConnector(isLastChild = isLastChild)
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
