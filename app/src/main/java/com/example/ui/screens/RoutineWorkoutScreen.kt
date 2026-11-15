package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ActiveRoutineWorkoutState
import com.example.data.model.Exercise
import com.example.data.model.DistanceFormat
import com.example.data.model.ExerciseType
import com.example.data.model.WeightUnit
import com.example.data.model.WeightConversion
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineParser
import com.example.data.model.WorkoutMetrics
import com.example.ui.components.RestTimerDockedButton
import com.example.ui.components.RestTimerDialog
import com.example.ui.components.LocalLiquidGlassEnabled
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassBackdrop
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
import com.example.ui.theme.OnAccent
import com.example.ui.theme.OnDanger
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.ExerciseEditorDialog
import com.example.ui.components.WorkoutTimerBadge
import kotlinx.coroutines.flow.map
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun RoutineWorkoutScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    /*
     * The workout state to draw, when the screen is being animated out.
     *
     * The view model has already cleared the active workout by then, and without a snapshot the
     * exiting screen would flash its "no active workout" placeholder while it slides away.
     */
    workoutSnapshot: ActiveRoutineWorkoutState? = null
) {
    val liveWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val activeWorkout = workoutSnapshot ?: liveWorkout
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    // Weights live in kilograms; this is the unit the athlete reads and types in
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val exerciseDefaultRests by viewModel.exerciseDefaultRests.collectAsStateWithLifecycle()
    val unit = weightUnit.suffix
    // Follows the "Autoplay exercise demos" setting, so the animation a routine workout shows behaves
    // like the one in the catalog instead of always looping.
    val autoplayExerciseDemos by viewModel.appSettings
        .map { it.autoplayExerciseDemos }
        .collectAsStateWithLifecycle(initialValue = true)
    val materialExpressive3 by viewModel.appSettings
        .map { it.materialExpressive3 }
        .collectAsStateWithLifecycle(initialValue = false)
    // When the workout in progress was last written to disk, for the "auto saved" indicator.
    val lastAutoSaveAt by viewModel.lastAutoSaveAt.collectAsStateWithLifecycle()
    val glassEnabled = LocalLiquidGlassEnabled.current
    val actionBarShape = RoundedCornerShape(16.dp)

    if (activeWorkout == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No active routine workout in progress.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val workout = activeWorkout!!
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showRestTimerDialog by rememberSaveable { mutableStateOf(false) }
    var finishNotes by remember { mutableStateOf("") }
    // The exercise the athlete tapped the name of. Same dialog as the Exercises tab, opened without
    // leaving the workout: the timer keeps running and the set numbering stays where it was.
    var exerciseShowingDetails by remember { mutableStateOf<Exercise?>(null) }
    var actionBarBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler {
        showExitDialog = true
    }

    // Live elapsed session timer
    var elapsedTimeSeconds by remember {
        mutableLongStateOf((System.currentTimeMillis() - workout.startTime) / 1000)
    }

    LaunchedEffect(workout.startTime) {
        while (true) {
            elapsedTimeSeconds = (System.currentTimeMillis() - workout.startTime).coerceAtLeast(0) / 1000
            delay(1000)
        }
    }

    val minutes = elapsedTimeSeconds / 60
    val seconds = elapsedTimeSeconds % 60
    val timerFormatted = "%02d:%02d".format(minutes, seconds)

    val totalSetsCount = workout.sets.size
    val completedSetsCount = workout.sets.count { it.isCompleted }
    val progressFraction = if (totalSetsCount > 0) completedSetsCount.toFloat() / totalSetsCount.toFloat() else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // The whole screen lifts with the keyboard.
        //
        // This is a list of weight and rep fields, and the set the athlete is editing is often the
        // last one on screen. Without this, the keyboard rose over the bottom of the list and covered
        // the very field they had just tapped, so they were typing blind. Resizing the root rather
        // than each row is what makes the list reflow and scroll to reveal the focused field, and it
        // keeps the top bar visible so exiting the workout stays possible mid edit.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .liquidGlassBackdrop(
                    enabled = glassEnabled,
                    bounds = actionBarBounds,
                    cornerRadius = 16.dp,
                    blurRadius = 30.dp,
                    scrimAlpha = 0.28f
                )
        ) {
            // === TOP BAR ===
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Exit / Stop Button
                        IconButton(
                            onClick = { showExitDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("exit_routine_workout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Workout",
                                tint = FireRed
                            )
                        }

                        // Title & Subtitle
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = workout.routineName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${workout.exercises.size} exercises • In-Progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }

                        // Live timer badge with the auto save cloud inside it, on the left of the
                        // time. The cloud says the session is written down, and pops a check when
                        // a save lands.
                        WorkoutTimerBadge(
                            timerText = timerFormatted,
                            lastAutoSaveAt = lastAutoSaveAt,
                            timerColor = MintGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MintGreen,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (workout.dayNote.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(DarkBg, RoundedCornerShape(10.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = workout.dayNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ActiveWorkoutInterface(
                    exercises = workout.exercises,
                    sets = workout.sets,
                    unit = weightUnit,
                    workoutKey = workout.startTime,
                    routineItems = workout.routineItems,
                    allExercises = allExercises,
                    materialExpressive3 = materialExpressive3,
                    bottomPadding = 112.dp,
                    onAddSet = { viewModel.addSetToRoutineWorkout(it) },
                    onAddDropSet = { viewModel.addDropSetToRoutineWorkout(it) },
                    onToggleSetComplete = { setToUpdate, completed ->
                        val wasCompleted = setToUpdate.isCompleted
                        viewModel.toggleRoutineWorkoutSetCompletion(setToUpdate, completed)
                        if (!wasCompleted && completed) {
                            val index = workout.exercises.indexOfFirst {
                                it.id == setToUpdate.exerciseId
                            }
                            val item = workout.routineItems.getOrNull(index)
                            val rest = WorkoutMetrics.effectiveRestSeconds(
                                setToUpdate,
                                item?.restSeconds ?: 0
                            )
                            if (rest > 0) {
                                viewModel.startRestTimer(rest, setToUpdate.exerciseName)
                            }
                        }
                    },
                    onUpdateSet = { setToUpdate, updatedSet ->
                        viewModel.updateRoutineWorkoutSet(setToUpdate, updatedSet)
                    },
                    onDeleteSet = { viewModel.deleteRoutineWorkoutSet(it) },
                    onAddExercise = { viewModel.addExerciseToRoutineWorkout(it) },
                    onRemoveExercise = { viewModel.removeExerciseFromRoutineWorkout(it) },
                    onRestoreExercise = { viewModel.restoreExerciseToRoutineWorkout(it) },
                    onMoveExercise = { from, to -> viewModel.moveRoutineExercise(from, to) },
                    onShowDetails = { exerciseShowingDetails = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // === FLOATING BOTTOM ACTION BAR WITH REST TIMER ===
        // The active workout is rendered instead of the main navigation host, so there is no
        // floating dock to clear here. The action bar belongs at the bottom edge, as it was before
        // the navigation clearance was added for the tab screens.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // Small button on the lower left (right on top of the number of sets completed)
            RestTimerDockedButton(
                viewModel = viewModel,
                onOpenDialog = { showRestTimerDialog = true },
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .onGloballyPositioned { actionBarBounds = it.boundsInRoot() }
                    .then(
                        if (glassEnabled) {
                            Modifier.liquidGlass(
                                shape = actionBarShape,
                                enabled = true,
                                tint = ElectricCyan,
                                cornerRadius = 16.dp,
                                elevation = 14.dp,
                                // The set list behind this bar is blurred by liquidGlassBackdrop.
                                // A lighter body lets that real blur show instead of painting the
                                // old top-lit white gradient over it.
                                backdropBlurred = true
                            )
                        } else {
                            Modifier
                        }
                    ),
                shape = actionBarShape,
                color = if (glassEnabled) Color.Transparent else DarkSurface,
                border = if (glassEnabled) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                },
                shadowElevation = if (glassEnabled) 0.dp else 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$completedSetsCount / $totalSetsCount Sets Completed",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (completedSetsCount == totalSetsCount && totalSetsCount > 0) MintGreen else TextWhite
                        )
                        Text(
                            text = "Elapsed: $timerFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Stop / Exit Button
                        OutlinedButton(
                            onClick = { showExitDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FireRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FireRed.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("routine_exit_bottom_button")
                        ) {
                            Text(
                                text = "Exit",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Finish Button
                        Button(
                            onClick = { showFinishDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("routine_finish_bottom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = OnAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Finish",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = OnAccent
                            )
                        }
                    }
                }
            }
        }
    }

    // === REST TIMER DIALOG ===
    if (showRestTimerDialog) {
        RestTimerDialog(
            viewModel = viewModel,
            onDismiss = { showRestTimerDialog = false }
        )
    }

    // === EXERCISE DETAIL DIALOG ===
    // The same dialog the Exercises tab opens, reused rather than rebuilt. It is what the athlete
    // sees when they tap an exercise name, and it has to be the same component or the two views of
    // one exercise drift apart the first time either changes.
    //
    // Media is attached from the Exercises tab only: the picker lives there with the edit flow, and
    // two pickers competing for one content uri is how a workout ends up with the wrong video.
    exerciseShowingDetails?.let { detailExercise ->
        ExerciseDetailDialog(
            exercise = detailExercise,
            unit = unit,
            onAddSet = {
                exerciseShowingDetails = null
                viewModel.addSetToRoutineWorkout(detailExercise)
            },
            onDismiss = { exerciseShowingDetails = null },
            autoplayDemos = autoplayExerciseDemos,
            // No pen while a workout is running: an exercise's fields are not something to change
            // mid set. See the comment on the editor below.
            onEdit = null,
            defaultRestSeconds = exerciseDefaultRests[detailExercise.id] ?: 0,
            onDefaultRestChange = { seconds ->
                viewModel.setExerciseDefaultRest(detailExercise.id, seconds)
            }
        )
    }

    // === EXIT CONFIRMATION DIALOG ===
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Exit Workout?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop this routine workout? Any in-progress sets from this session will be discarded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        viewModel.stopRoutineWorkout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed, contentColor = OnDanger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_exit_routine_button")
                ) {
                    Text(
                        text = "Exit Workout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnDanger
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false },
                    modifier = Modifier.testTag("cancel_exit_routine_button")
                ) {
                    Text(
                        text = "Continue Workout",
                        color = ElectricCyan
                    )
                }
            }
        )
    }

    // === FINISH WORKOUT DIALOG ===
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Complete Workout",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "You ticked $completedSetsCount of $totalSetsCount sets as done in $timerFormatted. Only the ticked sets are saved to your profile and appear on the Progress page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    if (completedSetsCount == 0) {
                        Text(
                            text = "No sets are ticked, so finishing now will not save a workout.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = FireRed
                        )
                    }
                    OutlinedTextField(
                        value = finishNotes,
                        onValueChange = { finishNotes = it },
                        label = { Text("Workout Notes (optional)") },
                        placeholder = { Text("Felt strong, hit target reps...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = MintGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_finish_notes_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        viewModel.finishRoutineWorkout(finishNotes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = OnAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_finish_routine_button")
                ) {
                    Text(
                        text = "Save & Complete",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = OnAccent
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishDialog = false },
                    modifier = Modifier.testTag("cancel_finish_routine_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = TextMuted
                    )
                }
            }
        )
    }
}
