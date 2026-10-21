package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.model.HomeDashboard
import com.example.data.model.RoutineDay
import com.example.data.model.Weekday
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.components.HomeHeatmap
import com.example.ui.components.HomeHeatmapMetric
import com.example.ui.components.HomeStatTile
import com.example.ui.components.TrainingQuotes
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Home dashboard.
 *
 * The landing screen of the app: it answers the questions an athlete has when they open a workout
 * logger, which are what day it is, what is planned for today, whether they can start right now,
 * what they have been doing, and when they last weighed in.
 *
 * Everything on it is read from the same records the rest of the app uses. There is no sample data
 * and no number that is not worked out from the athlete's own history.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSavedRoutines: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenLogWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val weekStartsOnMonday by viewModel.weekStartFlow.collectAsStateWithLifecycle()
    val savedRoutines by viewModel.savedRoutines.collectAsStateWithLifecycle()
    val activeRoutine by viewModel.activeRoutine.collectAsStateWithLifecycle()
    val activeRoutineDays by viewModel.activeRoutineDays.collectAsStateWithLifecycle()
    val activeRoutineWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingWorkoutDraft.collectAsStateWithLifecycle()
    val heatmap by viewModel.homeHeatmap.collectAsStateWithLifecycle()
    val lifetimeDays by viewModel.lifetimeTrainingDays.collectAsStateWithLifecycle()
    val weekTotals by viewModel.weekTotals.collectAsStateWithLifecycle()
    val recentWorkout by viewModel.recentWorkout.collectAsStateWithLifecycle()
    val weightLogs by viewModel.weightLogs.collectAsStateWithLifecycle()
    val workoutHistory by viewModel.workoutHistory.collectAsStateWithLifecycle()

    val now = rememberCurrentTimeMillis()
    val today = Weekday.of(now)
    val dateFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    var heatmapMetric by remember { mutableStateOf(HomeHeatmapMetric.VOLUME) }
    var showTrainingDayInfo by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }

    // The days on which a workout was recorded, so the schedule can tell a finished day from an
    // upcoming one.
    val trainedDays = remember(workoutHistory) {
        workoutHistory
            .map { HomeDashboard.startOfDay(it.session.startTime) }
            .toSet()
    }

    val latestWeight = remember(weightLogs) { weightLogs.maxByOrNull { it.timestamp } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // === Greeting ===
        item {
            Column {
                Text(
                    text = "Welcome to IronPulse, ${userProfile.name.ifBlank { "Athlete" }}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home_greeting")
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.testTag("home_date")
                )
            }
        }

        // === Today's workout ===
        item {
            val routine = activeRoutine
            TodayWorkoutCard(
                activeRoutineName = routine?.name,
                todayDay = activeRoutineDays.firstOrNull { it.weekday == today },
                todayWeekday = today,
                hasRoutines = savedRoutines.isNotEmpty(),
                hasActiveRoutine = routine != null,
                workoutInProgress = activeRoutineWorkout != null || activeSession != null,
                hasPendingDraft = pendingDraft != null,
                onStart = { day ->
                    viewModel.startRoutineWorkout(routine ?: return@TodayWorkoutCard, targetWeekday = day.weekday)
                },
                onChooseRoutine = onOpenSavedRoutines,
                onResume = onOpenLogWorkout
            )
        }

        // === Quick actions ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeActionButton(
                    label = "Saved Routines",
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSavedRoutines
                )
                HomeActionButton(
                    label = "Progress",
                    icon = Icons.Default.ShowChart,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProgress
                )
            }
        }

        // === The week of the scheduled routine ===
        if (activeRoutine != null && activeRoutineDays.isNotEmpty()) {
            item {
                val routine = activeRoutine
                WeekScheduleCard(
                    days = activeRoutineDays,
                    today = today,
                    now = now,
                    weekStartsOnMonday = weekStartsOnMonday,
                    trainedDays = trainedDays,
                    shortDateFormat = shortDateFormat,
                    onStartDay = { day ->
                        viewModel.startRoutineWorkout(routine ?: return@WeekScheduleCard, targetWeekday = day.weekday)
                    }
                )
            }
        }

        // === The numbers ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeStatTile(
                    value = lifetimeDays.toString(),
                    label = "Training days",
                    accent = GoldenAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { showTrainingDayInfo = true }
                )
                HomeStatTile(
                    value = weekTotals.workouts.toString(),
                    label = "Workouts this week",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeStatTile(
                    value = formatMinutes(weekTotals.minutes),
                    label = "Time this week",
                    modifier = Modifier.weight(1f)
                )
                HomeStatTile(
                    // The unit is in the label: the number is kilogram-reps, not a weight.
                    value = WeightConversion.format(weekTotals.volume, unit, decimals = 0, withUnit = false),
                    label = "${unit.suffix}-reps this week",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // === The rolling heat map ===
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_heatmap_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    HomeHeatmap(
                        cells = heatmap,
                        metric = heatmapMetric,
                        onMetricChange = { heatmapMetric = it },
                        weightUnit = unit
                    )
                }
            }
        }

        // === Body weight ===
        item {
            BodyWeightCard(
                latestWeightKg = latestWeight?.weightKg,
                latestWeightAt = latestWeight?.timestamp,
                previousWeightKg = remember(weightLogs) {
                    weightLogs.sortedByDescending { it.timestamp }.getOrNull(1)?.weightKg
                },
                weightUnit = unit,
                now = now,
                shortDateFormat = shortDateFormat,
                onLogWeight = { showWeightDialog = true }
            )
        }

        // === The last workout ===
        item {
            RecentWorkoutCard(
                title = recentWorkout?.session?.title,
                startedAt = recentWorkout?.session?.startTime,
                minutes = recentWorkout?.let {
                    HomeDashboard.minutes(it.session, it.sets)
                } ?: 0,
                setCount = recentWorkout?.sets?.size ?: 0,
                onOpen = onOpenProgress
            )
        }

        // === A line for the day ===
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "TODAY'S LINE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TrainingQuotes.forDay(now),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextWhite,
                        modifier = Modifier.testTag("home_quote")
                    )
                }
            }
        }
    }

    // === What counts as a training day ===
    if (showTrainingDayInfo) {
        AlertDialog(
            onDismissRequest = { showTrainingDayInfo = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Training days",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A workout counts as a training day when both of these are true:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite
                    )
                    Text(
                        text = "• It lasted more than five minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = "• It holds at least two completed sets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Only one day is counted per calendar date, however many workouts " +
                            "you did on it. The total is a lifetime count: it never resets, so a " +
                            "week off or a month off costs you nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrainingDayInfo = false }) {
                    Text("Got it", color = ElectricCyan)
                }
            }
        )
    }

    // === Log a weigh-in ===
    if (showWeightDialog) {
        LogWeightDialog(
            weightUnit = unit,
            onDismiss = { showWeightDialog = false },
            onSave = { kg ->
                viewModel.addWeightLog(kg, "Morning weigh-in")
                showWeightDialog = false
            }
        )
    }
}

