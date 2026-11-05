package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.ExerciseType
import com.example.data.model.HomeDashboard
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineDay
import com.example.data.model.StreakDayStatus
import com.example.data.model.Weekday
import com.example.data.model.WorkoutStreak
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.data.model.WorkoutMetrics
import com.example.data.model.describeCardioSet
import com.example.data.model.describeLoggedSetEffort
import com.example.ui.components.AppVersionFooter
import com.example.ui.components.HomeHeatmap
import com.example.ui.components.HomeHeatmapMetric
import com.example.ui.components.HomePhotoWidget
import com.example.ui.components.HomeStatTile
import com.example.ui.components.HomeWeeklyGoalWidget
import com.example.ui.components.HomeWidget
import com.example.ui.components.HomeWidgetManagerSheet
import com.example.ui.components.HomeWidgetsEmptyState
import com.example.ui.components.WorkoutStreakCard
import com.example.ui.components.TrainingQuotes
import com.example.ui.components.WeeklyGoalDialog
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
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val streak by viewModel.streakSummary.collectAsStateWithLifecycle()
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

    // The scheduled days that were actually completed, taken from the same streak summary the
    // streak card uses. A quick workout is activity, not adherence, so it can never tick a day.
    val completedScheduledDays = remember(streak.days) {
        streak.days
            .filter { it.status == StreakDayStatus.COMPLETED }
            .map { it.dayStart }
            .toSet()
    }

    val latestWeight = remember(weightLogs) { weightLogs.maxByOrNull { it.timestamp } }

    // === Widget configuration ===
    //
    // Every card on Home is a widget with a stable id. The athlete's order and the set of cards
    // they removed are kept together in DataStore, so the two can never disagree after a crash.
    // The saved list is merged with the known ids on read: a widget added in a new release appears
    // at its default place, while a removed one stays removed until it is added back.
    val widgetConfig by viewModel.homeWidgetConfig.collectAsStateWithLifecycle()
    val widgetPhotoPath by viewModel.homeWidgetPhotoPath.collectAsStateWithLifecycle()
    val weeklyGoal by viewModel.homeWidgetWeeklyGoal.collectAsStateWithLifecycle()
    val widgetPhotoError by viewModel.widgetPhotoError.collectAsStateWithLifecycle()
    val fullscreenEnabled by viewModel.fullscreenEnabled.collectAsStateWithLifecycle()
    val streakFocusRequest by viewModel.homeStreakFocusRequest.collectAsStateWithLifecycle()
    var showWidgetManager by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val widgetPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importWidgetPhoto(uri)
    }

    var orderedWidgets by remember {
        mutableStateOf(HomeWidget.resolve(widgetConfig.order, widgetConfig.hiddenIds))
    }
    var hiddenWidgetIds by remember {
        mutableStateOf(widgetConfig.hiddenIds ?: HomeWidget.defaultHiddenIds)
    }
    // The week card only exists when a routine is scheduled, so it is filtered out of the drag
    // list rather than occupying an invisible slot.
    val hasWeekWidget = activeRoutine != null && activeRoutineDays.isNotEmpty()
    val visibleWidgets = remember(orderedWidgets, hasWeekWidget) {
        orderedWidgets.filter { it != HomeWidget.WEEK || hasWeekWidget }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(streakFocusRequest, visibleWidgets) {
        if (streakFocusRequest > 0) {
            val index = visibleWidgets.indexOf(HomeWidget.STREAK)
            if (index >= 0) {
                // One greeting item precedes the widget items.
                listState.animateScrollToItem(index + 1)
            }
        }
    }
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    // Set for the short glide between the card under the finger and the slot it is released into.
    // While it is non-null the card still floats and the auto-scroll loop is stopped.
    var droppingWidgetId by remember { mutableStateOf<String?>(null) }
    // The finger's position in the list viewport, and where the widget would land if released now.
    // Both are kept as state so the preview gap can be kept under the card while the finger is held
    // still and the list auto-scrolls underneath it.
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var insertionIndex by remember { mutableStateOf<Int?>(null) }
    // The floating card's geometry relative to the finger, taken when the drag begins. The card is
    // lifted from its list slot, so once the slot scrolls out of the viewport the layout can no
    // longer describe where the card is; these keep the insertion index and the preview line
    // tracking the card under the finger anyway.
    //
    // The centre offset is where the finger touched within the card: centre - touch, so
    // cardCentre = finger + offset. The top offset is the inverse of the touch point, so
    // cardTop = finger + topOffset.
    var draggedCardCentreOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggedCardTopOffsetPx by remember { mutableFloatStateOf(0f) }
    var draggedCardHeightPx by remember { mutableFloatStateOf(0f) }
    // The last-workout card expands in place, and the floating copy must match it pixel for pixel.
    var recentWorkoutExpanded by remember(
        recentWorkout?.session?.title,
        recentWorkout?.session?.startTime,
        recentWorkout?.sets?.size
    ) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val autoScrollEdgePx = remember(density) { with(density) { 88.dp.toPx() } }

    // While a card is held, the list is laid out in the order it would have if the finger were
    // lifted: the dragged card keeps a slot of its own height at the insertion point, but it is
    // invisible there and drawn under the finger as an overlay. The other cards animate around the
    // gap, so the athlete sees the new arrangement before releasing.
    val previewWidgets = remember(visibleWidgets, draggingWidgetId, insertionIndex) {
        val id = draggingWidgetId
        val target = insertionIndex
        if (id == null || target == null) visibleWidgets
        else HomeWidget.previewOrder(visibleWidgets, id, target)
    }

    LaunchedEffect(widgetConfig) {
        // The manager and the drag write the list locally first and then to disk; this keeps a
        // later external change (a reset or "delete app data") in sync without fighting an
        // in-flight drag.
        if (draggingWidgetId == null) {
            hiddenWidgetIds = widgetConfig.hiddenIds ?: HomeWidget.defaultHiddenIds
            orderedWidgets = HomeWidget.resolve(widgetConfig.order, widgetConfig.hiddenIds)
        }
    }

    /**
     * Where the dragged widget would be inserted in [visibleWidgets] if the finger were lifted.
     *
     * The compared position is the card's current *screen* centre. The list may have scrolled since
     * the gesture began, so the centre is measured from the finger and the card geometry captured at
     * drag start rather than from the card's original list slot: while the finger is held at an edge
     * the slot itself can scroll away, and the card stays under the finger while the other cards
     * move past it. That is what makes the insertion index advance without moving the finger.
     *
     * Only the cards other than the dragged one take part in the comparison. The dragged card
     * already has a slot at [insertionIndex] and its own midpoint must not vote for a position.
     */
    fun currentInsertionIndex(): Int? {
        val id = draggingWidgetId ?: return null
        val layout = listState.layoutInfo
        val cardCentre = dragPointerY + draggedCardCentreOffsetPx

        val remaining = visibleWidgets.filter { it.id != id }
        if (remaining.isEmpty()) return 0
        val firstVisibleIndex = remaining.indexOfFirst { candidate ->
            layout.visibleItemsInfo.any { it.key == "widget_${candidate.id}" }
        }
        // Everything above the first visible card is off screen but still above the finger.
        if (firstVisibleIndex < 0) return insertionIndex

        var above = firstVisibleIndex
        for (index in firstVisibleIndex until remaining.size) {
            val candidate = remaining[index]
            val info = layout.visibleItemsInfo
                .firstOrNull { it.key == "widget_${candidate.id}" }
                ?: continue
            if (info.offset + info.size / 2f < cardCentre) above++
        }
        return above
    }

    /**
     * Finishes a drag: move the widget to the slot nearest the finger and remember it.
     *
     * The list already shows that slot as the gap the athlete has been dragging around, so the
     * commit changes the data behind the same picture. [finishWidgetDrag] is what usually calls
     * this, after the floating card has glided into the gap.
     */
    fun commitWidgetDrag() {
        val id = draggingWidgetId
        val target = insertionIndex
        if (id != null && target != null) {
            val from = visibleWidgets.indexOfFirst { it.id == id }
            if (from >= 0 && target != from) {
                val next = HomeWidget.moveToInsertion(
                    full = orderedWidgets,
                    visible = visibleWidgets,
                    draggedId = id,
                    insertionIndex = target
                )
                if (next != orderedWidgets) {
                    orderedWidgets = next
                    viewModel.setHomeWidgetOrder(next.map { it.id })
                }
            }
        }
        draggingWidgetId = null
        dragPointerY = 0f
        insertionIndex = null
    }

    // === The drop ===
    //
    // The list is already in its final order while the card is held. On release the only thing
    // left is to bring the floating card down into the gap; the list slot stays invisible until
    // the glide finishes, so the card cannot flash at the old position.
    LaunchedEffect(droppingWidgetId) {
        val id = droppingWidgetId ?: return@LaunchedEffect
        // Let the final insertion index reach the list before asking where its slot is.
        withFrameNanos { }
        withFrameNanos { }
        val slot = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "widget_$id" }
        if (slot != null) {
            animate(
                initialValue = dragPointerY,
                targetValue = slot.offset - draggedCardTopOffsetPx,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            ) { value, _ -> dragPointerY = value }
        }
        commitWidgetDrag()
        droppingWidgetId = null
    }

    // === Edge auto-scroll ===
    //
    // A long list cannot be reordered by dragging alone: the widget could never reach a slot that
    // is off screen. While a card is held within 88dp of the top or bottom edge the list scrolls
    // in that direction, faster the closer the finger is to the edge. The card stays under the
    // finger, the other cards move past it and the preview gap follows them.
    LaunchedEffect(draggingWidgetId) {
        if (draggingWidgetId == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive && draggingWidgetId != null && droppingWidgetId == null) {
            val frame = withFrameNanos { it }
            val dt = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrame = frame

            val viewportHeight = listState.layoutInfo.viewportSize.height
            if (viewportHeight > 0) {
                val edge = autoScrollEdgePx.coerceAtMost(viewportHeight / 3f)
                val fingerY = dragPointerY
                val direction = when {
                    fingerY < edge -> -1f
                    fingerY > viewportHeight - edge -> 1f
                    else -> 0f
                }
                if (direction != 0f) {
                    val depth = if (direction < 0f) {
                        (edge - fingerY) / edge
                    } else {
                        (fingerY - (viewportHeight - edge)) / edge
                    }.coerceIn(0f, 1f)
                    val speed = direction * (280f + 1100f * depth) * dt
                    // dispatchRawDelta applies the scroll straight to the list instead of going
                    // through the suspend scroll mutex, which the drag gesture holds. It returns
                    // the consumed portion, so the floating card can be kept under the finger.
                    val consumed = listState.dispatchRawDelta(speed)
                    if (consumed != 0f) {
                        val next = currentInsertionIndex()
                        if (next != null && next != insertionIndex) {
                            insertionIndex = next
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
        }
    }

    // The card content, shared by its slot in the list and the copy that follows the finger
    // during a drag. Both are composed from the same Home state, so the floating card never
    // disagrees with the card it was lifted from.
    val widgetContent: @Composable (HomeWidget) -> Unit = { widget ->
        when (widget) {
                HomeWidget.TODAY -> {
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
                            viewModel.startRoutineWorkout(
                                routine ?: return@TodayWorkoutCard,
                                targetWeekday = day.weekday
                            )
                        },
                        onChooseRoutine = onOpenSavedRoutines,
                        onResume = onOpenLogWorkout
                    )
                }

                HomeWidget.ACTIONS -> Row(
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

                HomeWidget.WEEK -> activeRoutine?.let { routine ->
                    WeekScheduleCard(
                        days = activeRoutineDays,
                        today = today,
                        now = now,
                        weekStartsOnMonday = weekStartsOnMonday,
                        completedScheduledDays = completedScheduledDays,
                        shortDateFormat = shortDateFormat,
                        onStartDay = { day ->
                            viewModel.startRoutineWorkout(
                                routine,
                                targetWeekday = day.weekday
                            )
                        }
                    )
                }

                HomeWidget.STREAK -> WorkoutStreakCard(summary = streak)

                HomeWidget.STATS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            value = WeightConversion.format(
                                weekTotals.volume,
                                unit,
                                decimals = 0,
                                withUnit = false
                            ),
                            label = "${unit.suffix}-reps this week",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HomeWidget.HEATMAP -> Card(
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

                HomeWidget.WEIGHT -> BodyWeightCard(
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

                HomeWidget.RECENT -> RecentWorkoutCard(
                    title = recentWorkout?.session?.title,
                    startedAt = recentWorkout?.session?.startTime,
                    minutes = recentWorkout?.let {
                        HomeDashboard.minutes(it.session, it.sets)
                    } ?: 0,
                    setCount = recentWorkout?.sets?.size ?: 0,
                    sets = recentWorkout?.sets.orEmpty(),
                    weightUnit = unit,
                    profileWeightKg = userProfile.currentWeightKg,
                    // Held here so the floating copy reads the same expansion as the card in the
                    // list; otherwise the card would change size as soon as it was picked up.
                    expanded = recentWorkoutExpanded,
                    onExpandedChange = { recentWorkoutExpanded = it },
                    onOpen = onOpenProgress
                )

                HomeWidget.QUOTE -> Card(
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

                HomeWidget.PHOTO -> HomePhotoWidget(
                    photoPath = widgetPhotoPath,
                    onChoosePhoto = {
                        widgetPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                HomeWidget.GOAL -> HomeWeeklyGoalWidget(
                    workouts = weekTotals.workouts,
                    goal = weeklyGoal,
                    onCustomize = { showGoalDialog = true }
                )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .testTag("home_screen"),
            // Clearance for the floating dock: the last widget can scroll fully above the glass.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // === Greeting and widget management ===
        item(key = "home_greeting") {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
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
                IconButton(
                    onClick = { showWidgetManager = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("home_widget_manage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Customize Home widgets",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // === The reorderable widgets ===
        items(
            items = previewWidgets,
            key = { widget -> "widget_${widget.id}" }
        ) { widget ->
            val isDragging = draggingWidgetId == widget.id

            Column(
                modifier = Modifier
                    .animateItem()
                    .graphicsLayer {
                        // The card is lifted out of its slot while it is being dragged: the slot
                        // keeps its height (so the list does not jump) but shows nothing, and the
                        // floating copy in the overlay is drawn under the finger instead. The slot
                        // itself is placed at the insertion point by [previewWidgets], so the other
                        // cards move aside live.
                        if (isDragging) alpha = 0f
                    }
                    // Long press anywhere on a card starts the reorder, so every widget can be
                    // moved without a separate grab strip taking up room on the page. A normal
                    // tap and the controls inside the cards keep working: the drag only begins
                    // after the hold.
                    .pointerInput(widget.id, visibleWidgets) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                droppingWidgetId = null
                                draggingWidgetId = widget.id
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == "widget_${widget.id}" }
                                dragPointerY = (info?.offset ?: 0) + startOffset.y
                                draggedCardHeightPx = (info?.size ?: 0).toFloat()
                                draggedCardTopOffsetPx = -startOffset.y
                                draggedCardCentreOffsetPx = draggedCardHeightPx / 2f - startOffset.y
                                insertionIndex = visibleWidgets.indexOfFirst { it.id == widget.id }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragPointerY += amount.y
                                val next = currentInsertionIndex()
                                if (next != null && next != insertionIndex) {
                                    insertionIndex = next
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                insertionIndex = currentInsertionIndex()
                                droppingWidgetId = widget.id
                            },
                            onDragCancel = {
                                droppingWidgetId = null
                                draggingWidgetId = null
                                dragPointerY = 0f
                                insertionIndex = null
                            }
                        )
                    }
                    .testTag("home_widget_${widget.id}")
            ) {
                widgetContent(widget)
            }
        }

        // An empty Home is a valid configuration, not a broken one. The way back is right here.
        if (orderedWidgets.isEmpty()) {
            item(key = "home_widgets_empty") {
                HomeWidgetsEmptyState(onOpenManager = { showWidgetManager = true })
            }
        }

        // The build stamp belongs to the dashboard, not to a widget: it is always the last item,
        // so it follows the cards when they are reordered and stays below them when one expands.
        item(key = "app_version_footer", contentType = "footer") {
            AppVersionFooter()
        }
        }

        // The card under the finger. It is composed outside the list, so it survives even after the
        // slot it was lifted from scrolls out of the viewport and the list disposes that item.
        val draggedWidget = draggingWidgetId?.let { id ->
            visibleWidgets.firstOrNull { it.id == id }
        }
        if (draggedWidget != null) {
            Box(
                modifier = Modifier
                    .offset {
                        // Keep the whole card on screen while it is held. Without this the top or
                        // bottom edge cuts the card in half when the finger reaches the auto-scroll
                        // zone, which hides the outline exactly when the drop target is at an end.
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        val maxTop = (viewportHeight - draggedCardHeightPx).coerceAtLeast(0f)
                        val top = (dragPointerY + draggedCardTopOffsetPx).coerceIn(0f, maxTop)
                        IntOffset(0, top.roundToInt())
                    }
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = 20.dp.toPx()
                        shape = RoundedCornerShape(18.dp)
                    }
                    // The stats grid and the quick actions are made of separate tiles, so the page
                    // used to show through the gaps between them while the widget floated. Paint
                    // the same page colour behind the whole widget, exactly as it is seen in the
                    // list; the card then reads as one lifted object.
                    .background(DarkBg, RoundedCornerShape(18.dp))
                    // The card is the selected thing on the page while it is held. The full-strength
                    // cyan outline matches the selected tab and makes it read as lifted, not just
                    // duplicated.
                    .border(2.dp, ElectricCyan, RoundedCornerShape(18.dp))
                    .testTag("home_widget_drag_overlay")
            ) {
                widgetContent(draggedWidget)
            }
        }
    }

    // === Widget management ===
    if (showWidgetManager) {
        HomeWidgetManagerSheet(
            enabled = orderedWidgets,
            hasWeekRoutine = hasWeekWidget,
            photoPath = widgetPhotoPath,
            weeklyGoal = weeklyGoal,
            photoError = widgetPhotoError,
            onDismiss = {
                showWidgetManager = false
                viewModel.clearWidgetPhotoError()
            },
            onAdd = { widget ->
                val next = HomeWidget.add(orderedWidgets, widget)
                if (next != orderedWidgets) {
                    val nextHidden = hiddenWidgetIds - widget.id
                    orderedWidgets = next
                    hiddenWidgetIds = nextHidden
                    viewModel.setHomeWidgetConfig(next.map { it.id }, nextHidden)
                }
            },
            onRemove = { widget ->
                val next = HomeWidget.remove(orderedWidgets, widget)
                if (next != orderedWidgets) {
                    val nextHidden = hiddenWidgetIds + widget.id
                    orderedWidgets = next
                    hiddenWidgetIds = nextHidden
                    viewModel.setHomeWidgetConfig(next.map { it.id }, nextHidden)
                    if (widget == HomeWidget.PHOTO) {
                        // The photo belongs to the widget, not to the athlete's gallery. Removing
                        // the widget is the point at which its app-private copy stops being needed.
                        viewModel.clearWidgetPhoto()
                    }
                }
            },
            onMove = { widget, delta ->
                val next = HomeWidget.move(orderedWidgets, widget, delta)
                if (next != orderedWidgets) {
                    orderedWidgets = next
                    viewModel.setHomeWidgetOrder(next.map { it.id })
                }
            },
            onChoosePhoto = {
                widgetPhotoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemovePhoto = { viewModel.clearWidgetPhoto() },
            onGoalChange = { viewModel.setHomeWidgetWeeklyGoal(it) },
            fullscreenEnabled = fullscreenEnabled
        )
    }

    // === Weekly goal customization from the card itself ===
    if (showGoalDialog) {
        WeeklyGoalDialog(
            goal = weeklyGoal,
            onDismiss = { showGoalDialog = false },
            onSave = { goal ->
                viewModel.setHomeWidgetWeeklyGoal(goal)
                showGoalDialog = false
            }
        )
    }

    // === A picked photo that could not be kept ===
    if (widgetPhotoError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearWidgetPhotoError() },
            containerColor = DarkSurface,
            modifier = Modifier.testTag("widget_photo_error_dialog"),
            title = {
                Text(
                    text = "Photo not saved",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = widgetPhotoError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearWidgetPhotoError() }) {
                    Text("OK", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
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
    completedScheduledDays: Set<Long>,
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
                val dateOfDay = WorkoutStreak.dateForWeekdayInWeek(
                    weekday = day.weekday,
                    reference = now,
                    weekStartsOnMonday = weekStartsOnMonday
                )
                val wasTrained = completedScheduledDays.contains(dateOfDay)
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
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("home_log_weight_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = OnAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Log Weight",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnAccent
                )
            }
        }
    }
}

/**
 * The last workout the athlete finished.
 *
 * The summary is always visible; tapping it slides the saved sets open underneath, without leaving
 * the Home page. That is the quickest way to answer "what did I actually do last time" before
 * starting the next session. The exercise names are bold group headings and every set is one
 * bullet, so a long workout still reads as a list instead of a paragraph.
 */
@Composable
private fun RecentWorkoutCard(
    title: String?,
    startedAt: Long?,
    minutes: Int,
    setCount: Int,
    sets: List<LoggedSet>,
    weightUnit: WeightUnit,
    profileWeightKg: Double,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "recent_workout_arrow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_workout_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (title != null) {
                            Modifier
                                .clickable { onExpandedChange(!expanded) }
                                .testTag("home_recent_workout_toggle")
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                                    append(
                                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                            .format(Date(startedAt))
                                    )
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
                }
                if (title != null) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) {
                            "Hide the sets of the last workout"
                        } else {
                            "Show the sets of the last workout"
                        },
                        tint = if (expanded) ElectricCyan else TextMuted,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && title != null,
                enter = expandVertically(
                    animationSpec = tween(280),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = tween(220),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_recent_workout_details"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DarkBorder)
                    )

                    if (sets.isEmpty()) {
                        Text(
                            text = "This workout has no saved sets.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    // Grouping keeps the exercise as the heading and its sets as bullets. The map
                    // preserves the order the exercises were performed in.
                    sets.groupBy { it.exerciseName }.forEach { (exerciseName, exerciseSets) ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = exerciseName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            exerciseSets.sortedBy { it.setNumber }.forEach { set ->
                                val bodyweight =
                                    BodyWeightTimeline.bodyWeightForSet(set, profileWeightKg)
                                val load = if (set.exerciseType == ExerciseType.CARDIO) {
                                    describeCardioSet(set, weightUnit)
                                } else {
                                    "${WorkoutMetrics.loadLabel(set, bodyweight, weightUnit)} × " +
                                        describeLoggedSetEffort(set)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "Set ${set.setNumber} — $load",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_recent_workout_see_history")
                    ) {
                        Text(
                            text = "See full history",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }
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
        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = OnAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = OnAccent
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
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("home_weight_save_button")
            ) {
                Text("Save", color = OnAccent, fontWeight = FontWeight.Bold)
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
