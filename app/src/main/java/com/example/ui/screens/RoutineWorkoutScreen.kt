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
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.RestTimerDockedButton
import com.example.ui.components.RestTimerDialog
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
import com.example.ui.components.ExerciseDetailDialog
import com.example.ui.components.ExerciseEditorDialog
import kotlinx.coroutines.flow.map
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun RoutineWorkoutScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val activeWorkout by viewModel.activeRoutineWorkout.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    // Weights live in kilograms; this is the unit the athlete reads and types in
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val unit = weightUnit.suffix
    // Follows the "Autoplay exercise demos" setting, so the animation a routine workout shows behaves
    // like the one in the catalog instead of always looping.
    val autoplayExerciseDemos by viewModel.appSettings
        .map { it.autoplayExerciseDemos }
        .collectAsStateWithLifecycle(initialValue = true)

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

                        // Live Timer Badge
                        Box(
                            modifier = Modifier
                                .background(DarkBg, RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MintGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = timerFormatted,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MintGreen
                                )
                            }
                        }
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

            // === EXERCISE LIST (SHOWS ONLY THE ONES SAVED IN THIS WORKOUT IN EXACT ORDER) ===
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "ROUTINE EXERCISES (${workout.exercises.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                }

                // Coach note for this day: a plain description above the exercises
                if (workout.dayNote.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "COACH'S NOTE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = workout.dayNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextWhite,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                if (workout.exercises.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = "No exercises found in this routine. Please edit the routine to add exercises.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = workout.exercises,
                        key = { _, exercise -> exercise.id }
                    ) { index, exercise ->
                        val exerciseSets = workout.sets.filter { it.exerciseId == exercise.id }
                        val routineItem = workout.routineItems.getOrNull(index)

                        RoutineExerciseExecutionCard(
                            orderIndex = index + 1,
                            exercise = exercise,
                            prescription = routineItem?.prescription ?: "",
                            sets = exerciseSets,
                            unit = weightUnit,
                            onAddSet = { viewModel.addSetToRoutineWorkout(exercise) },
                            onUpdateSet = { setIndexInWorkout, updatedSet ->
                                viewModel.updateRoutineWorkoutSet(setIndexInWorkout, updatedSet)
                            },
                            onDeleteSet = { setIndexInWorkout ->
                                viewModel.deleteRoutineWorkoutSet(setIndexInWorkout)
                            },
                            allWorkoutSets = workout.sets,
                            onShowDetails = { exerciseShowingDetails = exercise }
                        )
                    }
                }
            }
        }

        // === FLOATING BOTTOM ACTION BAR WITH REST TIMER ===
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
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                shadowElevation = 8.dp
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
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("routine_finish_bottom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Finish",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = DarkBg
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
            onEdit = null
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
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_exit_routine_button")
                ) {
                    Text(
                        text = "Exit Workout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_finish_routine_button")
                ) {
                    Text(
                        text = "Save & Complete",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DarkBg
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

@Composable
fun RoutineExerciseExecutionCard(
    orderIndex: Int,
    exercise: Exercise,
    prescription: String,
    sets: List<LoggedSet>,
    unit: WeightUnit,
    onAddSet: () -> Unit,
    onUpdateSet: (Int, LoggedSet) -> Unit,
    onDeleteSet: (Int) -> Unit,
    allWorkoutSets: List<LoggedSet>,
    onShowDetails: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_exercise_card_${exercise.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Exercise Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Order Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(ElectricCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, ElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$orderIndex",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Tapping the name opens the same detail dialog the Exercises tab uses. The
                        // athlete is mid set and cannot remember whether this variation wants a pause
                        // rep or a full one, and leaving the workout to find out loses the timer and
                        // the set numbering. Same component, same layout, one tap.
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onShowDetails() }
                                .padding(vertical = 2.dp)
                                .testTag("routine_exercise_name_${exercise.id}")
                        )
                        Text(
                            text = "${exercise.equipment} • ${exercise.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Type Badge
                val (badgeBg, badgeText, badgeColor) = when (exercise.exerciseType) {
                    ExerciseType.ADDED_WEIGHT -> Triple(CoralOrange.copy(alpha = 0.18f), "Added Wt / Cali", CoralOrange)
                    ExerciseType.PURE_BODYWEIGHT -> Triple(MintGreen.copy(alpha = 0.18f), "Bodyweight", MintGreen)
                    else -> Triple(ElectricCyan.copy(alpha = 0.18f), "Standard Wt", ElectricCyan)
                }

                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor
                    )
                }
            }

            // Target Prescription Chip if present
            if (prescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(DarkBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Target: $prescription",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = GoldenAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SET",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )

                // Cardio logs a duration and a distance where the other types log a load and a rep
                // count. The columns keep their positions so the table stays aligned; only the
                // headers change, which is why a run never asked for "WT 80 / REPS 8".
                val weightLabel = when (exercise.exerciseType) {
                    ExerciseType.ADDED_WEIGHT -> "+WT (${unit.suffix})"
                    ExerciseType.PURE_BODYWEIGHT -> "HOLD (s)"
                    ExerciseType.CARDIO -> "MIN"
                    else -> "WT (${unit.suffix})"
                }
                Text(
                    text = weightLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.Center
                )

                val repsLabel = when (exercise.exerciseType) {
                    ExerciseType.PURE_BODYWEIGHT -> "REPS"
                    ExerciseType.CARDIO -> DistanceFormat.suffixFor(WeightUnit.fromLabel(unit.suffix)).uppercase()
                    else -> "REPS"
                }
                Text(
                    text = repsLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "DONE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextMuted,
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Sets Rows
            sets.forEach { setItem ->
                val globalIndex = allWorkoutSets.indexOfFirst { it.exerciseId == setItem.exerciseId && it.setNumber == setItem.setNumber }
                if (globalIndex != -1) {
                    RoutineSetRow(
                        set = setItem,
                        unit = unit,
                        exerciseType = exercise.exerciseType,
                        onUpdate = { updated -> onUpdateSet(globalIndex, updated) },
                        onDelete = { onDeleteSet(globalIndex) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Add Set Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onAddSet,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_set_button_${exercise.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Set",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineSetRow(
    set: LoggedSet,
    unit: WeightUnit,
    exerciseType: String,
    onUpdate: (LoggedSet) -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = set.isCompleted

    // Left column. It means different things per type: a load for the weight types, a hold in seconds
    // for calisthenics, and minutes for cardio.
    var weightText by remember(set.weight, set.addedWeight, set.holdDurationSeconds, unit, exerciseType) {
        val v = when (exerciseType) {
            ExerciseType.ADDED_WEIGHT ->
                if (set.addedWeight > 0) {
                    WeightConversion.format(set.addedWeight, unit, withUnit = false)
                } else {
                    ""
                }
            ExerciseType.PURE_BODYWEIGHT -> if (set.holdDurationSeconds > 0) set.holdDurationSeconds.toString() else ""
            ExerciseType.CARDIO -> if (set.holdDurationSeconds > 0) (set.holdDurationSeconds / 60).toString() else ""
            else ->
                if (set.weight > 0) {
                    WeightConversion.format(set.weight, unit, withUnit = false)
                } else {
                    ""
                }
        }
        mutableStateOf(v)
    }

    // Right column. For cardio this is the distance, not a rep count.
    var repsText by remember(set.reps, set.distanceKm, unit, exerciseType) {
        mutableStateOf(
            when (exerciseType) {
                ExerciseType.CARDIO -> DistanceFormat.format(set.distanceKm, unit)
                else -> if (set.reps > 0) set.reps.toString() else ""
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCompleted) MintGreen.copy(alpha = 0.08f) else DarkBg,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isCompleted) MintGreen.copy(alpha = 0.4f) else DarkBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set Number
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .background(if (isCompleted) MintGreen.copy(alpha = 0.2f) else DarkSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${set.setNumber}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isCompleted) MintGreen else TextWhite
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Weight Input (or Hold Seconds)
        OutlinedTextField(
            value = weightText,
            onValueChange = { input ->
                weightText = input
                when (exerciseType) {
                    // A hold time is seconds, not a weight
                    ExerciseType.PURE_BODYWEIGHT ->
                        onUpdate(set.copy(holdDurationSeconds = input.toDoubleOrNull()?.toInt() ?: 0))
                    // Cardio records whole minutes, stored back as seconds like any other duration
                    ExerciseType.CARDIO ->
                        onUpdate(set.copy(holdDurationSeconds = (input.toDoubleOrNull()?.toInt() ?: 0) * 60))
                    // The typed value is in the athlete's unit and is stored as kilograms
                    ExerciseType.ADDED_WEIGHT ->
                        onUpdate(set.copy(addedWeight = WeightConversion.parseToKg(input, unit) ?: 0.0))
                    else ->
                        onUpdate(set.copy(weight = WeightConversion.parseToKg(input, unit) ?: 0.0))
                }
            },
            placeholder = {
                Text(
                    text = when (exerciseType) {
                        ExerciseType.ADDED_WEIGHT -> "+0"
                        ExerciseType.PURE_BODYWEIGHT -> "0s"
                        ExerciseType.CARDIO -> "0"
                        else -> "0"
                    },
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (exerciseType == ExerciseType.PURE_BODYWEIGHT) {
                    KeyboardType.Number
                } else {
                    KeyboardType.Decimal
                }
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = DarkBorder,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            modifier = Modifier
                .weight(1.2f)
                .height(48.dp)
                .testTag("set_weight_input_${set.exerciseId}_${set.setNumber}")
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Reps Input, or the distance for a cardio set
        OutlinedTextField(
            value = repsText,
            onValueChange = { input ->
                repsText = input
                if (exerciseType == ExerciseType.CARDIO) {
                    onUpdate(set.copy(distanceKm = DistanceFormat.toKm(input, unit)))
                } else {
                    onUpdate(set.copy(reps = input.toIntOrNull() ?: 0))
                }
            },
            placeholder = { Text("0", color = TextMuted, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (exerciseType == ExerciseType.CARDIO) {
                    KeyboardType.Decimal
                } else {
                    KeyboardType.Number
                }
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = DarkBorder,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            modifier = Modifier
                .weight(1.2f)
                .height(48.dp)
                .testTag("set_reps_input_${set.exerciseId}_${set.setNumber}")
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Done Checkbox and Delete Button
        Row(
            modifier = Modifier.width(72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark button (toggles completed)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCompleted) MintGreen else DarkSurface)
                    .border(1.dp, if (isCompleted) MintGreen else DarkBorder, RoundedCornerShape(8.dp))
                    .clickable { onUpdate(set.copy(isCompleted = !isCompleted)) }
                    .testTag("set_complete_check_${set.exerciseId}_${set.setNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Complete Set",
                    tint = if (isCompleted) DarkBg else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete set button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("delete_set_button_${set.exerciseId}_${set.setNumber}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