/**
 * The card that answers "what am I doing today".
 */
@Composable
private fun TodayWorkoutCard(
    activeRoutineName: String?,
    todayDay: RoutineDay?,
    todayWeekday: Int,
    hasRoutines: Boolean,
    hasActiveRoutine: Boolean,
    workoutInProgress: Boolean,
    hasPendingDraft: Boolean,
    onStart: (RoutineDay) -> Unit,
    onChooseRoutine: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_today_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    text = "TODAY • ${Weekday.name(todayWeekday).uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
            }

            when {
                // A workout is running: the athlete resumes it rather than starting another one.
                workoutInProgress || hasPendingDraft -> {
                    Text(
                        text = "Workout in progress",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Pick up where you left off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    PrimaryHomeButton(text = "Resume Workout", onClick = onResume)
                }

                // Nothing to train and nothing saved yet.
                !hasRoutines -> {
                    Text(
                        text = "No routines yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Create a weekly plan and IronPulse will tell you what to train each day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    PrimaryHomeButton(text = "Create a Routine", onClick = onChooseRoutine)
                }

                // Routines exist, but none of them is the week the athlete is following.
                !hasActiveRoutine -> {
                    Text(
                        text = "Choose your week",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Pick the saved routine you are following this week and it will be " +
                            "planned onto the days of the week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    PrimaryHomeButton(text = "Saved Routines", onClick = onChooseRoutine)
                }

                // Today is a rest day.
                todayDay == null || todayDay.isRestDay -> {
                    Text(
                        text = "Rest day",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "${Weekday.name(todayWeekday)} is a planned rest day in " +
                            "$activeRoutineName. You can still train anything you like.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    PrimaryHomeButton(text = "Choose Another Workout", onClick = onChooseRoutine)
                }

                // Today is a training day.
                else -> {
                    Text(
                        text = todayDay.dayTitle.ifBlank { Weekday.name(todayWeekday) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${todayDay.exercises.size} exercise${if (todayDay.exercises.size == 1) "" else "s"} • $activeRoutineName",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    PrimaryHomeButton(
                        text = "Start Workout",
                        onClick = { onStart(todayDay) },
                        testTag = "home_start_workout_button"
                    )
                }
            }
        }
    }
}

/**
 * The seven days of the scheduled routine.
 */
@Composable
private fun WeekScheduleCard(
    days: List<RoutineDay>,
    today: Int,
    now: Long,
    weekStartsOnMonday: Boolean,
    trainedDays: Set<Long>,
    shortDateFormat: SimpleDateFormat,
    onStartDay: (RoutineDay) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_week_schedule_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "YOUR WEEK",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            days.forEach { day ->
                val isToday = day.weekday == today
                // The date of this weekday in the calendar week the athlete is in. It is worked
                // out from the start of that week, so the dates under the days are the real dates
                // of this week and not the weekday number counted from today.
                val firstWeekday = if (weekStartsOnMonday) Weekday.MONDAY else Weekday.SUNDAY
                val weekStart = HomeDashboard.startOfWeek(now, weekStartsOnMonday)
                val position = (day.weekday - firstWeekday + Weekday.COUNT) % Weekday.COUNT
                val dateOfDay = weekStart + position * com.example.data.model.DAY_MS
                val wasTrained = trainedDays.contains(dateOfDay)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isToday) ElectricCyan.copy(alpha = 0.12f) else DarkSurfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isToday) ElectricCyan else DarkBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = !day.isRestDay) { onStartDay(day) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("home_week_day_${day.weekday}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.width(46.dp)) {
                        Text(
                            text = Weekday.shortName(day.weekday),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isToday) ElectricCyan else TextWhite
                        )
                        Text(
                            text = shortDateFormat.format(Date(dateOfDay)),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (day.isRestDay) {
                                "Rest day"
                            } else {
                                // The focus of the day when it has one, the first exercise of the
                                // day when it does not. Either way it says what is trained, not
                                // just which weekday it is.
                                day.dayTitle.ifBlank {
                                    day.exercises.firstOrNull()?.exerciseName
                                        ?: Weekday.name(day.weekday)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (day.isRestDay) TextMuted else TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                day.isRestDay -> "Nothing planned"
                                wasTrained -> "${day.exercises.size} exercise${if (day.exercises.size == 1) "" else "s"} • done"
                                else -> "${day.exercises.size} exercise${if (day.exercises.size == 1) "" else "s"}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wasTrained) MintGreen else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (wasTrained) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Trained",
                            tint = MintGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The last weigh-in, and the way to add the next one.
 */
@Composable
private fun BodyWeightCard(
    latestWeightKg: Double?,
    latestWeightAt: Long?,
    previousWeightKg: Double?,
    weightUnit: WeightUnit,
    now: Long,
    shortDateFormat: SimpleDateFormat,
    onLogWeight: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_weight_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BODY WEIGHT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (latestWeightKg == null) {
                    Text(
                        text = "No weigh-in yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Record your first one to start the timeline.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                } else {
                    Text(
                        text = WeightConversion.format(latestWeightKg, weightUnit),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        modifier = Modifier.testTag("home_latest_weight")
                    )
                    val change = if (previousWeightKg != null) latestWeightKg - previousWeightKg else null
                    Text(
                        text = buildString {
                            if (latestWeightAt != null) {
                                append(shortDateFormat.format(Date(latestWeightAt)))
                            }
                            if (change != null) {
                                if (isNotEmpty()) append(" • ")
                                val sign = if (change > 0) "+" else ""
                                append("$sign${WeightConversion.format(change, weightUnit)} since last")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Button(
                onClick = onLogWeight,
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("home_log_weight_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = DarkBg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Log Weight",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DarkBg
                )
            }
        }
    }
}

/**
 * The last workout the athlete finished.
 */
@Composable
private fun RecentWorkoutCard(
    title: String?,
    startedAt: Long?,
    minutes: Int,
    setCount: Int,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_workout_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "LAST WORKOUT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
            if (title == null) {
                Text(
                    text = "Nothing logged yet",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextWhite
                )
                Text(
                    text = "Finish a workout and it shows up here with its time and its sets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                if (startedAt != null) {
                                    append(SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(startedAt)))
                                }
                                append(" • ")
                                append(formatMinutes(minutes))
                                append(" • ")
                                append("$setCount sets")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * A quick action button.
 */
@Composable
private fun HomeActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("home_action_${label.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The main button of a card.
 */
@Composable
private fun PrimaryHomeButton(
    text: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = DarkBg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = DarkBg
        )
    }
}

/**
 * The quick weigh-in.
 *
 * The value is typed in the athlete's own unit and stored in kilograms, exactly like the weigh-in
 * on the Profile screen: one weight system, one place it is converted.
 */
@Composable
private fun LogWeightDialog(
    weightUnit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Log Body Weight",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your weigh-in in ${weightUnit.suffix}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    placeholder = { Text("e.g. 77.5", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_weight_input")
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = FireRed,
                        modifier = Modifier.testTag("home_weight_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = WeightConversion.parseToKg(input, weightUnit)
                    if (parsed == null) {
                        error = "That is not a weight IronPulse can use."
                    } else {
                        onSave(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("home_weight_save_button")
            ) {
                Text("Save", color = DarkBg, fontWeight = FontWeight.Bold)
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
 * The current time, refreshed as the day turns over and whenever the screen comes back.
 *
 * A dashboard that still said yesterday's date after midnight would be quietly wrong, so the value
 * is never captured once and left there.
 */
@Composable
fun rememberCurrentTimeMillis(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lifecycleOwner) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            // Wake up when the day turns over, not every minute.
            val millisToNextDay = HomeDashboard.startOfDay(current) +
                com.example.data.model.DAY_MS - current
            delay(millisToNextDay.coerceAtLeast(1_000L))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                now = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return now
}

/** Minutes written the way an athlete reads them: "45 min" or "1h 05m". */
private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes < 60 -> "$minutes min"
    else -> "%dh %02dm".format(minutes / 60, minutes % 60)
}
