package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavedRoutine
import com.example.data.model.RoutineParser
import com.example.data.model.UnstructuredParseState
import com.example.ui.components.RoutineExerciseListDisplay
import com.example.ui.components.SelectRoutineDayDialog
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FireRed
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnstructuredWorkoutDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToSavedRoutines: () -> Unit,
    onStartWorkoutDirectly: () -> Unit
) {
    val parseState by viewModel.unstructuredParseState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    var routineNameInput by remember { mutableStateOf("") }
    var workoutTextInput by remember { mutableStateOf("") }
    var showDayPickerForRoutine by remember { mutableStateOf<SavedRoutine?>(null) }

    val exampleSnippets = listOf(
        "Bench Press = 8 reps x 4 sets, Nordic Curls = 6 reps x 3 sets",
        "Incline Dumbbell Press 3x10, Pelican Curls 3x8, Dragon Flag 4x6",
        "Weighted Dips 4x6 @ 20kg, Zercher Squat 3x8, L-Sit Hold 3x20s"
    )

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
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                    .testTag("unstructured_dialog_surface"),
                color = DarkSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Program Parser",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextWhite
                                )
                                Text(
                                    text = "Convert unrecognized workout text into a structured routine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_unstructured_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Instruction / Info Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 Enter any workout protocol:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Type or paste your unstructured workout (e.g. \"exercise a = 8 reps x 4 sets\", shorthand, or bulleted notes). If any exercise isn't in your database yet, Gemini AI will automatically generate and register it to your local database!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Quick Example Chips
                        Column {
                            Text(
                                text = "TAP TO TRY EXAMPLES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                exampleSnippets.forEachIndexed { idx, snippet ->
                                    Box(
                                        modifier = Modifier
                                            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                workoutTextInput = snippet
                                                if (routineNameInput.isBlank()) {
                                                    routineNameInput = "Custom Protocol #${idx + 1}"
                                                }
                                                viewModel.resetUnstructuredParseState()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = snippet.take(35) + "...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Routine Name (Optional)
                        OutlinedTextField(
                            value = routineNameInput,
                            onValueChange = { routineNameInput = it },
                            label = { Text("Routine Name (Optional)") },
                            placeholder = { Text("e.g. Upper Body Hypertrophy") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedLabelColor = ElectricCyan,
                                unfocusedLabelColor = TextMuted,
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unstructured_routine_name_input")
                        )

                        // Unstructured Workout Text Field
                        Column {
                            Text(
                                text = "YOUR WORKOUT TEXT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = workoutTextInput,
                                onValueChange = {
                                    workoutTextInput = it
                                    if (parseState is UnstructuredParseState.Error) {
                                        viewModel.resetUnstructuredParseState()
                                    }
                                },
                                placeholder = {
                                    Text(
                                        "e.g.\nexercise a = 8 reps x 4 sets\nNordic Curls = 6 reps x 3 sets\nIncline Dumbbell Press = 10 reps x 3 sets",
                                        color = TextMuted.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                },
                                minLines = 4,
                                maxLines = 8,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unstructured_workout_text_input")
                            )
                        }

                        // Parse Error Banner (when gibberish or invalid)
                        AnimatedVisibility(
                            visible = parseState is UnstructuredParseState.Error,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val errorState = parseState as? UnstructuredParseState.Error
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unstructured_parse_error_banner"),
                                colors = CardDefaults.cardColors(containerColor = FireRed.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FireRed)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = FireRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Parse Error",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = FireRed
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = errorState?.reason ?: "Unrecognized or unreadable text. Please check your workout formatting.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextWhite,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tip: Make sure you include exercise names and sets/reps (e.g. 'Pushups = 15 reps x 3 sets').",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Parse Success Banner & Action Card
                        AnimatedVisibility(
                            visible = parseState is UnstructuredParseState.Success,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val successState = parseState as? UnstructuredParseState.Success
                            if (successState != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unstructured_parse_success_card"),
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MintGreen,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Workout Successfully Parsed!",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MintGreen
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Saved '${successState.routine.name}' with ${successState.exerciseCount} exercises under Saved Routines.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextWhite
                                        )

                                        // If new exercises were added to the database:
                                        if (successState.newExercisesCreated.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = ElectricCyan,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Added ${successState.newExercisesCreated.size} New Exercise(s) to Local Database:",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = ElectricCyan
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    successState.newExercisesCreated.forEach { ex ->
                                                        Text(
                                                            text = "• ${ex.name} (${ex.category} • ${ex.equipment})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextWhite
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "These exercises are now permanently stored in your database and appear in your Log Workout search!",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        val parsedDays = remember(successState.routine.exercisesJson, allExercises) {
                                            RoutineParser.parseDays(successState.routine.exercisesJson, allExercises)
                                        }
                                        var isPreviewExpanded by remember { mutableStateOf(false) }
                                        RoutineExerciseListDisplay(
                                            routine = successState.routine,
                                            allExercises = allExercises,
                                            isExpanded = isPreviewExpanded,
                                            onToggleExpand = { isPreviewExpanded = !isPreviewExpanded }
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Action Buttons: Start Workout directly OR View in Saved Routines
                                        Button(
                                            onClick = {
                                                if (parsedDays.size > 1) {
                                                    showDayPickerForRoutine = successState.routine
                                                } else {
                                                    viewModel.startRoutineWorkout(successState.routine, targetDayNumber = 1)
                                                    onStartWorkoutDirectly()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp)
                                                .testTag("start_workout_from_parser_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = DarkBg,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Start Workout Now",
                                                color = DarkBg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedButton(
                                            onClick = onNavigateToSavedRoutines,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .testTag("view_saved_routines_from_parser_button")
                                        ) {
                                            Text(
                                                text = "Go to Saved Routines Tab",
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                    // Footer Bottom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                viewModel.parseAndImportUnstructuredWorkout(
                                    rawText = workoutTextInput,
                                    customName = routineNameInput
                                )
                            },
                            enabled = workoutTextInput.isNotBlank() && parseState !is UnstructuredParseState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp)
                                .testTag("parse_unstructured_button")
                        ) {
                            if (parseState is UnstructuredParseState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = DarkBg,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Parsing Program...", color = DarkBg, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Parse & Save Routine", color = DarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showDayPickerForRoutine != null) {
            val pickerDays = remember(showDayPickerForRoutine, allExercises) {
                RoutineParser.parseDays(showDayPickerForRoutine!!.exercisesJson, allExercises)
            }
            SelectRoutineDayDialog(
                routineName = showDayPickerForRoutine!!.name,
                days = pickerDays,
                onSelectDay = { day ->
                    val r = showDayPickerForRoutine!!
                    viewModel.startRoutineWorkout(r, targetDayNumber = day.dayNumber)
                    showDayPickerForRoutine = null
                    onStartWorkoutDirectly()
                },
                onDismiss = { showDayPickerForRoutine = null }
            )
        }
    }
}
