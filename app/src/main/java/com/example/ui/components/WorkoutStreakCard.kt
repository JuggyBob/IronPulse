/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StreakDay
import com.example.data.model.StreakDayStatus
import com.example.data.model.StreakSummary
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Home dashboard's workout streak card.
 *
 * It is deliberately a widget rather than a screen: the current streak, the best streak, a
 * schedule-adherence heat map and the fire state are all visible without navigating away from the
 * dashboard. The data comes from [StreakSummary], which is derived from persisted schedule rows and
 * completed sessions, not from a counter that increments when the app opens.
 */
@Composable
fun WorkoutStreakCard(
    summary: StreakSummary,
    modifier: Modifier = Modifier
) {
    val statusLabel = when {
        summary.isAlive -> "ALIVE"
        summary.isBroken -> "BROKEN"
        summary.pendingToday -> "PENDING"
        !summary.hasSchedule -> "NO PLAN"
        else -> "READY"
    }
    val statusColor = when {
        summary.isAlive -> MintGreen
        summary.isBroken -> FireRed
        summary.pendingToday -> GoldenAmber
        else -> TextMuted
    }
    val currentColor = when {
        summary.isAlive -> MintGreen
        summary.isBroken -> FireRed
        else -> TextWhite
    }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val contextText = when {
        !summary.hasSchedule ->
            "Schedule a routine to start tracking your streak."
        summary.todayCompleted ->
            "Today's scheduled workout is complete."
        summary.pendingToday && summary.isAlive ->
            "Today's scheduled workout is pending. Complete it to keep the streak alive."
        summary.pendingToday ->
            "Today is scheduled. Finish it to start a new streak."
        summary.isBroken ->
            "A scheduled workout was missed. Your next completed scheduled day starts a new streak."
        summary.isAlive ->
            "Today is a rest day. The streak stays alive."
        summary.nextPlannedDay != null ->
            "Next scheduled: ${dateFormat.format(Date(summary.nextPlannedDay))}"
        else ->
            "No scheduled workout today."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_streak_widget")
            .semantics {
                contentDescription = when {
                    !summary.hasSchedule -> "No workout schedule yet."
                    summary.isAlive -> "Workout streak alive: ${summary.currentStreak} scheduled " +
                        "workout days. Best streak: ${summary.bestStreak}."
                    summary.pendingToday -> "Workout streak pending: today's scheduled workout " +
                        "has not been completed yet. Best streak: ${summary.bestStreak}."
                    else -> "Workout streak broken. Best streak: ${summary.bestStreak}."
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFire(
                    alive = summary.isAlive,
                    modifier = Modifier
                        .size(76.dp)
                        .testTag("streak_fire")
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CURRENT STREAK",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                color = statusColor
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = summary.currentStreak.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = currentColor,
                            modifier = Modifier.testTag("streak_current")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (summary.currentStreak == 1) "workout day" else "workout days",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = "Best streak: ${summary.bestStreak} " +
                            if (summary.bestStreak == 1) "workout day" else "workout days",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.testTag("streak_best")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SCHEDULE ADHERENCE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                StreakHeatmap(
                    summary = summary,
                    modifier = Modifier
                        .weight(1.35f)
                        .testTag("streak_heatmap")
                )
                // The heat map is deliberately compact (twelve weeks); this column fills the rest
                // of the card with the counts and the next scheduled day instead of leaving a
                // blank right edge.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "AT A GLANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = TextMuted
                    )
                    StreakMiniStat(
                        label = "Done",
                        value = summary.completedDays.toString(),
                        color = MintGreen
                    )
                    StreakMiniStat(
                        label = "Missed",
                        value = summary.missedDays.toString(),
                        color = if (summary.missedDays > 0) FireRed else TextMuted
                    )
                    StreakMiniStat(
                        label = "Best",
                        value = summary.bestStreak.toString(),
                        color = GoldenAmber
                    )
                    HorizontalDivider(
                        color = DarkBorder.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Text(
                        text = "NEXT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = TextMuted
                    )
                    Text(
                        text = nextScheduledText(summary, dateFormat),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp
                        ),
                        color = TextWhite,
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreakLegendDot(MintGreen, "Done")
                StreakLegendDot(FireRed, "Missed")
                StreakLegendDot(DarkSurfaceVariant, "Rest", border = DarkBorder)
                StreakLegendDot(GoldenAmber, "Pending")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = contextText,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 17.sp
            )
        }
    }
}

private fun nextScheduledText(
    summary: StreakSummary,
    dateFormat: SimpleDateFormat
): String = when {
    !summary.hasSchedule -> "No plan"
    summary.pendingToday -> "Today"
    summary.nextPlannedDay != null -> dateFormat.format(Date(summary.nextPlannedDay))
    summary.todayCompleted -> "Rest of today"
    else -> "No upcoming day"
}

@Composable
private fun StreakMiniStat(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = color
        )
    }
}

