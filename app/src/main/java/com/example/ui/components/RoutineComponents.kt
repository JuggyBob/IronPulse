package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exercise
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.RoutineSchedule
import com.example.data.model.SavedRoutine
import com.example.data.model.Weekday
import com.example.ui.theme.*

/**
 * Dialog allowing user to select which specific day's workout to start from a multi-day routine.
 */
@Composable
fun SelectRoutineDayDialog(
    routineName: String,
    days: List<RoutineDay>,
    onSelectDay: (RoutineDay) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextWhite,
        textContentColor = TextMuted,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("select_routine_day_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Select Training Day",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = routineName,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This routine trains on ${days.size} day${if (days.size == 1) "" else "s"} a week. Select which workout you want to perform today:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(days, key = { it.weekday }) { day ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDay(day) }
                                .testTag("select_day_item_${day.weekday}"),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(DarkBg, CircleShape)
                                                .border(1.dp, ElectricCyan, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = Weekday.initial(day.weekday),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ElectricCyan
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = RoutineSchedule.dayLabel(day),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextWhite
                                            )
                                            Text(
                                                text = "${day.exercises.size} prescribed exercises",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MintGreen
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { onSelectDay(day) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = DarkBg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Start",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = DarkBg
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = day.exercises.joinToString(" • ") { "${it.exerciseName} (${RoutineParser.shortPrescription(it.prescription, it.equipment).ifBlank { "${it.targetSets}x${it.targetReps}" }})" },
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_select_day_button")
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

/**
 * Formats routine exercises for display, replacing raw JSON with a clean, structured UI.
 *
 * Per day "Start" buttons are intentionally absent: the training day is picked with the single
 * "Start Workout" button below the routine (which opens the day picker for multi day routines).
 */
@Composable
fun RoutineExerciseListDisplay(
    routine: SavedRoutine,
    allExercises: List<Exercise>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(routine.exercisesJson, allExercises) {
        RoutineParser.parseDays(routine.exercisesJson, allExercises)
    }
    // Only the days that hold a workout. A rest day has nothing to show and would only take up
    // room that the training days need.
    val trainingDays = remember(days) { days.filter { !it.isRestDay } }

    Column(modifier = modifier.fillMaxWidth()) {
        if (trainingDays.isNotEmpty()) {
            if (!isExpanded) {
                // Collapsed Preview
                if (trainingDays.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        items(trainingDays) { day ->
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, DarkBorder)
                            ) {
                                Text(
                                    text = "${Weekday.shortName(day.weekday)} (${day.exercises.size} ex)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    val firstDayPreview = trainingDays.first().exercises.take(3).joinToString(", ") { "${it.exerciseName} (${RoutineParser.shortPrescription(it.prescription, it.equipment).ifBlank { "${it.targetSets}x${it.targetReps}" }})" }
                    Text(
                        text = "${Weekday.name(trainingDays.first().weekday)} Preview: $firstDayPreview" + if (trainingDays.first().exercises.size > 3 || trainingDays.size > 1) " ..." else "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val preview = trainingDays[0].exercises.take(4).joinToString(", ") { "${it.exerciseName} (${RoutineParser.shortPrescription(it.prescription, it.equipment).ifBlank { "${it.targetSets}x${it.targetReps}" }})" }
                    Text(
                        text = "${trainingDays[0].exercises.size} prescribed exercises: $preview" + if (trainingDays[0].exercises.size > 4) " ..." else "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = TextWhite,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Expanded View - Full Day by Day breakdown
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    trainingDays.forEach { day ->
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = RoutineSchedule.dayLabel(day),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextWhite,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = "• ${day.exercises.size} ex",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Coach note shown as a plain description above the exercises
                                if (day.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DarkBg.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = day.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    day.exercises.forEachIndexed { index, ex ->
                                        RoutineExerciseItemRow(index = index + 1, item = ex)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Tap to collapse ▲" else "Tap to expand routine details ▼",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ElectricCyan
                )
            }
        } else {
            // Fallback for plain text without JSON
            val cleanDisplay = RoutineParser.formatRoutineForDisplay(routine.exercisesJson, allExercises)
            Text(
                text = cleanDisplay,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = TextWhite,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Tap to collapse ▲" else "Tap to expand routine details ▼",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ElectricCyan
                )
            }
        }
    }
}

private fun formatExerciseNameForWrap(name: String): String {
    if (name.isBlank()) return name
    return name.split(" ").joinToString(" ") { word ->
        if (word.contains("-") || word.contains("\u00AD") || word.length < 8) {
            word
        } else {
            val lower = word.lowercase()
            when {
                lower.startsWith("calis") && word.length > 5 -> word.substring(0, 5) + "\u00AD" + word.substring(5)
                lower.startsWith("hyper") && word.length > 5 -> word.substring(0, 5) + "\u00AD" + word.substring(5)
                lower.startsWith("concen") && word.length > 6 -> word.substring(0, 6) + "\u00AD" + word.substring(6)
                lower.startsWith("hand") && word.length > 4 -> word.substring(0, 4) + "\u00AD" + word.substring(4)
                lower.startsWith("body") && word.length > 4 -> word.substring(0, 4) + "\u00AD" + word.substring(4)
                lower.startsWith("over") && word.length > 4 -> word.substring(0, 4) + "\u00AD" + word.substring(4)
                lower.startsWith("under") && word.length > 5 -> word.substring(0, 5) + "\u00AD" + word.substring(5)
                lower.startsWith("dumb") && word.length > 4 -> word.substring(0, 4) + "\u00AD" + word.substring(4)
                else -> {
                    val mid = (word.length + 1) / 2
                    word.substring(0, mid) + "\u00AD" + word.substring(mid)
                }
            }
        }
    }
}

@Composable
private fun RoutineExerciseItemRow(
    index: Int,
    item: RoutineExerciseItem
) {
    // The tag is deliberately short: "3 sets x 12 reps" and nothing else. A load or a comment
    // makes it wide enough to wrap onto its own line, and the load belongs to the athlete anyway.
    val displayPrescription = remember(item.prescription, item.equipment) {
        RoutineParser.shortPrescription(item.prescription, item.equipment).ifBlank {
            if (item.targetHoldSeconds > 0) "${item.targetSets} sets x ${item.targetHoldSeconds}s"
            else "${item.targetSets} sets x ${item.targetReps} reps"
        }
    }

    val formattedExerciseName = remember(item.exerciseName) {
        formatExerciseNameForWrap(item.exerciseName)
    }

    Surface(
        color = DarkBg.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Estimate tag width in dp based on font size labelSmall bold (~6.6dp per char + padding/borders)
            val estimatedTagWidth = remember(displayPrescription) {
                (displayPrescription.length * 6.6f).dp + 18.dp
            }

            // Total available width for content minus the index "$index." (~24dp) and spacers (~10dp)
            val rowAvailableWidth = (maxWidth - 34.dp).coerceAtLeast(0.dp)
            val remainingForName = rowAvailableWidth - estimatedTagWidth - 8.dp

            // The tag drops to a new line ONLY if the green tag and exercise name overlap significantly,
            // i.e., remaining space for the exercise name is squashed so much that it becomes completely
            // unreadable (less than 80dp, which is where 2-3 character lines occur).
            // Normal squashing (e.g. 90dp-200dp) is completely readable and stays on the same line.
            val shouldStackTag = remainingForName < 80.dp || (estimatedTagWidth > (rowAvailableWidth * 0.68f) && rowAvailableWidth < 300.dp)

            if (shouldStackTag) {
                // Stacked layout: Exercise details on top, green tag on new line underneath
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$index.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedExerciseName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp
                            ),
                            color = TextWhite,
                            softWrap = true
                        )
                        if (item.category.isNotBlank() || item.equipment.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = listOf(item.category, item.equipment).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Green tag on a new line under the exercise's name, muscle group and equipment description
                        Surface(
                            color = MintGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = displayPrescription,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MintGreen,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else {
                // Side-by-side layout: Fits comfortably on one line without unreadable squashing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$index.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = formattedExerciseName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp
                                ),
                                color = TextWhite,
                                softWrap = true,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.category.isNotBlank() || item.equipment.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = listOf(item.category, item.equipment).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MintGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = displayPrescription,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MintGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
