/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CustomRestTimer
import com.example.data.model.DistanceFormat
import com.example.data.model.DropSetRules
import com.example.data.model.EffortDisplay
import com.example.data.model.EffortScale
import com.example.data.model.Exercise
import com.example.data.model.ExerciseMedia
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseFieldCatalog
import com.example.data.model.ExerciseFieldValues
import com.example.data.model.LoggedSet
import com.example.data.model.MusclePart
import com.example.data.model.MusclePartResolver
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.SupersetRules
import com.example.data.model.RemovedActiveExercise
import com.example.data.model.RoutineParser
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.components.DropSetBranchConnector
import com.example.ui.components.DropSetBranchLeadIn
import com.example.ui.components.DropSetRowVisibility
import com.example.ui.components.DropSetBranchRow
import com.example.ui.components.ExerciseGifThumbnail
import com.example.ui.components.ExerciseTypeTag
import com.example.ui.components.MuscleBodyMap
import com.example.ui.components.WarmupBadge
import com.example.ui.components.orderedSetRows
import com.example.ui.components.rememberReducedMotion
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Completion of one body part across the whole workout.
 *
 * The denominator is every set training that part, including warm-up sets; the numerator is the
 * completed ones. It is a completion measure, not a performance record.
 */
data class BodyPartProgress(
    val part: MusclePart,
    val completed: Int,
    val total: Int
) {
    val fraction: Float
        get() = if (total > 0) (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

/** Body-part progress for every exercise in the current workout, not only the selected one. */
fun workoutBodyProgress(
    exercises: List<Exercise>,
    sets: List<LoggedSet>
): List<BodyPartProgress> {
    val byId = exercises.associateBy { it.id }
    val totals = LinkedHashMap<MusclePart, Int>()
    val completed = LinkedHashMap<MusclePart, Int>()

    for (set in sets) {
        val exercise = byId[set.exerciseId] ?: continue
        val parts = linkedSetOf<MusclePart>()
        MusclePartResolver.resolve(exercise)?.let(parts::add)
        parts.addAll(MusclePartResolver.resolveSecondary(exercise))
        for (part in parts) {
            totals[part] = (totals[part] ?: 0) + 1
            if (set.isCompleted) completed[part] = (completed[part] ?: 0) + 1
        }
    }

    val order = MusclePart.selectable.withIndex().associate { it.value to it.index }
    return totals.keys
        .sortedBy { order[it] ?: Int.MAX_VALUE }
        .map { part ->
            BodyPartProgress(
                part = part,
                completed = completed[part] ?: 0,
                total = totals.getValue(part)
            )
        }
}

/**
 * The one active-workout presentation used by saved Routine workouts and the Quick Workout tray.
 *
 * The host keeps the session, rest timer and bottom controls. This composable owns only the
 * exercise selector, the selected exercise overview, its set rows and the body-part visualization.
 */
@Composable
fun ActiveWorkoutInterface(
    exercises: List<Exercise>,
    sets: List<LoggedSet>,
    unit: WeightUnit,
    workoutKey: Long,
    onAddSet: (Exercise) -> Unit,
    onUpdateSet: (LoggedSet, LoggedSet) -> Unit,
    onDeleteSet: (LoggedSet) -> Unit,
    onShowDetails: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    routineItems: List<RoutineExerciseItem> = emptyList(),
    /** Catalog for the plus button's picker. The picker itself is the shared routine dialog. */
    allExercises: List<Exercise> = emptyList(),
    /** Adds one direct child under a main set. Session-only in both hosts. */
    onAddDropSet: (LoggedSet) -> Unit = {},
    /** Dedicated completion path so parent/child invariants and the rest timer stay in one place. */
    onToggleSetComplete: (LoggedSet, Boolean) -> Unit = { set, completed ->
        onUpdateSet(set, set.copy(isCompleted = completed))
    },
    /** Appends an exercise to this session only. */
    onAddExercise: (Exercise) -> Unit = {},
    /** Removes an exercise from this session and returns what Undo must restore. */
    onRemoveExercise: (Long) -> RemovedActiveExercise? = { null },
    onRestoreExercise: (RemovedActiveExercise) -> Unit = {},
    /** Reorders the live session; the routine host also persists routine-origin order. */
    onMoveExercise: (Int, Int) -> Unit = { _, _ -> },
    focusExerciseId: Long = 0L,
    bottomPadding: Dp = 16.dp,
    materialExpressive3: Boolean = false,
    /** True inside the Quick Workout tray: the whole interface scrolls and uses tighter chrome. */
    compact: Boolean = false,
    /**
     * Optional host-owned selection. The Quick Workout tray hoists it so minimising the tray does
     * not reset the exercise; the Routine screen lets the component own it.
     */
    selectedExerciseId: Long? = null,
    onSelectedExerciseChange: (Long) -> Unit = {},
    effortDisplay: EffortDisplay = EffortDisplay.RIR,
    /** Returns true when the pair was accepted; hosts persist routine-origin pairs. */
    onLinkSuperset: (Long, Int) -> Boolean = { _, _ -> false },
    onUnlinkSuperset: (Long) -> Boolean = { false }
) {
    var supersetDialogExerciseId by rememberSaveable(workoutKey) { mutableStateOf(0L) }
    var internalSelectedExerciseId by rememberSaveable(workoutKey) {
        mutableStateOf(exercises.firstOrNull()?.id ?: 0L)
    }
    var expandedSetKey by rememberSaveable(workoutKey) { mutableStateOf<String?>(null) }

    fun requestSuperset(exercise: Exercise) {
        if (exercises.size <= 1) return
        val index = exercises.indexOfFirst { it.id == exercise.id }
        if (index < 0) return
        val linked = SupersetRules.groupIdForExercise(sets, exercise.id) != null
        // At the ends there is only one valid direction: act immediately instead of opening a
        // one-option dialog. A linked exercise still opens the dialog so it can be unlinked.
        if (!linked && index == 0) {
            onLinkSuperset(exercise.id, 1)
            return
        }
        if (!linked && index == exercises.lastIndex) {
            onLinkSuperset(exercise.id, -1)
            return
        }
        supersetDialogExerciseId = exercise.id
    }
    var showBodyDialog by rememberSaveable(workoutKey) { mutableStateOf(false) }
    var showExercisePicker by rememberSaveable(workoutKey) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var undoSnapshot by remember { mutableStateOf<RemovedActiveExercise?>(null) }
    var undoJob by remember { mutableStateOf<Job?>(null) }

    val currentSelectedId = selectedExerciseId ?: internalSelectedExerciseId
    val reducedMotion = rememberReducedMotion()

    fun selectExercise(id: Long) {
        if (selectedExerciseId == null) internalSelectedExerciseId = id
        onSelectedExerciseChange(id)
    }

    fun requestExerciseRemoval(exercise: Exercise) {
        val snapshot = onRemoveExercise(exercise.id) ?: return
        undoSnapshot = snapshot
        undoJob?.cancel()
        undoJob = scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${exercise.name} removed from this workout",
                actionLabel = "Undo",
                withDismissAction = false,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                undoSnapshot?.let(onRestoreExercise)
            }
            undoSnapshot = null
        }
    }

    LaunchedEffect(exercises) {
        if (exercises.none { it.id == currentSelectedId }) {
            selectExercise(exercises.firstOrNull()?.id ?: 0L)
        }
    }
    LaunchedEffect(focusExerciseId) {
        if (focusExerciseId != 0L && exercises.any { it.id == focusExerciseId }) {
            selectExercise(focusExerciseId)
        }
    }
    // A panel belongs to one set. Switching exercise closes it rather than letting an animation or
    // expanded state leak into the next exercise.
    LaunchedEffect(currentSelectedId) {
        expandedSetKey = null
    }
    // A deleted exercise must not keep the picker or a stale undo action alive.
    LaunchedEffect(exercises.map { it.id }) {
        if (exercises.isEmpty()) undoJob?.cancel()
    }

    val selectedExercise = exercises.firstOrNull { it.id == currentSelectedId }
        ?: exercises.firstOrNull()
    val selectedIndex = selectedExercise?.let { ex -> exercises.indexOfFirst { it.id == ex.id } } ?: -1
    val routineItem = if (selectedIndex >= 0) routineItems.getOrNull(selectedIndex) else null
    val selectedSets = remember(sets, currentSelectedId) {
        sets.filter { it.exerciseId == currentSelectedId }
    }
    val bodyProgress = remember(exercises, sets) { workoutBodyProgress(exercises, sets) }
    val completedExerciseIds = remember(sets) { DropSetRules.completedExerciseIds(sets) }

    val picker: @Composable () -> Unit = {
        if (showExercisePicker) {
            PlanExerciseSearchDialog(
                allExercises = allExercises,
                alreadyAddedIds = exercises.map { it.id }.toSet(),
                unitSuffix = unit.suffix,
                onAdd = { exercise ->
                    onAddExercise(exercise)
                    selectExercise(exercise.id)
                    showExercisePicker = false
                },
                onDismiss = { showExercisePicker = false }
            )
        }
    }

    val snackbar: @Composable BoxScope.() -> Unit = {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = if (compact) 10.dp else bottomPadding.coerceAtMost(120.dp)
                )
        ) { data ->
            // The stock snackbar is a light surface. The rest of IronPulse is charcoal, so use the
            // same dark card language and let the accent colour mark the Undo action.
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(12.dp),
                containerColor = DarkSurfaceVariant,
                contentColor = TextWhite,
                actionContentColor = ElectricCyan,
                dismissActionContentColor = TextMuted,
                modifier = Modifier.testTag("active_workout_snackbar")
            )
        }
    }

    if (selectedExercise == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No exercises in this workout yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (allExercises.isNotEmpty()) {
                    AddExerciseCircleButton(onClick = { showExercisePicker = true })
                }
            }
            snackbar()
        }
        picker()
        return
    }

    val selectorRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkoutExerciseSelector(
                exercises = exercises,
                selectedExerciseId = selectedExercise.id,
                completedExerciseIds = completedExerciseIds,
                linkedExerciseIds = exercises.filter {
                    SupersetRules.groupIdForExercise(sets, it.id) != null
                }.map { it.id }.toSet(),
                supersetGroupByExercise = exercises.mapNotNull { exercise ->
                    SupersetRules.groupIdForExercise(sets, exercise.id)?.let { exercise.id to it }
                }.toMap(),
                compact = compact,
                modifier = Modifier.weight(1f),
                onSelect = ::selectExercise,
                onMove = onMoveExercise
            )

            Spacer(modifier = Modifier.width(6.dp))

            AddExerciseCircleButton(
                enabled = allExercises.isNotEmpty(),
                onClick = { showExercisePicker = true }
            )

            Spacer(modifier = Modifier.width(6.dp))

            WorkoutBodyPreview(
                progress = bodyProgress,
                onClick = { showBodyDialog = true },
                compact = compact
            )
        }
    }

    val exerciseBody: @Composable (Exercise, Boolean) -> Unit = { exercise, scrollableSets ->
        val exerciseIndex = exercises.indexOfFirst { it.id == exercise.id }
        val exerciseRoutineItem = routineItems.getOrNull(exerciseIndex)
        val exerciseSets = sets
            .filter { it.exerciseId == exercise.id }
            .sortedBy { it.setNumber }
        if (scrollableSets) {
            Column(modifier = Modifier.fillMaxSize()) {
                ExerciseOverview(
                    exercise = exercise,
                    routineItem = exerciseRoutineItem,
                    onShowDetails = { onShowDetails(exercise) },
                    compact = compact,
                    isSuperset = SupersetRules.groupIdForExercise(sets, exercise.id) != null,
                    showSupersetAction = exercises.size > 1,
                    onSupersetClick = { requestSuperset(exercise) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    ExerciseSetRows(
                        exercise = exercise,
                        sets = exerciseSets,
                        routineItem = exerciseRoutineItem,
                        unit = unit,
                        effortDisplay = effortDisplay,
                        expandedSetKey = expandedSetKey,
                        onToggleExpanded = { key ->
                            expandedSetKey = if (expandedSetKey == key) null else key
                        },
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        onAddSet = onAddSet,
                        onAddDropSet = onAddDropSet,
                        onToggleComplete = onToggleSetComplete,
                        onDeleteExercise = { requestExerciseRemoval(exercise) },
                        bottomPadding = bottomPadding,
                        reducedMotion = reducedMotion
                    )
                }
            }
        } else {
            Column {
                ExerciseOverview(
                    exercise = exercise,
                    routineItem = exerciseRoutineItem,
                    onShowDetails = { onShowDetails(exercise) },
                    compact = compact,
                    isSuperset = SupersetRules.groupIdForExercise(sets, exercise.id) != null,
                    showSupersetAction = exercises.size > 1,
                    onSupersetClick = { requestSuperset(exercise) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExerciseSetRows(
                    exercise = exercise,
                    sets = exerciseSets,
                    routineItem = exerciseRoutineItem,
                    unit = unit,
                    expandedSetKey = expandedSetKey,
                    onToggleExpanded = { key ->
                        expandedSetKey = if (expandedSetKey == key) null else key
                    },
                    onUpdateSet = onUpdateSet,
                    onDeleteSet = onDeleteSet,
                    onAddSet = onAddSet,
                    onAddDropSet = onAddDropSet,
                    onToggleComplete = onToggleSetComplete,
                    onDeleteExercise = { requestExerciseRemoval(exercise) },
                    bottomPadding = bottomPadding,
                    reducedMotion = reducedMotion,
                    effortDisplay = effortDisplay
                )
            }
        }
    }

    val swipeSpec: AnimatedContentTransitionScope<Long>.() -> ContentTransform = {
        if (reducedMotion) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            val from = exercises.indexOfFirst { it.id == initialState }
            val to = exercises.indexOfFirst { it.id == targetState }
            val direction = if (to >= from) 1 else -1
            (
                slideInHorizontally(tween(280)) { fullWidth -> direction * fullWidth / 3 } +
                    fadeIn(tween(180))
                ).togetherWith(
                slideOutHorizontally(tween(240)) { fullWidth -> -direction * fullWidth / 3 } +
                    fadeOut(tween(150))
            ).using(SizeTransform(clip = false))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (compact) {
            // The Quick Workout tray can be very short in its partial state. Scrolling the whole
            // interface, selector included, keeps every set row reachable instead of pinning a
            // header that leaves no room for the rows.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
            ) {
                selectorRow()
                // Keep the exercise summary close to the body-parts button. The old 8dp gap
                // read as a separate empty band under the selector row.
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = selectedExercise.id,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = swipeSpec,
                    label = "exercise_switch_compact"
                ) { exerciseId ->
                    exercises.firstOrNull { it.id == exerciseId }?.let { exercise ->
                        exerciseBody(exercise, false)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                selectorRow()
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedContent(
                    targetState = selectedExercise.id,
                    modifier = Modifier.weight(1f),
                    transitionSpec = swipeSpec,
                    label = "exercise_switch"
                ) { exerciseId ->
                    exercises.firstOrNull { it.id == exerciseId }?.let { exercise ->
                        exerciseBody(exercise, true)
                    }
                }
            }
        }
        snackbar()
    }

    picker()

    if (showBodyDialog) {
        BodyPartsTrainedDialog(
            progress = bodyProgress,
            materialExpressive3 = materialExpressive3,
            onDismiss = { showBodyDialog = false }
        )
    }

    if (supersetDialogExerciseId != 0L) {
        SupersetDialog(
            exerciseId = supersetDialogExerciseId,
            exercises = exercises,
            sets = sets,
            routineItems = routineItems,
            onLink = { id, direction ->
                if (onLinkSuperset(id, direction)) supersetDialogExerciseId = 0L
            },
            onUnlink = { id ->
                if (onUnlinkSuperset(id)) supersetDialogExerciseId = 0L
            },
            onDismiss = { supersetDialogExerciseId = 0L }
        )
    }
}

@Composable
private fun SupersetDialog(
    exerciseId: Long,
    exercises: List<Exercise>,
    sets: List<LoggedSet>,
    routineItems: List<RoutineExerciseItem>,
    onLink: (Long, Int) -> Unit,
    onUnlink: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val index = exercises.indexOfFirst { it.id == exerciseId }
    if (index < 0) return
    val linked = SupersetRules.groupIdForExercise(sets, exerciseId) != null
    val parallelItems = routineItems.size == exercises.size
    val canLinkPrevious = if (index <= 0) false else if (parallelItems) {
        SupersetRules.canLink(routineItems, index, -1)
    } else {
        SupersetRules.canLinkSets(sets, exerciseId, exercises[index - 1].id)
    }
    val canLinkNext = if (index >= exercises.lastIndex) false else if (parallelItems) {
        SupersetRules.canLink(routineItems, index, 1)
    } else {
        SupersetRules.canLinkSets(sets, exerciseId, exercises[index + 1].id)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Superset",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            text = "Link this exercise with an adjacent exercise. Each keeps its own sets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close superset dialog",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (canLinkPrevious && canLinkNext) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SupersetAction(
                            "Link previous",
                            modifier = Modifier.weight(1f)
                        ) { onLink(exerciseId, -1) }
                        SupersetAction(
                            "Link following",
                            modifier = Modifier.weight(1f)
                        ) { onLink(exerciseId, 1) }
                    }
                } else {
                    if (canLinkPrevious) {
                        SupersetAction(
                            "Link previous",
                            modifier = Modifier.fillMaxWidth()
                        ) { onLink(exerciseId, -1) }
                    }
                    if (canLinkNext) {
                        SupersetAction(
                            "Link following",
                            modifier = Modifier.fillMaxWidth()
                        ) { onLink(exerciseId, 1) }
                    }
                }
                if (linked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SupersetAction(
                        "Unlink superset",
                        modifier = Modifier.fillMaxWidth(),
                        accent = CoralOrange
                    ) {
                        onUnlink(exerciseId)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupersetAction(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = ElectricCyan,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Stable Compose key. A drop set shares its parent's set number, so the UID is the identity. */
private fun setRowKey(set: LoggedSet): String =
    set.setUid.takeIf { it.isNotBlank() }
        ?: "${set.exerciseId}:${set.setNumber}:${set.parentSetUid.orEmpty()}:${set.dropIndex}"

/** Readable, stable test/accessibility key that preserves the pre-drop-set naming for main sets. */
private fun setTestKey(set: LoggedSet): String =
    if (set.isDropSet) {
        "${set.exerciseId}:${set.setNumber}:d${set.dropIndex}"
    } else {
        "${set.exerciseId}:${set.setNumber}"
    }

@Composable
private fun WorkoutExerciseSelector(
    exercises: List<Exercise>,
    selectedExerciseId: Long,
    completedExerciseIds: Set<Long>,
    linkedExerciseIds: Set<Long> = emptySet(),
    supersetGroupByExercise: Map<Long, String> = emptyMap(),
    compact: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Long) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val stepPx = with(density) { 42.dp.toPx() }
    val scrollState = rememberScrollState()
    var dragFrom by remember { mutableStateOf(-1) }
    var dragTarget by remember { mutableStateOf(-1) }
    var dragBlockSize by remember { mutableStateOf(1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    val ids = exercises.map { it.id }

    fun resetDrag() {
        dragFrom = -1
        dragTarget = -1
        dragBlockSize = 1
        dragOffset = 0f
        draggingId = null
    }

    LaunchedEffect(draggingId, dragTarget, exercises.size) {
        // A long selector must remain reorderable at its ends. Scroll while the dragged chip is
        // held near an edge; the placement shift still follows the finger.
        while (draggingId != null) {
            when {
                dragTarget <= 1 -> scrollState.scrollBy(-10f)
                dragTarget >= exercises.lastIndex - 1 -> scrollState.scrollBy(10f)
            }
            delay(16)
        }
    }

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .pointerInput(ids, supersetGroupByExercise) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (exercises.isNotEmpty()) {
                            val index = (offset.x / stepPx).roundToInt()
                                .coerceIn(0, exercises.lastIndex)
                            val groupId = supersetGroupByExercise[exercises[index].id]
                            val partner = groupId?.let { group ->
                                exercises.indexOfFirst {
                                    it.id != exercises[index].id &&
                                        supersetGroupByExercise[it.id] == group
                                }
                            }?.takeIf { it >= 0 }
                            val start = if (partner == null) index else minOf(index, partner)
                            val size = if (partner == null) 1 else 2
                            dragFrom = start
                            dragTarget = start
                            dragBlockSize = size
                            dragOffset = 0f
                            draggingId = exercises[index].id
                        }
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val size = dragBlockSize
                        val targetStart = dragTarget
                        resetDrag()
                        if (from >= 0 && targetStart >= 0 && from != targetStart) {
                            // The controllers insert a moved block before `to`; when moving later,
                            // pass the last slot the block will occupy.
                            val to = if (targetStart > from) targetStart + size - 1 else targetStart
                            onMove(from, to)
                        }
                    },
                    onDragCancel = { resetDrag() },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.x
                        if (dragFrom >= 0) {
                            dragTarget = (dragFrom + (dragOffset / stepPx).roundToInt())
                                .coerceIn(0, (exercises.lastIndex - dragBlockSize + 1).coerceAtLeast(0))
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        exercises.forEachIndexed { index, exercise ->
            val isDragged = dragFrom >= 0 && index in dragFrom until (dragFrom + dragBlockSize)
            val shift = when {
                dragFrom < 0 || dragTarget < 0 -> 0f
                isDragged -> 0f
                dragTarget > dragFrom && index in (dragFrom + dragBlockSize)..(dragTarget + dragBlockSize - 1) ->
                    -dragBlockSize * stepPx
                dragTarget < dragFrom && index in dragTarget until dragFrom ->
                    dragBlockSize * stepPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(
                targetValue = shift,
                animationSpec = tween(150),
                label = "selector_shift_${exercise.id}"
            )
            key(exercise.id) {
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragged) 3f else 0f)
                        // Every member of the superset block follows the finger as one unit; the
                        // other chips open a block-sized gap.
                        .graphicsLayer {
                            translationX = if (isDragged) dragOffset else animatedShift
                            if (isDragged) {
                                scaleX = 0.78f
                                scaleY = 0.78f
                                alpha = 0.92f
                            }
                        }
                ) {
                    ExerciseNumberChip(
                        number = index + 1,
                        selected = exercise.id == selectedExerciseId,
                        completed = exercise.id in completedExerciseIds,
                        linked = exercise.id in linkedExerciseIds,
                        compact = compact,
                        onClick = { onSelect(exercise.id) }
                    )
                }
            }
        }
    }
}


@Composable
private fun SupersetIconButton(
    linked: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (linked) ElectricCyan.copy(alpha = 0.16f) else DarkSurfaceVariant)
            .border(
                1.dp,
                if (linked) ElectricCyan.copy(alpha = 0.65f) else DarkBorder,
                CircleShape
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (linked) "Manage superset" else "Link superset"
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = if (linked) ElectricCyan else TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AddExerciseCircleButton(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DarkSurface)
            .border(1.dp, if (enabled) ElectricCyan.copy(alpha = 0.7f) else DarkBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Add exercise to this workout" }
            .testTag("active_workout_add_exercise"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = if (enabled) ElectricCyan else TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ExerciseNumberChip(
    number: Int,
    selected: Boolean,
    completed: Boolean = false,
    linked: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    // Green is a state, not an instant paint. The colour eases in over 420 ms and reverts the same
    // way, while a short pop acknowledges the moment the exercise became complete.
    val background by animateColorAsState(
        targetValue = when {
            completed -> MintGreen
            selected -> ElectricCyan
            else -> DarkSurfaceVariant
        },
        animationSpec = tween(420),
        label = "exercise_chip_background_$number"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            completed -> MintGreen
            selected -> ElectricCyan
            linked -> ElectricCyan
            else -> DarkBorder
        },
        animationSpec = tween(420),
        label = "exercise_chip_border_$number"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            completed -> DarkBg
            selected -> DarkBg
            else -> TextWhite
        },
        animationSpec = tween(420),
        label = "exercise_chip_text_$number"
    )
    val scale = remember { Animatable(1f) }
    var wasCompleted by remember { mutableStateOf(completed) }
    LaunchedEffect(completed) {
        if (completed != wasCompleted) {
            wasCompleted = completed
            if (completed) {
                scale.snapTo(1f)
                scale.animateTo(1.16f, tween(120))
                scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
            }
        }
    }

    Box(
        modifier = Modifier
            .size(if (compact) 34.dp else 36.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(background)
            .border(if (linked) 2.5.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (linked) "Superset exercise $number" else "Exercise $number"
                stateDescription = when {
                    completed -> "Completed"
                    selected -> "Selected"
                    else -> "Not selected"
                }
            }
            .testTag("active_exercise_selector_$number"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun WorkoutBodyPreview(
    progress: List<BodyPartProgress>,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val progressMap = remember(progress) { progress.associate { it.part to it.fraction } }
    val size = if (compact) 54.dp else 62.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Body parts trained" }
            .testTag("workout_body_preview"),
        contentAlignment = Alignment.Center
    ) {
        MuscleBodyMap(
            highlighted = null,
            onSelect = null,
            maxFigureHeight = if (compact) 46.dp else 54.dp,
            progress = progressMap,
            compact = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 2.dp else 3.dp)
        )
    }
}

@Composable
private fun ExerciseOverview(
    exercise: Exercise,
    routineItem: RoutineExerciseItem?,
    onShowDetails: () -> Unit,
    compact: Boolean = false,
    isSuperset: Boolean = false,
    showSupersetAction: Boolean = true,
    onSupersetClick: () -> Unit = {}
) {
    val hasMedia = ExerciseMedia.resolve(
        exercise.customMediaUri,
        exercise.customMediaIsVideo,
        exercise.mediaPath
    ) != ExerciseMedia.Source.NONE
    val primary = exercise.category.ifBlank { exercise.target }
    val metadata = listOf(exercise.equipment, primary)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
    val prescription = routineItem?.let {
        RoutineParser.displayPrescription(it)
    }.orEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No reserved square when the exercise has no image: the text starts at the left edge.
            if (hasMedia) {
                ExerciseGifThumbnail(
                    localUri = exercise.customMediaUri,
                    localIsVideo = exercise.customMediaIsVideo,
                    mediaPath = exercise.mediaPath,
                    modifier = Modifier.size(if (compact) 44.dp else 56.dp)
                )
                Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = exercise.name,
                        style = if (compact) {
                            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onShowDetails)
                            .padding(vertical = 1.dp)
                            .testTag("active_exercise_name_${exercise.id}")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showSupersetAction) {
                            SupersetIconButton(
                                linked = isSuperset,
                                onClick = onSupersetClick,
                                testTag = "active_superset_${exercise.id}"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        ExerciseTypeTag(
                            exerciseType = exercise.exerciseType,
                            modifier = Modifier.widthIn(max = if (compact) 100.dp else 124.dp)
                        )
                    }
                }
                if (metadata.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (prescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Target: $prescription",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = GoldenAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}

@Composable
private fun AddSetButton(
    exerciseId: Long,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("active_workout_add_set_$exerciseId"),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                "Add Set",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }
    }
}

@Composable
private fun RemoveExerciseButton(
    exerciseId: Long,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("active_workout_delete_exercise_$exerciseId"),
        color = FireRed.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FireRed.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = FireRed.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                "Remove exercise",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = FireRed.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun AddDropSetButton(
    parent: LoggedSet,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("active_workout_add_drop_${parent.setNumber}"),
        color = DarkBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = ElectricCyan.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Add drop set",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun ActiveWorkoutSetRow(
    set: LoggedSet,
    exercise: Exercise,
    unit: WeightUnit,
    inheritedRest: Int,
    effortDisplay: EffortDisplay,
    effortRepBased: Boolean,
    expanded: Boolean,
    isDrop: Boolean = false,
    onToggleExpanded: () -> Unit,
    onUpdate: (LoggedSet) -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit,
    /**
     * Main-set only: its drop branch and the Add drop set control, rendered inside the same card.
     * A child passes nothing, which is what prevents recursive nesting.
     */
    dropContent: (@Composable () -> Unit)? = null
) {
    val key = setTestKey(set)
    // A working copy prevents a fast second keystroke from writing back a stale sibling field.
    var working by remember(key) { mutableStateOf(set) }
    LaunchedEffect(
        set.isWarmup,
        set.restSecondsOverride,
        set.isCompleted,
        set.setNumber,
        set.dropIndex,
        set.parentSetUid
    ) {
        working = working.copy(
            isWarmup = set.isWarmup,
            restSecondsOverride = set.restSecondsOverride,
            isCompleted = set.isCompleted,
            setNumber = set.setNumber,
            dropIndex = set.dropIndex,
            parentSetUid = set.parentSetUid
        )
    }

    val fields = remember(exercise.id, exercise.supportedFields, exercise.exerciseType) {
        exercise.exerciseFields
    }
    val primaryFields = fields.take(2)
    val detailFields = fields.drop(2)
    val fieldTexts = remember(key, unit) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field -> put(field.id, fieldText(working, field, unit)) }
        }
    }
    var restText by remember(key) {
        mutableStateOf((working.restSecondsOverride ?: inheritedRest).toString())
    }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 360f else 0f,
        animationSpec = tween(420),
        label = "set_number_rotation_$key"
    )

    fun publish(updated: LoggedSet) {
        working = updated
        onUpdate(updated)
    }

    val borderColor = when {
        expanded -> ElectricCyan.copy(alpha = 0.55f)
        working.isCompleted -> MintGreen.copy(alpha = 0.38f)
        working.isWarmup -> CoralOrange.copy(alpha = 0.38f)
        isDrop -> ElectricCyan.copy(alpha = 0.30f)
        else -> DarkBorder
    }

    // The number circle eases into its completed colour instead of switching instantly, and a
    // short pop marks the transition. `wasCompleted` prevents a restored workout from popping
    // every row on entry.
    val numberBackground by animateColorAsState(
        targetValue = when {
            working.isCompleted -> MintGreen.copy(alpha = 0.18f)
            working.isWarmup -> CoralOrange.copy(alpha = 0.16f)
            isDrop -> ElectricCyan.copy(alpha = 0.12f)
            else -> DarkSurfaceVariant
        },
        animationSpec = tween(380),
        label = "set_number_bg_$key"
    )
    val numberBorder by animateColorAsState(
        targetValue = when {
            working.isCompleted -> MintGreen
            working.isWarmup -> CoralOrange
            isDrop -> ElectricCyan.copy(alpha = 0.65f)
            else -> DarkBorder
        },
        animationSpec = tween(380),
        label = "set_number_border_$key"
    )
    val numberTextColor by animateColorAsState(
        targetValue = when {
            working.isCompleted -> MintGreen
            working.isWarmup -> CoralOrange
            isDrop -> ElectricCyan
            else -> TextWhite
        },
        animationSpec = tween(380),
        label = "set_number_text_$key"
    )
    val numberScale = remember { Animatable(1f) }
    var wasCompleted by remember(key) { mutableStateOf(working.isCompleted) }
    LaunchedEffect(working.isCompleted) {
        if (working.isCompleted != wasCompleted) {
            wasCompleted = working.isCompleted
            if (working.isCompleted) {
                numberScale.snapTo(1f)
                numberScale.animateTo(1.14f, tween(110))
                numberScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_set_card_$key"),
        color = if (working.isCompleted) MintGreen.copy(alpha = 0.05f) else DarkSurface,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isDrop) 30.dp else 36.dp)
                        .graphicsLayer {
                            rotationZ = if (isDrop) 0f else rotation
                            scaleX = numberScale.value
                            scaleY = numberScale.value
                        }
                        .clip(CircleShape)
                        .background(numberBackground)
                        .border(1.dp, numberBorder, CircleShape)
                        .clickable(onClick = onToggleExpanded)
                        .semantics {
                            contentDescription = if (isDrop) {
                                "Drop set ${working.dropIndex} of set ${working.setNumber} options"
                            } else {
                                "Set ${working.setNumber} options"
                            }
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        }
                        .testTag("active_set_number_$key"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDrop) "D${working.dropIndex}" else working.setNumber.toString(),
                        style = if (isDrop) {
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        } else {
                            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        color = numberTextColor
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    primaryFields.forEach { field ->
                        StepperField(
                            valueText = fieldTexts[field.id].orEmpty(),
                            keyboardType = keyboardForField(field),
                            testTag = "active_set_${fieldTestName(field)}_$key",
                            onValueChange = { input ->
                                fieldTexts[field.id] = input
                                publish(
                                    ExerciseFieldValues.write(
                                        working,
                                        field,
                                        parseFieldText(input, field, unit)
                                    )
                                )
                            },
                            onStep = { direction ->
                                val current = ExerciseFieldValues.read(working, field)
                                val next = stepFieldValue(current, field, direction, unit)
                                fieldTexts[field.id] = fieldText(
                                    ExerciseFieldValues.write(working, field, next),
                                    field,
                                    unit
                                )
                                publish(ExerciseFieldValues.write(working, field, next))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AnimatedCheckActionButton(
                    checked = working.isCompleted,
                    description = if (working.isCompleted) "Uncomplete set" else "Complete set",
                    onClick = {
                        val next = !working.isCompleted
                        working = working.copy(isCompleted = next)
                        onToggleComplete(next)
                    },
                    testTag = "active_set_complete_$key"
                )

                SquareActionButton(
                    icon = Icons.Default.Delete,
                    description = "Delete set",
                    onClick = onDelete,
                    tint = FireRed,
                    background = FireRed.copy(alpha = 0.10f),
                    border = FireRed.copy(alpha = 0.35f),
                    testTag = "active_set_delete_$key"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(140))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg)
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        color = DarkBorder,
                        thickness = 1.dp
                    )
                    if (detailFields.isNotEmpty()) {
                        SetDetailFieldsPanel(
                            fields = detailFields,
                            texts = fieldTexts,
                            unit = unit,
                            keySuffix = key,
                            onTextChange = { field, input ->
                                fieldTexts[field.id] = input
                                publish(
                                    ExerciseFieldValues.write(
                                        working,
                                        field,
                                        parseFieldText(input, field, unit)
                                    )
                                )
                            },
                            onStep = { field, direction ->
                                val current = ExerciseFieldValues.read(working, field)
                                val next = stepFieldValue(current, field, direction, unit)
                                val updated = ExerciseFieldValues.write(working, field, next)
                                fieldTexts[field.id] = fieldText(updated, field, unit)
                                publish(updated)
                            }
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = DarkBorder,
                            thickness = 1.dp
                        )
                    }
                    SetOptionsPanel(
                        set = working,
                        inheritedRest = inheritedRest,
                        restText = restText,
                        onRestTextChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(4)
                            restText = digits
                            publish(
                                working.copy(
                                    restSecondsOverride = digits.toIntOrNull()?.coerceIn(0, 3600)
                                )
                            )
                        },
                        onRestStep = { direction ->
                            val base = (working.restSecondsOverride ?: inheritedRest).coerceAtLeast(0)
                            val next = (base + direction * 15).coerceIn(0, 3600)
                            restText = next.toString()
                            publish(working.copy(restSecondsOverride = next))
                        },
                        onResetRest = {
                            restText = inheritedRest.toString()
                            publish(working.copy(restSecondsOverride = null))
                        },
                        onWarmupToggle = {
                            publish(working.copy(isWarmup = !working.isWarmup))
                        },
                        effortDisplay = effortDisplay,
                        effortRepBased = effortRepBased,
                        onEffortChange = { rpe ->
                            publish(working.copy(effortRpe = rpe))
                        }
                    )
                }
            }

            dropContent?.invoke()
        }
    }
}

@Composable
private fun SetDetailFieldsPanel(
    fields: List<ExerciseField>,
    texts: Map<String, String>,
    unit: WeightUnit,
    keySuffix: String,
    onTextChange: (ExerciseField, String) -> Unit,
    onStep: (ExerciseField, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "MORE FIELDS FOR THIS EXERCISE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan
        )
        fields.forEach { field ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    ExerciseFieldCatalog.label(field, unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.8f)
                )
                StepperField(
                    valueText = texts[field.id].orEmpty(),
                    keyboardType = keyboardForField(field),
                    testTag = "active_set_detail_${fieldTestName(field)}_$keySuffix",
                    onValueChange = { input -> onTextChange(field, input) },
                    onStep = { direction -> onStep(field, direction) },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}

private fun fieldText(set: LoggedSet, field: ExerciseField, unit: WeightUnit): String =
    when (field) {
        ExerciseField.REPS -> set.reps.toString()
        ExerciseField.WEIGHT -> formatWeight(set.weight, unit)
        ExerciseField.ADDED_WEIGHT -> formatWeight(set.addedWeight, unit)
        ExerciseField.HOLD_SECONDS -> set.holdDurationSeconds.toString()
        ExerciseField.TIME_SECONDS -> {
            val minutes = set.holdDurationSeconds / 60.0
            if (minutes % 1.0 == 0.0) minutes.toInt().toString() else "%.1f".format(minutes)
        }
        ExerciseField.DISTANCE_KM -> DistanceFormat.format(set.distanceKm, unit)
    }

private fun parseFieldText(input: String, field: ExerciseField, unit: WeightUnit): Double =
    when (field) {
        ExerciseField.REPS -> input.toIntOrNull()?.coerceAtLeast(0)?.toDouble() ?: 0.0
        ExerciseField.WEIGHT -> WeightConversion.parseToKg(input, unit) ?: 0.0
        ExerciseField.ADDED_WEIGHT -> WeightConversion.parseToKg(input, unit) ?: 0.0
        ExerciseField.HOLD_SECONDS -> input.toIntOrNull()?.coerceAtLeast(0)?.toDouble() ?: 0.0
        ExerciseField.TIME_SECONDS ->
            (input.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0) * 60.0
        ExerciseField.DISTANCE_KM -> DistanceFormat.toKm(input, unit)
    }

private fun stepFieldValue(
    current: Double,
    field: ExerciseField,
    direction: Int,
    unit: WeightUnit
): Double = when (field) {
    ExerciseField.REPS ->
        (current + direction).coerceAtLeast(0.0)
    ExerciseField.WEIGHT,
    ExerciseField.ADDED_WEIGHT ->
        (current + direction * weightStepKg(unit)).coerceAtLeast(0.0)
    ExerciseField.HOLD_SECONDS ->
        (current + direction * 5.0).coerceAtLeast(0.0)
    ExerciseField.TIME_SECONDS ->
        (current + direction * 60.0).coerceAtLeast(0.0)
    ExerciseField.DISTANCE_KM ->
        (current + direction * distanceStepKm(unit)).coerceAtLeast(0.0)
}

private fun keyboardForField(field: ExerciseField): KeyboardType = when (field) {
    ExerciseField.WEIGHT,
    ExerciseField.ADDED_WEIGHT,
    ExerciseField.DISTANCE_KM -> KeyboardType.Decimal
    else -> KeyboardType.Number
}

private fun fieldTestName(field: ExerciseField): String = when (field) {
    ExerciseField.REPS -> "reps"
    ExerciseField.WEIGHT -> "weight"
    ExerciseField.ADDED_WEIGHT -> "added_weight"
    ExerciseField.HOLD_SECONDS -> "hold"
    ExerciseField.TIME_SECONDS -> "cardio_time"
    ExerciseField.DISTANCE_KM -> "distance"
}

@Composable
private fun SetOptionsPanel(
    set: LoggedSet,
    inheritedRest: Int,
    restText: String,
    onRestTextChange: (String) -> Unit,
    onRestStep: (Int) -> Unit,
    onResetRest: () -> Unit,
    onWarmupToggle: () -> Unit,
    effortDisplay: EffortDisplay,
    effortRepBased: Boolean,
    onEffortChange: (Double?) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("active_set_options_${set.exerciseId}_${set.setNumber}")
    ) {
        val stack = maxWidth < 330.dp
        val restControl: @Composable (Modifier) -> Unit = { modifier ->
            RestForSetControl(
                set = set,
                inheritedRest = inheritedRest,
                restText = restText,
                onRestTextChange = onRestTextChange,
                onRestStep = onRestStep,
                onResetRest = onResetRest,
                modifier = modifier
            )
        }
        val warmup: @Composable (Modifier) -> Unit = { modifier ->
            WarmupToggle(
                checked = set.isWarmup,
                onToggle = onWarmupToggle,
                modifier = modifier
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stack) {
                restControl(Modifier.fillMaxWidth())
                warmup(Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    restControl(Modifier.weight(1f))
                    warmup(Modifier.weight(1f))
                }
            }
            EffortForSetControl(
                rpe = set.effortRpe,
                display = effortDisplay,
                repetitionBased = effortRepBased,
                onChange = onEffortChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EffortForSetControl(
    rpe: Double?,
    display: EffortDisplay,
    repetitionBased: Boolean,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag("active_set_effort_control")) {
        Text(
            text = "Effort for this set",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextWhite,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(DarkBg, RoundedCornerShape(9.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(9.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onChange(EffortScale.step(rpe, display, repetitionBased, -1))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                text = EffortScale.display(rpe, display, repetitionBased),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (rpe == null) TextMuted else TextWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onChange(EffortScale.step(rpe, display, repetitionBased, 1))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (rpe != null) {
            Text(
                text = "Clear effort",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onChange(null) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RestForSetControl(
    set: LoggedSet,
    inheritedRest: Int,
    restText: String,
    onRestTextChange: (String) -> Unit,
    onRestStep: (Int) -> Unit,
    onResetRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Rest for this set",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            if (set.restSecondsOverride != null) {
                // The reset action is an overlay, not a Row child. A Material IconButton enforces
                // a 48dp minimum touch target, which used to make this header 48dp tall the moment
                // an override existed and pushed the rest stepper below the warm-up control. The
                // matchParentSize layer is measured after the text, so it fills the header without
                // changing its height and the stepper stays exactly where it was.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onResetRest)
                            .testTag("active_set_rest_reset"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Use inherited rest",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        StepperBox(
            valueText = restText,
            onValueChange = onRestTextChange,
            keyboardType = KeyboardType.Number,
            suffix = "s",
            testTag = "active_set_rest_input",
            onStep = onRestStep,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                set.restSecondsOverride != null -> "Set for this set only"
                inheritedRest > 0 -> "Inherited: ${inheritedRest}s"
                else -> "Manual — no timer unless set"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WarmupToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = CoralOrange
    val boxColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.14f) else DarkBg,
        animationSpec = tween(180),
        label = "warmup_box_color"
    )
    val boxBorder by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.6f) else DarkBorder,
        animationSpec = tween(180),
        label = "warmup_box_border"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "warmup_check_scale"
    )
    Column(modifier = modifier) {
        Text(
            text = "Set type",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (checked) accent else TextWhite,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(9.dp))
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() }
                )
                .semantics {
                    contentDescription = "Warm-up set"
                    stateDescription = if (checked) "Warm-up" else "Normal set"
                }
                .testTag("active_set_warmup_toggle"),
            color = boxColor,
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(1.dp, boxBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .background(
                            if (checked) accent.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, if (checked) accent else TextMuted, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val scale = checkScale.coerceIn(0f, 1f)
                    if (scale > 0.01f) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = scale
                                    rotationZ = (1f - scale) * -35f
                                }
                        )
                    }
                }
                Text(
                    text = if (checked) "Warm-up set" else "Normal set",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (checked) accent else TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Volume, not a PR",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepperField(
    valueText: String,
    keyboardType: KeyboardType,
    testTag: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    StepperBox(
        valueText = valueText,
        onValueChange = onValueChange,
        keyboardType = keyboardType,
        testTag = testTag,
        onStep = onStep,
        modifier = modifier
    )
}

/** One rounded dark box containing the decrement, the input, and the increment. */
@Composable
private fun StepperBox(
    valueText: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    testTag: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .background(DarkBg, RoundedCornerShape(9.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            icon = Icons.Default.Remove,
            description = "Decrease value",
            onClick = { onStep(-1) },
            testTag = "${testTag}_minus"
        )
        BasicTextField(
            value = valueText,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .testTag(testTag),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    inner()
                }
            }
        )
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        StepButton(
            icon = Icons.Default.Add,
            description = "Increase value",
            onClick = { onStep(1) },
            testTag = "${testTag}_plus"
        )
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun SquareActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color,
    background: Color,
    border: Color,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AnimatedCheckActionButton(
    checked: Boolean,
    description: String,
    onClick: () -> Unit,
    testTag: String
) {
    val background by animateColorAsState(
        targetValue = if (checked) MintGreen.copy(alpha = 0.18f) else DarkBg,
        animationSpec = tween(280),
        label = "check_background_$testTag"
    )
    val border by animateColorAsState(
        targetValue = if (checked) MintGreen else DarkBorder,
        animationSpec = tween(280),
        label = "check_border_$testTag"
    )
    val ghostAlpha by animateFloatAsState(
        targetValue = if (checked) 0f else 0.45f,
        animationSpec = tween(160),
        label = "check_ghost_$testTag"
    )
    val drawProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(240),
        label = "check_draw_$testTag"
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = TextMuted.copy(alpha = ghostAlpha),
            modifier = Modifier.size(16.dp)
        )
        if (drawProgress > 0f) {
            val path = remember {
                Path().apply {
                    moveTo(5f, 12.5f)
                    lineTo(10f, 17f)
                    lineTo(19f, 7f)
                }
            }
            val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
            val length = remember(path) { measure.length }
            Canvas(modifier = Modifier.size(20.dp)) {
                val partial = Path()
                measure.getSegment(0f, length * drawProgress.coerceIn(0f, 1f), partial, true)
                scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(
                        path = partial,
                        color = MintGreen,
                        style = Stroke(width = 2.6f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseSetRows(
    exercise: Exercise,
    sets: List<LoggedSet>,
    routineItem: RoutineExerciseItem?,
    unit: WeightUnit,
    effortDisplay: EffortDisplay,
    expandedSetKey: String?,
    onToggleExpanded: (String) -> Unit,
    onUpdateSet: (LoggedSet, LoggedSet) -> Unit,
    onDeleteSet: (LoggedSet) -> Unit,
    onAddSet: (Exercise) -> Unit,
    onAddDropSet: (LoggedSet) -> Unit,
    onToggleComplete: (LoggedSet, Boolean) -> Unit,
    onDeleteExercise: () -> Unit,
    bottomPadding: Dp,
    reducedMotion: Boolean
) {
    val rows = remember(sets) { orderedSetRows(sets) }
    val scope = rememberCoroutineScope()
    var removingKeys by remember(exercise.id) { mutableStateOf<Set<String>>(emptySet()) }
    // Keys that already existed when this exercise first appeared. A key that is not in this set
    // is a set or drop set added while the exercise is open, so only that row runs the entrance
    // transition.
    val knownKeys = remember(exercise.id) { rows.map { setRowKey(it.set) }.toMutableSet() }
    var enteringKey by remember(exercise.id) { mutableStateOf<String?>(null) }
    val inheritedRest = routineItem?.restSeconds ?: 0

    // The controller removes a main set and its children together. The UI marks every affected row
    // for the collapse first, then commits once the rows have slid away, so there is no jump and no
    // orphaned branch.
    fun removeAnimated(target: LoggedSet) {
        val affected = rows.filter { row ->
            row.set.setUid == target.setUid ||
                (!target.isDropSet && row.set.parentSetUid == target.setUid)
        }.map { setRowKey(it.set) }.toSet()
        removingKeys = removingKeys + affected
        scope.launch {
            delay(210)
            onDeleteSet(target)
            removingKeys = removingKeys - affected
            knownKeys.removeAll(affected.toSet())
        }
    }

    Column {
        if (rows.isNotEmpty()) {
            SetColumnHeader(
                exercise.primaryFields.map { ExerciseFieldCatalog.shortLabel(it, unit) }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (rows.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No sets yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Add a set to start logging this exercise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // One card per main set. Its drop children and the Add drop set control live inside
            // that same card, and the branch gutter is aligned with the numbered circle above.
            rows.filter { !it.isDrop }.forEach { mainRow ->
                val main = mainRow.set
                val stableKey = setRowKey(main)
                val added = stableKey == enteringKey || knownKeys.add(stableKey)
                val visible = stableKey !in removingKeys
                val children = rows.filter {
                    it.isDrop && it.set.parentSetUid == main.setUid
                }

                key("group-$stableKey") {
                    AnimatedVisibility(
                        visible = visible,
                        enter = EnterTransition.None,
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                    ) {
                        AnimatedSetEntry(animateIn = added && !reducedMotion) {
                            ActiveWorkoutSetRow(
                                set = main,
                                exercise = exercise,
                                unit = unit,
                                inheritedRest = inheritedRest,
                                effortDisplay = effortDisplay,
                                effortRepBased = EffortScale.supportsRir(exercise),
                                expanded = expandedSetKey == stableKey,
                                isDrop = false,
                                onToggleExpanded = { onToggleExpanded(stableKey) },
                                onUpdate = { updated -> onUpdateSet(main, updated) },
                                onToggleComplete = { completed -> onToggleComplete(main, completed) },
                                onDelete = { removeAnimated(main) },
                                dropContent = {
                                    if (children.isNotEmpty()) {
                                        DropSetBranchLeadIn(
                                            width = 36.dp,
                                            gutter = 26.dp,
                                            modifier = Modifier.height(6.dp)
                                        )
                                        children.forEachIndexed { childIndex, childRow ->
                                            val child = childRow.set
                                            val childKey = setRowKey(child)
                                            val childAdded = childKey == enteringKey ||
                                                knownKeys.add(childKey)
                                            val childVisible = childKey !in removingKeys
                                            key("drop-$childKey") {
                                                // A Box overlay, not IntrinsicSize: the child
                                                // contains AnimatedVisibility, and a subcompose
                                                // layout cannot answer intrinsic measurements. The
                                                // connector is measured against the finished row and
                                                // draws at the parent circle's exact centre.
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    Canvas(modifier = Modifier.matchParentSize()) {
                                                        val gutterX = 26.dp.toPx()
                                                        val armEnd = 36.dp.toPx()
                                                        val centerY = size.height / 2f
                                                        drawLine(
                                                            color = Color(0xFF33556A),
                                                            start = androidx.compose.ui.geometry.Offset(
                                                                gutterX,
                                                                0f
                                                            ),
                                                            end = androidx.compose.ui.geometry.Offset(
                                                                gutterX,
                                                                if (childIndex == children.lastIndex) {
                                                                    centerY
                                                                } else {
                                                                    size.height
                                                                }
                                                            ),
                                                            strokeWidth = 1.2.dp.toPx()
                                                        )
                                                        drawLine(
                                                            color = Color(0xFF33556A),
                                                            start = androidx.compose.ui.geometry.Offset(
                                                                gutterX,
                                                                centerY
                                                            ),
                                                            end = androidx.compose.ui.geometry.Offset(
                                                                armEnd,
                                                                centerY
                                                            ),
                                                            strokeWidth = 1.2.dp.toPx()
                                                        )
                                                    }
                                                    DropSetRowVisibility(
                                                        visible = childVisible,
                                                        modifier = Modifier
                                                            .padding(
                                                                start = 36.dp,
                                                                end = 8.dp,
                                                                bottom = 4.dp
                                                            )
                                                    ) {
                                                        AnimatedSetEntry(
                                                            animateIn = childAdded && !reducedMotion
                                                        ) {
                                                            ActiveWorkoutSetRow(
                                                                set = child,
                                                                exercise = exercise,
                                                                unit = unit,
                                                                inheritedRest = inheritedRest,
                                                                effortDisplay = effortDisplay,
                                                                effortRepBased = EffortScale.supportsRir(exercise),
                                                                expanded = expandedSetKey == childKey,
                                                                isDrop = true,
                                                                onToggleExpanded = {
                                                                    onToggleExpanded(childKey)
                                                                },
                                                                onUpdate = { updated ->
                                                                    onUpdateSet(child, updated)
                                                                },
                                                                onToggleComplete = { completed ->
                                                                    onToggleComplete(child, completed)
                                                                },
                                                                onDelete = { removeAnimated(child) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AddDropSetButton(
                                        parent = main,
                                        onClick = { onAddDropSet(main) }
                                    )
                                    // The button used to sit 2dp from the card's bottom edge.
                                    // Keep it visually inside the card without adding a large
                                    // empty band.
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        AddSetButton(
            exerciseId = exercise.id,
            onClick = {
                enteringKey = "${exercise.id}:${sets.count { !it.isDropSet } + 1}"
                onAddSet(exercise)
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        RemoveExerciseButton(
            exerciseId = exercise.id,
            onClick = onDeleteExercise
        )
        Spacer(modifier = Modifier.height(bottomPadding))
    }
}

/**
 * A newly added set unfolds from the top and slides down into place. Existing rows are visible
 * immediately; only the row whose key just appeared runs the entrance transition.
 */
@Composable
private fun AnimatedSetEntry(
    animateIn: Boolean,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(!animateIn) }
    LaunchedEffect(animateIn) {
        if (animateIn) state.targetState = true
    }
    AnimatedVisibility(
        visibleState = state,
        enter = expandVertically(
            animationSpec = tween(320),
            expandFrom = Alignment.Top
        ) + slideInVertically(
            animationSpec = tween(320),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
    ) {
        content()
    }
}

@Composable
private fun SetColumnHeader(labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SET",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.width(72.dp))
    }
}

private fun formatWeight(kg: Double, unit: WeightUnit): String =
    WeightConversion.format(kg, unit, withUnit = false)

private fun weightStepKg(unit: WeightUnit): Double = when (unit) {
    WeightUnit.LB -> 5.0 / 2.2046226218
    else -> 2.5
}

private fun distanceStepKm(unit: WeightUnit): Double = when (unit) {
    WeightUnit.LB -> 0.1 / 0.621371
    else -> 0.1
}

@Composable
private fun BodyPartsTrainedDialog(
    progress: List<BodyPartProgress>,
    materialExpressive3: Boolean,
    onDismiss: () -> Unit
) {
    val progressMap = remember(progress) { progress.associate { it.part to it.fraction } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
                .testTag("body_parts_trained_dialog"),
            color = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DarkBorder),
            shadowElevation = 18.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Body Parts Trained",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            "Completed sets across this workout",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (progress.isEmpty()) {
                    Text(
                        "No muscle data for this workout yet. Add exercises with a mapped body part to see completion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MuscleBodyMap(
                            highlighted = null,
                            onSelect = null,
                            maxFigureHeight = 160.dp,
                            progress = progressMap,
                            compact = false
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        progress.forEach { item ->
                            BodyPartProgressRow(
                                item = item,
                                materialExpressive3 = materialExpressive3
                            )
                            Spacer(modifier = Modifier.height(9.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyPartProgressRow(
    item: BodyPartProgress,
    materialExpressive3: Boolean
) {
    Column(modifier = Modifier.testTag("body_part_progress_${item.part.name}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.part.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${item.completed} / ${item.total} sets completed",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.completed == item.total) MintGreen else TextMuted
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        WorkoutProgressBar(
            progress = item.fraction,
            expressive = materialExpressive3
        )
    }
}

/**
 * Normal Material progress unless the athlete enabled the existing Material Expressive 3 setting,
 * in which case the dot-snake treatment from the Settings preview is used. The animation depends
 * on a continuous phase, so recomposition does not restart it.
 */
@Composable
private fun WorkoutProgressBar(
    progress: Float,
    expressive: Boolean
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "body_part_progress"
    )
    if (!expressive) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("body_part_progress_bar"),
            color = ElectricCyan,
            trackColor = DarkSurfaceVariant
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "body_progress_snake")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "body_progress_phase"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .testTag("body_part_expressive_progress_bar")
    ) {
        val dot = 1.6.dp.toPx()
        val baseY = size.height / 2f
        var x = 0f
        while (x <= size.width) {
            val filled = x <= size.width * animated
            val y = baseY + sin(
                (x / size.width * 5f * PI.toFloat()) + phase * 2f * PI.toFloat()
            ) * (size.height * 0.28f)
            drawCircle(
                color = if (filled) ElectricCyan else lerp(DarkSurfaceVariant, DarkBorder, 0.5f),
                radius = dot,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
            x += dot * 2.4f
        }
    }
}
