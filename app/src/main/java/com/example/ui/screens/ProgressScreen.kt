package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.ui.components.AppVersionFooter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.ExerciseType
import com.example.data.model.WorkoutMetrics
import com.example.data.model.GeminiSummary
import com.example.data.model.LoggedSetNumbering
import com.example.data.model.MonthlyExerciseAnalyzer
import com.example.data.model.describeCardioSet
import com.example.data.model.describeLoggedSetEffort
import com.example.ui.components.ActivityHeatmapCard
import com.example.ui.components.AiUnavailableDialog
import com.example.ui.components.MarkdownView
import com.example.ui.components.WorkoutHistoryDialog
import com.example.ui.components.ProgressionLineChart
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
import com.example.ui.theme.VioletPurple
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ProgressMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The unit label for whichever progress metric is selected.
 *
 * One-rep-max and total volume are kilogram values, so they follow the athlete's unit. Max reps is a
 * count and has no mass unit at all, so it says "reps".
 */
private fun progressUnitLabel(metric: ProgressMetric, unit: String): String = when (metric) {
    ProgressMetric.MAX_REPS -> "reps"
    ProgressMetric.TOTAL_VOLUME -> "$unit-reps"
    else -> unit
}


private fun formatWeightDisplay(weight: Double): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", weight)
    }
}

