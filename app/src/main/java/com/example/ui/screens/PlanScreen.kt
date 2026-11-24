/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.model.Exercise
import com.example.data.model.PlanGoals
import com.example.data.model.EffortDisplay
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.SupersetRules
import com.example.data.model.RoutineParser
import com.example.data.model.sanitizedFor
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduleDaySlot
import com.example.data.model.SchedulePlan
import com.example.data.model.ScheduleTemplate
import com.example.data.model.ScheduleTemplates
import com.example.data.model.Weekday
import com.example.data.model.WeightUnit
import com.example.ui.components.AnimatedDropdownField
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class PlanEditorTarget(val routine: SavedRoutine?)

@Composable
fun PlanScreen(
    viewModel: MainViewModel,
    onLaunchRoutine: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPage by remember { mutableStateOf(0) }
    var editorTarget by remember { mutableStateOf<PlanEditorTarget?>(null) }
    var lastEditorTarget by remember { mutableStateOf<PlanEditorTarget?>(null) }
    var createRoutineForAssignment by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var pendingAssignment by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val pages = listOf("Schedules", "Routines")
    val routinesListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val schedulesListState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(editorTarget) {
        if (editorTarget != null) lastEditorTarget = editorTarget
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlanSelector(
                pages = pages,
                selected = selectedPage,
                onSelected = { selectedPage = it }
            )
            when (selectedPage) {
                0 -> SchedulesPage(
                    viewModel = viewModel,
                    onOpenRoutines = { selectedPage = 1 },
                    onCreateRoutineForAssignment = { scheduleId, weekday ->
                        createRoutineForAssignment = scheduleId to weekday
                        editorTarget = PlanEditorTarget(routine = null)
                    },
                    pendingAssignment = pendingAssignment,
                    onConsumePendingAssignment = { pendingAssignment = null },
                    listState = schedulesListState
                )
                else -> RoutinesPage(
                    viewModel = viewModel,
                    onCreate = { editorTarget = PlanEditorTarget(routine = null) },
                    onEdit = { editorTarget = PlanEditorTarget(routine = it) },
                    onLaunchRoutine = onLaunchRoutine,
                    listState = routinesListState
                )
            }
        }

        AnimatedVisibility(
            visible = editorTarget != null,
            enter = slideInHorizontally(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                initialOffsetX = { it }
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetX = { it }
            ) + fadeOut(tween(150))
        ) {
            val target = editorTarget ?: lastEditorTarget
            if (target != null) {
                RoutineEditorScreen(
                    viewModel = viewModel,
                    routine = target.routine,
                    onDismiss = {
                        editorTarget = null
                        createRoutineForAssignment = null
                    },
                    onSaved = {
                        val assignment = createRoutineForAssignment
                        editorTarget = null
                        createRoutineForAssignment = null
                        if (assignment != null) {
                            pendingAssignment = assignment
                            selectedPage = 0
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanSelector(pages: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(0.dp))
            .testTag("plan_selector")
    ) {
        val tabWidth = maxWidth / pages.size
        val underlineWidth = tabWidth * 0.55f
        val underlineOffset by animateDpAsState(
            targetValue = tabWidth * selected.toFloat() + (tabWidth - underlineWidth) / 2,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "plan_selector_offset"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.forEachIndexed { index, page ->
                val active = selected == index
                val titleColor by animateColorAsState(
                    targetValue = if (active) ElectricCyan else TextMuted,
                    animationSpec = tween(220),
                    label = "plan_page_title_$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .testTag("plan_page_${page.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = page,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = underlineOffset)
                .width(underlineWidth)
                .height(3.dp)
                .background(
                    ElectricCyan,
                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                )
                .testTag("plan_selector_indicator")
        )
    }
}

@Composable
private fun RoutinesPage(
    viewModel: MainViewModel,
    onCreate: () -> Unit,
    onEdit: (SavedRoutine) -> Unit,
    onLaunchRoutine: () -> Unit,
    listState: LazyListState
) {
    val routines by viewModel.savedRoutines.collectAsStateWithLifecycleCompat()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycleCompat()
    var deleteTarget by remember { mutableStateOf<SavedRoutine?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedRoutineIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var batchDeleteOpen by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectionMode) "Select Routines" else "My Routines (${routines.size})",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        if (selectionMode) "Tap routines to select" else "Each Routine is one individual workout",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectionMode && selectedRoutineIds.isNotEmpty()) ElectricCyan else TextMuted
                    )
                }
                if (routines.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectionMode = !selectionMode
                            selectedRoutineIds = emptySet()
                        },
                        modifier = Modifier.testTag("plan_select_routines")
                    ) {
                        Text(
                            if (selectionMode) "Cancel" else "Select",
                            color = if (selectionMode) TextMuted else ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!selectionMode) {
                    IconButton(
                        onClick = onCreate,
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .size(44.dp)
                            .testTag("plan_create_routine")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Routine", tint = ElectricCyan)
                    }
                }
            }
        }

        if (selectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (selectedRoutineIds.isEmpty()) DarkBorder else ElectricCyan.copy(alpha = 0.7f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedRoutineIds.size} selected",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selectedRoutineIds.isEmpty()) TextMuted else TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { batchDeleteOpen = true },
                        enabled = selectedRoutineIds.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralOrange,
                            contentColor = OnAccent,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("plan_delete_selected_routines")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (routines.isEmpty()) {
            item {
                EmptyPlanCard(
                    title = "No Routines yet",
                    body = "A Routine is a single reusable workout: one exercise list, one set of targets. Create one here, then assign it to any days of a Schedule.",
                    action = "Create Routine",
                    onAction = onCreate
                )
            }
        } else {
            items(routines, key = { it.id }) { routine ->
                val isExpanded = expanded[routine.id] == true
                RoutineCard(
                    routine = routine,
                    exercises = RoutineParser.parseSingleWorkout(routine.exercisesJson, allExercises),
                    expanded = isExpanded,
                    selectionMode = selectionMode,
                    selected = routine.id in selectedRoutineIds,
                    onToggleSelected = {
                        selectedRoutineIds = if (routine.id in selectedRoutineIds) {
                            selectedRoutineIds - routine.id
                        } else {
                            selectedRoutineIds + routine.id
                        }
                    },
                    onToggle = { expanded[routine.id] = !isExpanded },
                    onEdit = { onEdit(routine) },
                    onCopy = { viewModel.duplicateRoutine(routine) },
                    onExport = {
                        viewModel.openExport(
                            com.example.data.export.ExportTarget.Routine(routine.id)
                        )
                    },
                    onDelete = { deleteTarget = routine },
                    onStart = {
                        viewModel.startRoutineWorkout(routine)
                        onLaunchRoutine()
                    }
                )
            }
        }
    }

    deleteTarget?.let { routine ->
        ConfirmDeleteDialog(
            title = "Delete Routine?",
            message = "“${routine.name}” will be removed from every Schedule that references it. Those days will become rest days. Completed workout history is not changed.",
            onConfirm = {
                viewModel.deleteRoutine(routine)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (batchDeleteOpen) {
        val selected = routines.filter { it.id in selectedRoutineIds }
        ConfirmDeleteDialog(
            title = "Delete ${selected.size} Routine${if (selected.size == 1) "" else "s"}?",
            message = buildString {
                append(selected.joinToString("\n") { "• ${it.name}" })
                append("\n\nEvery Schedule that references these Routines will turn those days into rest days. Completed workout history is kept.")
            },
            onConfirm = {
                viewModel.deleteRoutines(selected) {
                    selectedRoutineIds = emptySet()
                    selectionMode = false
                }
                batchDeleteOpen = false
            },
            onDismiss = { batchDeleteOpen = false }
        )
    }
}

@Composable
private fun RoutineCard(
    routine: SavedRoutine,
    exercises: List<RoutineExerciseItem>,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit
) {
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "routine_arrow"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(220))
            .clickable(enabled = selectionMode) { onToggleSelected() }
            .testTag("plan_routine_card_${routine.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ElectricCyan.copy(alpha = 0.10f) else DarkSurface
        ),
        border = BorderStroke(1.dp, if (selected) ElectricCyan else DarkBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() }
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "One workout • ${exercises.size} exercise${if (exercises.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                if (!selectionMode) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = ElectricCyan,
                            modifier = Modifier.rotate(arrow)
                        )
                    }
                }
            }
            if (routine.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    routine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(
                visible = expanded && !selectionMode,
                enter = expandVertically(tween(240), Alignment.Top) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(200), Alignment.Top) + fadeOut(tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    if (exercises.isEmpty()) {
                        Text("No exercises in this Routine.", color = TextMuted)
                    } else {
                        exercises.forEachIndexed { index, item ->
                            ExerciseLine(index + 1, item)
                        }
                    }
                }
            }
            if (!selectionMode) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnAccent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Start",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier
                            .size(46.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_export_routine_${routine.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Routine", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .size(46.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_copy_routine_${routine.id}")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Routine", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(46.dp)
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_edit_routine_${routine.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Routine", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(46.dp)
                            .background(CoralOrange.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .testTag("plan_delete_routine_${routine.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Routine", tint = CoralOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseLine(index: Int, item: RoutineExerciseItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$index.",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.exerciseName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextWhite
            )
            Text(
                buildString {
                    append("${item.targetSets} sets x ")
                    if (item.targetHoldSeconds > 0) append("${item.targetHoldSeconds}s") else append("${item.targetReps} reps")
                    if (item.restSeconds > 0) append(" • rest ${item.restSeconds}s")
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        if (item.targetAddedWeightKg > 0 || item.targetWeight > 0) {
            val load = if (item.targetAddedWeightKg > 0) {
                "+${item.targetAddedWeightKg} kg"
            } else {
                "${item.targetWeight} kg"
            }
            Text(load, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
        }
    }
}

@Composable
private fun SchedulesPage(
    viewModel: MainViewModel,
    onOpenRoutines: () -> Unit,
    onCreateRoutineForAssignment: (Long, Int) -> Unit,
    pendingAssignment: Pair<Long, Int>?,
    onConsumePendingAssignment: () -> Unit,
    listState: LazyListState
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycleCompat()
    val slots by viewModel.scheduleSlots.collectAsStateWithLifecycleCompat()
    val routines by viewModel.savedRoutines.collectAsStateWithLifecycleCompat()
    val activeId by viewModel.activeRoutineId.collectAsStateWithLifecycleCompat()
    val weekStartsOnMonday by viewModel.weekStartFlow.collectAsStateWithLifecycleCompat()
    var createDialog by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf<SchedulePlan?>(null) }
    var deleteSchedule by remember { mutableStateOf<SchedulePlan?>(null) }
    var assignment by remember { mutableStateOf<Pair<SchedulePlan, Int>?>(null) }
    var startScheduleForDialog by remember { mutableStateOf<SchedulePlan?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedScheduleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var batchDeleteOpen by remember { mutableStateOf(false) }
    var starterExpanded by rememberSaveable { mutableStateOf(false) }
    var copyingTemplateId by remember { mutableStateOf<String?>(null) }
    var copiedTemplateId by remember { mutableStateOf<String?>(null) }
    var starterError by remember { mutableStateOf<String?>(null) }
    val copyFeedbackScope = androidx.compose.runtime.rememberCoroutineScope()
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    val routineById = remember(routines) { routines.associateBy { it.id } }
    val weekdayOrder = remember(weekStartsOnMonday) { Weekday.ordered(weekStartsOnMonday) }

    androidx.compose.runtime.LaunchedEffect(pendingAssignment, schedules) {
        val pending = pendingAssignment ?: return@LaunchedEffect
        schedules.firstOrNull { it.id == pending.first }?.let { schedule ->
            assignment = schedule to pending.second
            onConsumePendingAssignment()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectionMode) "Select Schedules" else "My Schedules (${schedules.size})",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        if (selectionMode) "Tap schedules to select" else "A Schedule assigns one Routine to each weekday",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectionMode && selectedScheduleIds.isNotEmpty()) ElectricCyan else TextMuted
                    )
                }
                if (schedules.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectionMode = !selectionMode
                            selectedScheduleIds = emptySet()
                        },
                        modifier = Modifier.testTag("plan_select_schedules")
                    ) {
                        Text(
                            if (selectionMode) "Cancel" else "Select",
                            color = if (selectionMode) TextMuted else ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!selectionMode) {
                    IconButton(
                        onClick = { createDialog = true },
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .size(44.dp)
                            .testTag("plan_create_schedule")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Schedule", tint = ElectricCyan)
                    }
                }
            }
        }

        if (selectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (selectedScheduleIds.isEmpty()) DarkBorder else ElectricCyan.copy(alpha = 0.7f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedScheduleIds.size} selected",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selectedScheduleIds.isEmpty()) TextMuted else TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { batchDeleteOpen = true },
                        enabled = selectedScheduleIds.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralOrange,
                            contentColor = OnAccent,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("plan_delete_selected_schedules")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (schedules.isEmpty()) {
            item {
                EmptyPlanCard(
                    title = "No Schedules yet",
                    body = "A Schedule is a recurring week of seven weekday slots. Copy a starter template below, or build one from your own Routines.",
                    action = "Create Schedule",
                    onAction = { createDialog = true }
                )
            }
        } else {
            items(schedules, key = { it.id }) { schedule ->
                val isExpanded = expanded[schedule.id] == true
                val days = weekdayOrder.map { weekday ->
                    schedule to slots.firstOrNull {
                        it.scheduleId == schedule.id && it.weekday == weekday
                    }
                }
                ScheduleCard(
                    schedule = schedule,
                    active = schedule.id == activeId,
                    expanded = isExpanded,
                    selectionMode = selectionMode,
                    selected = schedule.id in selectedScheduleIds,
                    onToggleSelected = {
                        selectedScheduleIds = if (schedule.id in selectedScheduleIds) {
                            selectedScheduleIds - schedule.id
                        } else {
                            selectedScheduleIds + schedule.id
                        }
                    },
                    days = days,
                    routineById = routineById,
                    onToggle = { expanded[schedule.id] = !isExpanded },
                    onActivate = { viewModel.setActiveSchedule(schedule.id) },
                    onStartSchedule = { startScheduleForDialog = schedule },
                    onEdit = { editSchedule = schedule },
                    onExport = {
                        viewModel.openExport(
                            com.example.data.export.ExportTarget.Schedule(schedule.id)
                        )
                    },
                    onDelete = { deleteSchedule = schedule },
                    onCopy = { viewModel.duplicateSchedule(schedule.id) },
                    onDayClick = { weekday -> assignment = schedule to weekday }
                )
            }
        }

        if (!selectionMode) {
            item {
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan_starter_header"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { starterExpanded = !starterExpanded }
                            .testTag("plan_starter_toggle"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Starter Schedules",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Text(
                                "${ScheduleTemplates.all.size} ready-made weekly plans",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = if (starterExpanded) "Collapse starters" else "Expand starters",
                            tint = ElectricCyan,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (starterExpanded) 180f else 0f)
                        )
                    }
                    AnimatedVisibility(
                        visible = starterExpanded,
                        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Copies create independent Schedules and Routines that you can edit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            ScheduleTemplates.all.forEach { template ->
                                TemplateCard(
                                    template = template,
                                    copying = copyingTemplateId == template.id,
                                    copied = copiedTemplateId == template.id,
                                    copyEnabled = copyingTemplateId == null,
                                    onCopy = {
                                        if (copyingTemplateId == null) {
                                            copyingTemplateId = template.id
                                            starterError = null
                                            viewModel.copyScheduleTemplate(
                                                template = template,
                                                onCopied = {
                                                    copyingTemplateId = null
                                                    copiedTemplateId = template.id
                                                    copyFeedbackScope.launch {
                                                        delay(1500)
                                                        if (copiedTemplateId == template.id) {
                                                            copiedTemplateId = null
                                                        }
                                                    }
                                                },
                                                onFailed = { message ->
                                                    copyingTemplateId = null
                                                    starterError = message
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                            if (starterError != null) {
                                Text(
                                    text = starterError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoralOrange
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (createDialog) {
        ScheduleInfoDialog(
            existing = null,
            onDismiss = { createDialog = false },
            onSave = { name, description, goal ->
                viewModel.createSchedule(name, description, goal)
                createDialog = false
            }
        )
    }
    editSchedule?.let { schedule ->
        ScheduleInfoDialog(
            existing = schedule,
            onDismiss = { editSchedule = null },
            onSave = { name, description, goal ->
                viewModel.updateSchedule(schedule.copy(name = name, description = description, goal = goal))
                editSchedule = null
            }
        )
    }
    deleteSchedule?.let { schedule ->
        ConfirmDeleteDialog(
            title = "Delete Schedule?",
            message = "“${schedule.name}” will be deleted. Its Routines and completed workout history are kept.",
            onConfirm = {
                viewModel.deleteSchedule(schedule.id)
                deleteSchedule = null
            },
            onDismiss = { deleteSchedule = null }
        )
    }

    startScheduleForDialog?.let { schedule ->
        val assignedDays = weekdayOrder.mapNotNull { weekday ->
            val routineId = slots.firstOrNull {
                it.scheduleId == schedule.id && it.weekday == weekday
            }?.routineId ?: return@mapNotNull null
            val routine = routineById[routineId] ?: return@mapNotNull null
            weekday to routine
        }
        StartScheduleDialog(
            schedule = schedule,
            assignedDays = assignedDays,
            onSelect = { weekday, routine ->
                viewModel.startScheduledWorkout(
                    routine = routine,
                    schedule = schedule,
                    weekday = weekday,
                    plannedDate = 0L
                )
                startScheduleForDialog = null
            },
            onDismiss = { startScheduleForDialog = null }
        )
    }

    if (batchDeleteOpen) {
        val selected = schedules.filter { it.id in selectedScheduleIds }
        ConfirmDeleteDialog(
            title = "Delete ${selected.size} Schedule${if (selected.size == 1) "" else "s"}?",
            message = buildString {
                append(selected.joinToString("\n") { "• ${it.name}" })
                append("\n\nThe Schedule records are removed. Their Routines remain available, and completed workout history is kept.")
                if (selected.any { it.id == activeId }) {
                    append("\n\nThe active Schedule will be cleared.")
                }
            },
            onConfirm = {
                viewModel.deleteSchedules(selected.map { it.id }) {
                    selectedScheduleIds = emptySet()
                    selectionMode = false
                }
                batchDeleteOpen = false
            },
            onDismiss = { batchDeleteOpen = false }
        )
    }
    assignment?.let { (schedule, weekday) ->
        AssignRoutineDialog(
            schedule = schedule,
            weekday = weekday,
            routines = routines,
            selectedRoutineId = slots.firstOrNull {
                it.scheduleId == schedule.id && it.weekday == weekday
            }?.routineId,
            onSelect = { routineId ->
                viewModel.setScheduleDay(schedule.id, weekday, routineId)
                assignment = null
            },
            onCreateRoutine = {
                assignment = null
                onCreateRoutineForAssignment(schedule.id, weekday)
            },
            onOpenRoutines = {
                assignment = null
                onOpenRoutines()
            },
            onDismiss = { assignment = null }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: SchedulePlan,
    active: Boolean,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    days: List<Pair<SchedulePlan, ScheduleDaySlot?>>,
    routineById: Map<Long, SavedRoutine>,
    onToggle: () -> Unit,
    onActivate: () -> Unit,
    onStartSchedule: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "schedule_arrow"
    )
    val trainingDays = days.count { (_, slot) ->
        slot?.routineId?.let(routineById::get) != null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(230))
            .clickable(enabled = selectionMode) { onToggleSelected() }
            .testTag("plan_schedule_card_${schedule.id}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> ElectricCyan.copy(alpha = 0.10f)
                active -> ElectricCyan.copy(alpha = 0.07f)
                else -> DarkSurface
            }
        ),
        border = BorderStroke(1.dp, if (selected || active) ElectricCyan else DarkBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() }
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            schedule.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (active) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OnAccent,
                                modifier = Modifier
                                    .background(ElectricCyan, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "$trainingDays training day${if (trainingDays == 1) "" else "s"} • ${schedule.goal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                if (!selectionMode) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = ElectricCyan,
                            modifier = Modifier.rotate(arrow)
                        )
                    }
                }
            }
            if (schedule.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    schedule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(
                visible = expanded && !selectionMode,
                enter = expandVertically(tween(250), Alignment.Top) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(210), Alignment.Top) + fadeOut(tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(Modifier.height(8.dp))
                    days.forEach { (_, slot) ->
                        val routine = slot?.routineId?.let(routineById::get)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(10.dp))
                                .clickable { onDayClick(slot?.weekday ?: 1) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Weekday.shortName(slot?.weekday ?: 1),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextMuted,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                routine?.name ?: "Rest Day",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (routine == null) TextMuted else TextWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (routine == null) "REST" else "${RoutineParser.parseSingleWorkout(routine.exercisesJson, emptyList()).size} ex",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (routine == null) TextMuted else ElectricCyan
                            )
                        }
                    }
                }
            }
            if (trainingDays == 0 && !selectionMode) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No workouts assigned yet. Tap a day to add a Routine.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            if (!selectionMode) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartSchedule,
                        enabled = trainingDays > 0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("plan_start_schedule_${schedule.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = OnAccent,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Start Workout", fontWeight = FontWeight.Bold)
                    }
                    if (!active) {
                        OutlinedButton(
                            onClick = onActivate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Set active", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_export_schedule_${schedule.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Schedule", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_copy_schedule_${schedule.id}")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Schedule", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("plan_edit_schedule_${schedule.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .background(CoralOrange.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .testTag("plan_delete_schedule_${schedule.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Schedule", tint = CoralOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ScheduleTemplate,
    copying: Boolean,
    copied: Boolean,
    copyEnabled: Boolean,
    onCopy: () -> Unit
) {
    val copyScale by animateFloatAsState(
        targetValue = if (copied) 1.16f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "template_copy_pop_${template.id}"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            1.dp,
            if (copied) MintGreen.copy(alpha = 0.7f) else DarkBorder
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = ElectricCyan)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(
                    "${template.trainingDays} days • ${template.goal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    template.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onCopy,
                enabled = copyEnabled && !copying,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = copyScale
                        scaleY = copyScale
                    }
                    .background(
                        if (copied) MintGreen.copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("plan_copy_template_${template.id}")
            ) {
                when {
                    copying -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ElectricCyan,
                        strokeWidth = 2.dp
                    )
                    copied -> Icon(
                        Icons.Default.Check,
                        contentDescription = "Template copied",
                        tint = MintGreen
                    )
                    else -> Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy template",
                        tint = if (copyEnabled) ElectricCyan else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlanCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextWhite)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OnAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScheduleInfoDialog(
    existing: SchedulePlan?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var goal by remember { mutableStateOf(existing?.goal ?: PlanGoals.all.first()) }
    var goalOpen by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { visible = true }

    fun closeWithAnimation() {
        if (!visible) return
        visible = false
        scope.launch {
            delay(150)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { closeWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = maxHeight * 0.9f
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)) +
                    scaleIn(tween(220), initialScale = 0.96f),
                exit = fadeOut(tween(140)) +
                    scaleOut(tween(170), targetScale = 0.97f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxDialogHeight)
                        .animateContentSize(tween(220))
                        .testTag("schedule_info_dialog"),
                    color = DarkSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (existing == null) "Create Schedule" else "Edit Schedule",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Text(
                                    "Seven weekday slots and one goal strategy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            IconButton(onClick = { closeWithAnimation() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Schedule Name *") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = planFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("schedule_name_input")
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description (optional)") },
                                placeholder = { Text("What is this weekly plan for?") },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = planFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            AnimatedDropdownField(
                                label = "Goal Strategy *",
                                value = goal,
                                icon = Icons.Default.Flag,
                                accent = ElectricCyan,
                                options = PlanGoals.all,
                                expanded = goalOpen,
                                onExpandedChange = { goalOpen = it },
                                onOptionSelected = {
                                    goal = it
                                    goalOpen = false
                                },
                                fieldTestTag = "schedule_goal_dropdown",
                                optionTestTagPrefix = "schedule_goal_option"
                            )
                            Text(
                                text = PlanGoals.description(goal),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .animateContentSize(tween(180))
                                    .testTag("schedule_goal_description")
                            )
                            Spacer(Modifier.height(2.dp))
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { closeWithAnimation() }) {
                                Text("Cancel", color = TextMuted)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        onSave(name.trim(), description.trim(), goal)
                                    }
                                },
                                enabled = name.isNotBlank(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = OnAccent
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("schedule_info_save")
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartScheduleDialog(
    schedule: SchedulePlan,
    assignedDays: List<Pair<Int, SavedRoutine>>,
    onSelect: (Int, SavedRoutine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    "Start a workout",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    schedule.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            if (assignedDays.isEmpty()) {
                Text(
                    "This Schedule has no assigned workouts yet. Assign a Routine to at least one weekday first.",
                    color = TextMuted
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choose a weekday workout. This will not change your active Schedule.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    assignedDays.forEach { (weekday, routine) ->
                        val exerciseCount = RoutineParser
                            .parseSingleWorkout(routine.exercisesJson, emptyList())
                            .size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { onSelect(weekday, routine) }
                                .padding(12.dp)
                                .testTag("plan_start_schedule_day_$weekday"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.13f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Weekday.shortName(weekday),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    routine.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$exerciseCount exercise${if (exerciseCount == 1) "" else "s"} • ${schedule.goal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start ${routine.name}",
                                tint = ElectricCyan
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun AssignRoutineDialog(
    schedule: SchedulePlan,
    weekday: Int,
    routines: List<SavedRoutine>,
    selectedRoutineId: Long?,
    onSelect: (Long?) -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutines: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(18.dp),
        title = { Text("${Weekday.name(weekday)} in ${schedule.name}", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            if (routines.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Create a Routine first, then come back and assign it to this day.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onCreateRoutine,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OnAccent)
                    ) { Text("Create Routine") }
                    OutlinedButton(onClick = onOpenRoutines, border = BorderStroke(1.dp, ElectricCyan)) {
                        Text("Open Routines", color = ElectricCyan)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    routines.forEach { routine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (routine.id == selectedRoutineId) ElectricCyan.copy(alpha = 0.15f)
                                    else DarkBg,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelect(routine.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(routine.name, color = TextWhite, fontWeight = FontWeight.SemiBold)
                                Text(
                                    RoutineParser.parseSingleWorkout(routine.exercisesJson, emptyList()).size.toString() + " exercises",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            if (routine.id == selectedRoutineId) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = ElectricCyan)
                            }
                        }
                    }
                    TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear this day (Rest Day)", color = CoralOrange)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, color = TextWhite, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = TextMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = CoralOrange, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun RoutineEditorScreen(
    viewModel: MainViewModel,
    routine: SavedRoutine?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycleCompat()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycleCompat()
    val effortDisplay by viewModel.appSettings
        .map { it.effortDisplay }
        .collectAsState(initial = EffortDisplay.RIR)
    val exerciseDefaultRests by viewModel.exerciseDefaultRests.collectAsStateWithLifecycleCompat()
    val autoplayExerciseDemos by viewModel.autoplayExerciseDemos.collectAsStateWithLifecycleCompat()
    var exerciseShowingDetails by remember { mutableStateOf<Exercise?>(null) }
    var name by remember { mutableStateOf(routine?.name.orEmpty()) }
    var description by remember { mutableStateOf(routine?.description.orEmpty()) }
    var showExerciseSearch by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var expandedExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var supersetDialogIndex by remember { mutableStateOf<Int?>(null) }
    val routineItems = remember(routine?.id) {
        mutableStateOf(
            routine?.let { saved ->
                runCatching { RoutineParser.parseSingleWorkout(saved.exercisesJson, allExercises) }
                    .getOrElse { emptyList() }
            }.orEmpty()
        )
    }

    androidx.compose.runtime.LaunchedEffect(routine?.id, allExercises.size) {
        if (routine != null && allExercises.isNotEmpty() && routineItems.value.isEmpty()) {
            routineItems.value = runCatching {
                RoutineParser.parseSingleWorkout(routine.exercisesJson, allExercises)
            }.getOrElse { emptyList() }
        }
    }

    val normalizedItems = routineItems.value.map { item ->
        val fields = allExercises.firstOrNull {
            it.id == item.exerciseId || it.name.equals(item.exerciseName, ignoreCase = true)
        }?.exerciseFields
        val sanitized = if (fields == null) item else item.sanitizedFor(fields)
        sanitized.copy(
            weekday = 0,
            dayTitle = "",
            dayNote = "",
            prescription = RoutineParser.buildPrescription(sanitized)
        )
    }
    val canSave = name.isNotBlank() && normalizedItems.isNotEmpty() && !saving

    fun requestRoutineSuperset(index: Int) {
        val items = routineItems.value
        if (items.size <= 1 || index !in items.indices) return
        val linked = !items[index].supersetGroupId.isNullOrBlank()
        // One valid neighbour at either end: link immediately. A linked pair still opens the
        // dialog so the athlete can unlink it.
        if (!linked && index == 0) {
            routineItems.value = SupersetRules.link(items, index, 1)
            return
        }
        if (!linked && index == items.lastIndex) {
            routineItems.value = SupersetRules.link(items, index, -1)
            return
        }
        supersetDialogIndex = index
    }

    fun saveRoutine() {
        if (!canSave) return
        saving = true
        if (routine == null) {
            viewModel.saveCustomRoutine(
                name = name,
                description = description,
                daysPerWeek = 1,
                goal = "Standalone Workout",
                exercises = normalizedItems,
                onSaved = {
                    saving = false
                    onSaved()
                }
            )
        } else {
            viewModel.saveEditedRoutine(
                routine.copy(
                    name = name.trim(),
                    description = description.trim(),
                    exercisesJson = RoutineParser.serializeExercises(normalizedItems),
                    daysPerWeek = 1,
                    modelVersion = 2,
                    scheduleVersion = 1
                ),
                onSaved = {
                    saving = false
                    onSaved()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("plan_routine_close")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Back", tint = ElectricCyan)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (routine == null) "Create New Routine" else "Edit Routine",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${routineItems.value.size} exercise${if (routineItems.value.size == 1) "" else "s"} added",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (routineItems.value.isEmpty()) TextMuted else MintGreen
                )
            }
            Button(
                onClick = { saveRoutine() },
                enabled = canSave,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = OnAccent,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextMuted
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("plan_routine_save")
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = OnAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (routine == null) "Save" else "Update",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "ROUTINE DETAILS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Routine Name *") },
                            placeholder = { Text("e.g. Push Day") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = planFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("routine_name_input")
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)") },
                            placeholder = { Text("What does this workout train?") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = planFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "WORKOUT EXERCISES (${routineItems.value.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                        Text(
                            "Use set defaults to edit targets, rest and drop sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    OutlinedButton(
                        onClick = { showExerciseSearch = true },
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("plan_add_exercises")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Add Exercise", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            if (routineItems.value.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(ElectricCyan.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No exercises added yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Search the full catalog and build this workout one exercise at a time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { showExerciseSearch = true },
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = OnAccent
                                )
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Search Exercises", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            itemsIndexed(routineItems.value) { index, item ->
                SelectedRoutineExerciseRow(
                    index = index,
                    item = item,
                    unit = unit,
                    expanded = expandedExerciseIndex == index,
                    supportedFields = allExercises.firstOrNull {
                        it.id == item.exerciseId ||
                            it.name.equals(item.exerciseName, ignoreCase = true)
                    }?.exerciseFields ?: com.example.data.model.ExerciseFieldCatalog.defaultsForType(item.exerciseType),
                    canMoveUp = index > 0,
                    canMoveDown = index < routineItems.value.lastIndex,
                    onMoveUp = {
                        val moved = SupersetRules.moveBlock(routineItems.value, index, index - 1)
                        if (moved != routineItems.value) {
                            routineItems.value = moved
                            expandedExerciseIndex = (index - 1).coerceAtLeast(0)
                        }
                    },
                    onMoveDown = {
                        val moved = SupersetRules.moveBlock(routineItems.value, index, index + 1)
                        if (moved != routineItems.value) {
                            routineItems.value = moved
                            expandedExerciseIndex = (index + 1).coerceAtMost(moved.lastIndex)
                        }
                    },
                    onToggleExpanded = {
                        expandedExerciseIndex = if (expandedExerciseIndex == index) null else index
                    },
                    onShowDetails = {
                        allExercises.firstOrNull {
                            it.id == item.exerciseId ||
                                it.name.equals(item.exerciseName, ignoreCase = true)
                        }?.let { exerciseShowingDetails = it }
                    },
                    onUpdate = { updated ->
                        routineItems.value = routineItems.value.toMutableList().also {
                            it[index] = updated
                        }
                    },
                    onDelete = {
                        routineItems.value = routineItems.value.toMutableList().also {
                            it.removeAt(index)
                        }
                        expandedExerciseIndex = null
                    },
                    isSuperset = !item.supersetGroupId.isNullOrBlank(),
                    showSupersetAction = routineItems.value.size > 1,
                    onSuperset = { requestRoutineSuperset(index) },
                    effortDisplay = effortDisplay
                )
            }
        }
    }

    if (showExerciseSearch) {
        PlanExerciseSearchDialog(
            allExercises = allExercises,
            alreadyAddedIds = routineItems.value.map { it.exerciseId }.toSet(),
            unitSuffix = unit.suffix,
            onAdd = { exercise ->
                if (routineItems.value.none { it.exerciseId == exercise.id }) {
                    routineItems.value = routineItems.value + defaultRoutineItemFor(
                        exercise,
                        weekday = 0,
                        defaultRestSeconds = exerciseDefaultRests[exercise.id] ?: 0
                    )
                }
            },
            onDismiss = { showExerciseSearch = false }
        )
    }

    supersetDialogIndex?.let { index ->
        SupersetEditorDialog(
            items = routineItems.value,
            index = index,
            onLink = { direction ->
                routineItems.value = SupersetRules.link(routineItems.value, index, direction)
                supersetDialogIndex = null
            },
            onUnlink = {
                routineItems.value = SupersetRules.unlink(routineItems.value, index)
                supersetDialogIndex = null
            },
            onDismiss = { supersetDialogIndex = null }
        )
    }

    // Same read-only sheet as the Create/Edit Routine page: no pen and no live Add Set.
    exerciseShowingDetails?.let { detailExercise ->
        ExerciseDetailDialog(
            exercise = detailExercise,
            unit = unit.suffix,
            onAddSet = null,
            onDismiss = { exerciseShowingDetails = null },
            autoplayDemos = autoplayExerciseDemos,
            onEdit = null
        )
    }
}

@Composable
private fun SupersetEditorDialog(
    items: List<RoutineExerciseItem>,
    index: Int,
    onLink: (Int) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    val linked = !items.getOrNull(index)?.supersetGroupId.isNullOrBlank()
    val canPrevious = SupersetRules.canLink(items, index, -1)
    val canNext = SupersetRules.canLink(items, index, 1)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
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
                if (canPrevious && canNext) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SupersetEditorAction(
                            "Link previous",
                            modifier = Modifier.weight(1f)
                        ) { onLink(-1) }
                        SupersetEditorAction(
                            "Link following",
                            modifier = Modifier.weight(1f)
                        ) { onLink(1) }
                    }
                } else {
                    if (canPrevious) {
                        SupersetEditorAction("Link previous", Modifier.fillMaxWidth()) { onLink(-1) }
                    }
                    if (canNext) {
                        SupersetEditorAction("Link following", Modifier.fillMaxWidth()) { onLink(1) }
                    }
                }
                if (linked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SupersetEditorAction(
                        "Unlink superset",
                        Modifier.fillMaxWidth(),
                        CoralOrange,
                        onUnlink
                    )
                }
            }
        }
    }
}

@Composable
private fun SupersetEditorAction(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = ElectricCyan,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f))
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
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun planFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkBg,
    unfocusedContainerColor = DarkBg,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted
)

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> =
    collectAsState()
