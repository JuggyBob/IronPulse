package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityDay
import com.example.data.model.ActivityHeatmap
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/** One square of the grid. */
private val CELL_SIZE = 15.dp

/** Gap between two squares, both horizontally and vertically. */
private val CELL_GAP = 3.dp

/** Width of one week column: the square plus its gap. */
private val WEEK_PITCH = CELL_SIZE + CELL_GAP

/**
 * Activity heat map: one year of training, one square per day.
 *
 * The month labels are generated from the very same week columns the grid renders, so a label always
 * sits above the first square of its month instead of marching evenly across the card. The colour
 * saturates with the effort of the day and switches between training time and lifted volume.
 */
@Composable
fun ActivityHeatmapCard(
    days: List<ActivityDay>,
    modifier: Modifier = Modifier
) {
    var useVolume by remember { mutableStateOf(false) }

    val today = remember(days) { System.currentTimeMillis() }
    val weeks = remember(today) { ActivityHeatmap.weeksEndingAt(today) }
    val monthLabels = remember(weeks) { ActivityHeatmap.monthLabels(weeks) }
    // Keys are normalised to local midnight: a day can be handed over with any time of day
    val byDay = remember(days) { days.associateBy { ActivityHeatmap.startOfDay(it.epochDayStart) } }
    val scrollState = rememberScrollState()

    // Land on the most recent weeks: a year of history scrolled to its empty start would hide
    // everything the athlete just did.
    LaunchedEffect(weeks) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("activity_heatmap_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Text(
                    text = "Last 12 months",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // Time / Volume switch
            Row(
                modifier = Modifier
                    .background(DarkBg, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                HeatmapToggle(
                    label = "Time",
                    selected = !useVolume,
                    onClick = { useVolume = false },
                    modifier = Modifier.testTag("activity_toggle_time")
                )
                HeatmapToggle(
                    label = "Volume",
                    selected = useVolume,
                    onClick = { useVolume = true },
                    modifier = Modifier.testTag("activity_toggle_volume")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Month labels, one slot per week column so they line up with the squares below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            weeks.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .width(WEEK_PITCH)
                        .padding(end = CELL_GAP)
                ) {
                    monthLabels[index]?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            // The label is wider than one column on purpose: it may run into the
                            // empty space above the next month, but it is never cut in half.
                            modifier = Modifier.width(WEEK_PITCH * 4)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // The grid: Monday on the top row, today outlined
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    (0 until 7).forEach { weekday ->
                        val dayStart = week.dayAt(weekday)
                        val entry = byDay[dayStart]
                        val intensity = entry?.let {
                            ActivityHeatmap.intensity(
                                minutes = it.minutes,
                                volume = it.volume,
                                setCount = it.setCount,
                                useVolume = useVolume
                            )
                        } ?: 0f
                        val isToday = dayStart == ActivityHeatmap.startOfDay(today)
                        val color by animateColorAsState(
                            targetValue = activityColor(intensity),
                            label = "activity_cell"
                        )
                        Box(
                            modifier = Modifier
                                .size(CELL_SIZE)
                                .background(color, RoundedCornerShape(3.dp))
                                .then(
                                    if (isToday) {
                                        Modifier.border(1.5.dp, MintGreen, RoundedCornerShape(3.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(CELL_GAP))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.width(6.dp))
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(13.dp)
                        .background(activityColor(level), RoundedCornerShape(3.dp))
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (useVolume) "More volume" else "More time",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun HeatmapToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (selected) DarkSurfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) ElectricCyan else TextMuted
        )
    }
}

/** Dim for a rest day, then progressively more vivid green up to fully saturated. */
private fun activityColor(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    return when {
        clamped <= 0f -> Color(0xFF1B2026)
        clamped < 0.25f -> Color(0xFF1B5E33)
        clamped < 0.5f -> Color(0xFF23893D)
        clamped < 0.75f -> Color(0xFF2FBE4C)
        else -> Color(0xFF3CE063)
    }
}