@Composable
private fun StreakLegendDot(
    color: Color,
    label: String,
    border: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
                .then(
                    if (border != null) {
                        Modifier.border(1.dp, border, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun StreakHeatmap(
    summary: StreakSummary,
    modifier: Modifier = Modifier
) {
    val weeks = remember(summary.days) { summary.days.chunked(7) }
    BoxWithConstraints(modifier = modifier) {
        val labelWidth = 22.dp
        val spacing = 3.dp
        val columns = weeks.size.coerceAtLeast(1)
        val gridWidth = (maxWidth - labelWidth).coerceAtLeast(0.dp)
        val availableCell = (gridWidth - spacing * (columns - 1)) / columns
        // Shrink to fit narrow cards instead of clipping the last column, but never grow past
        // the original compact 10dp square on wide cards.
        val cellSize = availableCell.coerceIn(5.dp, 10.dp)

        Row {
            Column(
                modifier = Modifier.width(labelWidth),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                repeat(7) { row ->
                    Box(
                        modifier = Modifier.height(cellSize),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val label = weekdayLabel(row, summary.weekStartsOnMonday)
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                        week.forEach { day ->
                            StreakCell(
                                day = day,
                                isToday = day.dayStart == summary.todayStart,
                                size = cellSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCell(day: StreakDay, isToday: Boolean, size: Dp) {
    val fill = when (day.status) {
        StreakDayStatus.COMPLETED -> MintGreen
        StreakDayStatus.MISSED -> FireRed.copy(alpha = 0.78f)
        StreakDayStatus.REST -> DarkSurfaceVariant
        StreakDayStatus.PENDING -> GoldenAmber.copy(alpha = 0.32f)
        StreakDayStatus.FUTURE -> DarkSurfaceVariant.copy(alpha = 0.34f)
        StreakDayStatus.UNTRACKED -> DarkBg.copy(alpha = 0.55f)
    }
    val edge = when {
        isToday -> ElectricCyan
        day.status == StreakDayStatus.PENDING -> GoldenAmber
        day.status == StreakDayStatus.UNTRACKED -> DarkBorder.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(fill, RoundedCornerShape(3.dp))
            .border(
                width = if (edge == Color.Transparent) 0.dp else 1.dp,
                color = edge,
                shape = RoundedCornerShape(3.dp)
            )
    )
}

private fun weekdayLabel(row: Int, weekStartsOnMonday: Boolean): String {
    val mondayIndex = if (weekStartsOnMonday) row else (row + 6) % 7
    return when (mondayIndex) {
        0 -> "Mon"
        2 -> "Wed"
        4 -> "Fri"
        else -> ""
    }
}

@Composable
private fun StreakFire(alive: Boolean, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    if (alive && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "streak_fire")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "streak_fire_phase"
        )
        Canvas(modifier = modifier) {
            drawStreakFire(alive = true, phase = phase)
        }
    } else {
        Canvas(modifier = modifier) {
            drawStreakFire(alive = alive, phase = 0.55f)
        }
    }
}

/**
 * Draws the living or burnt-out fire.
 *
 * The alive state uses three layered flame paths and a handful of embers. The burnt state removes
 * the animation and uses muted ash/smoke shapes. Both are drawn from the palette's warm accents so
 * Light, Dark, IronPulse and AMOLED remain coherent.
 */
private fun DrawScope.drawStreakFire(alive: Boolean, phase: Float) {
    val w = size.width
    val h = size.height
    val centerX = w / 2f

    if (alive) {
        // Warm ambient glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GoldenAmber.copy(alpha = 0.22f),
                    CoralOrange.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(centerX, h * 0.60f),
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = Offset(centerX, h * 0.60f)
        )

        val sway = sin(phase * 2f * PI.toFloat()) * w * 0.035f
        val lift = cos(phase * 2f * PI.toFloat()) * h * 0.015f
        val outer = Path().apply {
            moveTo(centerX, h * (0.06f + lift / h))
            cubicTo(
                w * (0.76f + sway / w), h * 0.26f,
                w * (0.82f - sway / w), h * 0.62f,
                centerX, h * 0.94f
            )
            cubicTo(
                w * 0.22f, h * 0.70f,
                w * (0.20f + sway / w), h * 0.40f,
                centerX, h * (0.06f + lift / h)
            )
            close()
        }
        drawPath(
            path = outer,
            brush = Brush.verticalGradient(
                colors = listOf(FireRed, CoralOrange, GoldenAmber),
                startY = h * 0.08f,
                endY = h * 0.94f
            )
        )

        val middle = Path().apply {
            moveTo(centerX, h * 0.24f)
            cubicTo(
                w * 0.66f, h * 0.38f,
                w * 0.68f, h * 0.64f,
                centerX, h * 0.84f
            )
            cubicTo(
                w * 0.34f, h * 0.66f,
                w * 0.34f, h * 0.44f,
                centerX, h * 0.24f
            )
            close()
        }
        drawPath(
            path = middle,
            brush = Brush.verticalGradient(
                colors = listOf(GoldenAmber, CoralOrange.copy(alpha = 0.95f), GoldenAmber),
                startY = h * 0.24f,
                endY = h * 0.84f
            )
        )

        val core = Path().apply {
            moveTo(centerX, h * 0.43f)
            cubicTo(
                w * 0.58f, h * 0.52f,
                w * 0.58f, h * 0.70f,
                centerX, h * 0.78f
            )
            cubicTo(
                w * 0.42f, h * 0.68f,
                w * 0.42f, h * 0.54f,
                centerX, h * 0.43f
            )
            close()
        }
        drawPath(path = core, color = GoldenAmber.copy(alpha = 0.95f))

        // Embers rise with the same phase but at different offsets, so they do not pulse in unison.
        val emberAlpha = 0.30f + 0.45f * abs(sin(phase * 2f * PI.toFloat()))
        listOf(
            Offset(w * 0.20f, h * 0.30f),
            Offset(w * 0.80f, h * 0.22f),
            Offset(w * 0.68f, h * 0.12f)
        ).forEachIndexed { index, point ->
            drawCircle(
                color = if (index == 1) CoralOrange.copy(alpha = emberAlpha) else GoldenAmber.copy(alpha = emberAlpha),
                radius = w * if (index == 2) 0.024f else 0.018f,
                center = point
            )
        }
    } else {
        // A low ash mound with a few grey smoke curls. No animation: an extinguished fire must read
        // as extinguished even at a glance.
        drawOval(
            color = DarkSurfaceVariant,
            topLeft = Offset(w * 0.18f, h * 0.68f),
            size = Size(w * 0.64f, h * 0.24f)
        )
        drawOval(
            color = DarkBorder.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.18f, h * 0.68f),
            size = Size(w * 0.64f, h * 0.24f),
            style = Stroke(width = 1.2.dp.toPx())
        )
        listOf(0.30f, 0.50f, 0.70f).forEachIndexed { index, x ->
            val smoke = Path().apply {
                moveTo(w * x, h * 0.64f)
                cubicTo(
                    w * (x - 0.08f), h * 0.48f,
                    w * (x + 0.10f), h * 0.34f,
                    w * (x - 0.02f), h * 0.18f
                )
            }
            drawPath(
                path = smoke,
                color = TextMuted.copy(alpha = 0.28f - index * 0.04f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        listOf(
            Offset(w * 0.34f, h * 0.76f),
            Offset(w * 0.58f, h * 0.80f),
            Offset(w * 0.70f, h * 0.74f)
        ).forEach { point ->
            drawCircle(
                color = FireRed.copy(alpha = 0.24f),
                radius = w * 0.014f,
                center = point
            )
        }
        // A faint grey ember glow, not a flame.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TextMuted.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(centerX, h * 0.74f),
                radius = w * 0.40f
            ),
            radius = w * 0.40f,
            center = Offset(centerX, h * 0.74f)
        )
    }
}
