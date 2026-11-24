package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.describeCardioSet
import com.example.data.model.ExerciseType
import com.example.data.model.describeLoggedSetEffort
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.EffortDisplay
import com.example.data.model.EffortScale
import com.example.data.model.LocalDateKey
import com.example.data.model.LoggedSet
import com.example.data.model.DropSetRules
import com.example.data.model.WorkoutMetrics
import com.example.data.model.WorkoutSession
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
import com.example.ui.theme.OnAccent
import com.example.ui.theme.OnDanger
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Filter modes for workout history date searching.
 */
enum class HistoryDateFilterMode(val label: String) {
    ALL("All Dates"),
    ON_DATE("On Date"),
    BEFORE_DATE("Before Date"),
    AFTER_DATE("After Date")
}

/**
 * Formats a weight without trailing ",0" noise (e.g. 80.0 -> "80", 82.5 -> "82.5").
 */
private fun formatWeightDisplay(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toInt().toString() else String.format(Locale.getDefault(), "%.1f", weight)

/**
 * Full-screen Workout History destination.
 *
 * The list owns the search/date/exercise filter state and opens a workout as a separate route,
 * so the old dialog and its nested detail swap are gone. Filter state is saveable so returning
 * from a workout restores the exact list the athlete left.
 */
@Composable
fun WorkoutHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val loggedExercises by viewModel.distinctLoggedExercises.collectAsStateWithLifecycle()
    val workoutSummaries by viewModel.workoutSummaries.collectAsStateWithLifecycle()
    val summaryByWorkout = remember(workoutSummaries) {
        workoutSummaries.associateBy { it.workoutId }
    }
    var sessionToDelete by remember { mutableStateOf<WorkoutSession?>(null) }

    // Search query & Date filtering state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var dateFilterMode by rememberSaveable { mutableStateOf(HistoryDateFilterMode.ALL) }
    var selectedDateMillis by rememberSaveable {
        mutableStateOf(System.currentTimeMillis())
    }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var pendingDateMode by remember { mutableStateOf<HistoryDateFilterMode?>(null) }
    var selectedExercise by rememberSaveable { mutableStateOf<String?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }



    // Date formatter
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateFilterLabel = remember(dateFilterMode, selectedDateMillis, shortDateFormat) {
        if (dateFilterMode == HistoryDateFilterMode.ALL) {
            "All dates"
        } else {
            "${dateFilterMode.label}: ${shortDateFormat.format(Date(selectedDateMillis))}"
        }
    }
    // Compute start and end of selected day
    val (startOfSelectedDay, endOfSelectedDay) = remember(selectedDateMillis) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        // Move by a calendar day, not a fixed 24 h, so DST changes do not move the boundary.
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis - 1L
        Pair(start, end)
    }

    // Session membership for the exercise filter. No set payload is needed on the list screen.
    val exerciseSessionIds by remember(selectedExercise) {
        val exercise = selectedExercise
        if (exercise.isNullOrBlank()) flowOf(emptySet<Long>())
        else viewModel.getSessionIdsForExercise(exercise)
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    // Filtered sessions
    val filteredSessions by remember(
        allSessions,
        searchQuery,
        dateFilterMode,
        startOfSelectedDay,
        endOfSelectedDay,
        selectedExercise,
        exerciseSessionIds
    ) {
        derivedStateOf {
            allSessions.filter { session ->
                // Name / Note query match
                val matchesQuery = searchQuery.isBlank() ||
                    session.title.contains(searchQuery.trim(), ignoreCase = true) ||
                    session.notes.contains(searchQuery.trim(), ignoreCase = true)

                // Date match
                val matchesDate = when (dateFilterMode) {
                    HistoryDateFilterMode.ALL -> true
                    HistoryDateFilterMode.ON_DATE -> session.startTime in startOfSelectedDay..endOfSelectedDay
                    HistoryDateFilterMode.BEFORE_DATE -> session.startTime < startOfSelectedDay
                    HistoryDateFilterMode.AFTER_DATE -> session.startTime > endOfSelectedDay
                }

                val matchesExercise = selectedExercise.isNullOrBlank() ||
                    session.id in exerciseSessionIds

                matchesQuery && matchesDate && matchesExercise
            }
        }
    }

    // The list is grouped by training day: a separator line introduces each day, newest first.
    //
    // The separators and the session cards have to live in ONE list: a LazyColumn lays out every
    // entry of one items{} block before the next block, so two blocks would stack all the headers
    // above all the cards instead of interleaving them.
    val dayLabelFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()) }
    val historyRows = remember(filteredSessions, dayLabelFormat) {
        val sorted = filteredSessions.sortedByDescending { it.startTime }
        sorted.flatMap { session ->
            val previous = sorted.indexOf(session).takeIf { it > 0 }?.let { sorted[it - 1] }
            val startsNewDay = previous == null || dayKeyOf(previous.startTime) != dayKeyOf(session.startTime)
            buildList {
                if (startsNewDay) {
                    add(
                        WorkoutHistoryRow.DayHeader(
                            label = dayLabelFormat.format(Date(session.startTime)).uppercase(),
                            key = dayKeyOf(session.startTime)
                        )
                    )
                }
                add(WorkoutHistoryRow.SessionCard(session))
            }
        }
    }

    // Full-screen list. The dock overlays it, so the scrolling area reserves bottom space.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("workout_history_screen")
    ) {
                    // Header Row: back on the left, matching every other pushed screen.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("back_from_workout_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = ElectricCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Workout History",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Text(
                                text = "${filteredSessions.size} of ${allSessions.size} workouts",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search by Workout Name Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_workout_history_input"),
                        placeholder = { Text("Search workout by name or notes...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Date filter: one dropdown, with the same calendar dialog used elsewhere.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { showDateMenu = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("history_date_filter_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (dateFilterMode == HistoryDateFilterMode.ALL) DarkBorder
                                    else ElectricCyan.copy(alpha = 0.55f)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = if (dateFilterMode == HistoryDateFilterMode.ALL) TextMuted else ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateFilterLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (dateFilterMode == HistoryDateFilterMode.ALL) TextMuted else ElectricCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (dateFilterMode != HistoryDateFilterMode.ALL) {
                                IconButton(
                                    onClick = {
                                        dateFilterMode = HistoryDateFilterMode.ALL
                                        pendingDateMode = null
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("clear_history_date_filter")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear date filter",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showDateMenu,
                            onDismissRequest = { showDateMenu = false },
                            modifier = Modifier.width(190.dp),
                            containerColor = DarkSurface
                        ) {
                            HistoryDateFilterMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            color = if (dateFilterMode == mode) ElectricCyan else TextWhite,
                                            fontWeight = if (dateFilterMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        if (dateFilterMode == mode) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        showDateMenu = false
                                        if (mode == HistoryDateFilterMode.ALL) {
                                            dateFilterMode = HistoryDateFilterMode.ALL
                                            pendingDateMode = null
                                        } else {
                                            pendingDateMode = mode
                                            showCalendarPicker = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Exercise filter: complete sessions that contain the chosen exercise.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showExercisePicker = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("history_exercise_filter_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selectedExercise == null) DarkBorder else ElectricCyan.copy(alpha = 0.55f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = if (selectedExercise == null) TextMuted else ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedExercise ?: "All exercises",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedExercise == null) TextMuted else ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (selectedExercise != null) {
                            IconButton(
                                onClick = { selectedExercise = null },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_history_exercise_filter")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear exercise filter",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sessions List
                    if (filteredSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (allSessions.isEmpty()) "No past workouts logged yet." else "No workouts match your search, date or exercise filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isNotBlank() || dateFilterMode != HistoryDateFilterMode.ALL) {
                                    TextButton(
                                        onClick = {
                                            searchQuery = ""
                                            dateFilterMode = HistoryDateFilterMode.ALL
                                            selectedExercise = null
                                        }
                                    ) {
                                        Text("Clear Filters", color = ElectricCyan)
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // One interleaved list: a day separator, then that day's cards
                            items(
                                items = historyRows,
                                key = { it.rowKey },
                                contentType = { "history_row" }
                            ) { row ->
                                when (row) {
                                    is WorkoutHistoryRow.DayHeader -> DaySeparatorRow(row)
                                    is WorkoutHistoryRow.SessionCard -> {
                                        val session = row.session
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenSession(session.id) }
                                        .testTag("session_card_${session.id}"),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DarkBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Clickable Workout Title
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onOpenSession(session.id) }
                                            ) {
                                                Text(
                                                    text = session.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = TextWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(session.startTime)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                            }

                                            // Saved AI summary. Disabled when this workout has none.
                                            IconButton(
                                                onClick = { viewModel.openWorkoutSummary(session.id) },
                                                enabled = summaryByWorkout[session.id] != null,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("workout_summary_${session.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AssignmentTurnedIn,
                                                    contentDescription = if (summaryByWorkout[session.id] != null) {
                                                        "Open AI workout summary"
                                                    } else {
                                                        "No AI summary for this workout"
                                                    },
                                                    tint = if (summaryByWorkout[session.id] != null) {
                                                        ElectricCyan
                                                    } else {
                                                        TextMuted.copy(alpha = 0.35f)
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = { sessionToDelete = session },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Session",
                                                    tint = FireRed.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Notes / Breakthrough preview
                                        if (session.notes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = DarkBg.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = session.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = ElectricCyan,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Tap prompt to view exercises and sets
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Tap to view exercises & sets",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MintGreen,
                                                fontSize = 11.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MintGreen,
                                                modifier = Modifier.size(14.dp)
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

    // Built-in Calendar Picker Dialog (cannot exceed current system date)
    if (showCalendarPicker) {

        CalendarPickerDialog(
            initialDateMillis = selectedDateMillis,
            onDateSelected = { pickedMillis ->
                selectedDateMillis = pickedMillis
                pendingDateMode?.let { dateFilterMode = it }
                pendingDateMode = null
                showCalendarPicker = false
            },
            onDismiss = {
                pendingDateMode = null
                showCalendarPicker = false
            }
        )
    }

    if (showExercisePicker) {
        ExerciseHistoryFilterDialog(
            exercises = loggedExercises,
            selected = selectedExercise,
            onSelect = {
                selectedExercise = it
                showExercisePicker = false
            },
            onDismiss = { showExercisePicker = false }
        )
    }

    // Delete Confirmation Dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkSurface,
            title = { Text("Delete Workout Session", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${sessionToDelete!!.title}' and all sets recorded for this workout?",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(sessionToDelete!!)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed, contentColor = OnDanger)
                ) {
                    Text("Delete", color = OnDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * Exercise picker for Workout History. It searches only the exercises that actually appear in
 * completed sets, so every result is guaranteed to produce at least one session.
 */
@Composable
private fun ExerciseHistoryFilterDialog(
    exercises: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(exercises, query) {
        val needle = query.trim()
        if (needle.isBlank()) exercises
        else exercises.filter { it.contains(needle, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Filter by exercise", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_exercise_search_input"),
                    placeholder = { Text("Search exercise...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear exercise search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )

                Surface(
                    color = if (selected == null) ElectricCyan.copy(alpha = 0.14f) else DarkBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (selected == null) ElectricCyan.copy(alpha = 0.55f) else DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                ) {
                    Text(
                        text = "All exercises",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected == null) ElectricCyan else TextWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }

                if (matches.isEmpty()) {
                    Text(
                        text = "No logged exercise matches \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(matches, key = { it }) { exercise ->
                            Surface(
                                color = if (exercise == selected) {
                                    ElectricCyan.copy(alpha = 0.14f)
                                } else {
                                    DarkBg
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (exercise == selected) {
                                        ElectricCyan.copy(alpha = 0.55f)
                                    } else {
                                        DarkBorder
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(exercise) }
                            ) {
                                Text(
                                    text = exercise,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (exercise == selected) ElectricCyan else TextWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

/**
 * Built-in Calendar Dialog that does not allow selecting dates past the current system date.
 */
@Composable
fun CalendarPickerDialog(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val systemToday = remember { Calendar.getInstance() }
    val todayYear = systemToday.get(Calendar.YEAR)
    val todayMonth = systemToday.get(Calendar.MONTH)
    val todayDay = systemToday.get(Calendar.DAY_OF_MONTH)

    // Current selected day, month, year
    var selectedYear by remember(initialDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
        mutableIntStateOf(cal.get(Calendar.YEAR))
    }
    var selectedMonth by remember(initialDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
        mutableIntStateOf(cal.get(Calendar.MONTH))
    }
    var selectedDay by remember(initialDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
        mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH))
    }

    // Current viewed month & year
    var viewedYear by remember(initialDateMillis) {
        mutableIntStateOf(selectedYear)
    }
    var viewedMonth by remember(initialDateMillis) {
        mutableIntStateOf(selectedMonth)
    }

    // Cannot advance past today's month/year
    val canGoNextMonth = remember(viewedYear, viewedMonth, todayYear, todayMonth) {
        if (viewedYear < todayYear) true
        else viewedMonth < todayMonth
    }

    val monthName = remember(viewedMonth) {
        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, viewedMonth) }
        SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
    }

    // Calculate calendar grid for the viewed month
    val daysInMonth = remember(viewedYear, viewedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(viewedYear, viewedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewedYear)
            set(Calendar.MONTH, viewedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday, 1 = Monday, etc.
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Date",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Text(
                    text = "Max: Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Month & Year Selector with Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (viewedMonth == 0) {
                                viewedMonth = 11
                                viewedYear -= 1
                            } else {
                                viewedMonth -= 1
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = TextWhite)
                    }

                    Text(
                        text = "$monthName $viewedYear",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )

                    IconButton(
                        onClick = {
                            if (canGoNextMonth) {
                                if (viewedMonth == 11) {
                                    viewedMonth = 0
                                    viewedYear += 1
                                } else {
                                    viewedMonth += 1
                                }
                            }
                        },
                        enabled = canGoNextMonth
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next Month",
                            tint = if (canGoNextMonth) TextWhite else TextMuted.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Day of Week Header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayHeader ->
                        Text(
                            text = dayHeader,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Days Grid (6 rows max x 7 columns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Empty leading slots
                    items(firstDayOfWeek) {
                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    // Days of month
                    items(daysInMonth) { index ->
                        val day = index + 1
                        val isFuture = if (viewedYear > todayYear) {
                            true
                        } else if (viewedYear == todayYear && viewedMonth > todayMonth) {
                            true
                        } else if (viewedYear == todayYear && viewedMonth == todayMonth) {
                            day > todayDay
                        } else {
                            false
                        }

                        val isSelected = (day == selectedDay && viewedMonth == selectedMonth && viewedYear == selectedYear)
                        val isToday = (day == todayDay && viewedMonth == todayMonth && viewedYear == todayYear)

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> ElectricCyan
                                        isToday -> ElectricCyan.copy(alpha = 0.2f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isToday && !isSelected) 1.dp else 0.dp,
                                    color = if (isToday && !isSelected) ElectricCyan else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable(enabled = !isFuture) {
                                    selectedDay = day
                                    selectedMonth = viewedMonth
                                    selectedYear = viewedYear
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$day",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when {
                                    isSelected -> OnAccent
                                    isFuture -> TextMuted.copy(alpha = 0.25f)
                                    isToday -> ElectricCyan
                                    else -> TextWhite
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resultCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth)
                        set(Calendar.DAY_OF_MONTH, selectedDay.coerceIn(1, daysInMonth))
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onDateSelected(resultCal.timeInMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OnAccent)
            ) {
                Text("Select Date", color = OnAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

/**
 * Full-screen workout detail route. The session is resolved from the existing allSessions flow,
 * so deleting a session while the detail is open cannot leave a stale copy on screen.
 */
@Composable
fun WorkoutDayDetailScreen(
    viewModel: MainViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val session = remember(allSessions, sessionId) {
        allSessions.firstOrNull { it.id == sessionId }
    }
    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to History",
                    tint = ElectricCyan
                )
            }
            Text(
                text = "This workout is no longer available.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        WorkoutDayDetailContent(session = session, viewModel = viewModel, onBack = onBack)
    }
}

/**
 * Detailed Workout Day Page View.
 * Displays:
 * - Workout Title, Date & Time, and Diary Breakthrough / Notes
 * - Complete list of exercises done for that specific workout day ONLY
 * - Each exercise formatted with sets, reps, weight, and completion state
 */
@Composable
private fun WorkoutDayDetailContent(
    session: WorkoutSession,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val setsForSession: List<LoggedSet> by viewModel.getSetsForSession(session.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // The body weight for the set rows.
    //
    // Each set carries the body weight it was done at, so a set from last month keeps its own
    // weight after a weigh-in today. Reading the current profile weight instead is what made a
    // new weigh-in rewrite the history of everything logged before it.
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val effortDisplay by viewModel.appSettings
        .map { it.effortDisplay }
        .collectAsStateWithLifecycle(initialValue = EffortDisplay.RIR)
    val weightUnit = WeightUnit.fromLabel(userProfile.preferredUnit)
    val unit = weightUnit.suffix

    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a", Locale.getDefault()) }

    // Group sets by exercise name (preserving execution order)
    val groupedExercises = remember(setsForSession) {
        val map = LinkedHashMap<String, MutableList<LoggedSet>>()
        for (set in setsForSession) {
            map.getOrPut(set.exerciseName) { mutableListOf() }.add(set)
        }
        map
    }

    // A1/A2 is derived from the same group id stored on the recorded sets. Supersets are shown
    // with a paperclip badge in each exercise header instead of indenting one exercise under the
    // other, which used to consume a large part of the row on phones.
    val supersetLabels = remember(setsForSession) {
        val groups = LinkedHashMap<String, LinkedHashSet<String>>()
        setsForSession.forEach { set ->
            val groupId = set.supersetGroupId
            if (!set.isDropSet && !groupId.isNullOrBlank()) {
                groups.getOrPut(groupId) { LinkedHashSet() }.add(set.exerciseName)
            }
        }
        buildMap {
            groups.values.filter { it.size == 2 }.forEach { names ->
                names.forEachIndexed { index, name ->
                    put(name, "A${index + 1}")
                }
            }
        }
    }

    // The volume of the session, in kilogram-reps.
    //
    // The load a set actually moved is what counts: the external load for a barbell set, the body
    // weight plus the added load for a weighted bodyweight set, and the body weight for a plain
    // bodyweight set. Counting only the external load made every bodyweight session read as zero
    // volume, which is the opposite of what the athlete did.
    //
    // The body weight is the one the set carries, so it is the weight the athlete had at the time
    // of that set. Reading today's weight instead would rewrite the volume of every session ever
    // logged the moment the athlete weighs in.
    val totalVolumeKg = remember(setsForSession, userProfile.currentWeightKg) {
        WorkoutMetrics.sessionVolume(setsForSession) { set ->
            BodyWeightTimeline.bodyWeightForSet(set, userProfile.currentWeightKg)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_to_history_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to History",
                    tint = ElectricCyan
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(session.startTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Diary Breakthrough / Workout Notes Card
            if (session.notes.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ElectricCyan, CircleShape)
                                )
                                Text(
                                    text = "Diary Breakthrough & Notes",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = session.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextWhite,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Summary Stats Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("EXERCISES", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "${groupedExercises.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan
                            )
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("TOTAL SETS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "${setsForSession.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MintGreen
                            )
                        }
                    }

                    if (totalVolumeKg > 0.0) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("VOLUME", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                                // The number on its own line and the unit under it: "1280 kg-reps"
                                // does not fit on one line in a tile this narrow.
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = formatWeightDisplay(WeightConversion.fromKg(totalVolumeKg, weightUnit)),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = GoldenAmber,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "$unit-reps",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = TextMuted,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        // The number is the metric. If the tile is very narrow,
                                        // the unit gives way first instead of hiding the value.
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Exercise & Sets Section Header
            item {
                Text(
                    text = "Exercises & Sets Performed",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (groupedExercises.isEmpty()) {
                item {
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No individual sets or exercises logged for this workout session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(
                    groupedExercises.entries.toList(),
                    key = { _, entry -> entry.key }
                ) { index, (exerciseName, sets) ->
                    val supersetLabel = supersetLabels[exerciseName]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (supersetLabel != null) ElectricCyan.copy(alpha = 0.42f) else DarkBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Exercise Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricCyan
                                    )
                                }
                                Text(
                                    text = exerciseName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (supersetLabel != null) {
                                    SupersetHistoryBadge(supersetLabel)
                                }
                                Surface(
                                    color = MintGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f))
                                ) {
                                    val mainCount = sets.count { !it.isDropSet }
                                    val dropCount = sets.count { it.isDropSet }
                                    val mainLabel = "$mainCount Set${if (mainCount == 1) "" else "s"}"
                                    Text(
                                        text = if (dropCount > 0) {
                                            val dropLabel = "$dropCount Drop${if (dropCount == 1) "" else "s"}"
                                            "$mainLabel + $dropLabel"
                                        } else {
                                            mainLabel
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MintGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sets Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SET", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                                Text("REPS / DURATION · LOAD", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text("DONE", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Main sets and their branches. orderedSetRows is shared with the
                            // active workout and Progress, so a child can never be shown as an
                            // unrelated top-level set.
                            val orderedRows = orderedSetRows(sets)
                            orderedRows.forEachIndexed { idx, ordered ->
                                if (ordered.isDrop) {
                                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                        // The set label column is 8 dp padding + 42 dp text. The
                                        // branch grows from its centre, not from the card edge.
                                        DropSetBranchConnector(
                                            isLastChild = ordered.isLastChild,
                                            width = 34.dp,
                                            gutter = 23.dp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            HistoryRecordedSetRow(
                                                set = ordered.set,
                                                label = "D${ordered.set.dropIndex}",
                                                isDrop = true,
                                                currentBodyWeightKg = userProfile.currentWeightKg,
                                                weightUnit = weightUnit,
                                                effortDisplay = effortDisplay
                                            )
                                        }
                                    }
                                } else {
                                    HistoryRecordedSetRow(
                                        set = ordered.set,
                                        label = "#${ordered.set.setNumber}",
                                        isDrop = false,
                                        currentBodyWeightKg = userProfile.currentWeightKg,
                                        weightUnit = weightUnit,
                                        effortDisplay = effortDisplay,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }

                                if (idx < orderedRows.lastIndex) {
                                    HorizontalDivider(
                                        color = DarkBorder.copy(alpha = 0.35f),
                                        thickness = 0.5.dp
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

/** Coloured paperclip badge that marks both members of a recorded superset. */
@Composable
private fun SupersetHistoryBadge(label: String) {
    Surface(
        color = ElectricCyan.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Superset $label",
                tint = ElectricCyan,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }
    }
}

/**
 * One recorded set in workout history.
 *
 * Main sets keep the original row layout. Drop sets reuse the same columns, with a D-number and
 * the branch drawn by the caller; nothing about the values or status icons differs.
 */
@Composable
private fun HistoryRecordedSetRow(
    set: LoggedSet,
    label: String,
    isDrop: Boolean,
    currentBodyWeightKg: Double,
    weightUnit: WeightUnit,
    effortDisplay: EffortDisplay,
    modifier: Modifier = Modifier
) {
    val effortText = WorkoutMetrics.recordedEffortLabel(set, weightUnit) +
        (set.effortRpe?.let { rpe ->
            " · " + EffortScale.display(rpe, effortDisplay, set.reps > 0)
        } ?: "")
    val hasRecordedEffort = effortText != "-"
    val bodyweightAtSet = BodyWeightTimeline.bodyWeightForSet(set, currentBodyWeightKg)
    val loadText = WorkoutMetrics.loadLabel(set, bodyweightAtSet, weightUnit)
    val showLoadAsSecondary = hasRecordedEffort &&
        !(set.exerciseType == ExerciseType.CARDIO && set.weight <= 0.0 && set.addedWeight <= 0.0) &&
        loadText.isNotBlank()
    val secondaryColor = if (
        set.distanceKm > 0.0 ||
        set.holdDurationSeconds > 0 ||
        set.addedWeight > 0.0 ||
        set.weight > 0.0
    ) {
        ElectricCyan
    } else {
        TextMuted
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isDrop) ElectricCyan else TextWhite,
            modifier = Modifier.width(34.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (set.isWarmup) {
                    WarmupBadge(compact = true)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (hasRecordedEffort) effortText else loadText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite,
                    maxLines = 2
                )
            }
            if (showLoadAsSecondary) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = loadText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (set.isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MintGreen,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text("-", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Timezone-stable local calendar key the workout list is grouped by. */
private fun dayKeyOf(timestamp: Long): Long = LocalDateKey.keyOf(timestamp)

/**
 * One row of the workout history list.
 *
 * A day separator and a session card cannot be emitted from two separate items {} blocks: a
 * LazyColumn renders every entry of one block before the next one, so the headers would all pile up
 * above the cards. They are therefore interleaved into a single list.
 */
private sealed interface WorkoutHistoryRow {
    val rowKey: String

    data class DayHeader(val label: String, val key: Long) : WorkoutHistoryRow {
        override val rowKey: String get() = "day_header_$key"
    }

    data class SessionCard(val session: WorkoutSession) : WorkoutHistoryRow {
        override val rowKey: String get() = "session_card_${session.id}"
    }
}

/**
 * A full separator line naming the training day, so one day's log is never read as part of the
 * next one.
 */
@Composable
private fun DaySeparatorRow(header: WorkoutHistoryRow.DayHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
            .testTag("day_separator_${header.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = header.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        HorizontalDivider(
            color = ElectricCyan.copy(alpha = 0.35f),
            thickness = 1.dp,
            modifier = Modifier.weight(1f)
        )
    }
}
