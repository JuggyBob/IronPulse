package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DAY_MS
import com.example.data.model.HomeDashboard
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Which of the two metrics the rolling heat map shows.
 */
enum class HomeHeatmapMetric(val label: String) {
    VOLUME("Volume"),
    DURATION("Duration")
}

/**
 * The last seven calendar dates as a compact heat map.
 *
 * Seven cells, one per date, today included. The cell colour shows how hard the day was, and
 * tapping a cell says exactly what was done that day: the number is never the only way to read
 * it, because a colour alone tells the athlete nothing about the size of the number.
 */
@Composable
fun HomeHeatmap(
    cells: List<HomeDashboard.DayCell>,
    metric: HomeHeatmapMetric,
    onMetricChange: (HomeHeatmapMetric) -> Unit,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    var selected by remember(cells) { mutableStateOf<Int?>(null) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST 7 DAYS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            // The metric switches the numbers, never the dates: the seven dates are the same
            // whether the athlete is looking at volume or at time.
            Row(
                modifier = Modifier
                    .background(DarkBg, RoundedCornerShape(8.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                HomeHeatmapMetric.entries.forEach { option ->
                    val isSelected = option == metric
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ElectricCyan else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onMetricChange(option) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("home_heatmap_metric_${option.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) DarkBg else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cells.forEachIndexed { index, cell ->
                val intensity = cellIntensity(cell, metric)
                val color = heatColor(intensity)
                val isSelected = selected == index
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(color, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) TextWhite else DarkBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selected = if (isSelected) null else index }
                            .testTag("home_heatmap_cell_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cell.setCount > 0) {
                            Text(
                                text = cell.setCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (intensity > 0.55f) DarkBg else TextWhite
                            )
                        }
                    }
                    Text(
                        text = dateFormat.format(Date(cell.dayStart)).substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The exact value of the selected day, so the colours are never the only information.
        val selectedCell = selected?.let { cells.getOrNull(it) }
        val detail = when {
            selectedCell == null -> "Tap a day to see what you trained."
            !selectedCell.hasSession -> "No workout recorded on ${dateFormat.format(Date(selectedCell.dayStart))}."
            metric == HomeHeatmapMetric.VOLUME ->
                "${dateFormat.format(Date(selectedCell.dayStart))}: " +
                    "${WeightConversion.format(selectedCell.volume, weightUnit, decimals = 0, withUnit = false)} ${weightUnit.suffix}-reps"
            else ->
                "${dateFormat.format(Date(selectedCell.dayStart))}: " +
                    "${selectedCell.minutes} min • ${selectedCell.setCount} sets"
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.testTag("home_heatmap_detail")
        )
    }
}

/**
 * How vivid a day is, from 0 to 1.
 *
 * The scale is the app's own: a day is fully vivid at ninety minutes of training, and a day with
 * logged sets always keeps a visible floor of colour so a trained day never renders as empty.
 */
private fun cellIntensity(cell: HomeDashboard.DayCell, metric: HomeHeatmapMetric): Float {
    if (cell.setCount <= 0) return 0f
    val raw = when (metric) {
        HomeHeatmapMetric.VOLUME ->
            (cell.volume / 20_000.0).toFloat()
        HomeHeatmapMetric.DURATION ->
            (cell.minutes / 90f)
    }
    return raw.coerceIn(0f, 1f).coerceAtLeast(0.22f)
}

/** The colour of a cell, from the same dark palette the rest of the app uses. */
private fun heatColor(intensity: Float): androidx.compose.ui.graphics.Color = when {
    intensity <= 0f -> DarkBg
    intensity < 0.3f -> DarkSurfaceVariant
    intensity < 0.55f -> ElectricCyan.copy(alpha = 0.45f)
    intensity < 0.8f -> ElectricCyan.copy(alpha = 0.75f)
    else -> ElectricCyan
}

/**
 * One number of the dashboard, with its label.
 */
@Composable
fun HomeStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = ElectricCyan,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp, horizontal = 10.dp)
            .testTag("home_stat_${label.lowercase().replace(" ", "_")}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A motivational line for the day.
 *
 * The line changes once a day, not once a recomposition, so it does not flicker while the athlete
 * scrolls the dashboard.
 */
object TrainingQuotes {

    /**
     * The quote for a given moment.
     *
     * The day of the year picks the line, so the same quote is shown for the whole day and a new
     * one arrives tomorrow.
     */
    fun forDay(timestamp: Long): String {
        val day = Math.floorDiv(timestamp, DAY_MS)
        val index = Math.floorMod(day, quotes.size.toLong()).toInt()
        return quotes[index]
    }

    private val quotes = listOf(
        "Consistency beats intensity when the plan is long.",
        "The set you almost skipped is the one that counts.",
        "Strong form first, then more weight.",
        "A finished session is a session that happened.",
        "Rest is part of the program, not a break from it.",
        "Small progress, repeated, outruns big days.",
        "Show up on the days you do not feel like it.",
        "Every rep is practice for the next one.",
        "The plan is simple. Doing it is the work.",
        "Train the movement you are weakest at.",
        "Momentum comes from starting, not from motivation.",
        "Leave one good rep in the tank and come back stronger.",
        "Progress hides in the boring weeks.",
        "Control the weight before you chase it.",
        "A routine you follow beats a routine you design.",
        "Difficulty is earned, not chosen.",
        "Today you lay the groundwork for next month.",
        "Sleep and food are part of the training.",
        "Measure the trend, not the single session.",
        "Discipline is doing it on the day you would rather not.",
        "The bar does not care how you feel, and neither should the plan.",
        "Own the easy sets and the hard ones alike.",
        "One more week of this beats a new plan every Monday.",
        "Finish what you started, then raise the bar."
    )
}
