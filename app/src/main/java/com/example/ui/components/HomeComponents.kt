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
import com.example.ui.theme.OnAccent
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
 * One card of the Home dashboard, in the order the athlete arranged it.
 *
 * The ids are what is written to the preferences and must stay stable: changing one would make a
 * saved order stop matching the widget. [label] is only read aloud by accessibility services and by
 * the reorder handle.
 */
enum class HomeWidget(
    val id: String,
    val label: String,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean = true,
    val customizable: Boolean = false
) {
    TODAY(
        "today",
        "today's workout",
        "Today's Workout",
        "Your scheduled session, or the next step when nothing is planned."
    ),
    ACTIONS(
        "actions",
        "quick actions",
        "Quick Actions",
        "One-tap shortcuts to your saved routines and your progress."
    ),
    WEEK(
        "week",
        "this week's schedule",
        "This Week",
        "The seven-day plan from the routine you are following."
    ),
    STREAK(
        "streak",
        "workout streak",
        "Workout Streak",
        "Consecutive planned workout days completed, with a schedule-adherence heat map."
    ),
    STATS(
        "stats",
        "training numbers",
        "Training Numbers",
        "Training days, workouts, time and volume for this week."
    ),
    HEATMAP(
        "heatmap",
        "activity heat map",
        "Last 7 Days",
        "A compact heat map of volume or duration for the last seven days."
    ),
    WEIGHT(
        "weight",
        "body weight",
        "Body Weight",
        "Your latest weigh-in, the change and a quick log button."
    ),
    RECENT(
        "recent",
        "last workout",
        "Last Workout",
        "The most recent session with its sets and totals."
    ),
    QUOTE(
        "quote",
        "today's line",
        "Today's Line",
        "A short training line that changes with the day."
    ),
    PHOTO(
        "photo",
        "motivation photo",
        "Motivation Photo",
        "A picture from your gallery, cropped to fill the card.",
        defaultEnabled = false,
        customizable = true
    ),
    GOAL(
        "goal",
        "weekly goal",
        "Weekly Goal",
        "Your own target for workouts this week, with live progress.",
        defaultEnabled = false,
        customizable = true
    );

    companion object {
        /**
         * The widgets a fresh install shows, in their default order.
         *
         * The optional widgets are deliberately absent: a photo card with no photo and a goal card
         * the athlete never asked for would make the first Home page worse, not better. They are
         * available from the widget manager and are one tap away.
         */
        fun defaultVisible(): List<HomeWidget> = entries.filter { it.defaultEnabled }

        /** The optional widgets a fresh install leaves off the Home page. */
        val defaultHiddenIds: Set<String>
            get() = entries.filterNot { it.defaultEnabled }.map { it.id }.toSet()

        /**
         * Merge a saved order with the widgets this build knows about.
         *
         * The saved order is the authority for everything it contains. A widget that the athlete
         * has never seen is inserted at the position it has in the default order (just before the
         * next widget that is already placed), so a release that adds a card does not dump it at
         * the bottom of the page. Ids that no longer exist are dropped, and duplicates are removed.
         *
         * Removed widgets are not handled here: [resolve] filters them out after the merge, so the
         * saved order can still remember where a widget would go if it is added back.
         */
        fun merge(savedIds: List<String>): List<HomeWidget> {
            val known = savedIds
                .mapNotNull { id -> entries.firstOrNull { it.id == id } }
                .distinct()
                .toMutableList()

            for (widget in entries) {
                if (widget in known) continue
                val successor = entries
                    .drop(widget.ordinal + 1)
                    .firstOrNull { it in known }
                val insertAt = if (successor != null) known.indexOf(successor) else known.size
                known.add(insertAt, widget)
            }
            return known
        }

        /**
         * The widgets the athlete should actually see.
         *
         * [hiddenIds] is the set of widgets removed from Home. A null set means the athlete has
         * never customized the page, so the optional widgets keep their default-hidden state; an
         * empty set means they have customized it and deliberately enabled everything.
         */
        fun resolve(savedIds: List<String>, hiddenIds: Set<String>?): List<HomeWidget> {
            val hidden = hiddenIds ?: defaultHiddenIds
            return merge(savedIds).filterNot { it.id in hidden }
        }

        /** Every widget that is not currently on Home, in the manager's default order. */
        fun available(enabled: List<HomeWidget>): List<HomeWidget> =
            entries.filterNot { it in enabled }

        /**
         * Puts [widget] back on Home at the place it has in the default order.
         *
         * The successor is the first widget after it that is already visible, so a widget added
         * later still lands between the same neighbours it would have on a fresh install. Adding a
         * widget that is already visible is a no-op, which is what keeps a double tap from
         * duplicating a card.
         */
        fun add(visible: List<HomeWidget>, widget: HomeWidget): List<HomeWidget> {
            if (widget in visible) return visible
            val defaultIndex = entries.indexOf(widget)
            val successor = entries.drop(defaultIndex + 1).firstOrNull { it in visible }
            val insertAt = if (successor != null) visible.indexOf(successor) else visible.size
            return visible.toMutableList().apply { add(insertAt, widget) }
        }

        /** Removes [widget] from the visible page without touching anything it displays. */
        fun remove(visible: List<HomeWidget>, widget: HomeWidget): List<HomeWidget> =
            visible.filterNot { it == widget }

        /**
         * Moves [widget] by [delta] slots, clamped at the ends.
         *
         * The manager's up and down buttons are the accessible alternative to the long-press drag
         * on Home. A move that would leave the list is a no-op rather than an exception.
         */
        fun move(
            visible: List<HomeWidget>,
            widget: HomeWidget,
            delta: Int
        ): List<HomeWidget> {
            val from = visible.indexOf(widget)
            if (from < 0) return visible
            val to = (from + delta).coerceIn(0, visible.lastIndex)
            if (to == from) return visible
            return visible.toMutableList().apply { add(to, removeAt(from)) }
        }

        /**
         * The visible order while a drag is in progress.
         *
         * [draggedId] is lifted out of [visible] and put back at [insertionIndex], so the cards
         * after the gap can move down (or up) under the finger before the athlete releases. An
         * index equal to the card's own position returns [visible] unchanged.
         */
        fun previewOrder(
            visible: List<HomeWidget>,
            draggedId: String,
            insertionIndex: Int
        ): List<HomeWidget> {
            val from = visible.indexOfFirst { it.id == draggedId }
            if (from < 0) return visible
            val remaining = visible.filterIndexed { index, _ -> index != from }
            val target = insertionIndex.coerceIn(0, remaining.size)
            if (target == from) return visible
            return remaining.toMutableList().apply { add(target, visible[from]) }
        }

        /**
         * Moves [draggedId] to [insertionIndex] in the visible list.
         *
         * [full] is the complete saved order, including widgets that are hidden on this Home page
         * (the week card without a routine). [visible] is what the athlete can actually drag, so
         * [insertionIndex] is an index in the visible list with the dragged widget removed. The
         * hidden widgets keep their place relative to the visible anchor, which is why this cannot
         * simply reorder [full] by raw index.
         *
         * An index equal to the dragged widget's current position means "no move", and an index
         * outside the list is clamped instead of throwing.
         */
        fun moveToInsertion(
            full: List<HomeWidget>,
            visible: List<HomeWidget>,
            draggedId: String,
            insertionIndex: Int
        ): List<HomeWidget> {
            val from = visible.indexOfFirst { it.id == draggedId }
            if (from < 0) return full

            val remaining = visible.filterIndexed { index, _ -> index != from }
            val target = insertionIndex.coerceIn(0, remaining.size)
            if (target == from || remaining.isEmpty()) return full

            val movingIndex = full.indexOfFirst { it.id == draggedId }
            if (movingIndex < 0) return full

            // Before the anchor, or after the last visible widget when the finger is past the end.
            val anchorId = remaining.getOrNull(target)?.id ?: remaining.last().id
            val insertAfterAnchor = target >= remaining.size

            val moved = full.toMutableList()
            val moving = moved.removeAt(movingIndex)
            val anchorIndex = moved.indexOfFirst { it.id == anchorId }
            if (anchorIndex < 0) return full
            moved.add(if (insertAfterAnchor) anchorIndex + 1 else anchorIndex, moving)
            return moved
        }
    }
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
                            color = if (isSelected) OnAccent else TextMuted
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
                                color = if (intensity > 0.55f) OnAccent else TextWhite
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