@Composable
fun ProgressScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val distinctExercises by viewModel.distinctLoggedExercises.collectAsStateWithLifecycle()
    val selectedExercise by viewModel.selectedExerciseForProgress.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val availableAddedWeights by viewModel.availableAddedWeights.collectAsStateWithLifecycle()
    val selectedAddedWeightFilter by viewModel.selectedAddedWeightFilter.collectAsStateWithLifecycle()
    val chartPointsKg by viewModel.progressChartPoints.collectAsStateWithLifecycle()
    val exerciseHistorySets by viewModel.exerciseHistorySets.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val monthlySummaryState by viewModel.monthlySummaryState.collectAsStateWithLifecycle()
    val savedSummaries by viewModel.savedSummaries.collectAsStateWithLifecycle()
    val isSavedSummariesOpen by viewModel.isSavedSummariesOpen.collectAsStateWithLifecycle()
    val openedSavedSummary by viewModel.openedSavedSummary.collectAsStateWithLifecycle()
    val aiErrorDialog by viewModel.aiErrorDialog.collectAsStateWithLifecycle()
    val activityDays by viewModel.activityDays.collectAsStateWithLifecycle()

    // Weights are stored in kilograms; this is the unit the athlete reads
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val unit = weightUnit.suffix
    // Converted once, so the chart, the peak and the latest value all speak the athlete's unit
    val chartPoints = remember(chartPointsKg, weightUnit) {
        chartPointsKg.map { point -> point.copy(value = WeightConversion.fromKg(point.value, weightUnit)) }
    }

    var isExerciseSelectorExpanded by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var exerciseSearchQuery by remember { mutableStateOf("") }

    // A summary belongs to the exercise it was generated for: drop it when the selection changes
    LaunchedEffect(selectedExercise) {
        if (monthlySummaryState !is MainViewModel.MonthlySummaryState.Idle) {
            viewModel.dismissMonthlySummary()
        }
    }

    val matchingCompletedExercises = remember(distinctExercises, exerciseSearchQuery) {
        if (exerciseSearchQuery.isBlank()) {
            distinctExercises
        } else {
            distinctExercises.filter { it.contains(exerciseSearchQuery, ignoreCase = true) }
        }
    }

    val isPureBodyweight = remember(exerciseHistorySets) {
        exerciseHistorySets.isNotEmpty() && exerciseHistorySets.all { it.weight == 0.0 && it.addedWeight == 0.0 }
    }
    val hasAddedWeight = remember(exerciseHistorySets) {
        exerciseHistorySets.any { it.addedWeight > 0.0 || (it.exerciseType == ExerciseType.ADDED_WEIGHT && it.addedWeight == 0.0) }
    }

    // The tag is unwrapped before it is compared: a Double compared against a Double? is boxed on
    // both sides, and a boxed Double is compared by reference rather than by value.
    val displayedHistorySets = remember(exerciseHistorySets, selectedAddedWeightFilter, userProfile.currentWeightKg) {
        selectedAddedWeightFilter?.let { tag ->
            // The same rule the chart uses: a set belongs to the tag when its external load, its
            // added load or the body weight it was done at is the one that was picked.
            //
            // The body weight has to be the one the set carries, not the one the athlete has
            // today. A set done at 80 kg and a set done at 84 kg are two different bodyweight
            // entries, and the "BW: 80 kg" tag has to show only the first one.
            exerciseHistorySets.filter { set ->
                set.weight == tag ||
                    set.addedWeight == tag ||
                    (set.weight == 0.0 && set.addedWeight == 0.0 &&
                        BodyWeightTimeline.bodyWeightForSet(set, userProfile.currentWeightKg) == tag)
            }
        } ?: exerciseHistorySets
    }

    val historyDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val groupedHistory = remember(displayedHistorySets) {
        // Sets are always listed chronologically inside their training day
        displayedHistorySets
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
            .groupBy { historyDateFormat.format(Date(it.timestamp)) }
    }
    val groupedDates = remember(groupedHistory) { groupedHistory.keys.toList() }

    // Position of every set inside its own training day, derived from the complete (unfiltered)
    // history. This keeps the numbering continuous (1, 2, 3...) after a set was deleted, while
    // still showing the real set number when a weight filter hides part of the day.
    val setPositionById = remember(exerciseHistorySets) {
        LoggedSetNumbering.positionsBySetId(exerciseHistorySets)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header, with the workout history entry point: the log of past sessions is a record of
        // training, so it belongs next to the analytics rather than next to the "+" of a new one.
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Analytics & Progress",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Isolated metric curves preventing skewed bodyweight data",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("workout_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Workout History",
                            tint = ElectricCyan
                        )
                    }
                }
            }
        }

        // Activity heat map: one year of training, one square per day
        item {
            ActivityHeatmapCard(days = activityDays)
        }

        // Exercise Selector Dropdown Card (Searchable within completed exercises)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exercise_selector_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT EXERCISE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        if (distinctExercises.isNotEmpty()) {
                            Text(
                                text = "${distinctExercises.size} completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MintGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // Dropdown Trigger Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isExerciseSelectorExpanded) ElectricCyan else ElectricCyan.copy(alpha = 0.5f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                isExerciseSelectorExpanded = !isExerciseSelectorExpanded
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("exercise_dropdown_trigger"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    distinctExercises.isEmpty() -> "No exercises logged"
                                    selectedExercise.isNotBlank() -> selectedExercise
                                    else -> "Select a completed exercise"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isExerciseSelectorExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (isExerciseSelectorExpanded) "Close Menu" else "Open Menu",
                            tint = if (isExerciseSelectorExpanded) ElectricCyan else TextMuted
                        )
                    }

                    // Searchable Dropdown suggestions (Filters ONLY completed exercises)
                    AnimatedVisibility(
                        visible = isExerciseSelectorExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .background(DarkBg, RoundedCornerShape(10.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = exerciseSearchQuery,
                                onValueChange = { exerciseSearchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Search ${distinctExercises.size} completed exercises...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (exerciseSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { exerciseSearchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("progress_exercise_search_input")
                            )

                            if (distinctExercises.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No completed exercises logged yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            } else if (matchingCompletedExercises.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No completed exercise matching \"$exerciseSearchQuery\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    matchingCompletedExercises.forEach { exName ->
                                        val isSelected = exName == selectedExercise
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) ElectricCyan.copy(alpha = 0.15f)
                                                    else DarkSurface
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) ElectricCyan else DarkBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    viewModel.selectedExerciseForProgress.value = exName
                                                    viewModel.selectedAddedWeightFilter.value = null
                                                    isExerciseSelectorExpanded = false
                                                    exerciseSearchQuery = ""
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .testTag("select_progress_exercise_$exName"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = exName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isSelected) ElectricCyan else TextWhite,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = ElectricCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini monthly summary for the currently selected exercise
        item {
            val hasSelection = selectedExercise.isNotBlank()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_monthly_summary_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(ElectricCyan, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GEMINI MONTHLY SUMMARY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                        }
                        Text(
                            text = "Last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (hasSelection) {
                            "Send the last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days of \"$selectedExercise\" to Gemini and get a strength trend analysis for it."
                        } else {
                            "Select an exercise above to analyse its strength trend with Gemini."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.requestMonthlySummary() },
                            enabled = hasSelection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DarkBg,
                                disabledContainerColor = DarkSurfaceVariant,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("gemini_monthly_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (hasSelection) DarkBg else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini Monthly Summary",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (hasSelection) DarkBg else TextMuted,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Saved summaries history
                        IconButton(
                            onClick = { viewModel.openSavedSummaries() },
                            modifier = Modifier
                                .size(46.dp)
                                .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .testTag("saved_summaries_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Saved monthly summaries",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dynamic Weight Isolation Filter (Works for Added-Weight Calisthenics, Dumbbells/Barbells, and Bodyweight)
        if (exerciseHistorySets.isNotEmpty() && availableAddedWeights.isNotEmpty()) {
            item {
                val activeAccent = if (hasAddedWeight) CoralOrange else ElectricCyan
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("isolated_weight_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(activeAccent, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasAddedWeight) "ISOLATED ADDED WEIGHT TAG" else "ISOLATED WEIGHT TAG",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = activeAccent
                                )
                            }
                            Text(
                                text = if (hasAddedWeight) "Isolates weighted curve" else "Isolates by logged weight",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Hoisted out of the LazyRow: LazyListScope is not a composable scope
                        val displayWeights = remember(availableAddedWeights, weightUnit) {
                            availableAddedWeights.map { WeightConversion.fromKg(it, weightUnit) }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "All Weights" tag
                            item {
                                val isAllSelected = selectedAddedWeightFilter == null
                                FilterChip(
                                    selected = isAllSelected,
                                    onClick = { viewModel.selectedAddedWeightFilter.value = null },
                                    label = { Text("All Weights", fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeAccent,
                                        selectedLabelColor = DarkBg,
                                        containerColor = DarkSurfaceVariant,
                                        labelColor = TextWhite
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isAllSelected,
                                        borderColor = DarkBorder,
                                        selectedBorderColor = activeAccent
                                    ),
                                    modifier = Modifier.testTag("weight_filter_all")
                                )
                            }

                            items(
                                items = displayWeights,
                                key = { it },
                                contentType = { "weight_filter_chip" }
                            ) { weightVal ->
                                val selectedFilterKg = selectedAddedWeightFilter
                                val isSelected = selectedFilterKg != null &&
                                    WeightConversion.fromKg(selectedFilterKg, weightUnit) == weightVal
                                val formattedNum = formatWeightDisplay(weightVal)

                                // What the value stands for is derived from the logged sets, so a mixed
                                // "+0 / +20 kg" exercise labels every chip correctly.
                                val label = when {
                                    weightVal > 0.0 &&
                                        exerciseHistorySets.any {
                                            WeightConversion.fromKg(it.addedWeight, weightUnit) == weightVal
                                        } -> {
                                        "+$formattedNum $unit"
                                    }
                                    weightVal > 0.0 &&
                                        exerciseHistorySets.any {
                                            WeightConversion.fromKg(it.weight, weightUnit) == weightVal
                                        } -> {
                                        "$formattedNum $unit"
                                    }
                                    // Otherwise it is the body weight the athlete had that day
                                    else -> {
                                        "BW: $formattedNum $unit"
                                    }
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedAddedWeightFilter.value = WeightConversion.toKg(weightVal, weightUnit) },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeAccent,
                                        selectedLabelColor = DarkBg,
                                        containerColor = DarkSurfaceVariant,
                                        labelColor = TextWhite
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = DarkBorder,
                                        selectedBorderColor = activeAccent
                                    ),
                                    modifier = Modifier.testTag("weight_filter_$weightVal")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Metric Selector Tabs (Max Reps / 1RM / Total Volume)
        item {
            val metrics = listOf(
                ProgressMetric.ESTIMATED_1RM,
                ProgressMetric.MAX_REPS,
                ProgressMetric.TOTAL_VOLUME
            )
            val selectedIndex = metrics.indexOf(selectedMetric).coerceAtLeast(0)

            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = DarkSurface,
                contentColor = ElectricCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = ElectricCyan,
                        height = 3.dp
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                metrics.forEachIndexed { index, metric ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.selectedMetric.value = metric },
                        text = {
                            Text(
                                text = metric.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedIndex == index) ElectricCyan else TextMuted
                            )
                        }
                    )
                }
            }
        }

        // Interactive Canvas Line Chart
        item {
            // The axis label has to describe what is being plotted, not the athlete's weight unit.
            //
            // This passed `unit` unconditionally, so the Max Reps graph drew a "kg" axis over a rep
            // count. One-rep-max and total volume are genuinely kilogram values; max reps is not.
            ProgressionLineChart(
                points = chartPoints,
                yAxisUnit = progressUnitLabel(selectedMetric, unit),
                lineColor = when (selectedMetric) {
                    ProgressMetric.ESTIMATED_1RM -> ElectricCyan
                    ProgressMetric.MAX_REPS -> MintGreen
                    ProgressMetric.TOTAL_VOLUME -> VioletPurple
                }
            )
        }

        // Summary Stats Highlights
        if (chartPoints.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val peakValue = chartPoints.maxOfOrNull { it.value } ?: 0.0
                    val latestValue = chartPoints.lastOrNull()?.value ?: 0.0
                    val totalSets = exerciseHistorySets.size

                    val peakStr = if (selectedMetric == ProgressMetric.MAX_REPS) {
                        "${peakValue.toInt()} reps"
                    } else {
                        "${formatWeightDisplay(peakValue)} ${progressUnitLabel(selectedMetric, unit)}"
                    }

                    val latestStr = if (selectedMetric == ProgressMetric.MAX_REPS) {
                        "${latestValue.toInt()} reps"
                    } else {
                        "${formatWeightDisplay(latestValue)} ${progressUnitLabel(selectedMetric, unit)}"
                    }

                    StatBox(
                        title = "All-Time Best",
                        value = peakStr,
                        icon = Icons.Default.TrendingUp,
                        color = GoldenAmber,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Latest",
                        value = latestStr,
                        icon = Icons.Default.Speed,
                        color = MintGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Sets Logged",
                        value = "$totalSets",
                        icon = Icons.Default.Layers,
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Chronological Log History for Exercise
        item {
            Text(
                text = "SESSION HISTORY",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
        }

        if (exerciseHistorySets.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (distinctExercises.isEmpty()) {
                            "No exercises logged. Log a workout and tick the sets you completed to see them here."
                        } else {
                            "No history sets found for this exercise."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(items = groupedDates, key = { it }, contentType = { "history_date_group" }) { dateStr ->
                val setsOnDate = groupedHistory[dateStr].orEmpty()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_history_card_${dateStr.replace(" ", "_")}"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${setsOnDate.size} sets",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            // 3-dot date action menu
                            Box {
                                var showDateMenu by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showDateMenu = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("date_menu_${dateStr.replace(" ", "_")}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Session options",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showDateMenu,
                                    onDismissRequest = { showDateMenu = false },
                                    modifier = Modifier.background(DarkSurfaceVariant)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete All ${setsOnDate.size} Sets for This Date", color = FireRed, fontSize = 13.sp) },
                                        onClick = {
                                            showDateMenu = false
                                            viewModel.deleteSets(setsOnDate)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = FireRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        setsOnDate.forEach { set ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Set ${setPositionById[set.id] ?: set.setNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        modifier = Modifier.width(48.dp)
                                    )

                                    // The load the set actually moved, and the work done with it.
                                    //
                                    // The load is the same text the workout history shows, so the
                                    // same set is never described two different ways. A bodyweight
                                    // set says the body weight it was done at: an empty weight field
                                    // used to read as "+0 kg", which is the opposite of what the
                                    // athlete did.
                                    val bodyweightAtSet =
                                        BodyWeightTimeline.bodyWeightForSet(set, userProfile.currentWeightKg)
                                    val descriptor = when (set.exerciseType) {
                                        ExerciseType.CARDIO -> describeCardioSet(set, weightUnit)
                                        else -> "${WorkoutMetrics.loadLabel(set, bodyweightAtSet, weightUnit)} x ${describeLoggedSetEffort(set)}"
                                    }
                                    Text(
                                        text = descriptor,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteLoggedSet(set) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("delete_set_${set.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Set",
                                        tint = TextMuted.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // The build stamp, at the very bottom of the analytics.
        item(key = "app_version_footer", contentType = "footer") {
            AppVersionFooter()
        }
    }

    // Gemini monthly summary dialog (loading / error / formatted AI report)
    when (val summaryState = monthlySummaryState) {
        is MainViewModel.MonthlySummaryState.Loading -> GeminiSummaryLoadingDialog(
            exerciseName = summaryState.exerciseName,
            onDismiss = { viewModel.dismissMonthlySummary() }
        )

        is MainViewModel.MonthlySummaryState.Success -> GeminiSummaryDialog(
            exerciseName = summaryState.exerciseName,
            windowLabel = summaryState.windowLabel,
            markdown = summaryState.markdown,
            onDismiss = { viewModel.dismissMonthlySummary() }
        )

        is MainViewModel.MonthlySummaryState.Error -> AiUnavailableDialog(
            title = "Summary Unavailable",
            message = summaryState.message,
            onDismiss = { viewModel.dismissMonthlySummary() }
        )

        MainViewModel.MonthlySummaryState.Idle -> Unit
    }

    // The full log of every completed session
    if (showHistoryDialog) {
        WorkoutHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false }
        )
    }

    // Saved summaries popup (list -> detail)
    if (isSavedSummariesOpen) {
        SavedSummariesDialog(
            summaries = savedSummaries,
            weightUnit = weightUnit,
            onOpenSummary = { viewModel.openSavedSummary(it) },
            onDeleteSummary = { viewModel.deleteSavedSummary(it) },
            onDismiss = { viewModel.closeSavedSummaries() }
        )
    }
    openedSavedSummary?.let { summary ->
        GeminiSummaryDialog(
            exerciseName = summary.exerciseName,
            windowLabel = "Generated ${summaryDateFormat.format(Date(summary.createdAt))} • last ${summary.windowDays} days",
            markdown = summary.report,
            onDismiss = { viewModel.closeOpenedSavedSummary() }
        )
    }

    // Shared "AI unavailable" dialog (missing key, rate limit, network)
    aiErrorDialog?.let { errorState ->
        AiUnavailableDialog(
            title = errorState.title,
            message = errorState.message,
            onDismiss = { viewModel.dismissAiError() }
        )
    }
}

private val summaryDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

/**
 * Shown while Gemini is analysing the monthly data of the selected exercise.
 */
@Composable
private fun GeminiSummaryLoadingDialog(
    exerciseName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Gemini Monthly Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            }
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = ElectricCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Analysing the last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days of \"$exerciseName\"...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_monthly_summary_button")
            ) {
                Text("Cancel", color = TextMuted)
            }
        },
        dismissButton = null
    )
}

/**
 * Renders Gemini's markdown report with the app's markdown renderer.
 */
@Composable
private fun GeminiSummaryDialog(
    exerciseName: String,
    windowLabel: String,
    markdown: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("gemini_summary_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = exerciseName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Gemini analysis • $windowLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("close_monthly_summary_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close summary",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("gemini_summary_content")
            ) {
                MarkdownView(markdown = markdown)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("done_monthly_summary_button")
            ) {
                Text("Done", color = ElectricCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = null
    )
}

/**
 * Lists every Gemini monthly summary generated so far. Tapping one opens its full report,
 * every entry can be deleted individually.
 */
@Composable
private fun SavedSummariesDialog(
    summaries: List<GeminiSummary>,
    weightUnit: WeightUnit,
    onOpenSummary: (GeminiSummary) -> Unit,
    onDeleteSummary: (GeminiSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val savedSummariesUnit = weightUnit
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                    .testTag("saved_summaries_dialog"),
                color = DarkSurface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ---------------- Top Bar Header ----------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Saved Monthly Summaries",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${summaries.size} Coach Report${if (summaries.size == 1) "" else "s"} Stored On This Device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_saved_summaries_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close summaries",
                                tint = TextMuted
                            )
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // ---------------- Scrollable Body ----------------
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // What this feature does
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 What Is A Monthly Summary?",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "The IronPulse coach reads your last ${MonthlyExerciseAnalyzer.WINDOW_DAYS} days of logged sets for one exercise and writes a coach-style report: Total volume, best estimated 1RM, progression trend, consistency and what to change next. Every report is stored offline on this device, so you can re-read it any time (they are removed with \"Delete App Data\").",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        if (summaries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No saved summaries yet",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Pick an exercise above and tap \"Gemini Summary\" to generate one.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "TAP A REPORT TO READ IT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextMuted
                            )

                            summaries.forEach { summary ->
                                val trendColor = when {
                                    summary.trendPercent > 2.0 -> MintGreen
                                    summary.trendPercent < -2.0 -> FireRed
                                    else -> TextMuted
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                        .clickable { onOpenSummary(summary) }
                                        .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                                        .testTag("saved_summary_item_${summary.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = summary.exerciseName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TextWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${summaryDateFormat.format(Date(summary.createdAt))} • " +
                                                    "${summary.setCount} sets • ${summary.daysLogged} day${if (summary.daysLogged == 1) "" else "s"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Est. 1RM ${formatWeightDisplay(WeightConversion.fromKg(summary.bestOneRm, savedSummariesUnit))} ${savedSummariesUnit.suffix}  " +
                                                    "trend ${if (summary.trendPercent >= 0) "+" else ""}${formatWeightDisplay(summary.trendPercent)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = trendColor
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSummary(summary) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_saved_summary_${summary.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete summary",
                                            tint = FireRed.copy(alpha = 0.85f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // ---------------- Footer ----------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("done_saved_summaries_button")
                        ) {
                            Text(
                                text = "Done",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = TextWhite,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
