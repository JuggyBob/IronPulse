package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.describeCardioSet
import com.example.data.model.ExerciseType
import com.example.data.model.describeLoggedSetEffort
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.LoggedSet
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
 * Enlarged, comprehensive Workout History Dialog.
 * Supports:
 * - Search by workout name or notes
 * - Date filtering (Before a date, On a specific date, After a date)
 * - Built-in calendar picker capped at current system date
 * - Click on workout title or Info button to open full Workout Day details (all sets, reps, weight)
 */
@Composable
fun WorkoutHistoryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    var selectedSessionForDetail by remember { mutableStateOf<WorkoutSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<WorkoutSession?>(null) }

    // Search query & Date filtering state
    var searchQuery by remember { mutableStateOf("") }
    var dateFilterMode by remember { mutableStateOf(HistoryDateFilterMode.ALL) }
    var selectedDateMillis by remember {
        mutableStateOf(System.currentTimeMillis())
    }
    var showCalendarPicker by remember { mutableStateOf(false) }

    // Date formatter
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
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
        val end = start + (24 * 60 * 60 * 1000L) - 1L
        Pair(start, end)
    }

    // Filtered sessions
    val filteredSessions by remember(allSessions, searchQuery, dateFilterMode, startOfSelectedDay, endOfSelectedDay) {
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

                matchesQuery && matchesDate
            }
        }
    }

    // Back handler for detail sub-view
    BackHandler(enabled = selectedSessionForDetail != null) {
        selectedSessionForDetail = null
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = selectedSessionForDetail == null
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .testTag("workout_history_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            if (selectedSessionForDetail != null) {
                // Day details view with all exercises, sets, reps, weight
                WorkoutDayDetailView(
                    session = selectedSessionForDetail!!,
                    viewModel = viewModel,
                    onBack = { selectedSessionForDetail = null }
                )
            } else {
                // History List View with Search and Date Filters
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
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

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_workout_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
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

                    // Date Filter Modes Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HistoryDateFilterMode.entries.forEach { mode ->
                            val isSelected = dateFilterMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    dateFilterMode = mode
                                    if (mode != HistoryDateFilterMode.ALL) {
                                        showCalendarPicker = true
                                    }
                                },
                                label = {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = DarkBg,
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = DarkBorder,
                                    selectedBorderColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("date_filter_chip_${mode.name}")
                            )
                        }
                    }

                    // Active Date Filter Badge if not ALL
                    if (dateFilterMode != HistoryDateFilterMode.ALL) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable { showCalendarPicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${dateFilterMode.label}: ${shortDateFormat.format(Date(selectedDateMillis))}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "(Tap to change)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            IconButton(
                                onClick = { dateFilterMode = HistoryDateFilterMode.ALL },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear filter", tint = TextMuted, modifier = Modifier.size(14.dp))
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
                                    text = if (allSessions.isEmpty()) "No past workouts logged yet." else "No workouts match your search or date filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isNotBlank() || dateFilterMode != HistoryDateFilterMode.ALL) {
                                    TextButton(
                                        onClick = {
                                            searchQuery = ""
                                            dateFilterMode = HistoryDateFilterMode.ALL
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
                                        .clickable { selectedSessionForDetail = session }
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
                                                    .clickable { selectedSessionForDetail = session }
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Close Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = DarkBg, fontWeight = FontWeight.Bold)
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
                showCalendarPicker = false
            },
            onDismiss = { showCalendarPicker = false }
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
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed)
                ) {
                    Text("Delete", color = TextWhite, fontWeight = FontWeight.Bold)
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
                                    isSelected -> DarkBg
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
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
            ) {
                Text("Select Date", color = DarkBg, fontWeight = FontWeight.Bold)
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
 * Detailed Workout Day Page View.
 * Displays:
 * - Workout Title, Date & Time, and Diary Breakthrough / Notes
 * - Complete list of exercises done for that specific workout day ONLY
 * - Each exercise formatted with sets, reps, weight, and completion state
 */
@Composable
fun WorkoutDayDetailView(
    session: WorkoutSession,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val setsForSession: List<LoggedSet> by viewModel.getSetsForSession(session.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Body weight for the set rows: taken from the athlete profile stat, which is always present.
    // Weigh-ins are optional and unrelated to what the athlete actually lifted.
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val bodyWeightAt = remember(userProfile.currentWeightKg) {
        { _: Long -> BodyWeightTimeline.currentWeightOrNull(userProfile.currentWeightKg) }
    }
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

    val totalVolumeKg = remember(setsForSession) {
        setsForSession.sumOf { set ->
            val totalWeight = set.weight + set.addedWeight
            set.reps * totalWeight
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("VOLUME", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                                Text(
                                    text = "${formatWeightDisplay(WeightConversion.fromKg(totalVolumeKg, weightUnit))} $unit",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = GoldenAmber
                                )
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
                items(groupedExercises.entries.toList(), key = { it.key }) { (exerciseName, sets) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Exercise Header
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
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = exerciseName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextWhite
                                    )
                                }

                                Surface(
                                    color = MintGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${sets.size} Sets",
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
                                Text("SET", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                                Text("REPS / DURATION", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text("WEIGHT", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                Text("STATUS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Sets Rows
                            sets.forEachIndexed { idx, set ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Set Number
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextWhite,
                                        modifier = Modifier.width(42.dp)
                                    )

                                    // Reps or Hold Duration
                                    Text(
                                        text = describeLoggedSetEffort(set),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextWhite,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Weight: always the real load that was lifted, never a bare
                                    // "Bodyweight" label. A cardio set has no load at all, so it takes
                                    // this column for its distance and time instead, which keeps the
                                    // run's numbers in the history list rather than only in the
                                    // Progress tab.
                                    val bodyweightAtSet = bodyWeightAt(set.timestamp)
                                    val weightText = if (set.exerciseType == ExerciseType.CARDIO) {
                                        describeCardioSet(set, weightUnit)
                                    } else when {
                                        set.addedWeight > 0.0 -> {
                                            // +X kg, plus the body weight that applied back then
                                            val bwText = if (bodyweightAtSet != null) {
                                                " (+${formatWeightDisplay(WeightConversion.fromKg(bodyweightAtSet, weightUnit))} $unit BW)"
                                            } else {
                                                ""
                                            }
                                            "+${formatWeightDisplay(WeightConversion.fromKg(set.addedWeight, weightUnit))} $unit$bwText"
                                        }
                                        set.weight > 0.0 -> "${formatWeightDisplay(WeightConversion.fromKg(set.weight, weightUnit))} $unit"
                                        bodyweightAtSet != null -> "${formatWeightDisplay(WeightConversion.fromKg(bodyweightAtSet, weightUnit))} $unit BW"
                                        // No weigh-in existed on that day: never borrow today's weight
                                        else -> "Own body weight"
                                    }
                                    Text(
                                        text = weightText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (
                                                set.exerciseType == ExerciseType.CARDIO ||
                                                set.addedWeight > 0.0 ||
                                                set.weight > 0.0
                                            ) {
                                                ElectricCyan
                                            } else {
                                                TextMuted
                                            }
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        textAlign = TextAlign.End
                                    )

                                    // Status
                                    Box(
                                        modifier = Modifier.width(48.dp),
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

                                if (idx < sets.size - 1) {
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

        Spacer(modifier = Modifier.height(10.dp))

        // Return Button
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back to All Workouts", fontWeight = FontWeight.Bold)
        }
    }
}

/** Local midnight of a timestamp: the key the workout list is grouped by. */
private fun dayKeyOf(timestamp: Long): Long {
    val dayMs = 24L * 60L * 60L * 1000L
    return timestamp - Math.floorMod(timestamp, dayMs)
}

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
